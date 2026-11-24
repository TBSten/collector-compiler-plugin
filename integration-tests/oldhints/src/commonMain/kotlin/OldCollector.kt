package oldhints

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

annotation class OldMark

@CollectorFor<OldMark>()
object OldCollector : Collector<String>
