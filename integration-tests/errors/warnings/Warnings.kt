// Warning cases: compiled on their own, because warnings are not printed when compilation fails.
// Each line marked `// W: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

@Conv val number: Int = 1 // W: ITEM_NOT_CONVERTIBLE

@CollectorFor<Conv>() @CollectorFor<Conv>() object Twice : Collector<Shape> // W: COLLECTOR_FOR_DUPLICATE

// spec 06+: an enum entry has no KClass of its own, and an enum class has no instance
enum class EntryForKClass { @defs.EnumKind ENTRY } // W: ITEM_NOT_CONVERTIBLE

@defs.Palette enum class EnumForInstance : defs.Tinted { ; override val hex get() = "" } // W: ITEM_NOT_CONVERTIBLE
