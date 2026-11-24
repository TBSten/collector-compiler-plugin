package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.ItemIdentity
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.lookupTracker
import org.jetbrains.kotlin.fir.recordNameLookup
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/** A declaration of this module annotated with one or more collected annotations. */
internal data class ItemDef(
    val identity: ItemIdentity,
    val annotations: Set<ClassId>,
    val declaration: FirDeclaration,
    val holder: ClassId = CollectorNames.holder(identity),
)

/** The result of [ItemScanner.scan] for the module [module]. */
internal class OwnItems(
    private val module: String,
    val items: List<ItemDef>,
    /**
     * spec P11: annotation X -> the first (by FQN) declaration of this module annotated with it although no Collector
     * for X is visible. The marker of `collectorMissed_<X>` names that declaration.
     */
    val missed: Map<ClassId, String>,
) {
    fun missedMarker(annotation: ClassId): ClassId = CollectorNames.missedMarker(annotation, module)

    /** Holder object -> the item it stands for. */
    val byHolder: Map<ClassId, ItemDef> = items.associateBy { it.holder }

    /** The marker interfaces of the `collectorMissed_` hints -> the declaration each one names. */
    val missedMarkers: Map<ClassId, String> = missed.mapKeys { (annotation, _) -> missedMarker(annotation) }
}

/** The name under which [this] can be an item, or null for declarations that are never items. */
internal fun FirDeclaration.itemName(): String? = when (this) {
    is FirRegularClass -> name.asString()
    is FirNamedFunction -> name.asString()
    is FirProperty -> name.asString()
    is FirEnumEntry -> name.asString()
    else -> null
}

/** What kind of item [this] can be, or null for declarations that are never items. */
private fun FirDeclaration.itemKind(): ItemIdentity.Kind? = when (this) {
    is FirRegularClass -> ItemIdentity.Kind.CLASS
    is FirNamedFunction -> ItemIdentity.Kind.FUNCTION
    is FirProperty -> ItemIdentity.Kind.PROPERTY
    is FirEnumEntry -> ItemIdentity.Kind.ENUM_ENTRY
    else -> null
}

/**
 * Finds the items of this module by walking its source files.
 *
 * The collected annotations are only known once the Collectors are (they come from `@CollectorFor<X>` and from hints
 * on the classpath), so they can not be registered as predicates. The walk runs when the first generated declaration
 * is asked for, before annotations are resolved, so [AnnotationResolver] tells which class each one names.
 * Meta-annotations (annotation classes that carry a collected annotation) are followed the same way.
 */
@OptIn(DirectDeclarationsAccess::class)
internal class ItemScanner(private val session: FirSession, private val module: String, private val collectors: CollectorIndex) {
    private val resolver get() = collectors.annotationResolver

    /**
     * [inherited] are the items of the source sets this one depends on (commonMain for jvmMain): they are compiled
     * together with it, so what they already declare must not be declared again.
     */
    fun scan(inherited: List<OwnItems>): OwnItems {
        val provider = session.firProvider
        val found = mutableListOf<ItemDef>()
        val missed = mutableMapOf<ClassId, MutableList<String>>()
        for (pkg in provider.symbolProvider.symbolNamesProvider.getPackageNames().orEmpty()) {
            for (file in provider.getFirFilesByPackage(FqName(pkg))) {
                val used = mutableSetOf<ClassId>()
                for (decl in file.declarations) {
                    collectItems(file, decl, containers = emptyList(), found, used, missed)
                }
                // A Collector for any of these annotations may appear upstream later: make IC recompile this file then.
                val tracker = session.lookupTracker ?: continue
                for (id in used) {
                    tracker.recordNameLookup(CollectorNames.collectorForHintName(id), CollectorNames.HINTS_PACKAGE.asString(), null, file.source)
                }
            }
        }
        // Files that feed generated top-level declarations must be recompiled on every IC round,
        // otherwise their holders disappear (the predicate-based provider does this automatically).
        found.forEach { session.lookupTracker?.recordDirtyDeclaration(it.declaration.symbol) }

        val inheritedItems = inherited.flatMap { it.items }
        // An expect declaration and its actual are one item (spec 06+): the holder of the expect declaration stands
        // for both, so an annotated actual gets none of its own.
        val expected = (inheritedItems + found).filter { it.declaration.isExpect }.mapTo(mutableSetOf()) { it.identity.kind to it.identity.fqName }
        val own = found.filterNot { it.declaration.isActual && (it.identity.kind to it.identity.fqName) in expected }
        // The same declaration twice (overloads) is reported by the checker; keep the first one here.
        val unique = own.distinctBy { it.identity.path }.sortedBy { it.identity }
        // Names differing only in case clash as class files on case-insensitive file systems: number them.
        val taken = inheritedItems.mapTo(mutableSetOf()) { it.holder.caseInsensitiveName() }
        val result = unique.map { item ->
            val number = generateSequence(0) { it + 1 }.first { taken.add(CollectorNames.holder(item.identity, it).caseInsensitiveName()) }
            if (number == 0) item else item.copy(holder = CollectorNames.holder(item.identity, number))
        }
        debugLog { "own items: ${result.map { it.identity.fqName + "@" + it.annotations + " -> " + it.holder.shortClassName }}" }
        // one marker per annotation and module, whichever source set misses it first
        val inheritedMissed = inherited.flatMapTo(mutableSetOf()) { it.missed.keys }
        return OwnItems(module, result, missed.filterKeys { it !in inheritedMissed }.mapValues { (_, fqNames) -> fqNames.min() })
    }

