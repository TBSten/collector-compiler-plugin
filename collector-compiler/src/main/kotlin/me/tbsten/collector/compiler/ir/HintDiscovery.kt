package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.ItemIdentity
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.ClassId

/** One collected item as seen from a call site. */
internal class CollectedEntry(
    val holder: IrClassSymbol,
    val identity: ItemIdentity,
    /** `as_<enc(C)>()` on the holder. */
    val asFunction: IrSimpleFunctionSymbol,
    /** Annotations X the item is collected through (for collectWithAnnotation / collectAsMap). */
    val annotations: List<ClassId>,
)

/**
 * Phase 2: finds the items of a Collector by reading hints, those of this module and of everything on its classpath.
 *
 * One instance per file. [finder] must be that file's `finderForSource`, which records each lookup for incremental
 * compilation as made from the file.
 */
internal class HintDiscovery(private val finder: DeclarationFinder) {
    private val entriesByCollector = mutableMapOf<ClassId, List<CollectedEntry>>()

    /** The items of [collector], sorted by declaration FQN. Holders without `as_<C>()` are left out. */
    fun entriesFor(collector: ClassId): List<CollectedEntry> = entriesByCollector.getOrPut(collector) { findEntries(collector) }

    private fun findEntries(collector: ClassId): List<CollectedEntry> {
        // collectorKey_<C>(a: X): the annotations C collects
        val xs = finder.findFunctions(CollectorNames.collectorKeyHint(collector))
            .mapNotNull { it.owner.hintAnnotation() }
            .distinct()
        val asName = CollectorNames.asFunction(collector)
        // item_<X>(i: Item_…): the holders of the items annotated with X
        return xs.flatMap { x -> finder.findFunctions(CollectorNames.itemHint(x)).map { x to it } }
            .mapNotNull { (x, hint) -> hint.owner.parameters.firstOrNull()?.type?.classOrNull?.let { x to it } }
            .groupBy({ it.second }, { it.first })
            .mapNotNull { (holder, annotations) ->
                val identity = holder.owner.itemIdentity() ?: return@mapNotNull null
                val fn = holder.owner.functions.firstOrNull { it.name == asName } ?: return@mapNotNull null
                CollectedEntry(holder, identity, fn.symbol, annotations.distinct())
            }
            .sortedBy { it.identity }
    }

    /** The items of every Collector tagged [tag], sorted by FQN; an item reached through several of them appears once. */
    fun entriesForTag(tag: String): List<CollectedEntry> =
        finder.findFunctions(CollectorNames.collectorTagHint(tag))
            .mapNotNull { it.owner.hintCollector() }
            .distinct()
            .sortedBy { it.asFqNameString() }
            .flatMap { entriesFor(it) }
            .distinctBy { it.holder }
            .sortedBy { it.identity }
}
