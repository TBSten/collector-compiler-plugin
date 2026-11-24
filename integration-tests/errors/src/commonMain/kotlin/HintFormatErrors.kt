// Hints from another plugin version (module oldhints) must be reported, not misread.
// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import me.tbsten.collector.collectAsList
import oldhints.OldCollector
import oldhints.OldMark

@OldMark val staleItem = "stale" // E: HINT_FORMAT_MISMATCH

fun staleCollector() {
    OldCollector.collectAsList() // E: HINT_FORMAT_MISMATCH
}
