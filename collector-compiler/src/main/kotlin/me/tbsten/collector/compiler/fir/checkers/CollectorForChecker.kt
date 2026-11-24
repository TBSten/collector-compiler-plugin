package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.collectorForTagArgument
import me.tbsten.collector.compiler.fir.collectorForTarget
import me.tbsten.collector.compiler.fir.stringLiteral
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.types.classId

/** Checks classes annotated with `@CollectorFor`: only objects that implement `Collector<T>` are Collectors. */
internal object CollectorForChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val annotations = declaration.annotations.filter { it.toAnnotationClassId(context.session) == CollectorNames.COLLECTOR_FOR }
        if (annotations.isEmpty()) return
        val source = declaration.source ?: return
        if (declaration.classKind != ClassKind.OBJECT) {
            reporter.reportOn(source, CollectorErrors.COLLECTOR_FOR_NOT_OBJECT)
            return
        }
        if (declaration.symbol.resolvedSuperTypes.none { it.classId == CollectorNames.COLLECTOR }) {
            reporter.reportOn(source, CollectorErrors.COLLECTOR_FOR_NOT_COLLECTOR)
        }
        for (annotation in annotations) {
            val tag = annotation.collectorForTagArgument() ?: continue
            if (tag.stringLiteral() == null) {
                val tagSource = tag.source ?: annotation.source ?: continue
                reporter.reportOn(tagSource, CollectorErrors.COLLECTOR_FOR_TAG_NOT_LITERAL)
            }
        }
        val repeated = annotations.mapNotNull { it.collectorForTarget() }.groupBy { it }.filterValues { it.size > 1 }.keys
        for (x in repeated) reporter.reportOn(source, CollectorErrors.COLLECTOR_FOR_DUPLICATE, x.asFqNameString())
    }
}
