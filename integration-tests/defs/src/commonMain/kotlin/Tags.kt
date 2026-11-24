package defs

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

const val PLUGINS = "plugins"
const val PLUG = "plug"
const val PLUGINS_FROM_PARTS = PLUG + "ins"

annotation class PluginA
annotation class PluginB
annotation class Solo

interface Plugin { val id: String }

// two Collectors merged by a tag
@CollectorFor<PluginA>(tag = "plugins")
object PluginACollector : Collector<Plugin>

@CollectorFor<PluginB>("plugins")
object PluginBCollector : Collector<Plugin>

// one Collector for two annotations (repeatable @CollectorFor)
@CollectorFor<PluginA>()
@CollectorFor<PluginB>()
object AllPluginsCollector : Collector<Plugin>

@CollectorFor<Solo>(tag = "solo")
object SoloCollector : Collector<Plugin>

// annotations with arguments (spec P6)
annotation class Screen(val path: String, val order: Int = 0)

interface Page { val title: String }

@CollectorFor<Screen>()
object ScreenCollector : Collector<Page>

// an array and a vararg as arguments: the keys of collectAsMap are compared across modules
annotation class Keyed(val names: Array<String> = [], vararg val more: Int)

@CollectorFor<Keyed>()
object KeyedCollector : Collector<String>

@Keyed(names = ["a", "b"]) val keyedInDefs = "defs"

@Repeatable
annotation class Sample(val name: String)

@CollectorFor<Sample>()
object SampleCollector : Collector<() -> String>

// one Collector under two tags (COLLECTOR_FOR_DUPLICATE warns about the repeated annotation; both tags work)
@CollectorFor<Solo>(tag = "solo-a")
@CollectorFor<Solo>(tag = "solo-b")
object SoloTwiceCollector : Collector<Plugin>
