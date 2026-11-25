// How annotations are written: what the plugin can not follow is an error, not an item that is silently left out.
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor
import me.tbsten.collector.CollectorFor as Register

annotation class Aliased
typealias CollectorForAlias<X> = CollectorFor<X>

@Register<Aliased>() object ImportAliasCollector : Collector<Shape> // E: COLLECTOR_FOR_ALIASED

@CollectorForAlias<Aliased>() object TypeAliasCollector : Collector<Shape> // E: COLLECTOR_FOR_ALIASED

open class Base { annotation class FromSuper }

@CollectorFor<Base.FromSuper>() object FromSuperCollector : Collector<String>

object Sub : Base() {
    // a class nested in a supertype is in scope here, which the plugin does not follow
    @FromSuper val bySimpleName = "by simple name" // E: ANNOTATION_MISREAD
    @Base.FromSuper val qualified = "qualified"
}
