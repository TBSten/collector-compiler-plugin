package edge.misplaced

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

annotation class Spot

@CollectorFor<Spot>()
object SpotCollector : Collector<String>

@Spot val topLevel = "top level"

object InObject {
    @Spot val member = "in an object"
}

// Where nothing can be collected the annotation is left out with a warning (ITEM_NOT_ALLOWED_HERE), and the rest of
// the file is collected as usual.
class InClass {
    @Spot val member = "in a class"

    @Spot inner class Inner

    fun local(): String {
        @Spot val local = "local"
        return local
    }

    companion object {
        @Spot val member = "in a companion"
    }
}

fun parameter(@Spot value: String) = value
