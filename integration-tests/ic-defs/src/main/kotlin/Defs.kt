package ic.defs

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

annotation class Tracked

@CollectorFor<Tracked>()
object TrackedCollector : Collector<String>
