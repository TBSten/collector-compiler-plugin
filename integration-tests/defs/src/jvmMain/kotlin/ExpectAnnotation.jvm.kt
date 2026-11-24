package defs

annotation class JvmMarker

actual typealias Marker = JvmMarker

@Marker
val markedOnPlatform = "defs platform"
