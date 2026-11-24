// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.EntryCollector
import defs.Greeter
import defs.NoneCollector
import defs.SingleCollector
import me.tbsten.collector.*

fun receiverIsNotAnObjectReference(c: Collector<Greeter>) {
    c.collectAsList() // E: RECEIVER_NOT_COLLECTOR_OBJECT
    val alias = EntryCollector
    alias.collectAsList() // E: RECEIVER_NOT_COLLECTOR_OBJECT
}

object NotACollector : Collector<Greeter>

fun receiverWithoutCollectorFor() {
    NotACollector.collectAsList() // E: RECEIVER_NOT_COLLECTOR_OBJECT
}

fun singleCounts() {
    EntryCollector.collectSingle() // E: COLLECT_SINGLE_COUNT
    NoneCollector.collectSingle() // E: COLLECT_SINGLE_COUNT
    EntryCollector.lazyCollectSingle() // E: COLLECT_SINGLE_COUNT
    EntryCollector.collectSingleOrNull() // E: COLLECT_SINGLE_OR_NULL_COUNT
    EntryCollector.lazyCollectSingleOrNull() // E: COLLECT_SINGLE_OR_NULL_COUNT
}

fun fine() {
    SingleCollector.collectSingle()
    NoneCollector.collectSingleOrNull()
    SingleCollector.lazyCollectSingleOrNull()
}
