import defs.EntryCollector
import defs.Greeter
import defs.NoneCollector
import defs.SingleCollector
import me.tbsten.collector.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertNotSame

private val expected get() = listOf("defs", "a", "b", "c") + extraEntries()

// top-level initializers exercise lambdas whose parent is not a function
val topLevelLazy: List<Greeter> by EntryCollector.lazyCollectAsList()
val topLevelSequence: Sequence<Greeter> = EntryCollector.collectAsSequence()

class CollectFunctionsTest {
    @Test fun set() = assertEquals(expected, EntryCollector.collectAsSet().map { it.name })
    @Test fun array() = assertEquals(expected, EntryCollector.collectAsArray().map { it.name })
    @Test fun sequence() = assertEquals(expected, EntryCollector.collectAsSequence().map { it.name }.toList())

    @Test
    fun sequenceCreatesItemsOnlyWhenReached() {
        // class C is instantiated per element, so two passes give two instances
        val first = EntryCollector.collectAsSequence().first { it.name == "c" }
        val second = EntryCollector.collectAsSequence().first { it.name == "c" }
        assertNotSame(first, second)
    }

    @Test fun single() = assertEquals("only", SingleCollector.collectSingle().name)
    @Test fun singleOrNullWithOne() = assertEquals("only", SingleCollector.collectSingleOrNull()?.name)
    @Test fun singleOrNullWithNone() = assertNull(NoneCollector.collectSingleOrNull())

    @Test
    fun lazyVariants() {
        val list by EntryCollector.lazyCollectAsList()
        val set by EntryCollector.lazyCollectAsSet()
        val seq by EntryCollector.lazyCollectAsSequence()
        val array by EntryCollector.lazyCollectAsArray()
        val single by SingleCollector.lazyCollectSingle()
        val none by NoneCollector.lazyCollectSingleOrNull()
        assertEquals(expected, list.map { it.name })
        assertEquals(expected, set.map { it.name })
        assertEquals(expected, seq.map { it.name }.toList())
        assertEquals(expected, array.map { it.name })
        assertEquals("only", single.name)
        assertNull(none)
    }

    @Test
    fun lazyIsCreatedOnce() {
        val lazy = EntryCollector.lazyCollectAsList()
        assertSame(lazy.value, lazy.value)
    }

    @Test
    fun topLevelInitializers() {
        assertEquals(expected, topLevelLazy.map { it.name })
        assertEquals(expected, topLevelSequence.map { it.name }.toList())
    }
}
