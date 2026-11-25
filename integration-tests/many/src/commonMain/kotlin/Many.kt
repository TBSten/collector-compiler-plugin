package many

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

annotation class Entry(val n: Int)

@CollectorFor<Entry>()
object EntryCollector : Collector<Any>

@CollectorFor<Entry>()
object FactoryCollector : Collector<() -> Any>

// The items are generated: @Entry(0) object O0000 … (see build.gradle.kts)
