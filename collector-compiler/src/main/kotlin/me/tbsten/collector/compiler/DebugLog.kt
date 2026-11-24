package me.tbsten.collector.compiler

internal val debugLogEnabled: Boolean =
    System.getProperty("collector.debug") != null || System.getenv("COLLECTOR_DEBUG") != null

/**
 * Prints compiler-plugin internals when `-Dcollector.debug` is set on the compiler process (or COLLECTOR_DEBUG in its
 * env). The message is only built when the switch is on.
 */
internal inline fun debugLog(message: () -> String) {
    if (debugLogEnabled) System.err.println("[collector] ${message()}")
}
