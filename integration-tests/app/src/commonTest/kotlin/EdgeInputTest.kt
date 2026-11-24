import defs.EnumKindCollector
import defs.PaletteCollector
import defs.PaletteFactoryCollector
import defs.Labeled
import defs.LabeledCollector
import defs.LongNameCollector
import defs.SettingCollector
import defs.Tinted
import defs.TwiceCollector
import defs.Weighted
import defs.WeightedCollector
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
    }

    /** Whether the expect declaration, the actual one or both carry the annotation, they are one item. */
    @Test
    fun expectAndActualAreOneItem() {
        // edge.PlatformTint, then the functions edge.tintOnActual and edge.tintOnBoth, which return it
        val tint = edge.PlatformTint
        assertEquals(listOf(Color.BLUE, Color.RED, tint, tint, tint), PaletteFactoryCollector.collectAsList().map { it() })
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

    /** Its holder is named after a shortened form of the name; the item itself is unaffected. */
    @Test
    fun longNamesAreCollected() {
        assertEquals(listOf("long"), LongNameCollector.collectAsList())
    }

    /** Default values and a private class among the arguments of a collected annotation. */
    @Test
    fun annotationsAreBuiltWhereTheyWereWritten() {
        assertEquals(
            listOf("near near 1 Any", "far far 2 Secret"),
            WeightedCollector.collectWithAnnotation<Weighted, String>().map { (a, value) -> "$value ${a.name} ${a.weight} ${a.kind.simpleName}" },
        )
    }
}
