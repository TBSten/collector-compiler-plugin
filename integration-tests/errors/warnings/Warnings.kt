// Warning cases: compiled on their own, because warnings are not printed when compilation fails.
// Each line marked `// W: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

@Conv val number: Int = 1 // W: ITEM_NOT_CONVERTIBLE

@CollectorFor<Conv>() @CollectorFor<Conv>() object Twice : Collector<Shape> // W: COLLECTOR_FOR_DUPLICATE
