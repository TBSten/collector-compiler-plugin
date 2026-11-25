import edge.priv.privateStepNames
import edge.priv.privateTaskFactories
import edge.priv.privateTasks
import edge.priv.privateTasksByOrder
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateDeclarationsTest {
    /** A private annotation, a private Collector and a private T of one file: nothing generated outside the file names them. */
    @Test
    fun privateRegistryIsCollected() {
        // FQN order: edge.priv.First, edge.priv.Second, then the function edge.priv.third
        assertEquals(listOf("first", "second"), privateTasks())
        assertEquals(listOf("1 first", "2 second"), privateTasksByOrder())
        assertEquals(listOf("first", "second", "first"), privateTaskFactories())
    }

    /** One hint per annotation of a Collector, also when the hints can name neither annotation. */
    @Test
    fun privateCollectorOfTwoPrivateAnnotations() {
        // edge.priv.extraName, edge.priv.stepName
        assertEquals(listOf("extra", "step"), privateStepNames())
    }

    /** The tag of a private Collector is as public as any other. */
    @Test
    fun privateCollectorIsReachedByTagFromAnotherModule() {
        assertEquals(2, collectAsList<Any>("private tasks").size)
    }
}
