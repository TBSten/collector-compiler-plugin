package defs

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

// An annotation that is a type alias on the JVM and a class elsewhere, like the @Preview of multiplatform Compose.
expect annotation class Marker()

@CollectorFor<Marker>()
object MarkerCollector : Collector<String>

@Marker
val markedInCommon = "defs common"
