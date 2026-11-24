import defs.MarkerCollector
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpectAnnotationJvmTest {
    /** On the JVM defs.Marker is a type alias of defs.JvmMarker: both names mark an item. */
    @Test
    fun theAliasAndTheClassBehindIt() {
        assertEquals(
            // defs.markedInCommon, defs.markedOnPlatform, edge.markedByTheJvmName, edge.markedDownstream, edge.markedDownstreamOnJvm
            listOf("defs common", "defs platform", "items1 jvm name", "items1 common", "items1 jvm"),
            MarkerCollector.collectAsList(),
        )
    }
}
