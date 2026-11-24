package ic.app

import ic.defs.TrackedCollector
import me.tbsten.collector.collectAsList

// scripts/ic-check.sh changes the other files, never this one, and reads what this prints
fun main() {
    println(TrackedCollector.collectAsList())
}
