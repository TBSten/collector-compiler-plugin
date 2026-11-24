import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import composedefs.PreviewCollector
import composedefs.rendered
import kotlinx.coroutines.Dispatchers
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

private class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) {}
    override fun insertBottomUp(index: Int, instance: Unit) {}
    override fun remove(index: Int, count: Int) {}
    override fun move(from: Int, to: Int, count: Int) {}
    override fun onClear() {}
}

class ComposeAppTest {
    @Test
    fun collectsComposablePreviews() {
        val previews = PreviewCollector.collectAsList()
        val composition = Composition(UnitApplier(), Recomposer(Dispatchers.Unconfined))
        composition.setContent { previews.forEach { it() } }
        assertEquals(listOf("home", "settings"), rendered)
        composition.dispose()
    }
}
