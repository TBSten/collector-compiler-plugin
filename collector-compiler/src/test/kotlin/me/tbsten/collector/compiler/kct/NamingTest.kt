package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test plan G: hint names and clashes (G2 and G7 are in GapsTest). */
class NamingTest {
    private val defs = "Defs.kt" to """
        package defs
        import me.tbsten.collector.*
        annotation class E
        class Route
        @CollectorFor<E>() object Any_ : Collector<Any>
        @CollectorFor<E>() object Fns : Collector<() -> Any>
        @CollectorFor<E>() object Routes : Collector<Route.() -> Any>
    """

    /** G3: overloads and same-named declarations. Plan: all collected separately; impl: ITEM_NAME_CLASH (D7). */
    @Test
    fun g3_overloads() {
        val current = compile("main", files = arrayOf(
            defs,
            "Items.kt" to """
                package items
                import defs.*
                @E fun f() = "f()"
                @E fun f(x: Int) = "f(Int)"
            """,
        ))
        println("G3 now: ${current.diagnostics}")
        undecided("D7 (overloads)") {
            val r = box(
                defs,
                "Items.kt" to """
                    package items
                    import defs.*
                    @E fun f() = "f()"
                    @E fun f(x: Int) = "f(Int)"
                    @E fun Route.g() = "Route.g"
                    @E fun String.g() = "String.g"
                """,
                boxFile("Fns.collectAsList().map { it() } + Routes.collectAsList().map { it(Route()) }"),
            )
            assertEquals(listOf("f()", "Route.g"), r)
        }
    }

    /** G5: long FQNs (deep packages, non-ASCII names that escape to 6 characters each) still fit file name limits. */
    @Test
    fun g5_longNames() {
        val pkg = (1..12).joinToString(".") { "segment$it" }
        val jp = "日本語の名前".repeat(6)
        // Kotlin's own class file for the object is 12 + 108 bytes long; the holder escapes each character to 6.
        val compiled = compile("main", files = arrayOf(
            defs,
            "Items.kt" to """
                package $pkg
                @defs.E object `$jp` { override fun toString() = "jp" }
            """,
            boxFile("Any_.collectAsList().map { it.toString() }"),
        ))
        assertEquals(listOf("jp"), compiled.box())
        val longest = compiled.outputDir.walk().filter { it.isFile }.maxOf { it.name.toByteArray().size }
        assertTrue(longest <= 255, "a class file name is $longest bytes")
    }

    /** G6: names that need escaping compile and are collected. */
    @Test
    fun g6_unusualIdentifiers() {
        val result = box(
            defs,
            "Items.kt" to """
                package `in`.`fun`
                @defs.E object `weird name` { override fun toString() = "weird" }
                @defs.E object `dollar${'$'}sign` { override fun toString() = "dollar" }
                @defs.E object under_score { override fun toString() = "under" }
            """,
            boxFile("Any_.collectAsList().map { it.toString() }"),
        )
        assertEquals(listOf("dollar", "under", "weird"), result)
    }

    /** G9: hints can't be referenced from user code. */
    @Test
    fun g9_hintsAreHidden() {
        val upstream = compile("up", files = arrayOf(defs, "Items.kt" to "package items\n@defs.E object A")).requireOk()
        val hints = upstream.outputDir.resolve("collector/hints").list().orEmpty().sorted()
        assertTrue(hints.isNotEmpty())
        // reference the holder by its real name, whatever the naming scheme is
        val holder = hints.single { it.startsWith("Item_") && it.endsWith(".class") && '$' !in it }.removeSuffix(".class")
        val user = compile("main", listOf(upstream), files = arrayOf("Use.kt" to """
            fun use() = collector.hints.$holder
        """))
        assertTrue(!user.ok && user.messages.contains("DEPRECATION_ERROR"), "the holder $holder could be referenced:\n${user.messages}")
        val hintCall = compile("main", listOf(upstream), files = arrayOf("Use.kt" to """
            fun use() = collector.hints.${hints.first { it.startsWith("CollectorFor_") || it.startsWith("collectorFor_") }.removeSuffix(".class").removeSuffix("Kt")}(TODO(), TODO())
        """))
        assertTrue(!hintCall.ok && hintCall.messages.contains("UNRESOLVED_REFERENCE"), hintCall.messages)
    }
}
