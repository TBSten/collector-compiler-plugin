package composeitems

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import composedefs.rendered
import ext.Preview

@Composable
fun Label(text: String) {
    val t = remember(text) { text }
    rendered += t
}

@Preview
@Composable
private fun HomePreview() { Label("home") }

@Preview(name = "settings")
@Composable
fun SettingsPreview() { Label("settings") }

// Multipreviews: an annotation class carrying @Preview counts as those @Previews wherever it is used.
// AllPreviews (this module) nests ThemePreviews (a library) and adds one more.
@ext.ThemePreviews
@Preview(name = "large")
annotation class AllPreviews

@ext.ThemePreviews
@Composable
fun ThemedPreview() { Label("themed") }

@AllPreviews
@Composable
private fun EverywherePreview() { Label("everywhere") }

// Not collected (a warning): the value of a property with a @Composable getter can only be read while composing,
// which a Collector does not do.
@Preview(name = "getter")
val getterPreview: @Composable () -> Unit
    @Composable get() = { Label("getter") }
