package me.tbsten.collector

// Every function in this file is a placeholder: the Collector compiler plugin replaces each call
// with the collected items while compiling the module that contains the call. Items are always in
// the FQN order of their declarations. The bodies only run when the plugin was not applied.

/**
 * Thrown when a `collect*` call reaches runtime, which means the Collector compiler plugin
 * was not applied to the module that contains the call.
 */
public class CollectorPluginNotAppliedException internal constructor() : IllegalStateException(
    "The Collector compiler plugin was not applied to the module that calls collect*(). " +
        "Apply the 'me.tbsten.collector' Gradle plugin.",
)

@PublishedApi
internal fun notApplied(): Nothing = throw CollectorPluginNotAppliedException()

/** Every collected item as a [List]. */
public fun <T> Collector<T>.collectAsList(): List<T> = notApplied()

/** Every collected item as a [Set] that keeps the FQN order. */
public fun <T> Collector<T>.collectAsSet(): Set<T> = notApplied()

/** Every collected item as a [Sequence]. Each item is created only when the sequence reaches it. */
public fun <T> Collector<T>.collectAsSequence(): Sequence<T> = notApplied()

/** Every collected item as an [Array]. */
public inline fun <reified T> Collector<T>.collectAsArray(): Array<T> = notApplied()

/** The only collected item. Zero or two or more items is a compile error. */
public fun <T> Collector<T>.collectSingle(): T = notApplied()

/** The only collected item, or `null` when there is none. Two or more items is a compile error. */
public fun <T> Collector<T>.collectSingleOrNull(): T? = notApplied()

/** [collectAsList], created on first access. */
public fun <T> Collector<T>.lazyCollectAsList(): Lazy<List<T>> = notApplied()

/** [collectAsSet], created on first access. */
public fun <T> Collector<T>.lazyCollectAsSet(): Lazy<Set<T>> = notApplied()

/** [collectAsSequence], created on first access. */
public fun <T> Collector<T>.lazyCollectAsSequence(): Lazy<Sequence<T>> = notApplied()

/** [collectAsArray], created on first access. */
public inline fun <reified T> Collector<T>.lazyCollectAsArray(): Lazy<Array<T>> = notApplied()

/** [collectSingle], created on first access. */
public fun <T> Collector<T>.lazyCollectSingle(): Lazy<T> = notApplied()

/** [collectSingleOrNull], created on first access. */
public fun <T> Collector<T>.lazyCollectSingleOrNull(): Lazy<T?> = notApplied()
