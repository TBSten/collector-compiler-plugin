package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals

/** Test plan F: visibility and the bridges for private declarations. */
class VisibilityTest {
    private fun defs() = compile("defs", files = arrayOf("Defs.kt" to """
        package defs
        import me.tbsten.collector.*
        annotation class E
        interface Named { val name: String }
        class Route { val log = mutableListOf<String>() }
        @CollectorFor<E>() object Any_ : Collector<Any>
        @CollectorFor<E>() object Routes : Collector<Route.() -> Unit>
        @CollectorFor<E>() object Fns : Collector<() -> String>
    """)).requireOk()

    /** F2: every kind of private declaration in another module. */
    @Test
    fun f2_privateDeclarations() {
        val d = defs()
        val items = compile("items", listOf(d), files = arrayOf("Items.kt" to """
            package items
            import defs.*
            @E private object PObj { override fun toString() = "PObj" }
            @E private class PCls { override fun toString() = "PCls" }
            @E private val pVal = "pVal"
            @E private fun Route.pExt() { log += "pExt" }
            @E private fun pFun() = "pFun"
        """)).requireOk()
        val result = box(
            boxFile("""listOf(
                Any_.collectAsList().map { it.toString() },
                Route().also { r -> Routes.collectAsList().forEach { it(r) } }.log,
                Fns.collectAsList().map { it() },
            )"""),
            deps = listOf(items),
        )
        assertEquals(listOf(listOf("PCls", "PObj", "pVal"), listOf("pExt"), listOf("pFun")), result)
    }

    /** F3: internal declarations of another module. */
    @Test
    fun f3_internalDeclarations() {
        val d = defs()
        val items = compile("items", listOf(d), files = arrayOf("Items.kt" to """
            package items
            import defs.*
            @E internal object IObj { override fun toString() = "IObj" }
            @E internal val iVal = "iVal"
        """)).requireOk()
        assertEquals(listOf("IObj", "iVal"), box(boxFile("Any_.collectAsList().map { it.toString() }"), deps = listOf(items)))
    }

    /** F4: same-named private functions in two files of one package are separate items, ordered by file name. */
    @Test
    fun f4_samePrivateNameInTwoFiles() {
        val d = defs()
        val items = compile("items", listOf(d), files = arrayOf(
            "A.kt" to "package items\n@defs.E private fun preview() = \"a\"",
            "B.kt" to "package items\n@defs.E private fun preview() = \"b\"",
        )).requireOk()
        assertEquals(listOf("a", "b"), box(boxFile("Fns.collectAsList().map { it() }"), deps = listOf(items)))
    }

    /** F5: an object nested in a private class is collected, or rejected with a clear error. */
    @Test
    fun f5_nestedInPrivateClass() {
        val d = defs()
        val items = compile("items", listOf(d), files = arrayOf("Items.kt" to """
            package items
            private class Outer { @defs.E object In { override fun toString() = "In" } }
        """))
        if (items.ok) {
            assertEquals(listOf("In"), box(boxFile("Any_.collectAsList().map { it.toString() }"), deps = listOf(items)))
        } else {
            assertEquals(1, items.diagnostics.count { " e " in it }, items.messages)
        }
        println("F5: nested in a private class -> ${if (items.ok) "collected" else items.diagnostics}")
    }
}
