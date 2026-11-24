package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.annotatesDeclaration
import me.tbsten.collector.compiler.fir.collectorIndex
import me.tbsten.collector.compiler.fir.conversionProblem
import me.tbsten.collector.compiler.fir.itemName
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFileSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId

/**
 * Checks declarations annotated with an annotation some visible Collector collects: where they are declared, that
 * their name is unique, and that at least one Collector can convert them.
 */
internal object CollectedItemChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        val session = context.session
        val index = session.collectorIndex
        val annotations = declaration.annotations.filter { it.annotatesDeclaration() }
            .mapNotNullTo(mutableSetOf()) { it.toAnnotationClassId(session) }
            .filterTo(mutableSetOf()) { it in index.targetAnnotations }
        if (annotations.isEmpty()) return
        val source = declaration.source ?: return

        placementProblem(declaration)?.let {
            reporter.reportOn(source, CollectorErrors.ITEM_NOT_ALLOWED_HERE, it)
            return
        }
        nameClash(declaration, index.targetAnnotations)?.let {
            reporter.reportOn(source, CollectorErrors.ITEM_NAME_CLASH, it)
        }

        val collectors = annotations.flatMap { index.collectorsFor(it) }.distinctBy { it.collector }
        val problems = collectors.map { c ->
            val t = c.elementType
            c to if (t == null) "T is unknown" else conversionProblem(session, declaration, t)
        }
        if (problems.isNotEmpty() && problems.all { it.second != null }) {
            val detail = problems.joinToString("; ") { (c, problem) -> "${c.collector.shortClassName}: $problem" }
            reporter.reportOn(source, CollectorErrors.ITEM_NOT_CONVERTIBLE, detail)
        }
    }

    /** Why [declaration] can not be an item where it is declared, or null when it can. */
    context(context: CheckerContext)
    private fun placementProblem(declaration: FirDeclaration): String? {
        when (declaration) {
            is FirValueParameter -> return "parameters can not be collected"
            is FirTypeAlias -> return "type aliases can not be collected"
            is FirEnumEntry -> return null
            is FirRegularClass, is FirNamedFunction, is FirProperty -> Unit
            else -> return "only classes, objects, properties and functions can be collected"
        }
        val containers = context.containingDeclarations
        if (containers.any { it !is FirFileSymbol && !(it is FirRegularClassSymbol && !it.isLocal) }) {
            return "local declarations can not be collected"
        }
        if (declaration is FirRegularClass) {
            if (declaration.isInner) return "inner classes need an outer instance"
            if (declaration.symbol.isLocal) return "local declarations can not be collected"
            return null
        }
        val owner = containers.lastOrNull() as? FirRegularClassSymbol ?: return null
        if (owner.classKind != ClassKind.OBJECT) return "members of ${owner.classId.shortClassName} need an instance; move it into an object or to the top level"
        return null
    }

    /** The name of [declaration] when another collected declaration in the same container has it too. */
    @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
    context(context: CheckerContext)
    private fun nameClash(declaration: FirDeclaration, targets: Set<ClassId>): String? {
        val name = declaration.itemName() ?: return null
        val siblings = when (val owner = context.containingDeclarations.lastOrNull()) {
            is FirRegularClassSymbol -> owner.fir.declarations
            is FirFileSymbol -> owner.fir.declarations
            else -> return null
        }
        val clashes = siblings.count { other ->
            other.itemName() == name &&
                other.annotations.any { it.annotatesDeclaration() && it.toAnnotationClassId(context.session) in targets }
        }
        return if (clashes > 1) name else null
    }
}
