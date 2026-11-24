package me.tbsten.collector.compiler.kct

import kotlin.test.Test
import kotlin.test.assertEquals

/** Test plan A (declaration kind × T, successful side) and B (what can't be converted or annotated). */
class ConversionTest {
    private fun defs(t: String, imports: String = "") = "Defs.kt" to """
        package defs
        import me.tbsten.collector.*
        $imports
        annotation class E
        interface Decoder { val name: String }
        @CollectorFor<E>() object C : Collector<$t>
    """

    private fun boxOf(body: String, imports: String = "") = "Box.kt" to """
        import defs.*
        import me.tbsten.collector.*
        $imports
        fun box(): Any? = $body
    """

    /** A2: an object collected as `() -> R` returns the same instance on every call. */
    @Test
    fun a2_objectAsFunctionType() {
        val result = box(
            defs("() -> Decoder"),
            "Items.kt" to """
                package items
                @defs.E object Png : defs.Decoder { override val name = "png" }
            """,
            boxOf("C.collectSingle().let { f -> listOf(f().name, f() === f(), f() === items.Png) }"),
        )
        assertEquals(listOf("png", true, true), result)
    }

    /** A3: interface, abstract class, class and object collected as KClass, in FQN order. */
    @Test
    fun a3_kClassOfAnyKind() {
        val result = box(
            defs("kotlin.reflect.KClass<out Feature>", "interface Feature"),
            "Items.kt" to """
                package items
                import defs.*
                @E interface D : Feature
                @E abstract class B : Feature
                @E object A : Feature
                @E class C : Feature
                @E sealed class S : Feature
            """,
            boxOf("C.collectAsList().map { it.simpleName }"),
        )
        assertEquals(listOf("A", "B", "C", "D", "S"), result)
    }

    /** A4: named and unnamed companion objects; their FQNs are Repo.Factory and Other.Companion. */
    @Test
    fun a4_companionObjects() {
        val result = box(
            defs("Any"),
            "Items.kt" to """
                package items
                import defs.E
                class Repo { @E companion object Factory { override fun toString() = "Repo.Factory" } }
                class Other { @E companion object { override fun toString() = "Other.Companion" } }
            """,
            boxOf("C.collectAsList().map { it.toString() } + (C.collectAsList()[1] === items.Repo.Factory)"),
        )
        assertEquals(listOf("Other.Companion", "Repo.Factory", true), result)
    }

    /** A5 is in integration-tests; A6: a class with arguments is collected as its constructor. */
    @Test
    fun a6_classWithArgumentsAsFactory() {
        val result = box(
            defs("(String) -> Job", "interface Job { fun run(): String }"),
            "Items.kt" to """
                package items
                import defs.*
                @E class SyncJob(val ctx: String) : Job { override fun run() = "sync " + ctx }
            """,
            boxOf("C.collectSingle().let { f -> listOf(f(\"a\").run(), f(\"b\").run(), f(\"a\") !== f(\"a\")) }"),
        )
        assertEquals(listOf("sync a", "sync b", true), result)
    }

    /** A7: the secondary no-argument constructor is used for a plain T; a private constructor is not usable. */
    @Test
    fun a7_constructors() {
        val result = box(
            defs("Any"),
            "Items.kt" to """
                package items
                @defs.E class Two(val x: Int) {
                    constructor() : this(7)
                    override fun toString() = "Two(" + x + ")"
                }
            """,
            boxOf("C.collectAsList().map { it.toString() }"),
        )
        assertEquals(listOf("Two(7)"), result)
        // With T = Any the class is accepted (its KClass is an Any), but the holder then calls the private
        // constructor: FIR and IR pick different conversions.
        val hidden = compile("main", files = arrayOf(
            defs("Any"),
            "Items.kt" to """
                package items
                @defs.E class Hidden private constructor()
            """,
        ))
        knownBug("A7: a class whose only no-argument constructor is private is instantiated through it (invalid IR)") {
            hidden.requireOk()
        }
    }

    /**
     * A3 with T = Any: an interface is only convertible as its KClass (KClass<I> is an Any), so FIR accepts it, but
     * the IR side tries to instantiate it and crashes looking for a constructor. Same root cause as the A7 case.
     */
    @Test
    fun a3_interfaceWithTAny() {
        val compiled = compile("main", files = arrayOf(
            defs("Any"),
            "Items.kt" to """
                package items
                @defs.E interface Plugin
            """,
            boxFile("C.collectAsList().size"),
        ))
        knownBug("A3/A7: with T = Any, FIR accepts a class through its KClass but IR converts by constructor (interface: compiler crash)") {
            assertEquals(1, compiled.box())
        }
    }

