// Warning cases: compiled on their own, because warnings are not printed when compilation fails.
// Each line marked `// W: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor
import me.tbsten.collector.collectAsList

@Conv val number: Int = 1 // W: ITEM_NOT_CONVERTIBLE

@CollectorFor<Conv>() @CollectorFor<Conv>() object Twice : Collector<Shape> // W: COLLECTOR_FOR_DUPLICATE

// spec 06+: an enum entry has no KClass of its own, and an enum class has no instance
enum class EntryForKClass { @defs.EnumKind ENTRY } // W: ITEM_NOT_CONVERTIBLE

@defs.Palette enum class EnumForInstance : defs.Tinted { ; override val hex get() = "" } // W: ITEM_NOT_CONVERTIBLE

// spec P11: items2 uses @Unseen but could not see this Collector when it was compiled
@CollectorFor<ext.Unseen>() object UnseenCollector : Collector<Any>

fun missed() {
    UnseenCollector.collectAsList() // W: COLLECT_MISSED_ITEMS
}

// :defs and :items1 saw the Collector of @Weighted that :defs declares, but not this one, which came later: their
// items have holders, without a conversion for it
@CollectorFor<defs.Weighted>() object LateWeightedCollector : Collector<Any>

@defs.Weighted("here") val weightedHere = "seen by both Collectors"

fun unseen() {
    LateWeightedCollector.collectAsList() // W: COLLECT_MISSED_ITEMS
    defs.WeightedCollector.collectAsList() // every module saw this one
}
