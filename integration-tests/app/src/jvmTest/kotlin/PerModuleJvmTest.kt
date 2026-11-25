import defs.PerModuleCollector
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

class PerModuleJvmTest {
    /** The same top-level property in two modules is two items on the JVM, ordered by module. */
    @Test
    fun sameTopLevelNameInTwoModules() {
        assertEquals(listOf("items1", "items2"), PerModuleCollector.collectAsList())
    }
}
