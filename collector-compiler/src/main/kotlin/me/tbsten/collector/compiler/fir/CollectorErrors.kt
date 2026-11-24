package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.psi.KtElement

object CollectorErrors : KtDiagnosticsContainer() {
    /** `collect*()` was called on something other than a `@CollectorFor` object reference. */
    val RECEIVER_NOT_COLLECTOR_OBJECT by error0<KtElement>()

    /** `collectSingle()` found a number of items other than one (arg: description of the count). */
    val COLLECT_SINGLE_COUNT by error1<KtElement, String>()

    /** `collectSingleOrNull()` found more than one item (arg: description of the count). */
    val COLLECT_SINGLE_OR_NULL_COUNT by error1<KtElement, String>()

    /** `collect*(tag = …)` with a tag that is not a compile-time constant. */
    val COLLECT_TAG_NOT_CONSTANT by error0<KtElement>()

    /** A Collector with the tag whose T is not a subtype of the requested T (arg: those Collectors). */
    val COLLECT_TAG_TYPE_MISMATCH by error1<KtElement, String>()

    /** `collectWithAnnotation<A, T>()` where some collected annotation is not an A (arg: those annotations). */
    val COLLECT_ANNOTATION_TYPE_MISMATCH by error1<KtElement, String>()

    /** `collectAsMap()` where two items have equal annotations (arg: the key and the items). */
    val COLLECT_AS_MAP_DUPLICATE_KEY by error1<KtElement, String>()

    /** A callable reference such as `XCollector::collectAsList`. */
    val COLLECT_CALLABLE_REFERENCE by error0<KtElement>()

    /** Hints written by another version of the plugin (arg: those hints). */
    val HINT_FORMAT_MISMATCH by error1<KtElement, String>()

    /** `tag` of `@CollectorFor` that is not a string literal. */
    val COLLECTOR_FOR_TAG_NOT_LITERAL by error0<KtElement>()

    /** `@CollectorFor` on something other than an object. */
    val COLLECTOR_FOR_NOT_OBJECT by error0<KtElement>()

    /** `@CollectorFor` on an object that does not implement `Collector<T>`. */
    val COLLECTOR_FOR_NOT_COLLECTOR by error0<KtElement>()

    /** `@CollectorFor` written through an import alias or a type alias, which hides the Collector from the plugin. */
    val COLLECTOR_FOR_ALIASED by error0<KtElement>()

    /** An annotation the plugin read differently from the compiler (arg: the annotations concerned). */
    val ANNOTATION_MISREAD by error1<KtElement, String>()

    /**
     * A collected annotation reached through an annotation class of a Java source of this module, which the plugin
     * can not look into (arg: those annotation classes).
     */
    val META_ANNOTATION_IN_JAVA_SOURCE by error1<KtElement, String>()

    /** The same annotation X twice on one Collector (arg: X). */
    val COLLECTOR_FOR_DUPLICATE by warning1<KtElement, String>()

    /** A collected annotation on a declaration that can not be collected (arg: why). */
    val ITEM_NOT_ALLOWED_HERE by error1<KtElement, String>()

    /** Two collected declarations with the same name in one file or one object (arg: the name). */
    val ITEM_NAME_CLASH by error1<KtElement, String>()

    /** A collected annotation on an actual declaration that its expect declaration, the item, does not have (arg: the annotations). */
    val ITEM_ANNOTATION_ONLY_ON_ACTUAL by warning1<KtElement, String>()

    /** A collected declaration that no visible Collector can convert (arg: per-Collector reasons). */
    val ITEM_NOT_CONVERTIBLE by warning1<KtElement, String>()

    /**
     * Declarations with a collected annotation that the Collector of the call does not get, because it was not
     * visible where they are declared (arg: the annotations, with examples).
     */
    val COLLECT_MISSED_ITEMS by warning1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = Renderers

