import defs.EnumKindCollector
import defs.PaletteCollector
import defs.PaletteFactoryCollector
import defs.SettingCollector
import defs.Tinted
import edge.Color
import edge.Size
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

class EdgeInputTest {
    @Test
    fun enumEntriesAreCollectedLikeObjects() {
        // FQN order: edge.Color.BLUE, edge.Color.RED, then the actual of edge.PlatformTint (items2)
        assertEquals(listOf(Color.BLUE, Color.RED, edge.PlatformTint), PaletteCollector.collectAsList())
        assertEquals(listOf(Color.BLUE, Color.RED, edge.PlatformTint), PaletteFactoryCollector.collectAsList().map { it() })
    }

    @Test
    fun enumClassesAreCollectedAsKClass() {
        assertEquals(listOf(Color::class, Size::class), EnumKindCollector.collectAsList())
    }

    @Test
    fun onlyPropertyTargetedAnnotationsCount() {
        assertEquals(listOf("plain", "property"), SettingCollector.collectAsList())
    }

    @Test
    fun runAndApplyReceiversWorkLikeWith() {
        val expected = listOf(Color.BLUE, Color.RED, edge.PlatformTint)
        assertEquals(expected, PaletteCollector.run { collectAsList() })
        var applied: List<Tinted>? = null
        PaletteCollector.apply { applied = collectAsList() }
        assertEquals(expected, applied)
    }
}
