package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.collectorIndex
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.getRetention
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.forEachType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid

/**
 * Checks annotation classes annotated with an annotation X some visible Collector collects: meta-annotations, which
 * count as X wherever they are used (a Compose multipreview).
 *
 * The X on a meta-annotation is read where the meta-annotation is used: by the module that uses it, from what the
 * module of the meta-annotation compiled, and its arguments are built again in the file of each item. Both can fail
 * for reasons only the declaration of the meta-annotation shows.
 */
internal object MetaAnnotationChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return
        val session = context.session
        val index = session.collectorIndex
        val visibility = declaration.effectiveVisibility
        for (annotation in declaration.annotations) {
            val x = annotation.toAnnotationClassId(session) ?: continue
            if (index.targetsOf(x).isEmpty()) continue
            val source = annotation.source ?: declaration.source ?: continue
            // An annotation with SOURCE retention is not kept on the compiled annotation class: other modules do not see it.
            val xClass = session.symbolProvider.getClassLikeSymbolByClassId(x) as? FirRegularClassSymbol
            if (visibility.publicApi && xClass?.getRetention(session) == AnnotationRetention.SOURCE) {
                reporter.reportOn(source, CollectorErrors.META_ANNOTATION_SOURCE_RETENTION, x.shortClassName.asString())
            }
            // A private meta-annotation is only used in its own file, which sees what its arguments see.
            if (!visibility.privateApi) {
                val hidden = annotation.referencedClasses(session).filter { it.resolvedStatus.effectiveVisibility.privateApi }
                if (hidden.isNotEmpty()) {
                    val names = hidden.map { it.classId.shortClassName.asString() }.distinct().joinToString()
                    reporter.reportOn(source, CollectorErrors.META_ANNOTATION_PRIVATE_ARGUMENT, x.shortClassName.asString(), names)
                }
            }
        }
    }

    /** The classes the arguments of this annotation refer to: `Foo::class`, an enum entry, an annotation as an argument. */
    private fun FirAnnotation.referencedClasses(session: FirSession): List<FirClassLikeSymbol<*>> {
        val result = mutableListOf<FirClassLikeSymbol<*>>()
        val visitor = object : FirDefaultVisitorVoid() {
            override fun visitElement(element: FirElement) {
                // the type of each part of an argument names every class it refers to: KClass<Foo>, the enum class, …
                (element as? FirExpression)?.resolvedType?.forEachType { type ->
                    (type as? ConeClassLikeType)?.lookupTag?.toSymbol(session)?.let(result::add)
                }
                element.acceptChildren(this)
            }
        }
        argumentMapping.mapping.values.forEach { it.accept(visitor) }
        return result
    }
}
