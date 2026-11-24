package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test plan E: receivers and call sites (E1-E3 are in integration-tests and GapsTest). */
class ReceiverTest {
    private val defs = "Defs.kt" to """
        package defs
        import me.tbsten.collector.*
        annotation class E
        @CollectorFor<E>() object C : Collector<String>
    """
    private val items = "Items.kt" to """
        package items
        @defs.E val a = "a"
    """

    /** E4: ways of writing the same direct reference. */
    @Test
    fun e4_receiverSpellings() {
        val result = box(
            defs, items,
            boxFile(
                "listOf((defs.C).collectAsList(), EC.collectAsList(), defs.C.collectAsList())",
                imports = "import defs.C as EC",
            ),
        )
        assertEquals(List(3) { listOf("a") }, result)
        val safeCall = runCatching {
            box(defs, items, boxFile("@Suppress(\"UNNECESSARY_SAFE_CALL\") defs.C?.collectAsList()"))
        }
        assertEquals(listOf("a"), safeCall.getOrThrow())
        undecided("E4 typealias receiver (plan: same result; not in the spec)") {
            assertEquals(listOf("a"), box(defs, items, "Alias.kt" to "typealias TC = defs.C", boxFile("TC.collectAsList()")))
        }
    }

    /** E5: a generic wrapper hides the object, so the call inside it is an error. */
    @Test
    fun e5_genericWrapper() {
        assertDiagnostics(
            defs,
            "Wrapper.kt" to """
                import me.tbsten.collector.*
                fun <T> Collector<T>.all() = collectAsList() // E: RECEIVER_NOT_COLLECTOR_OBJECT
            """,
        )
    }

    /** E6: the call is replaced wherever it appears. */
    @Test
    fun e6_callSites() {
        val result = box(
            defs, items,
            "Sites.kt" to """
                import defs.C
                import me.tbsten.collector.*
                val topLevel = C.collectAsList()
                val byLazy by lazy { C.collectAsList() }
                fun defaultArg(x: List<String> = C.collectAsList()) = x
                class WithInit {
                    val v: List<String>
                    init { v = C.collectAsList() }
                }
                fun localFun(): List<String> {
                    fun inner() = C.collectAsList()
                    return inner()
                }
                suspend fun suspending() = C.collectAsList()
                fun inLambda() = listOf(1).flatMap { C.collectAsList() }
                inline fun inlined() = C.collectAsList()
            """,
            boxFile(
                """listOf(
                    topLevel, byLazy, defaultArg(), WithInit().v, localFun(), inLambda(), inlined(),
                    run { var r: Any? = null; ::suspending.startCoroutine(Continuation(EmptyCoroutineContext) { r = it.getOrThrow() }); r },
                )""",
                imports = "import kotlin.coroutines.*",
            ),
        )
        assertEquals(List(8) { listOf("a") }, result)
    }

    /** E8: an item that collects its own Collector while being initialized must not deadlock (behavior documented). */
    @Test
    fun e8_initializationCycle() {
        val compiled = compile("main", files = arrayOf(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class E
                @CollectorFor<E>() object C : Collector<Any?>
            """,
            "Items.kt" to """
                package items
                import me.tbsten.collector.*
                @defs.E object Cyclic { val seen = defs.C.collectAsList() }
            """,
            boxFile("items.Cyclic.seen.map { it?.toString() }"),
        )).requireOk()
        var result: Any? = null
        val t = Thread { result = compiled.box() }.apply { start(); join(10_000) }
        assertTrue(!t.isAlive, "collecting during the item's own initialization did not finish")
        // Documented behavior on the JVM: the list has the one item, the object itself (its INSTANCE is set before
        // the init block runs).
        println("E8: the item sees $result while it is initialized")
        assertEquals(1, (result as List<*>).size)
    }
}
