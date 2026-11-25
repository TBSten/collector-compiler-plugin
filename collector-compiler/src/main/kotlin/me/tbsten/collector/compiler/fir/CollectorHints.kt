package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.ItemIdentity
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

/** An item as a call site sees it, with the X annotations copied onto its holder (for collectAsMap keys). */
internal class VisibleItem(val identity: ItemIdentity, val annotations: List<FirAnnotation>) {
    val fqName: String get() = identity.fqName
}

/**
 * Reads hints the way a call site sees them: those of this module and of everything on its classpath.
 *
 * Only for checkers. Hint generation must not come here: looking up `collector.hints` through the session's symbol
 * provider asks the generator, which would recurse.
 */
class CollectorHints(session: FirSession) : FirExtensionSessionComponent(session) {
    private fun hints(id: CallableId): List<FirNamedFunctionSymbol> =
        session.symbolProvider.getTopLevelFunctionSymbols(id.packageName, id.callableName)

    private fun FirNamedFunctionSymbol.parameterClassId(index: Int): ClassId? =
        valueParameterSymbols.getOrNull(index)?.resolvedReturnType?.classId

    private val annotationsByCollector: FirCache<ClassId, List<ClassId>, Nothing?> =
        session.firCachesFactory.createCache { collector ->
            hints(CollectorNames.collectorKeyHint(collector)).mapNotNull { it.hintAnnotation() }.distinct()
        }

    /**
     * Annotations X the collector [collector] collects, read from its `collectorKey_` hints: as the module of the
     * Collector knows them, which is what the hints of the items are named after.
     */
    internal fun annotationsOf(collector: ClassId): List<ClassId> = annotationsByCollector.getValue(collector)

    /** The holders of the items annotated with [annotation], read from the `item_` hints. */
    internal fun holdersOf(annotation: ClassId): List<ClassId> =
        hints(CollectorNames.itemHint(annotation)).mapNotNull { it.parameterClassId(0) }.distinct()

    /** The Collectors declared with [tag], read from the `collectorTag_` hints. */
    internal fun collectorsWithTag(tag: String): List<FirRegularClassSymbol> =
        hints(CollectorNames.collectorTagHint(tag))
            .mapNotNull { it.hintCollector(parameter = 0) }
            .distinct()
            .mapNotNull { session.symbolProvider.getClassLikeSymbolByClassId(it) as? FirRegularClassSymbol }

    /** Every hint the result of a call depends on: its tag hints, the Collectors' key hints and their item hints. */
    internal fun hintsOf(tag: String?, collectors: List<ClassId>): List<FirNamedFunctionSymbol> = buildList {
        if (tag != null) addAll(hints(CollectorNames.collectorTagHint(tag)))
        for (collector in collectors) {
            val keys = hints(CollectorNames.collectorKeyHint(collector))
            addAll(keys)
            keys.mapNotNull { it.hintAnnotation() }.distinct().forEach { x -> addAll(hints(CollectorNames.itemHint(x))) }
        }
    }

    /**
     * Declarations annotated with [annotation] in modules that saw no Collector for it (spec P11), read from the
     * `collectorMissed_` hints: one example per module, sorted.
     */
    internal fun missedDeclarationsOf(annotation: ClassId): List<String> =
        hints(CollectorNames.missedHint(annotation))
            .filter { it.hasCurrentHintFormat() }
            .mapNotNull { it.parameterClassId(1) }
            .mapNotNull { session.symbolProvider.getClassLikeSymbolByClassId(it)?.missedDeclaration() }
            .distinct().sorted()

    /**
     * The X's whose items a Collector of [annotation] could have: [annotation], and the other X's of visible
     * Collectors that are the same class here (an `expect` annotation and the class its `actual` is an alias of).
     */
    internal fun sameAnnotationAs(annotation: ClassId): Set<ClassId> {
        val index = session.collectorIndex
        return index.targetsOf(index.annotationClassOf(annotation)) + annotation
    }

    /**
     * Items annotated with [annotation] that [collector] does not get because it was out of sight where they are
     * declared: in a module compiled before the Collector existed, or in a source set the Collector is not part of
     * (an item of commonMain and a Collector of jvmMain). One example per module, with the number of the others, sorted.
     *
     * These items have a holder, made for the Collectors their module did see. Declarations of a module that saw no
     * Collector at all have none; see [missedDeclarationsOf].
     */
    internal fun unseenItemsOf(collector: ClassId, annotation: ClassId): List<String> =
        sameAnnotationAs(annotation).flatMap { holdersOf(it) }.distinct()
            .mapNotNull { session.symbolProvider.getClassLikeSymbolByClassId(it) }
            .filter { holder -> holder.visibleCollectors().let { it.isNotEmpty() && collector !in it } }
            .mapNotNull { it.itemIdentity() }
            .groupBy { it.module }
            .map { (_, items) -> items.min().fqName + if (items.size > 1) " and ${items.size - 1} more" else "" }
            .sorted()

    /**
     * The items the call site sees for [collectors], sorted by FQN, with the same rules as the IR rewrite: one per
     * holder, and only holders that convert for the Collector.
     */
    internal fun itemsOf(collectors: List<ClassId>): List<VisibleItem> {
        val result = linkedMapOf<ClassId, VisibleItem>()
        for (collector in collectors) {
            val asName = CollectorNames.asFunction(collector)
            val xs = annotationsOf(collector)
            val holders = xs.flatMap { holdersOf(it) }.distinct()
            for (holderId in holders) {
                if (holderId in result) continue
                val holder = session.symbolProvider.getClassLikeSymbolByClassId(holderId) as? FirRegularClassSymbol ?: continue
                val identity = holder.itemIdentity() ?: continue
                val scope = session.declaredMemberScope(holder, null)
                if (scope.getFunctions(asName).isEmpty()) continue
                // annotations_<X>() carries copies of the X annotations of the item, and nothing else
                val annotations = xs.flatMap { x ->
                    scope.getFunctions(CollectorNames.annotationsFunction(x)).flatMap { it.resolvedAnnotationsWithArguments }
                }
                result[holderId] = VisibleItem(identity, annotations)
            }
        }
        return result.values.sortedBy { it.identity }
    }
}

internal val FirSession.collectorHints: CollectorHints by FirSession.sessionComponentAccessor()
