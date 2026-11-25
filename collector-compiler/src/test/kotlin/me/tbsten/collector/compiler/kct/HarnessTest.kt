package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals

class HarnessTest {
    @Test
    fun collectsAcrossModules() {
        val defs = compile("defs", files = arrayOf("Defs.kt" to """
            package defs
            import me.tbsten.collector.*
            annotation class Entry
            interface Greeter { fun greet(): String }
            @CollectorFor<Entry>() object EntryCollector : Collector<Greeter>
        """)).requireOk()
        val items = compile("items", listOf(defs), files = arrayOf("Items.kt" to """
            package items
            import defs.*
            @Entry object B : Greeter { override fun greet() = "b" }
            @Entry object A : Greeter { override fun greet() = "a" }
        """)).requireOk()
        val result = box("Box.kt" to """
            import defs.*
            import me.tbsten.collector.*
            fun box() = EntryCollector.collectAsList().map { it.greet() }
        """, deps = listOf(items))
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun receiverDiagnostic() {
        assertDiagnostics("Errors.kt" to """
            import me.tbsten.collector.*
            annotation class Entry
            @CollectorFor<Entry>() object EntryCollector : Collector<Any>
            fun f(c: Collector<Any>) {
                c.collectAsList() // E: RECEIVER_NOT_COLLECTOR_OBJECT
            }
        """)
    }
}
