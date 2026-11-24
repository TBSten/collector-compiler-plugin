package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.collectorElementType
import me.tbsten.collector.compiler.fir.collectorHints
import me.tbsten.collector.compiler.fir.collectorIndex
import me.tbsten.collector.compiler.fir.describeHint
import me.tbsten.collector.compiler.fir.hasCurrentHintFormat
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
 * - every hint the result depends on was written with this plugin's hint format;
 * - no declaration is left out because the Collector was out of sight where it is declared (a warning, spec P11);
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
        val stale = session.collectorHints.hintsOf(call.tag, call.collectors.map { it.classId }).filter { !it.hasCurrentHintFormat() }
        if (stale.isNotEmpty()) {
            reporter.reportOn(source, CollectorErrors.HINT_FORMAT_MISMATCH, stale.joinToString { it.describeHint() })
            return
        }
        val hints = session.collectorHints
        val xs = call.collectors.flatMap { hints.annotationsOf(it.classId) }.distinct()
        val missed = xs.mapNotNull { x ->
            val unseen = call.collectors.filter { x in hints.annotationsOf(it.classId) }.flatMap { hints.unseenItemsOf(it.classId, x) }
            val examples = (hints.missedDeclarationsOf(x) + unseen).distinct().sorted()
            if (examples.isEmpty()) null else "@${x.shortClassName} (${examples.joinToString()})"
        }
        if (missed.isNotEmpty()) reporter.reportOn(source, CollectorErrors.COLLECT_MISSED_ITEMS, missed.joinToString("; "))
        if (call.function.kind.withAnnotations) {
            val a = expression.typeArgument(0) ?: return
            val mismatched = xs.filter { x -> !session.collectorIndex.annotationClassOf(x).constructClassLikeType().isSubtypeOf(a, session) }
            if (mismatched.isNotEmpty()) {
                reporter.reportOn(source, CollectorErrors.COLLECT_ANNOTATION_TYPE_MISMATCH, mismatched.joinToString { it.asFqNameString() })
            }
        }
    }

    private fun FirFunctionCall.typeArgument(index: Int): ConeKotlinType? =
        (typeArguments.getOrNull(index) as? FirTypeProjectionWithVariance)?.typeRef?.coneTypeOrNull

    private fun ConeKotlinType.renderShort(): String = toString().substringAfterLast('/')
}
