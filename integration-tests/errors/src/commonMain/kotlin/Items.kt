// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

private val any = object : Shape { override val id = "any" }

class Holder {
    @Conv val member: Shape = any // E: ITEM_NOT_ALLOWED_HERE
    @Conv fun memberFunction(name: String): Shape = any // E: ITEM_NOT_ALLOWED_HERE
    @Conv inner class Inner : Shape by any // E: ITEM_NOT_ALLOWED_HERE
}

fun localDeclarations() {
    @Conv val local: Shape = any // E: ITEM_NOT_ALLOWED_HERE
    @Conv class LocalClass : Shape by any // E: ITEM_NOT_ALLOWED_HERE
    println(local)
}

fun parameter(@Conv shape: Shape) = shape // E: ITEM_NOT_ALLOWED_HERE

@Conv fun overloaded(): Shape = any // E: ITEM_NAME_CLASH
@Conv fun overloaded(name: String): Shape = any // E: ITEM_NAME_CLASH

@CollectorFor<Conv>() class NotAnObject : Collector<Shape> // E: COLLECTOR_FOR_NOT_OBJECT

@CollectorFor<Conv>() object NotACollectorObject // E: COLLECTOR_FOR_NOT_COLLECTOR
