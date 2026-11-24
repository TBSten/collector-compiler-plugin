package me.tbsten.collector

// Collect by tag: every Collector declared with `@CollectorFor<X>(tag = "…")` and the same tag is merged into one
// set of items, in FQN order. [tag] must be a compile-time constant, and every merged Collector's T must be a
// subtype of the requested T (both are compile errors otherwise). Placeholders like those in Collect.kt.

/** Every item of the Collectors tagged [tag], as a [List]. */
public fun <T> collectAsList(tag: String): List<T> = notApplied()

/** Every item of the Collectors tagged [tag], as a [Set] that keeps the FQN order. */
public fun <T> collectAsSet(tag: String): Set<T> = notApplied()

/** Every item of the Collectors tagged [tag], as a [Sequence] that creates each item only when it reaches it. */
public fun <T> collectAsSequence(tag: String): Sequence<T> = notApplied()

/** Every item of the Collectors tagged [tag], as an [Array]. */
public inline fun <reified T> collectAsArray(tag: String): Array<T> = notApplied()

/** The only item of the Collectors tagged [tag]. Zero or two or more items is a compile error. */
public fun <T> collectSingle(tag: String): T = notApplied()

/** The only item of the Collectors tagged [tag], or `null`. Two or more items is a compile error. */
public fun <T> collectSingleOrNull(tag: String): T? = notApplied()

/** [collectAsList] by tag, created on first access. */
public fun <T> lazyCollectAsList(tag: String): Lazy<List<T>> = notApplied()

/** [collectAsSet] by tag, created on first access. */
public fun <T> lazyCollectAsSet(tag: String): Lazy<Set<T>> = notApplied()

/** [collectAsSequence] by tag, created on first access. */
public fun <T> lazyCollectAsSequence(tag: String): Lazy<Sequence<T>> = notApplied()

/** [collectAsArray] by tag, created on first access. */
public inline fun <reified T> lazyCollectAsArray(tag: String): Lazy<Array<T>> = notApplied()

/** [collectSingle] by tag, created on first access. */
public fun <T> lazyCollectSingle(tag: String): Lazy<T> = notApplied()

/** [collectSingleOrNull] by tag, created on first access. */
public fun <T> lazyCollectSingleOrNull(tag: String): Lazy<T?> = notApplied()
