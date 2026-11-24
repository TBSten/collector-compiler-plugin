package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.lookupTracker
import org.jetbrains.kotlin.fir.recordNameLookup
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/** A declaration of this module annotated with one or more collected annotations. */
internal data class ItemDef(
    val fqName: String,
    val annotations: Set<ClassId>,
    val declaration: FirDeclaration,
    val holder: ClassId = CollectorNames.holder(fqName),
)

/** The result of [ItemScanner.scan]. */
internal class OwnItems(val items: List<ItemDef>) {
    /** Holder object -> the item it stands for. */
    val byHolder: Map<ClassId, ItemDef> = items.associateBy { it.holder }
}

/** The name under which [this] can be an item, or null for declarations that are never items. */
internal fun FirDeclaration.itemName(): String? = when (this) {
    is FirRegularClass -> name.asString()
    is FirNamedFunction -> name.asString()
    is FirProperty -> name.asString()
    is FirEnumEntry -> name.asString()
    else -> null
}

/**
 * Finds the items of this module by walking its source files.
 *
 * The collected annotations are only known once the Collectors are (they come from `@CollectorFor<X>` and from hints
 * on the classpath), so they can not be registered as predicates. The walk runs when the first generated declaration
 * is asked for, before annotations are resolved, so an unresolved annotation is matched through the package and the
 * imports of its file. Meta-annotations (annotation classes that carry a collected annotation) are followed the same
 * way.
 */
@OptIn(DirectDeclarationsAccess::class)
internal class ItemScanner(private val session: FirSession) {
    fun scan(targets: Set<ClassId>): OwnItems {
        val provider = session.firProvider
        val found = mutableListOf<ItemDef>()
        for (pkg in provider.symbolProvider.symbolNamesProvider.getPackageNames().orEmpty()) {
            for (file in provider.getFirFilesByPackage(FqName(pkg))) {
                val candidates = mutableSetOf<String>()
                for (decl in file.declarations) {
                    collectItems(file, decl, file.packageDirective.packageFqName.asString(), containerIsObject = true, targets, found, candidates)
                }
                // A Collector for any of these annotations may appear upstream later: make IC recompile this file then.
                val tracker = session.lookupTracker ?: continue
                for (fq in candidates) {
                    tracker.recordNameLookup(CollectorNames.collectorForHintName(fq), CollectorNames.HINTS_PACKAGE.asString(), null, file.source)
                }
            }
        }
        // Files that feed generated top-level declarations must be recompiled on every IC round,
        // otherwise their holders disappear (the predicate-based provider does this automatically).
        found.forEach { session.lookupTracker?.recordDirtyDeclaration(it.declaration.symbol) }
        // Same FQN twice (overloads) is reported by the checker; keep the first one here.
        val unique = found.distinctBy { it.fqName }.sortedBy { it.fqName }
        // Names differing only in case clash as class files on case-insensitive file systems: number them.
        val result = unique.groupBy { it.holder.shortClassName.asString().lowercase() }.values.flatMap { group ->
            group.mapIndexed { i, item -> if (i == 0) item else item.copy(holder = CollectorNames.holder(item.fqName, i)) }
        }
        debugLog { "own items: ${result.map { it.fqName + "@" + it.annotations + " -> " + it.holder.shortClassName }}" }
        return OwnItems(result)
    }

    private fun collectItems(
        file: FirFile, decl: FirDeclaration, prefix: String, containerIsObject: Boolean, targets: Set<ClassId>,
        out: MutableList<ItemDef>, candidates: MutableSet<String>,
    ) {
        val name = decl.itemName() ?: return
        val fq = if (prefix.isEmpty()) name else "$prefix.$name"
        // An annotation class annotated with X is a meta-annotation (like a Compose multipreview), not an item.
        if (decl is FirRegularClass && decl.classKind == ClassKind.ANNOTATION_CLASS) return
        val annotations = mutableSetOf<ClassId>()
        for (ann in decl.annotations) {
            if (!ann.annotatesDeclaration()) continue
            val names = annotationCandidates(ann, file)
            candidates += names
            val id = resolveAnnotation(ann, file, targets)
            if (id != null && id in targets) {
                annotations += id
                continue
            }
            // @M where annotation class M carries X (directly or through further meta-annotations)
            val reached = names.flatMapTo(mutableSetOf()) { metaAnnotations(it, mutableSetOf()) }
            candidates += reached
            targets.filterTo(annotations) { it.asFqNameString() in reached }
        }
        // Members of a class need an instance and inner classes an outer instance: the checker reports those.
        val placeable = when (decl) {
            is FirRegularClass -> !decl.isInner
            is FirEnumEntry -> true
            else -> containerIsObject
        }
        if (annotations.isNotEmpty() && placeable) out += ItemDef(fq, annotations, decl)
        if (decl is FirRegularClass && !decl.isInner) {
            for (member in decl.declarations) {
                collectItems(file, member, fq, decl.classKind == ClassKind.OBJECT, targets, out, candidates)
            }
        }
    }

