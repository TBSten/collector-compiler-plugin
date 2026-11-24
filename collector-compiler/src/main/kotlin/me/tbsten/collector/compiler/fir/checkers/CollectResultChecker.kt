package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectKind
import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.collectorHints
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.platform.isCommon

/**
 * Checks what depends on the full set of items: the item count of `collectSingle()` / `collectSingleOrNull()`.
 *
 * Platform kind: the full set of items is only known when compiling for a platform, so metadata (common) compilation
 * is skipped.
 */
internal object CollectResultChecker : FirFunctionCallChecker(MppCheckerKind.Platform) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val call = collectCall(expression) ?: return
        val kind = call.function.kind
        if (kind != CollectKind.SINGLE && kind != CollectKind.SINGLE_OR_NULL) return
        val session = context.session
        if (session.moduleData.platform.isCommon()) return
        val collector = call.collector ?: return
        val source = expression.source ?: return
        val items = session.collectorHints.itemsOf(collector.classId)
        when (kind) {
            CollectKind.SINGLE -> if (items.size != 1) reporter.reportOn(source, CollectorErrors.COLLECT_SINGLE_COUNT, describeCount(items))
            else -> if (items.size > 1) reporter.reportOn(source, CollectorErrors.COLLECT_SINGLE_OR_NULL_COUNT, describeCount(items))
        }
    }

    private fun describeCount(items: List<String>): String =
        if (items.isEmpty()) "no item was found" else "${items.size} items were found (${items.joinToString()})"
}
