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
