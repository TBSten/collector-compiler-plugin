import defs.AllPluginsCollector
import defs.EntryCollector
import defs.PLUG
import defs.PLUGINS
import defs.PLUGINS_FROM_PARTS
import defs.Page
import defs.Plugin
import defs.SampleCollector
import defs.Screen
import defs.ScreenCollector
import me.tbsten.collector.collectAsList
import me.tbsten.collector.collectAsMap
import me.tbsten.collector.collectSingle
import me.tbsten.collector.collectWithAnnotation
import me.tbsten.collector.lazyCollectAsMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PLUGINS_IN_THIS_FILE = "$PLUG" + "ins"

class TagAndAnnotationTest {
    @Test
    fun tagMergesCollectorsAndKeepsEachItemOnce() =
        assertEquals(listOf("alpha", "beta", "both"), collectAsList<Plugin>(tag = "plugins").map { it.id })

    @Test
    fun tagCanBeAConstVal() =
        assertEquals(listOf("alpha", "beta", "both"), collectAsList<Plugin>(tag = PLUGINS).map { it.id })

    @Test
    fun tagCanBeJoinedConstants() {
        val expected = listOf("alpha", "beta", "both")
        assertEquals(expected, collectAsList<Plugin>(tag = PLUGINS_FROM_PARTS).map { it.id })
        assertEquals(expected, collectAsList<Plugin>(tag = PLUGINS_IN_THIS_FILE).map { it.id })
        assertEquals(expected, collectAsList<Plugin>(tag = "plug" + "ins").map { it.id })
        assertEquals(expected, collectAsList<Plugin>(tag = "${PLUG}ins").map { it.id })
    }

    @Test
    fun tagWithASupertype() = assertEquals(3, collectAsList<Any>(tag = "plugins").size)

    @Test
    fun unknownTagIsEmpty() = assertTrue(collectAsList<Plugin>(tag = "nothing").isEmpty())

    @Test
    fun singleByTag() = assertEquals("only", collectSingle<Plugin>(tag = "solo").id)

    @Test
    fun oneCollectorUnderTwoTags() {
        assertEquals("only", collectSingle<Plugin>(tag = "solo-a").id)
        assertEquals("only", collectSingle<Plugin>(tag = "solo-b").id)
    }

    @Test
    fun repeatableCollectorFor() =
        assertEquals(listOf("alpha", "beta", "both"), AllPluginsCollector.collectAsList().map { it.id })

    @Test
    fun implicitReceiver() = with(EntryCollector) {
        assertEquals(listOf("defs", "a", "b", "c") + extraEntries(), collectAsList().map { it.name })
    }

    @Test
    fun explicitThisReceiver() {
        val names = EntryCollector.run { this.collectAsList() }.map { it.name }
        assertEquals(EntryCollector.collectAsList().map { it.name }, names)
    }

    @Test
    fun withAnnotation() {
        val pairs: List<Pair<Screen, Page>> = ScreenCollector.collectWithAnnotation()
        assertEquals(listOf("/home" to "Home", "/settings" to "Settings"), pairs.map { (a, p) -> a.path to p.title })
        assertEquals(listOf(0, 1), pairs.map { it.first.order })
    }

    @Test
    fun repeatedAnnotationsGiveOnePairEach() {
        val pairs = SampleCollector.collectWithAnnotation<defs.Sample, () -> String>()
        assertEquals(listOf("plain" to "plain", "light" to "themed", "dark" to "themed"), pairs.map { (a, f) -> a.name to f() })
        assertEquals(2, SampleCollector.collectAsList().size)
    }

    @Test
    fun asMap() {
        val map: Map<Screen, Page> = ScreenCollector.collectAsMap()
        assertEquals(listOf("/home", "/settings"), map.keys.map { it.path })
        assertEquals("Home", map.entries.first { it.key.path == "/home" }.value.title)
    }

    private val lazyMap by ScreenCollector.lazyCollectAsMap<Screen, Page>()

    @Test
    fun lazyAsMap() = assertEquals(2, lazyMap.size)
}
