package ext

/** An "external" annotation that knows nothing about Collector. */
annotation class Ext

/** Stand-in for androidx `@Preview`: repeatable, has arguments, knows nothing about Collector. */
@Repeatable
annotation class Preview(val name: String = "")
