// Expect declarations for ActualWarnings.jvm.kt.
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

annotation class OnExpect
annotation class OnActualOnly

@CollectorFor<OnExpect>() object OnExpectCollector : Collector<() -> String>
@CollectorFor<OnActualOnly>() object OnActualOnlyCollector : Collector<() -> String>

@OnExpect expect fun annotatedExpect(): String

expect fun plainExpect(): String
