package composedefs

import androidx.compose.runtime.Composable
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

@CollectorFor<ext.Preview>()
object PreviewCollector : Collector<@Composable () -> Unit>

val rendered = mutableListOf<String>()
