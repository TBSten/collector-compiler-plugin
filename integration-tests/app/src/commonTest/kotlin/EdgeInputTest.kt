import defs.EnumKindCollector
import defs.PaletteCollector
import defs.PaletteFactoryCollector
import defs.Labeled
import defs.LabeledCollector
import defs.SettingCollector
import defs.Tinted
import defs.TwiceCollector
import edge.Color
import edge.Size
import me.tbsten.collector.collectAsList
import me.tbsten.collector.collectWithAnnotation
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

    @Test
    fun metaAnnotationsCountAsTheirAnnotation() {
        // annotation classes themselves are not items
        assertEquals(listOf("dependency", "own"), LabeledCollector.collectAsList())
        val labels = LabeledCollector.collectWithAnnotation<Labeled, String>().map { it.first.label }
        // in source order, meta-annotations expanded in place
        assertEquals(listOf("from-defs", "direct", "from-defs", "own"), labels)
    }

    /** A package and an outer class can spell the same FQN in two modules: both are items, ordered by module. */
    @Test
    fun packageAndOuterClassWithTheSameName() {
        assertEquals(listOf("package edge.twice", "object edge.twice"), TwiceCollector.collectAsList())
    }
}
