package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test plan PBT-6: every declaration kind × T shape × visibility, against the conversion table of the spec (04) as
 * written in the README. The space is small, so it is enumerated instead of sampled.
 */
class ConversionTablePropertyTest {
    private enum class Kind(val prefix: String, val declare: (vis: String, name: String) -> String) {
        OBJECT("O", { v, n -> "${v}object $n : defs.Base { override val label = \"$n\" }" }),
        CLASS("K", { v, n -> "${v}class $n : defs.Base { override val label = \"$n\" }" }),
        CLASS_WITH_ARGS("W", { v, n -> "${v}class $n(val s: String) : defs.Base { override val label = \"$n\" + s }" }),
        INTERFACE("I", { v, n -> "${v}interface $n : defs.Base" }),
        ABSTRACT("A", { v, n -> "${v}abstract class $n : defs.Base" }),
        VAL("V", { v, n -> "${v}val $n: defs.Base = object : defs.Base { override val label = \"$n\" }" }),
        FUN0("F", { v, n -> "${v}fun $n(): defs.Base = object : defs.Base { override val label = \"$n\" }" }),
        FUN1("G", { v, n -> "${v}fun $n(s: String): defs.Base = object : defs.Base { override val label = \"$n\" + s }" }),
    }

    /** T, how box() turns one collected value into a comparable string, and the kinds the table accepts. */
    private data class Shape(val t: String, val show: String, val accepts: Set<Kind>)

    private val shapes = listOf(
        Shape("Base", "it.label", setOf(Kind.OBJECT, Kind.CLASS, Kind.VAL)),
        Shape("() -> Base", "it().label", setOf(Kind.OBJECT, Kind.CLASS, Kind.FUN0)),
        Shape("(String) -> Base", "it(\"!\").label.removeSuffix(\"!\")", setOf(Kind.CLASS_WITH_ARGS, Kind.FUN1)),
        Shape("kotlin.reflect.KClass<out Base>", "it.simpleName!!", setOf(Kind.OBJECT, Kind.CLASS, Kind.CLASS_WITH_ARGS, Kind.INTERFACE, Kind.ABSTRACT)),
    )

    private val visibilities = listOf("" to "pub", "internal " to "int", "private " to "pri")

    @Test
    fun pbt6_conversionTable() {
        val failures = mutableListOf<String>()
        for (shape in shapes) {
            val defs = compile("defs", files = arrayOf("Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class E
                interface Base { val label: String }
                @CollectorFor<E>() object C : Collector<${shape.t}>
            """)).requireOk()
            val declarations = Kind.entries.flatMap { kind -> visibilities.map { (v, tag) -> Triple(kind, v, "${kind.prefix}_$tag") } }
            val source = "package items\n" + declarations.joinToString("\n") { (kind, v, name) -> "@defs.E " + kind.declare(v, name) }
            val items = compile("items", listOf(defs), files = arrayOf("Items.kt" to source)).requireOk()
            val expectedRejected = declarations.mapIndexedNotNull { i, (kind, _, _) -> if (kind in shape.accepts) null else "Items.kt:${i + 2} ITEM_NOT_CONVERTIBLE" }
            val actualRejected = items.diagnostics.map { it.split(" ").let { (at, _, id) -> "$at $id" } }
            if (expectedRejected.sorted() != actualRejected.sorted()) {
                failures += "T = ${shape.t}: not convertible\n  expected ${expectedRejected.sorted()}\n  actual   ${actualRejected.sorted()}"
            }
            val expected = declarations.filter { it.first in shape.accepts }.map { it.third }.sorted()
            val actual = runCatching { box(boxFile("C.collectAsList().map { ${shape.show} }"), deps = listOf(items)) }
            if (actual.getOrNull() != expected) failures += "T = ${shape.t}: collected\n  expected $expected\n  actual   ${actual.getOrElse { it.message?.lines()?.take(3) }}"
        }
        assertEquals("", failures.joinToString("\n"))
    }
}
