package me.tbsten.collector.compiler.fir

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

    /** `tag` of `@CollectorFor` that is not a string literal. */
    val COLLECTOR_FOR_TAG_NOT_LITERAL by error0<KtElement>()

    /** `@CollectorFor` on something other than an object. */
    val COLLECTOR_FOR_NOT_OBJECT by error0<KtElement>()

    /** `@CollectorFor` on an object that does not implement `Collector<T>`. */
    val COLLECTOR_FOR_NOT_COLLECTOR by error0<KtElement>()

    /** The same annotation X twice on one Collector (arg: X). */
    val COLLECTOR_FOR_DUPLICATE by warning1<KtElement, String>()

    /** A collected annotation on a declaration that can not be collected (arg: why). */
    val ITEM_NOT_ALLOWED_HERE by error1<KtElement, String>()

    /** Two collected declarations with the same fully-qualified name (arg: the name). */
    val ITEM_NAME_CLASH by error1<KtElement, String>()

    /** A collected declaration that no visible Collector can convert (arg: per-Collector reasons). */
    val ITEM_NOT_CONVERTIBLE by warning1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = Renderers

    private object Renderers : BaseDiagnosticRendererFactory() {
        override val MAP by KtDiagnosticFactoryToRendererMap("Collector") { map ->
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
            map.put(COLLECTOR_FOR_TAG_NOT_LITERAL, "The tag of @CollectorFor must be a string literal.")
            map.put(COLLECTOR_FOR_NOT_OBJECT, "@CollectorFor can only be used on an object.")
            map.put(COLLECTOR_FOR_NOT_COLLECTOR, "An object annotated with @CollectorFor must implement Collector<T>.")
            map.put(COLLECTOR_FOR_DUPLICATE, "@CollectorFor<{0}> is repeated on the same object.", CommonRenderers.STRING)
            map.put(ITEM_NOT_ALLOWED_HERE, "This declaration can not be collected: {0}.", CommonRenderers.STRING)
            map.put(
                ITEM_NAME_CLASH,
                "Collected declarations must have distinct names, but {0} is used more than once (overloads are not supported).",
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
