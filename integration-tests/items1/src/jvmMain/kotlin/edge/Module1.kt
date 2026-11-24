package edge

import defs.PerModule

@PerModule internal val module = "items1"

@defs.Marker
val markedDownstreamOnJvm = "items1 jvm"

// the class the alias defs.Marker stands for
@defs.JvmMarker
val markedByTheJvmName = "items1 jvm name"
