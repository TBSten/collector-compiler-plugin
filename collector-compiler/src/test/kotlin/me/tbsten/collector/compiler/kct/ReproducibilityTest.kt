package me.tbsten.collector.compiler.kct

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Test plan O1: the same input gives the same output. */
class ReproducibilityTest {
    @Test
    fun o1_sameInputSameOutput() {
        val files = arrayOf(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class E
                @CollectorFor<E>() object C : Collector<Any>
            """,
            "Items.kt" to """
                package items
                @defs.E object B
                @defs.E object A
                @defs.E private val p = 1
                @defs.E fun f() = 2
            """,
            boxFile("C.collectAsList().size"),
        )
        fun outputs(dir: File) = dir.walk().filter { it.isFile && it.extension == "class" }
            .associate { it.relativeTo(dir).path to it.readBytes().toList() }
        val first = outputs(compile("main", files = files).requireOk().outputDir)
        val second = outputs(compile("main", files = files).requireOk().outputDir)
        assertEquals(first.keys.sorted(), second.keys.sorted())
        assertEquals(emptyList(), first.keys.filter { first[it] != second[it] }, "class files differ between two compilations")
    }
}
