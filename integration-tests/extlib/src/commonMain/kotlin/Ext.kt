package ext

/** An "external" annotation that knows nothing about Collector. */
annotation class Ext

/** Stand-in for androidx `@Preview`: repeatable, has arguments, knows nothing about Collector. */
@Repeatable
annotation class Preview(val name: String = "")

/** Stand-in for a multipreview from a library: an annotation class carrying @Preview twice. */
@Preview(name = "light")
@Preview(name = "dark")
annotation class ThemePreviews

/** Only collected by a Collector the modules using it can not see (errors/warnings): those uses are missed. */
annotation class Unseen
