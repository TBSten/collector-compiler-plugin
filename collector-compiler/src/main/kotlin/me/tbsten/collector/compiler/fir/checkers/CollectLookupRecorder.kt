package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.fir.collectorHints
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.lookupTracker
import org.jetbrains.kotlin.fir.recordNameLookup
import org.jetbrains.kotlin.name.CallableId

/**
 * Records incremental-compilation lookups from the calling file to the hints that decide the result of a
 * `collect*()` call (its tag hints, the Collectors' key hints and their item hints), so that a hint added, removed
 * or changed anywhere recompiles the call site. Reports nothing.
 */
internal object CollectLookupRecorder : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val call = collectCall(expression) ?: return
        val session = context.session
        val tracker = session.lookupTracker ?: return
        val fileSource = context.containingFileSymbol?.source
        val pkg = CollectorNames.HINTS_PACKAGE.asString()
        fun lookup(hint: CallableId) = tracker.recordNameLookup(hint.callableName, pkg, expression.source, fileSource)

        call.tag?.let { lookup(CollectorNames.collectorTagHint(it)) }
        for (collector in call.collectors) {
            lookup(CollectorNames.collectorKeyHint(collector.classId))
            for (x in session.collectorHints.annotationsOf(collector.classId)) lookup(CollectorNames.itemHint(x))
        }
    }
}
