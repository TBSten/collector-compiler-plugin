package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.collectorElementType
import me.tbsten.collector.compiler.fir.collectorHints
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.isSubtypeOf

/**
 * Checks the receiver, the tag and the type arguments of `collect*()` calls:
 * - the receiver is a direct reference to a `@CollectorFor` object, or the implicit receiver of `with(XCollector) { … }`;
 * - a tag is a compile-time constant, and every Collector with it collects a subtype of the requested T;
 * - for `collectWithAnnotation` / `collectAsMap`, every collected annotation is an A.
 */
internal object CollectCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val call = collectCall(expression) ?: return
        val session = context.session
        val source = expression.source ?: return
        if (!call.receiverIsValid) {
            reporter.reportOn(expression.extensionReceiver?.source ?: source, CollectorErrors.RECEIVER_NOT_COLLECTOR_OBJECT)
            return
        }
        if (call.byTag) {
            if (call.tag == null) {
                reporter.reportOn(expression.argumentList.arguments.firstOrNull()?.source ?: source, CollectorErrors.COLLECT_TAG_NOT_CONSTANT)
                return
            }
            val t = expression.typeArgument(0)
            val mismatched = call.collectors.filter { c -> t != null && c.collectorElementType()?.isSubtypeOf(t, session) != true }
            if (mismatched.isNotEmpty()) {
                val detail = mismatched.joinToString { "${it.classId.shortClassName} (Collector<${it.collectorElementType()?.renderShort()}>)" }
                reporter.reportOn(source, CollectorErrors.COLLECT_TAG_TYPE_MISMATCH, detail)
            }
        }
        if (call.function.kind.withAnnotations) {
            val a = expression.typeArgument(0) ?: return
            val xs = call.collectors.flatMap { session.collectorHints.annotationsOf(it.classId) }.distinct()
            val mismatched = xs.filter { x -> !x.constructClassLikeType().isSubtypeOf(a, session) }
            if (mismatched.isNotEmpty()) {
                reporter.reportOn(source, CollectorErrors.COLLECT_ANNOTATION_TYPE_MISMATCH, mismatched.joinToString { it.asFqNameString() })
            }
        }
    }

    private fun FirFunctionCall.typeArgument(index: Int): ConeKotlinType? =
        (typeArguments.getOrNull(index) as? FirTypeProjectionWithVariance)?.typeRef?.coneTypeOrNull

    private fun ConeKotlinType.renderShort(): String = toString().substringAfterLast('/')
}
