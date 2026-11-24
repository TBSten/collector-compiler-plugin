package many

import me.tbsten.collector.collectAsArray
import me.tbsten.collector.collectAsList
import me.tbsten.collector.collectAsMap
import me.tbsten.collector.collectAsSequence
import me.tbsten.collector.collectAsSet
import me.tbsten.collector.collectWithAnnotation
import me.tbsten.collector.lazyCollectAsList
import me.tbsten.collector.lazyCollectAsSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 1100 items: the lists are built in chunks, and nothing may be lost or reordered at the chunk borders. */
class ManyTest {
    private val count = 1100

    @Test
    fun listsKeepEveryItemInOrder() {
        val list = EntryCollector.collectAsList()
        assertEquals(count, list.size)
        assertSame(O0000, list.first())
        assertSame(O0499, list[499])
        assertSame(O0500, list[500])
        assertSame(O1099, list.last())
        assertEquals(list, EntryCollector.collectAsArray().toList())
        assertEquals(list, EntryCollector.collectAsSet().toList())
        val lazyList by EntryCollector.lazyCollectAsList()
        assertEquals(list, lazyList)
    }

    @Test
    fun sequencesAndFunctionTypes() {
        assertEquals(EntryCollector.collectAsList(), EntryCollector.collectAsSequence().toList())
        assertEquals(count, EntryCollector.lazyCollectAsSequence().value.count())
        val factories = FactoryCollector.collectAsList()
        assertEquals(count, factories.size)
        assertSame(O0777, factories[777]())
    }

    @Test
    fun annotationsStayWithTheirItems() {
        val pairs = EntryCollector.collectWithAnnotation<Entry, Any>()
        assertEquals(List(count) { it }, pairs.map { it.first.n })
        assertSame(O1000, pairs[1000].second)
        assertEquals(count, EntryCollector.collectAsMap<Entry, Any>().size)
    }
}