    /** A8: a top-level val is its value; a var is read when the items are retrieved. */
    @Test
    fun a8_topLevelValAndVar() {
        val result = box(
            defs("Any"),
            "Items.kt" to """
                package items
                @defs.E val homeModule = "home"
                @defs.E var counter = 1
            """,
            boxOf("""run {
                val before = C.collectAsList()
                items.counter = 2
                listOf(before, C.collectAsList(), C.collectAsSequence().toList())
            }"""),
        )
        assertEquals(listOf(listOf(1, "home"), listOf(2, "home"), listOf(2, "home")), result)
    }

    /** A10: extension functions as an extension function type. */
    @Test
    fun a10_extensionFunctions() {
        val result = box(
            defs("Route.() -> Unit", "class Route { val log = mutableListOf<String>() }"),
            "Items.kt" to """
                package items
                import defs.*
                @E fun Route.users() { log += "users" }
                @E fun Route.admin() { log += "admin" }
            """,
            boxOf("Route().also { r -> C.collectAsList().forEach { it(r) } }.log"),
        )
        assertEquals(listOf("admin", "users"), result)
    }

    /** A12: val and fun members of an object are collected with the object as receiver. */
    @Test
    fun a12_objectMembers() {
        val result = box(
            defs("Any"),
            "Items.kt" to """
                package items
                object Routes {
                    var calls = 0
                    @defs.E val home: String get() = "home" + (++calls)
                }
            """,
            boxOf("C.collectAsList() + C.collectAsList()"),
        )
        assertEquals(listOf("home1", "home2"), result)
        val fns = box(
            defs("Route.() -> String", "class Route(val name: String)"),
            "Items.kt" to """
                package items
                import defs.*
                object Routes { val prefix = "/"; @E fun Route.admin() = prefix + name }
            """,
            boxOf("C.collectAsList().map { it(Route(\"admin\")) }"),
        )
        assertEquals(listOf("/admin"), fns)
    }

    /** A13: a suspend function matches `suspend () -> R` only. */
    @Test
    fun a13_suspendFunctions() {
        val result = box(
            defs("suspend () -> String"),
            "Items.kt" to """
                package items
                @defs.E suspend fun sync(): String = "synced"
            """,
            boxOf(
                """run {
                    var out: Any? = null
                    C.collectSingle().startCoroutine(Continuation(EmptyCoroutineContext) { out = it.getOrThrow() })
                    out
                }""",
                imports = "import kotlin.coroutines.*",
            ),
        )
        assertEquals("synced", result)
        assertDiagnostics(
            defs("() -> String"),
            "Items.kt" to """
                package items
                @defs.E suspend fun sync(): String = "synced" // E|W: ITEM_NOT_CONVERTIBLE
            """,
        )
    }

    /**
     * A14: functions with default arguments, vararg or type parameters only match a function type that is exactly
     * their signature (spec 06+). The implementation uses subtyping (D5) and rejects type parameters (D7).
     */
    @Test
    fun a14_functionShapes() {
        assertDiagnostics(
            defs("() -> Int"),
            "Items.kt" to """
                package items
                @defs.E fun withDefault(x: Int = 1): Int = x // E|W: ITEM_NOT_CONVERTIBLE
                @defs.E fun withVararg(vararg x: Int): Int = x.size // E|W: ITEM_NOT_CONVERTIBLE
                @defs.E fun exact(): Int = 1
            """,
        )
        undecided("D5 (exact function type match)") {
            assertDiagnostics(
                defs("(Int) -> Any"),
                "Items.kt" to """
                    package items
                    @defs.E fun narrower(x: Number): Int = 1 // E|W: ITEM_NOT_CONVERTIBLE
                """,
            )
        }
        undecided("D5 (Unit-returning T accepts any return type)") {
            assertDiagnostics(
                defs("() -> Unit"),
                "Items.kt" to """
                    package items
                    @defs.E fun returnsInt(): Int = 1 // E|W: ITEM_NOT_CONVERTIBLE
                """,
            )
        }
        undecided("D7 (generic functions)") {
            val r = box(
                defs("() -> Any"),
                "Items.kt" to """
                    package items
                    @defs.E fun <T> generic(): List<T> = emptyList()
                """,
                boxOf("C.collectAsList().size"),
            )
            assertEquals(1, r)
        }
    }

