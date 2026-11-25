package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.collectorIndex
import me.tbsten.collector.compiler.fir.collectorItemIndex
import me.tbsten.collector.compiler.fir.conversionProblem
import me.tbsten.collector.compiler.fir.describeHint
import me.tbsten.collector.compiler.fir.ItemDef
import me.tbsten.collector.compiler.fir.expandedAnnotationIds
import me.tbsten.collector.compiler.fir.isJavaSourceClass
import me.tbsten.collector.compiler.fir.itemKind
import me.tbsten.collector.compiler.fir.itemName
import me.tbsten.collector.compiler.fir.withMetaAnnotations
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFileSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

/**
 * Checks declarations annotated with an annotation some visible Collector collects: where they are declared, that
 * their name is unique, and that at least one Collector can convert them.
 */
internal object CollectedItemChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        val session = context.session
        val index = session.collectorIndex
        // annotation classes carrying X are meta-annotations (multipreviews), not items
        if (declaration is FirRegularClass && declaration.classKind == ClassKind.ANNOTATION_CLASS) return
        val allAnnotations = expandedAnnotationIds(session, declaration)
        val source = declaration.source ?: return
        // a Collector for one of these annotations was built by another plugin version, so it is not read
        val hintNames = allAnnotations.mapTo(mutableSetOf()) { CollectorNames.collectorForHintName(it) }
        val stale = index.incompatibleCollectorHints.filter { it.name in hintNames }
        if (stale.isNotEmpty()) {
            reporter.reportOn(source, CollectorErrors.HINT_FORMAT_MISMATCH, stale.joinToString { it.describeHint() })
        }
        // the X's of the visible Collectors this declaration is annotated with
        val annotations = allAnnotations.flatMapTo(mutableSetOf()) { index.targetsOf(it) }
        val scanned = session.collectorItemIndex.own.scannedAnnotations(declaration.symbol)
        if (annotations.isEmpty() && scanned.isEmpty()) return

        if (annotations.isNotEmpty()) {
            placementProblem(declaration)?.let {
                reporter.reportOn(source, CollectorErrors.ITEM_NOT_ALLOWED_HERE, it)
                return
            }
        }
        // The items were found before the compiler resolved annotations (see AnnotationResolver). Where the two
        // disagree the hints are wrong, so the declaration must not compile.
        if (scanned != annotations) {
            // the one case known in advance: a meta-annotation whose annotations could not be read that early
            val missed = annotations - scanned
            val unread = allAnnotations.filter { carrier ->
                session.isJavaSourceClass(carrier) &&
                    withMetaAnnotations(session, listOf(carrier)).any { it != carrier && index.targetsOf(it).any(missed::contains) }
            }
            if (unread.isNotEmpty()) {
                reporter.reportOn(source, CollectorErrors.META_ANNOTATION_IN_JAVA_SOURCE, unread.joinToString { "@${it.shortClassName}" })
                return
            }
            val misread = missed + (scanned - annotations)
            reporter.reportOn(source, CollectorErrors.ANNOTATION_MISREAD, misread.joinToString { it.asFqNameString() })
            return
        }
        // An actual declaration belongs to the item of its expect declaration (spec 06+), which is the one checked.
        // Its holder was declared with the common source set, so what only the actual is annotated with is lost.
        expectItemOf(declaration)?.let { expect ->
            val onlyHere = annotations - expect.annotations
            if (onlyHere.isNotEmpty()) {
                reporter.reportOn(source, CollectorErrors.ITEM_ANNOTATION_ONLY_ON_ACTUAL, onlyHere.joinToString { "@${it.shortClassName}" })
            }
            return
        }
        nameClash(declaration)?.let {
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

    /** The item of the expect declaration [declaration] is the actual of, or null. */
    context(context: CheckerContext)
    private fun expectItemOf(declaration: FirDeclaration): ItemDef? {
        if (!declaration.isActual) return null
        val kind = declaration.itemKind() ?: return null
        val fqName = when (val symbol = declaration.symbol) {
            is FirClassLikeSymbol<*> -> symbol.classId.asSingleFqName()
            is FirCallableSymbol<*> -> symbol.callableId?.asSingleFqName()
            else -> null
        } ?: return null
        return context.session.collectorItemIndex.expectItem(kind, fqName.asString())
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

    /**
     * The name of [declaration] when the same file or the same object has another collected declaration of that name:
     * an overload, or a function and a property. Their holders would be one.
     *
     * Declarations of the same name in two files of a package are two items (`private fun Preview()` in every file).
     * A top-level class clashes with nothing: its package can not have another one, and functions and properties
     * are told apart from it by their file.
     */
    @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
    context(context: CheckerContext)
    private fun nameClash(declaration: FirDeclaration): String? {
        val name = declaration.itemName() ?: return null
        val siblings = when (val owner = context.containingDeclarations.lastOrNull()) {
            is FirRegularClassSymbol -> owner.fir.declarations
            is FirFileSymbol -> if (declaration is FirRegularClass) return null else owner.fir.declarations.filter { it !is FirRegularClass }
            else -> return null
        }.filter { it.itemName() == name }
        val index = context.session.collectorIndex
        fun FirDeclaration.collected() = expandedAnnotationIds(context.session, this).any { index.targetsOf(it).isNotEmpty() }
        // an expect declaration and its actual are one item (spec 06+)
        val expectCollected = siblings.any { it.isExpect && it.collected() }
        val clashes = siblings.count { !it.isExpect && (it.collected() || (expectCollected && it.isActual)) } +
            if (expectCollected && siblings.none { it.isActual }) 1 else 0
        return if (clashes > 1) name else null
    }

    private val FirDeclaration.isExpect get() = (this as? FirMemberDeclaration)?.status?.isExpect == true
    private val FirDeclaration.isActual get() = (this as? FirMemberDeclaration)?.status?.isActual == true
}
