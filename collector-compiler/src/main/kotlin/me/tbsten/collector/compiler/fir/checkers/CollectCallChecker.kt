package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.fir.CollectorErrors
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/** Checks that `collect*()` is called on a `@CollectorFor` object. */
internal object CollectCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val call = collectCall(expression) ?: return
        if (call.collector == null) {
            val source = expression.extensionReceiver?.source ?: expression.source ?: return
            reporter.reportOn(source, CollectorErrors.RECEIVER_NOT_COLLECTOR_OBJECT)
        }
    }
}
