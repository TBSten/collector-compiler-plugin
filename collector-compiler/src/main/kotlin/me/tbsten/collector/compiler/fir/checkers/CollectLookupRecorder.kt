package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.fir.collectorHints
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.lookupTracker
import org.jetbrains.kotlin.fir.recordClassLikeLookup
import org.jetbrains.kotlin.fir.recordClassMemberLookup
import org.jetbrains.kotlin.fir.recordNameLookup
import org.jetbrains.kotlin.fir.symbols.impl.FirFileSymbol
import org.jetbrains.kotlin.name.CallableId

/**
 * Tells incremental compilation what the result of a `collect*()` call depends on, so that a change anywhere
 * recompiles the call site. Reports nothing.
 *
 * - Lookups from the calling file to the hints that decide the result and its warnings: the tag hints, the Collectors
 *   and their key hints, the item and missed hints of their annotations, and each holder with the members the call
 *   reads (`as_<C>()`, which a holder has or not depending on the type of the item, and `annotations_<X>()`).
 * - The calling file itself is recompiled in every round. The hints of this module are generated again in every
 *   round, and one that is no longer generated is not reported as removed, so no lookup would bring the call back.
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

        context.containingDeclarations.firstOrNull { it !is FirFileSymbol }?.let { tracker.recordDirtyDeclaration(it) }
        call.tag?.let { lookup(CollectorNames.collectorTagHint(it)) }
        for (collector in call.collectors) {
            // T of the Collector decides which items convert; a call by tag does not name the Collector itself
            tracker.recordClassLikeLookup(collector.classId, expression.source, fileSource)
            lookup(CollectorNames.collectorKeyHint(collector.classId))
            val asFunction = CollectorNames.asFunction(collector.classId).asString()
            for (x in session.collectorHints.annotationsOf(collector.classId)) {
                lookup(CollectorNames.itemHint(x))
                lookup(CollectorNames.missedHint(x))
                val annotationsFunction = CollectorNames.annotationsFunction(x).asString()
                for (holder in session.collectorHints.holdersOf(x)) {
                    tracker.recordClassLikeLookup(holder, expression.source, fileSource)
                    tracker.recordClassMemberLookup(asFunction, holder, expression.source, fileSource)
                    tracker.recordClassMemberLookup(annotationsFunction, holder, expression.source, fileSource)
                }
                // the holders under another name of the same annotation only matter for the warning about unseen items
                for (other in session.collectorHints.sameAnnotationAs(x) - x) {
                    lookup(CollectorNames.itemHint(other))
                    session.collectorHints.holdersOf(other).forEach { tracker.recordClassLikeLookup(it, expression.source, fileSource) }
                }
            }
        }
    }
}
