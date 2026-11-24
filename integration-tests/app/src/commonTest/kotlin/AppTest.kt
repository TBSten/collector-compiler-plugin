import defs.EntryCollector
import defs.ExtCollector
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {
    @Test
    fun collectsAcrossModules() {
        assertEquals(listOf("defs", "a", "b", "c") + extraEntries(), EntryCollector.collectAsList().map { it.name })
    }

    @Test
    fun collectsExternalAnnotationAndPrivateFunction() {
        assertEquals(listOf("hello", "world"), ExtCollector.collectAsList().map { it() })
    }
}
