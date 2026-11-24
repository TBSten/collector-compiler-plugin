package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorNames
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.ClassId

/** One collected item as seen from a call site. */
internal class CollectedEntry(
    val holder: IrClassSymbol,
    val fqName: String,
    /** `as_<enc(C)>()` on the holder. */
    val asFunction: IrSimpleFunctionSymbol,
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
    fun entriesFor(collector: IrClass): List<CollectedEntry> =
        entriesByCollector.getOrPut(collector.classId!!) { findEntries(collector.classId!!) }

    private fun findEntries(collector: ClassId): List<CollectedEntry> {
        // collectorKey_<C>(a: X): the annotations C collects
        val xs = finder.findFunctions(CollectorNames.collectorKeyHint(collector))
            .mapNotNull { it.owner.parameters.firstOrNull()?.type?.classOrNull?.owner?.classId }
            .distinct()
        val asName = CollectorNames.asFunction(collector)
        // item_<X>(i: Item_…): the holders of the items annotated with X
        return xs.flatMap { x -> finder.findFunctions(CollectorNames.itemHint(x)) }
            .mapNotNull { it.owner.parameters.firstOrNull()?.type?.classOrNull }
            .distinct()
            .mapNotNull { holder ->
                val fn = holder.owner.functions.firstOrNull { it.name == asName } ?: return@mapNotNull null
                CollectedEntry(holder, CollectorNames.decodeHolderName(holder.owner.name.asString()), fn.symbol)
            }
            .sortedBy { it.fqName }
    }
}
