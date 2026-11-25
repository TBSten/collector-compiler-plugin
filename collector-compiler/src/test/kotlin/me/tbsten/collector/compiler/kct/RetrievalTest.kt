package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test plan D: the collect functions. */
class RetrievalTest {
    private val logDefs = "Defs.kt" to """
        package defs
        import me.tbsten.collector.*
        annotation class E
        val log = mutableListOf<String>()
        @CollectorFor<E>() object C : Collector<Any>
    """
    private val loggingItems = "Items.kt" to """
        package items
        import defs.*
        @E object A { init { log += "A" } }
        @E object B { init { log += "B" } }
        @E object D { init { log += "D" } }
    """

    /** D2: a Sequence creates items only when iteration reaches them. */
    @Test
    fun d2_sequenceIsLazy() {
        val result = box(
            logDefs, loggingItems,
            boxFile("""run {
                val s = C.collectAsSequence()
                val before = log.toList()
                s.first()
                listOf(before, log.toList())
            }"""),
        )
        assertEquals(listOf(emptyList(), listOf("A")), result)
    }

    /** D4: lazy versions create nothing before the first access and keep the same value after it. */
    @Test
    fun d4_lazyVersions() {
        val result = box(
            logDefs, loggingItems,
            boxFile("""run {
                val l = C.lazyCollectAsList()
                val before = log.toList()
                val first = l.value
                listOf(before, log.toList(), first === l.value, l.isInitialized())
            }"""),
        )
        assertEquals(listOf(emptyList<String>(), listOf("A", "B", "D"), true, true), result)
    }

    /** D4: lazyCollectAsSequence (its item lambdas once got the wrong parent, found by PBT-5). */
    @Test
    fun d4_lazySequence() {
        val result = box(logDefs, loggingItems, boxFile("C.lazyCollectAsSequence().value.map { it.toString().substringBefore('@') }.toList()"))
        assertEquals(listOf("items.A", "items.B", "items.D"), result)
    }

    /** D5: collectAsSet keeps the first of equal items, in order. */
    @Test
    fun d5_setOfEqualItems() {
        val result = box(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class E
                @CollectorFor<E>() object C : Collector<String>
            """,
            "Items.kt" to """
                package items
                import defs.E
                @E val a = "x"
                @E val b = "y"
                @E val c = "x"
            """,
            boxFile("C.collectAsSet().toList()"),
        )
        assertEquals(listOf("x", "y"), result)
    }

    /** D6: arrays of function types, nullable and primitive T. */
    @Test
    fun d6_arrays() {
        val result = box(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                annotation class E
                @CollectorFor<E>() object Fns : Collector<() -> Int>
                @CollectorFor<E>() object Ints : Collector<Int>
                @CollectorFor<E>() object Nullables : Collector<String?>
            """,
            "Items.kt" to """
                package items
                import defs.E
                @E fun one() = 1
                @E val two = 2
                @E val none: String? = null
            """,
            boxFile("""listOf(
                Fns.collectAsArray().map { it() },
                Ints.collectAsArray().let { it::class.java.componentType.name + it.toList() },
                Nullables.collectAsArray().toList(),
                Ints.lazyCollectAsArray().value.toList(),
            )"""),
        )
        assertEquals(listOf(listOf(1), "java.lang.Integer[2]", listOf(null, "two").filter { it == null }, listOf(2)), result)
    }

    /** D7: collectWithAnnotation / collectAsMap pair each item with its annotation (annotation first). */
    @Test
    fun d7_withAnnotation() {
        val defs = "Defs.kt" to """
            package defs
            import me.tbsten.collector.*
            @Repeatable annotation class Flag(val key: String)
            @CollectorFor<Flag>() object Flags : Collector<Any>
        """
        val files = arrayOf(
            defs,
            "Items.kt" to """
                package items
                import defs.Flag
                @Flag("dark-mode") object DarkMode { override fun toString() = "DarkMode" }
                @Flag("beta") @Flag("beta-2") object Beta { override fun toString() = "Beta" }
            """,
            boxFile("""listOf(
                Flags.collectWithAnnotation<Flag, Any>().map { (a, v) -> a.key + "=" + v },
                Flags.collectAsMap<Flag, Any>().map { (a, v) -> a.key + "=" + v },
                Flags.collectAsList().map { it.toString() },
                Flags.lazyCollectAsMap<Flag, Any>().value.size,
            )"""),
        )
        // the pairs are built with the call site's types (they once failed IR validation)
        val result = compile(Module("main", files.toMap())).box()
        assertEquals(
            listOf(
                listOf("beta=Beta", "beta-2=Beta", "dark-mode=DarkMode"),
                listOf("beta=Beta", "beta-2=Beta", "dark-mode=DarkMode"),
                listOf("Beta", "DarkMode"),
                3,
            ),
            result,
        )
        assertDiagnostics(
            defs,
            "Items.kt" to """
                package items
                import defs.*
                import me.tbsten.collector.*
                @Flag("same") object X
                @Flag("same") object Y
                val m = Flags.collectAsMap<Flag, Any>() // E: COLLECT_AS_MAP_DUPLICATE_KEY
                val ok = Flags.collectWithAnnotation<Flag, Any>()
                val wrong = Flags.collectWithAnnotation<Deprecated, Any>() // E: COLLECT_ANNOTATION_TYPE_MISMATCH
            """,
        )
    }

    /** D8: collecting by tag merges Collectors; an empty tag is empty; the requested T must be a supertype. */
    @Test
    fun d8_tags() {
        val defs = "Defs.kt" to """
            package defs
            import me.tbsten.collector.*
            interface Plugin
            annotation class A
            annotation class B
            @CollectorFor<A>(tag = "plugins") object AC : Collector<Plugin>
            @CollectorFor<B>(tag = "plugins") object BC : Collector<Plugin>
        """
        val items = "Items.kt" to """
            package items
            import defs.*
            @A object PA : Plugin { override fun toString() = "PA" }
            @B object PB : Plugin { override fun toString() = "PB" }
            @A @B object PAB : Plugin { override fun toString() = "PAB" }
        """
        val result = box(
            defs, items,
            boxFile("""listOf(
                collectAsList<Plugin>("plugins").map { it.toString() },
                collectAsList<Any>("plugins").size,
                collectAsList<Plugin>("nothing"),
                collectSingleOrNull<Plugin>("nothing"),
            )"""),
        )
        assertEquals(listOf(listOf("PA", "PAB", "PB"), 3, emptyList<Any>(), null), result)
        assertDiagnostics(
            defs, items,
            "Errors.kt" to """
                import defs.*
                import me.tbsten.collector.*
                fun f(tag: String) {
                    collectAsList<String>("plugins") // E: COLLECT_TAG_TYPE_MISMATCH
                    collectAsList<Plugin>(tag) // E: COLLECT_TAG_NOT_CONSTANT
                    collectSingle<Plugin>("nothing") // E: COLLECT_SINGLE_COUNT
                    collectSingle<Plugin>("plugins") // E: COLLECT_SINGLE_COUNT
                }
            """,
        )
    }

    /** D9: without the plugin the placeholders throw CollectorPluginNotAppliedException. */
    @Test
    fun d9_pluginNotApplied() {
        val compiled = compile("main", plugin = false, files = arrayOf(logDefs, boxFile("C.collectAsList()"))).requireOk()
        val error = runCatching { compiled.box() }.exceptionOrNull()
        assertTrue(error?.javaClass?.simpleName == "CollectorPluginNotAppliedException", "got $error")
    }
}
