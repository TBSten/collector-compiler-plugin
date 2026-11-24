package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Inputs suspected to break the implementation (test plan 07, step 2). IDs refer to the test plan
 * ("Collector テストケース一覧").
 */
class GapsTest {
    private val defs = """
        package defs
        import me.tbsten.collector.*
        annotation class Entry
        @CollectorFor<Entry>() object EntryCollector : Collector<Any>
        @CollectorFor<Entry>() object FnCollector : Collector<() -> Any>
    """

    /**
     * G2: `C` in package `a.b` and `C` nested in class `a.b` have the same FQN string. Kotlin rejects that in one module
     * (PACKAGE_CONFLICTS_WITH_CLASSIFIER), so they come from two modules.
     */
    @Test
    fun g2_packageAndNestedClassWithTheSameFqName() {
        val d = compile("defs", files = arrayOf("Defs.kt" to defs)).requireOk()
        val pkg = compile("pkg", listOf(d), files = arrayOf("Pkg.kt" to """
            package a.b
            import defs.Entry
            @Entry object C { override fun toString() = "package a.b, object C" }
        """)).requireOk()
        val nested = compile("nested", listOf(d), files = arrayOf("Nested.kt" to """
            package a
            import defs.Entry
            class b { @Entry object C { override fun toString() = "package a, b.C" } }
        """)).requireOk()
        val result = runCatching {
            box(
                "Box.kt" to """
                    import defs.*
                    import me.tbsten.collector.*
                    fun box() = EntryCollector.collectAsList().map { it.toString() }.sorted()
                """,
                deps = listOf(pkg, nested),
            )
        }
        knownBug("G2: holder names come from the FQN string, which loses the package/class boundary") {
            assertEquals(listOf("package a, b.C", "package a.b, object C"), result.getOrThrow())
        }
    }

    /** E2: a callable reference to a collect function can't be replaced and must not compile. */
    @Test
    fun e2_callableReferences() {
        assertDiagnostics(
            "Defs.kt" to defs,
            "Refs.kt" to """
                import defs.*
                import me.tbsten.collector.*
                val a = EntryCollector::collectAsList // E: COLLECT_CALLABLE_REFERENCE
                val b = listOf(EntryCollector).map(Collector<Any>::collectAsList) // E: COLLECT_CALLABLE_REFERENCE
                val c: () -> List<Any> = EntryCollector::collectAsSet.let { { it().toList() } } // E: COLLECT_CALLABLE_REFERENCE
            """,
        )
    }

    /** E3: implicit receivers that still point at the object directly (spec 06+); `it` of let is a variable. */
    @Test
    fun e3_implicitReceivers() {
        val result = box(
            "Defs.kt" to defs,
            "Items.kt" to """
                package items
                @defs.Entry object A { override fun toString() = "A" }
            """,
            "Box.kt" to """
                import defs.*
                import me.tbsten.collector.*
                fun box() = listOf(
                    with(EntryCollector) { collectAsList() },
                    EntryCollector.run { collectAsList() },
                    run { EntryCollector.apply { }.let { 0 } }.let { with(EntryCollector) { collectAsSet().toList() } },
                ).map { l -> l.map { it.toString() } }
            """,
        )
        assertEquals(List(3) { listOf("A") }, result)
        assertDiagnostics(
            "Defs.kt" to defs,
            "Let.kt" to """
                import defs.*
                import me.tbsten.collector.*
                val x = EntryCollector.let { it.collectAsList() } // E: RECEIVER_NOT_COLLECTOR_OBJECT
                val y = EntryCollector.also { it.collectAsList() } // E: RECEIVER_NOT_COLLECTOR_OBJECT
            """,
        )
    }

    /** G7: the different ways of writing the annotation reference select exactly the right X. */
    @Test
    fun g7_annotationReferences() {
        val upstream = compile("ann", files = arrayOf(
            "Ann.kt" to """
                package p
                import me.tbsten.collector.*
                annotation class Entry
                @CollectorFor<Entry>() object PCollector : Collector<Any>
            """,
            "Other.kt" to """
                package q
                annotation class Entry
            """,
        )).requireOk()
        val result = box(
            "Alias.kt" to """
                package u1
                import p.Entry as E
                @E object ByAlias
            """,
            "Star.kt" to """
                package u2
                import p.*
                @Entry object ByStar
            """,
            "Fq.kt" to """
                package u3
                @p.Entry object ByFqName
            """,
            "OtherStar.kt" to """
                package u4
                import q.*
                @Entry object NotCollectedQ
            """,
            "Explicit.kt" to """
                package u5
                import p.*
                import q.Entry
                @Entry object NotCollectedExplicitQ
            """,
            "SamePackage.kt" to """
                package u6
                import p.*
                annotation class Entry
                @Entry object NotCollectedSamePackage
            """,
            "Box.kt" to """
                import me.tbsten.collector.*
                fun box() = p.PCollector.collectAsList().map { it::class.simpleName }
            """,
            deps = listOf(upstream),
        )
        knownBug("G7: annotation references are matched by guessing FQNs from imports, so an explicit import or a same-package class loses to a star import") {
            assertEquals(listOf("ByAlias", "ByStar", "ByFqName"), result)
        }
    }

    /** C7: an annotation from kotlin.* (default import) can be X. */
    @Test
    fun c7_defaultImportedAnnotation() {
        val result = box(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                @CollectorFor<Deprecated>() object DeprecatedCollector : Collector<Any>
            """,
            "Items.kt" to """
                package items
                @Deprecated("x") object Old { override fun toString() = "Old" }
            """,
            "Box.kt" to """
                @file:Suppress("DEPRECATION")
                import me.tbsten.collector.*
                fun box() = defs.DeprecatedCollector.collectAsList().map { it.toString() }
            """,
        )
        assertEquals(listOf("Old"), result)
    }

    /** N1: thousands of items must not overflow the 64 KB method limit of the JVM. */
    @Test
    fun n1_manyItems() {
        val n = 6000
        val items = (0 until n).joinToString("\n") { "@defs.Entry object O%05d".format(it) }
        val result = runCatching {
            box(
                "Defs.kt" to defs,
                "Items.kt" to "package items\n$items",
                "Box.kt" to """
                    import me.tbsten.collector.*
                    fun box() = listOf(
                        defs.EntryCollector.collectAsList().size,
                        defs.EntryCollector.collectAsSequence().count(),
                        defs.EntryCollector.collectAsSet().size,
                    )
                """,
            )
        }
        knownBug("N1: every item is passed to one listOf(...) call in the calling method") {
            assertEquals(listOf(n, n, n), result.getOrThrow())
        }
    }
}
