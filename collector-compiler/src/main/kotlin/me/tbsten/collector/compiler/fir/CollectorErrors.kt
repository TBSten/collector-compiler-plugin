package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

object CollectorErrors : KtDiagnosticsContainer() {
    /** `collect*()` was called on something other than a `@CollectorFor` object reference. */
    val RECEIVER_NOT_COLLECTOR_OBJECT by error0<KtElement>()

    /** `collectSingle()` found a number of items other than one (arg: description of the count). */
    val COLLECT_SINGLE_COUNT by error1<KtElement, String>()

    /** `collectSingleOrNull()` found more than one item (arg: description of the count). */
    val COLLECT_SINGLE_OR_NULL_COUNT by error1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = Renderers

    private object Renderers : BaseDiagnosticRendererFactory() {
        override val MAP by KtDiagnosticFactoryToRendererMap("Collector") { map ->
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
