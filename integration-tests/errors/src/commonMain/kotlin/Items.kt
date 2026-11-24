// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

private val any = object : Shape { override val id = "any" }

// One file or one object can not have two collected declarations of a name. Two files of a package can (see
// edge.perfile in :items1).
@Conv fun overloaded(): Shape = any // E: ITEM_NAME_CLASH
@Conv fun overloaded(name: String): Shape = any // E: ITEM_NAME_CLASH

@Conv fun functionAndProperty(): Shape = any // E: ITEM_NAME_CLASH
@Conv val functionAndProperty: Shape = any // E: ITEM_NAME_CLASH

// a top-level class and a function of its name (a factory function) are two items; in an object they are one name
@Conv class Factory : Shape by any
@Conv fun Factory(name: String): Shape = any

object ClashHolder {
    @Conv class Factory : Shape by any // E: ITEM_NAME_CLASH
    @Conv fun Factory(name: String): Shape = any // E: ITEM_NAME_CLASH
}

@CollectorFor<Conv>() class NotAnObject : Collector<Shape> // E: COLLECTOR_FOR_NOT_OBJECT

@CollectorFor<Conv>() object NotACollectorObject // E: COLLECTOR_FOR_NOT_COLLECTOR

// X of @CollectorFor<X> must be an annotation class, without type parameters (their arguments are not told apart)
annotation class Generic<T>
@CollectorFor<Generic<String>>() object GenericCollector : Collector<String> // E: COLLECTOR_FOR_GENERIC_ANNOTATION
@CollectorFor<Nothing>() object NothingCollector : Collector<String> // E: COLLECTOR_FOR_NOT_ANNOTATION_CLASS
@CollectorFor<Annotation>() object AnyAnnotationCollector : Collector<String> // E: COLLECTOR_FOR_NOT_ANNOTATION_CLASS
