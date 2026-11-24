package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectFunction
import me.tbsten.collector.compiler.fir.CollectorErrors
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallableReferenceAccessChecker
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol

/** `XCollector::collectAsList` can not be rewritten: the call site is what gets replaced. */
internal object CollectReferenceChecker : FirCallableReferenceAccessChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCallableReferenceAccess) {
        val symbol = expression.calleeReference.toResolvedCallableSymbol() ?: return
        CollectFunction.of(symbol.callableId) ?: return
        val source = expression.source ?: return
        reporter.reportOn(source, CollectorErrors.COLLECT_CALLABLE_REFERENCE)
    }
}
