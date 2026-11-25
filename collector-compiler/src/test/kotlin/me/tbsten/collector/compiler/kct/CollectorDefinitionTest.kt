package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals

/** Test plan C: how Collectors are declared. */
class CollectorDefinitionTest {
    /** C1: mistakes in placing @CollectorFor. */
    @Test
    fun c1_wrongCollectorFor() {
        assertDiagnostics(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class E
                const val TAG = "t"
                @CollectorFor<E>() class OnAClass : Collector<Any> // E: COLLECTOR_FOR_NOT_OBJECT
                @CollectorFor<E>() object NotACollector // E: COLLECTOR_FOR_NOT_COLLECTOR
                @CollectorFor<E>(tag = "t" + "u") object Concatenated : Collector<Any> // E: COLLECTOR_FOR_TAG_NOT_LITERAL
                @CollectorFor<E>(tag = "t") object Literal : Collector<Any>
            """,
        )
        undecided("D9 (a const val tag on @CollectorFor: plan C1 says it compiles, the implementation requires a literal)") {
            assertDiagnostics(
                "Defs.kt" to """
                    package defs
                    import me.tbsten.collector.*
                    annotation class E
                    const val TAG = "t"
                    @CollectorFor<E>(tag = TAG) object ConstTag : Collector<Any>
                """,
            )
        }
        undecided("D8 (Collector<T> implemented through an intermediate interface)") {
            assertDiagnostics(
                "Defs.kt" to """
                    package defs
                    import me.tbsten.collector.*
                    annotation class E
                    interface MyCollector : Collector<Any>
                    @CollectorFor<E>() object Indirect : MyCollector
                """,
            )
        }
    }

    /** C2: one Collector for two annotations collects their union; a declaration with both appears once. */
    @Test
    fun c2_repeatableCollectorFor() {
        val result = box(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class A
                annotation class B
                @CollectorFor<A>() @CollectorFor<B>() object AB : Collector<Any>
            """,
            "Items.kt" to """
                package items
                import defs.*
                @A val onlyA = "onlyA"
                @B val onlyB = "onlyB"
                @A @B val both = "both"
            """,
            boxFile("AB.collectAsList()"),
        )
        assertEquals(listOf("both", "onlyA", "onlyB"), result)
    }

    /** C3: the same X twice on one Collector is a warning. */
    @Test
    fun c3_duplicateCollectorFor() {
        assertDiagnostics(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class A
                @CollectorFor<A>() @CollectorFor<A>() object Twice : Collector<Any> // W: COLLECTOR_FOR_DUPLICATE
            """,
        )
    }

    /** C4: several Collectors for one X each take what fits their T. */
    @Test
    fun c4_severalCollectorsForOneAnnotation() {
        val result = box(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class Preview
                @CollectorFor<Preview>() object Functions : Collector<() -> String>
                @CollectorFor<Preview>() object Classes : Collector<kotlin.reflect.KClass<*>>
            """,
            "Items.kt" to """
                package items
                import defs.Preview
                @Preview fun home() = "home"
                @Preview class Settings
            """,
            boxFile("listOf(Functions.collectAsList().map { it() }, Classes.collectAsList().map { it.simpleName })"),
        )
        assertEquals(listOf(listOf("home"), listOf("Settings")), result)
    }

    /** C5: a Collector may be a companion object, nested, or internal (used from its own module). */
    @Test
    fun c5_collectorPlacement() {
        val defs = compile("defs", files = arrayOf("Defs.kt" to """
            package defs
            import me.tbsten.collector.*
            annotation class E
            class Host { @CollectorFor<E>() companion object : Collector<Any> }
            object Outer { @CollectorFor<E>() object Nested : Collector<Any> }
            @CollectorFor<E>() internal object Internal : Collector<Any>
            fun fromInternal() = Internal.collectAsList()
        """)).requireOk()
        val items = compile("items", listOf(defs), files = arrayOf("Items.kt" to """
            package items
            @defs.E val x = "x"
        """)).requireOk()
        val result = box(
            boxFile("listOf(Host.Companion.collectAsList(), Outer.Nested.collectAsList(), fromInternal())"),
            deps = listOf(items),
        )
        // fromInternal() was compiled in defs, which does not see items
        assertEquals(listOf(listOf("x"), listOf("x"), emptyList()), result)
        // `Host` alone is how a companion is usually referenced
        val viaClassName = runCatching { box(boxFile("Host.collectAsList()"), deps = listOf(items)) }
        assertEquals(listOf("x"), viaClassName.getOrThrow())
    }

    /** C7 (Java annotation, SOURCE retention, nested annotation class) — the kotlin.* case is in GapsTest. */
    @Test
    fun c7_specialAnnotations() {
        val result = box(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                @Retention(AnnotationRetention.SOURCE) annotation class Src
                object Ns { annotation class Nested }
                @CollectorFor<Src>() object SrcC : Collector<Any>
                @CollectorFor<Ns.Nested>() object NestedC : Collector<Any>
                @CollectorFor<java.lang.FunctionalInterface>() object JavaC : Collector<kotlin.reflect.KClass<*>>
            """,
            "Items.kt" to """
                package items
                import defs.*
                @Src val s = "src"
                @Ns.Nested val n = "nested"
                @java.lang.FunctionalInterface fun interface J { fun f() }
            """,
            boxFile("listOf(SrcC.collectAsList(), NestedC.collectAsList(), JavaC.collectAsList().map { it.simpleName })"),
        )
        assertEquals(listOf(listOf("src"), listOf("nested"), listOf("J")), result)
    }

    /** C8: a Collector that is itself an item collects itself without looping. */
    @Test
    fun c8_collectorCollectsItself() {
        val result = box(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class E
                @E @CollectorFor<E>() object Self : Collector<Any> { override fun toString() = "Self" }
            """,
            boxFile("Self.collectAsList().map { it.toString() }"),
        )
        assertEquals(listOf("Self"), result)
    }
}
