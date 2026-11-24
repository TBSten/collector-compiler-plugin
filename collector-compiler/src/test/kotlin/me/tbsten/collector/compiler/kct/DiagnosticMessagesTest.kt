package me.tbsten.collector.compiler.kct

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test plan M2: the message of every diagnostic, compared with a golden file. Run with
 * `-Dcollector.updateGolden=true` (./gradlew :collector-compiler:test -Pcollector.updateGolden) to rewrite it.
 */
class DiagnosticMessagesTest {
    private val golden = File("src/test/resources/diagnostic-messages.txt")

    @Test
    fun m2_messages() {
        val defs = "Defs.kt" to """
            package defs
            import me.tbsten.collector.*
            annotation class E
            @Repeatable annotation class Flag(val key: String)
            interface Plugin
            @CollectorFor<E>() object C : Collector<Plugin>
            @CollectorFor<E>() object Many : Collector<Any>
            @CollectorFor<Flag>() object Flags : Collector<Any>
            @CollectorFor<E>(tag = "p") object Tagged : Collector<Plugin>
        """
        val errors = compile("main", files = arrayOf(
            defs,
            "Errors.kt" to """
                import defs.*
                import me.tbsten.collector.*
                annotation class X
                @CollectorFor<X>() class NotObject : Collector<Any>
                @CollectorFor<X>() object NotCollector
                @CollectorFor<X>(tag = "a" + "b") object NotLiteral : Collector<Any>
                class Holder { @E val member = 1 }
                @E fun f() = 1
                @E fun f(x: Int) = x
                @E object P1 : Plugin
                @E object P2 : Plugin
                @Flag("k") object K1
                @Flag("k") object K2
                fun calls(c: Collector<Any>, tag: String) {
                    c.collectAsList()
                    Many.collectSingle()
                    Many.collectSingleOrNull()
                    collectAsList<Plugin>(tag)
                    collectAsList<String>("p")
                    Flags.collectWithAnnotation<Deprecated, Any>()
                    Flags.collectAsMap<Flag, Any>()
                    val r = Many::collectAsList
                }
            """,
        ))
        val warnings = compile("main", files = arrayOf(
            defs,
            "Warnings.kt" to """
                import me.tbsten.collector.*
                annotation class Y
                annotation class Z
                @CollectorFor<Y>() @CollectorFor<Y>() object Twice : Collector<Any>
                @CollectorFor<Z>() object Zs : Collector<defs.Plugin>
                @Z object NotAPlugin
            """,
        ))
        val definitions = compile("main", files = arrayOf(
            "Definitions.kt" to """
                import me.tbsten.collector.*
                annotation class Generic<T>
                @CollectorFor<Generic<String>>() object GenericCollector : Collector<String>
                @CollectorFor<Nothing>() object NothingCollector : Collector<String>
                annotation class Linked(val to: kotlin.reflect.KClass<*> = Any::class)
                @CollectorFor<Linked>() object LinkedCollector : Collector<Any>
                private class HiddenTarget
                @Linked(HiddenTarget::class) annotation class LinkedToHidden
            """,
        ))
        val sourceRetention = compile("main", files = arrayOf(
            "SourceRetention.kt" to """
                import me.tbsten.collector.*
                @Retention(AnnotationRetention.SOURCE) annotation class SourceOnly
                @CollectorFor<SourceOnly>() object SourceOnlyCollector : Collector<Any>
                @SourceOnly annotation class ThroughSourceOnly
            """,
        ))
        val java = compile("main", files = arrayOf(
            defs,
            "defs/JavaMeta.java" to """
                package defs;
                @defs.E
                public @interface JavaMeta {}
            """,
            "JavaItems.kt" to "@defs.JavaMeta object ThroughJava : defs.Plugin",
        ))
        val actual = (errors.messages + warnings.messages + definitions.messages + sourceRetention.messages + java.messages).lines()
            .mapNotNull { Regex("""^([ew]): \S+ \[([A-Z_]+)] (.*)$""").find(it) }
            // severity left out: ITEM_NOT_CONVERTIBLE's is undecided (D1); the other tests check severities
            .map { m -> "${m.groupValues[2]}: ${m.groupValues[3]}" }
            .distinct().sorted().joinToString("\n", postfix = "\n")
        if (System.getProperty("collector.updateGolden") == "true") golden.writeText(actual)
        assertEquals(golden.readText(), actual)
        val ids = actual.lines().filter { it.isNotBlank() }.map { it.substringBefore(":") }.toSet()
        // every diagnostic of the plugin except the ones needing other modules (HINT_FORMAT_MISMATCH, COLLECT_MISSED_ITEMS)
        // or expect/actual (ITEM_ANNOTATION_ONLY_ON_ACTUAL, covered by integration-tests)
        val expectedIds = setOf(
            "RECEIVER_NOT_COLLECTOR_OBJECT", "COLLECT_SINGLE_COUNT", "COLLECT_SINGLE_OR_NULL_COUNT", "COLLECT_TAG_NOT_CONSTANT",
            "COLLECT_TAG_TYPE_MISMATCH", "COLLECT_ANNOTATION_TYPE_MISMATCH", "COLLECT_AS_MAP_DUPLICATE_KEY", "COLLECT_CALLABLE_REFERENCE",
            "COLLECTOR_FOR_TAG_NOT_LITERAL", "COLLECTOR_FOR_NOT_OBJECT", "COLLECTOR_FOR_NOT_COLLECTOR", "COLLECTOR_FOR_DUPLICATE",
            "ITEM_NOT_ALLOWED_HERE", "ITEM_NAME_CLASH", "ITEM_NOT_CONVERTIBLE", "META_ANNOTATION_IN_JAVA_SOURCE",
            "COLLECTOR_FOR_GENERIC_ANNOTATION", "COLLECTOR_FOR_NOT_ANNOTATION_CLASS", "META_ANNOTATION_PRIVATE_ARGUMENT",
            "META_ANNOTATION_SOURCE_RETENTION",
        )
        assertTrue(ids.containsAll(expectedIds), "missing: ${expectedIds - ids}")
    }
}