    private object Renderers : BaseDiagnosticRendererFactory() {
        override val MAP by KtDiagnosticFactoryToRendererMap("Collector") { map ->
            map.put(
                COLLECT_MISSED_ITEMS,
                "Some declarations are not collected because the Collector was not visible where they are declared " +
                    "(another module, or a source set the Collector is not part of): {0}. " +
                    "Make the Collector visible there, e.g. declare it next to the annotation.",
                CommonRenderers.STRING,
            )
            map.put(COLLECT_TAG_NOT_CONSTANT, "The tag must be a compile-time constant (a string literal or a const val).")
            map.put(
                COLLECT_TAG_TYPE_MISMATCH,
                "Every Collector with this tag must collect a subtype of the requested type, but these do not: {0}.",
                CommonRenderers.STRING,
            )
            map.put(
                COLLECT_ANNOTATION_TYPE_MISMATCH,
                "The annotation type argument must be a supertype of every collected annotation, but these are not: {0}.",
                CommonRenderers.STRING,
            )
            map.put(COLLECT_AS_MAP_DUPLICATE_KEY, "collectAsMap() would have the same key twice: {0}.", CommonRenderers.STRING)
            map.put(
                COLLECT_CALLABLE_REFERENCE,
                "Collect functions can not be referenced; call them directly (the call is replaced at compile time).",
            )
            map.put(
                HINT_FORMAT_MISMATCH,
                "Found Collector hints written by a different version of the Collector plugin: {0}. This plugin reads hint " +
                    "format ${CollectorNames.HINT_FORMAT}; rebuild those modules with the same Collector version.",
                CommonRenderers.STRING,
            )
            map.put(COLLECTOR_FOR_TAG_NOT_LITERAL, "The tag of @CollectorFor must be a string literal.")
            map.put(COLLECTOR_FOR_NOT_OBJECT, "@CollectorFor can only be used on an object.")
            map.put(COLLECTOR_FOR_NOT_COLLECTOR, "An object annotated with @CollectorFor must implement Collector<T>.")
            map.put(
                COLLECTOR_FOR_ALIASED,
                "@CollectorFor must be written with its own name: through an import alias or a type alias the Collector is not registered.",
            )
            map.put(
                ANNOTATION_MISREAD,
                "Collector reads annotations before the compiler resolves them and could not tell which annotation this is: {0}. " +
                    "Write the annotation with its full name, or import it explicitly.",
                CommonRenderers.STRING,
            )
            map.put(
                META_ANNOTATION_IN_JAVA_SOURCE,
                "{0} is declared in a Java source of this module, where Collector can not read the annotations on it, so this " +
                    "declaration is not collected through it. Declare it in Kotlin or in another module, or annotate this declaration directly.",
                CommonRenderers.STRING,
            )
            map.put(COLLECTOR_FOR_DUPLICATE, "@CollectorFor<{0}> is repeated on the same object.", CommonRenderers.STRING)
            map.put(ITEM_NOT_ALLOWED_HERE, "This declaration can not be collected: {0}.", CommonRenderers.STRING)
            map.put(
                ITEM_NAME_CLASH,
                "Collected declarations of a file or of an object must have distinct names, but {0} is used more than once (overloads are not supported).",
                CommonRenderers.STRING,
            )
            map.put(
                ITEM_ANNOTATION_ONLY_ON_ACTUAL,
                "{0} is not collected here: the item is the expect declaration, which is not annotated with it. Annotate the expect declaration too.",
                CommonRenderers.STRING,
            )
            map.put(
                ITEM_NOT_CONVERTIBLE,
                "No Collector visible here can collect this declaration, so it is skipped: {0}.",
                CommonRenderers.STRING,
            )
            map.put(
                RECEIVER_NOT_COLLECTOR_OBJECT,
                "The receiver of collect functions must be a direct reference to an object annotated with @CollectorFor.",
            )
            map.put(
                COLLECT_SINGLE_COUNT,
                "collectSingle() needs exactly one item, but {0}. Use collectSingleOrNull() or collectAsList() instead.",
                CommonRenderers.STRING,
            )
            map.put(
                COLLECT_SINGLE_OR_NULL_COUNT,
                "collectSingleOrNull() allows at most one item, but {0}. Use collectAsList() instead.",
                CommonRenderers.STRING,
            )
        }
    }
}
