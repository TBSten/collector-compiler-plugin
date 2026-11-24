// Each line marked `// W: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import me.tbsten.collector.collectAsList

// The item is the expect declaration, declared with the common source set: what only the actual adds is not collected.
@OnExpect @OnActualOnly actual fun annotatedExpect(): String = "" // W: ITEM_ANNOTATION_ONLY_ON_ACTUAL

// the actual of a plain expect declaration is an item of its own
@OnActualOnly actual fun plainExpect(): String = ""

// A Collector of a platform source set is out of sight of the common one, like a Collector of a later module.
@me.tbsten.collector.CollectorFor<OnExpect>() object JvmOnExpectCollector : me.tbsten.collector.Collector<() -> String>

fun unseenInCommon() {
    JvmOnExpectCollector.collectAsList() // W: COLLECT_MISSED_ITEMS
    OnExpectCollector.collectAsList()
}
