package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.collectorForTagArgument
import me.tbsten.collector.compiler.fir.collectorForTarget
import me.tbsten.collector.compiler.fir.collectorForTargetIsUnresolved
import me.tbsten.collector.compiler.fir.collectorIndex
import me.tbsten.collector.compiler.fir.stringLiteral
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId

/** X of a resolved `@CollectorFor<X>`, type aliases expanded. */
private fun FirAnnotation.resolvedTarget(session: FirSession): ClassId? =
    annotationTypeRef.coneTypeOrNull?.typeArguments?.firstOrNull()?.type?.fullyExpandedType(session)?.classId

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
        // An X that does not resolve is the compiler's error to report; nothing below can be told about it.
        if (annotations.any { it.collectorForTargetIsUnresolved() }) return
        for (annotation in annotations) {
            val x = annotation.resolvedTarget(context.session) ?: continue
            val xClass = context.session.symbolProvider.getClassLikeSymbolByClassId(x) as? FirRegularClassSymbol
            val problem = when {
                xClass?.classKind != ClassKind.ANNOTATION_CLASS -> CollectorErrors.COLLECTOR_FOR_NOT_ANNOTATION_CLASS
                xClass.typeParameterSymbols.isNotEmpty() -> CollectorErrors.COLLECTOR_FOR_GENERIC_ANNOTATION
                else -> continue
            }
            reporter.reportOn(annotation.source ?: source, problem, x.asFqNameString())
        }
        val index = context.session.collectorIndex
        if (!index.isRegistered(declaration.classId)) {
            reporter.reportOn(source, CollectorErrors.COLLECTOR_FOR_ALIASED)
            return
        }
        // X was read before the compiler resolved types (see AnnotationResolver): the two must agree
        val registered = index.ownCollectors.filter { it.collector == declaration.classId }.map { index.annotationClassOf(it.annotation) }
        val misread = annotations.mapNotNull { it.resolvedTarget(context.session) }.filter { it !in registered }
        if (misread.isNotEmpty()) {
            reporter.reportOn(source, CollectorErrors.ANNOTATION_MISREAD, misread.joinToString { it.asFqNameString() })
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