    private fun ClassId.caseInsensitiveName(): String = shortClassName.asString().lowercase()

    private val FirDeclaration.isExpect: Boolean get() = (this as? FirMemberDeclaration)?.status?.isExpect == true
    private val FirDeclaration.isActual: Boolean get() = (this as? FirMemberDeclaration)?.status?.isActual == true

    /**
     * [containers] are the classes around [decl], outermost first. [used] gets every annotation class met on the way,
     * collected or not.
     */
    private fun collectItems(
        file: FirFile, decl: FirDeclaration, containers: List<FirRegularClass>, out: MutableList<ItemDef>,
        used: MutableSet<ClassId>, missed: MutableMap<ClassId, MutableList<String>>,
    ) {
        val kind = decl.itemKind() ?: return
        val name = decl.itemName() ?: return
        val relativeName = (containers.map { it.name.asString() } + name).joinToString(".")
        // the file is part of what a top-level function or property is: on the JVM it is their class
        val fileName = file.name.takeIf { containers.isEmpty() && decl !is FirRegularClass }
        val identity = ItemIdentity(kind, module, file.packageDirective.packageFqName.asString(), fileName, relativeName)
        // An annotation class annotated with X is a meta-annotation (like a Compose multipreview), not an item.
        if (decl is FirRegularClass && decl.classKind == ClassKind.ANNOTATION_CLASS) return
        // Members of a class need an instance and inner classes an outer instance: the checker reports those.
        val placeable = when (decl) {
            is FirRegularClass -> !decl.isInner
            is FirEnumEntry -> true
            else -> containers.lastOrNull()?.let { it.classKind == ClassKind.OBJECT } ?: true
        }
        val annotations = mutableSetOf<ClassId>()
        for (annotation in decl.annotations) {
            if (!annotation.annotatesDeclaration()) continue
            val named = resolver.resolve(annotation, file, containers) ?: continue
            used += named
            for (annotationClass in withMetaAnnotations(named)) {
                used += annotationClass
                val targets = collectors.targetsOf(annotationClass)
                annotations += targets
                // spec P11: remember annotations without a visible Collector, so that call sites downstream can warn
                if (targets.isEmpty() && placeable && !neverCollected(annotationClass) && resolver.isAnnotationClass(annotationClass)) {
                    missed.getOrPut(annotationClass) { mutableListOf() } += identity.fqName
                }
            }
        }
        if (annotations.isNotEmpty() && placeable) out += ItemDef(identity, annotations, decl)
        if (decl is FirRegularClass && !decl.isInner) {
            for (member in decl.declarations) {
                collectItems(file, member, containers + decl, out, used, missed)
            }
        }
    }

    /**
     * The annotation class [named] is, and the annotation classes on it and on those in turn: `@M` where annotation
     * class M carries X counts as X.
     */
    private fun withMetaAnnotations(named: ClassId): Set<ClassId> {
        val reached = linkedSetOf<ClassId>()
        fun visit(id: ClassId) {
            val annotationClass = resolver.expand(id) ?: return
            if (!reached.add(annotationClass) || annotationClass.isNeverMetaAnnotation()) return
            resolver.annotationsOn(annotationClass).forEach(::visit)
        }
        visit(named)
        return reached
    }

    /** Annotations from these packages are never what a Collector collects, so they are not recorded as missed. */
    private fun neverCollected(annotation: ClassId): Boolean {
        val pkg = annotation.packageFqName.asString()
        return NEVER_COLLECTED_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }
    }
}

/** Packages whose annotations are never collected (and appear on almost every declaration), see neverCollected(). */
private val NEVER_COLLECTED_PACKAGES = listOf(
    "kotlin", "java", "javax", "org.jetbrains.annotations", "androidx.compose.runtime", "me.tbsten.collector",
)