    /**
     * FQNs of the annotations on annotation class [fqName], and transitively on the annotation classes among them, or
     * nothing when [fqName] is not an annotation class. Works before annotations are resolved: classes of this module
     * are read from source with their file's imports, classes of dependencies from their metadata.
     */
    @OptIn(SymbolInternals::class)
    private fun metaAnnotations(fqName: String, seen: MutableSet<String>): Set<String> {
        if (!seen.add(fqName) || fqName.startsWith("kotlin.")) return emptySet()
        val reached = mutableSetOf<String>()
        for (id in classIdCandidates(fqName)) {
            val own = session.firProvider.getFirClassifierByFqName(id) as? FirRegularClass
            val names: List<Set<String>> = when {
                own != null -> {
                    if (own.classKind != ClassKind.ANNOTATION_CLASS) continue
                    val file = session.firProvider.getFirClassifierContainerFileIfAny(id) ?: continue
                    own.annotations.map { annotationCandidates(it, file) }
                }
                else -> {
                    val dep = session.dependenciesSymbolProvider.getClassLikeSymbolByClassId(id) as? FirRegularClassSymbol ?: continue
                    if (dep.classKind != ClassKind.ANNOTATION_CLASS) continue
                    dep.fir.annotations.mapNotNull { a -> a.annotationTypeRef.coneTypeOrNull?.classId?.asFqNameString()?.let { setOf(it) } }
                }
            }
            for (group in names) {
                reached += group
                group.forEach { reached += metaAnnotations(it, seen) }
            }
        }
        return reached
    }

    /** The class ids an FQN may stand for: top level, or nested in a class one level up. */
    private fun classIdCandidates(fqName: String): List<ClassId> {
        val fq = FqName(fqName)
        val result = mutableListOf(ClassId.topLevel(fq))
        if (!fq.parent().isRoot) result += ClassId(fq.parent().parent(), FqName(fq.parent().shortName().asString() + "." + fq.shortName()), isLocal = false)
        return result
    }

    /** Resolves an annotation that may still be unresolved at this phase by comparing it against the target set. */
    private fun resolveAnnotation(ann: FirAnnotation, file: FirFile, targets: Set<ClassId>): ClassId? {
        ann.annotationTypeRef.coneTypeOrNull?.classId?.let { return it }
        val candidates = annotationCandidates(ann, file)
        return targets.firstOrNull { it.asSingleFqName().asString() in candidates }
    }

    /** Every FQN the (possibly unresolved) annotation could refer to, judging from the file's package and imports. */
    private fun annotationCandidates(ann: FirAnnotation, file: FirFile): Set<String> {
        ann.annotationTypeRef.coneTypeOrNull?.classId?.let { return setOf(it.asSingleFqName().asString()) }
        val ref = ann.annotationTypeRef as? FirUserTypeRef ?: return emptySet()
        val parts = ref.qualifier.map { it.name.asString() }
        val candidates = mutableSetOf<String>()
        candidates += parts.joinToString(".")
        if (!file.packageDirective.packageFqName.isRoot) candidates += file.packageDirective.packageFqName.asString() + "." + parts.joinToString(".")
        for (imp in file.imports) {
            val fq = imp.importedFqName ?: continue
            if (imp.isAllUnder) {
                candidates += fq.asString() + "." + parts.joinToString(".")
            } else {
                val alias = imp.aliasName ?: fq.shortName()
                if (alias.asString() == parts.first()) candidates += (listOf(fq.asString()) + parts.drop(1)).joinToString(".")
            }
        }
        return candidates
    }
}