    /** A15: enum class as KClass, enum entries like objects, data object and value class as usual (spec 06+). */
    @Test
    fun a15_enumsDataObjectsValueClasses() {
        val result = box(
            defs("Any"),
            "Items.kt" to """
                package items
                import defs.E
                enum class Color { @E RED, GREEN, @E BLUE }
                @E data object Single
                @JvmInline @E value class Id(val v: Int = 3)
            """,
            boxOf("C.collectAsList().map { it.toString() }"),
        )
        assertEquals(listOf("BLUE", "RED", "Id(v=3)", "Single"), result)
        val kclasses = box(
            defs("kotlin.reflect.KClass<*>"),
            "Items.kt" to """
                package items
                @defs.E enum class Color { RED }
            """,
            boxOf("C.collectAsList().map { it.simpleName }"),
        )
        assertEquals(listOf("Color"), kclasses)
        assertDiagnostics(
            defs("kotlin.reflect.KClass<*>"),
            "Items.kt" to """
                package items
                enum class Color { @defs.E RED } // E|W: ITEM_NOT_CONVERTIBLE
            """,
        )
        val asFunction = box(
            defs("() -> Any"),
            "Items.kt" to """
                package items
                enum class Color { @defs.E RED }
            """,
            boxOf("C.collectSingle()() === items.Color.RED"),
        )
        assertEquals(true, asFunction)
    }

    /** A16: only `@E` and `@property:E` annotate the property; `@get:E` and `@field:E` are ignored (spec 06+). */
    @Test
    fun a16_useSiteTargets() {
        val result = box(
            defs("Any"),
            "Items.kt" to """
                package items
                import defs.E
                @E val a = "a"
                @property:E val b = "b"
                @get:E val c = "c"
                @field:E val d = "d"
            """,
            boxOf("C.collectAsList()"),
        )
        assertEquals(listOf("a", "b"), result)
    }

    /** B1: declarations an annotation can't be placed on. */
    @Test
    fun b1_notAllowedHere() {
        assertDiagnostics(
            defs("Any"),
            "Items.kt" to """
                package items
                import defs.E
                class Holder {
                    @E val member = 1 // E: ITEM_NOT_ALLOWED_HERE
                    @E fun memberFun() = 1 // E: ITEM_NOT_ALLOWED_HERE
                    @E inner class Inner // E: ITEM_NOT_ALLOWED_HERE
                }
                fun local() {
                    @E val x = 1 // E: ITEM_NOT_ALLOWED_HERE
                    @E class Local // E: ITEM_NOT_ALLOWED_HERE
                }
                fun param(@E p: Int) = p // E: ITEM_NOT_ALLOWED_HERE
            """,
        )
        // E can't target a typealias (default targets), so the typealias case uses an annotation that can
        assertDiagnostics(
            "Defs.kt" to """
                package defs
                import me.tbsten.collector.*
                @Target(AnnotationTarget.TYPEALIAS, AnnotationTarget.CLASS) annotation class TA
                @CollectorFor<TA>() object C : Collector<Any>
            """,
            "Items.kt" to """
                package items
                @defs.TA typealias Alias = String // E: ITEM_NOT_ALLOWED_HERE
            """,
        )
    }

    /** B2: the "error" cells of the conversion table. */
    @Test
    fun b2_notConvertible() {
        assertDiagnostics(
            defs("Decoder"),
            "Items.kt" to """
                package items
                import defs.*
                @E class WithArgs(override val name: String) : Decoder // E|W: ITEM_NOT_CONVERTIBLE
                @E interface Iface : Decoder // E|W: ITEM_NOT_CONVERTIBLE
                @E fun fn(): Decoder = TODO() // E|W: ITEM_NOT_CONVERTIBLE
                @E abstract class Abstract : Decoder // E|W: ITEM_NOT_CONVERTIBLE
            """,
        )
    }

    /** B3: type arguments and nullability are part of the match. */
    @Test
    fun b3_typeMatchDetails() {
        assertDiagnostics(
            defs("List<String>"),
            "Items.kt" to """
                package items
                @defs.E val ints: List<Int> = listOf(1) // E|W: ITEM_NOT_CONVERTIBLE
                @defs.E val maybe: List<String>? = null // E|W: ITEM_NOT_CONVERTIBLE
                @defs.E val star: List<*> = listOf("a") // E|W: ITEM_NOT_CONVERTIBLE
                @defs.E val ok: List<String> = listOf("a")
            """,
        )
    }

    /** B4: a declaration that no visible Collector's T accepts. Plan: warning; spec P3/P10: error (D1). */
    @Test
    fun b4_noCollectorAccepts() {
        assertDiagnostics(
            defs("Decoder"),
            "Items.kt" to """
                package items
                @defs.E object NotADecoder // E|W: ITEM_NOT_CONVERTIBLE
            """,
        )
        undecided("D1 (not convertible is an error in spec P3/P10, a warning in the design and implementation)") {
            assertDiagnostics(
                defs("Decoder"),
                "Items.kt" to """
                    package items
                    @defs.E object NotADecoder // E: ITEM_NOT_CONVERTIBLE
                """,
            )
        }
    }
}
