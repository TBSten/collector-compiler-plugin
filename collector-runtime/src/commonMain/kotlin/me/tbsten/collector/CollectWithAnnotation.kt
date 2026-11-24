package me.tbsten.collector

// Collect together with the annotation instances (spec P6). [A] must be a supertype of every annotation the
// Collector collects (a compile error otherwise). A declaration with the annotation repeated appears once per
// annotation. Placeholders like those in Collect.kt.

/**
 * Every collected item paired with its annotation.
 *
 * ```
 * val routes: List<Pair<Route, Screen>> = ScreenCollector.collectWithAnnotation()
 * ```
 */
public fun <A : Annotation, T> Collector<T>.collectWithAnnotation(): List<Pair<A, T>> = notApplied()

/**
 * Every collected item keyed by its annotation (annotations are equal when their arguments are).
 * Two items with equal annotations are a compile error when the arguments are visible to the compiler,
 * and an [IllegalStateException] otherwise.
 */
public fun <A : Annotation, T> Collector<T>.collectAsMap(): Map<A, T> = notApplied()

/** [collectWithAnnotation], created on first access. */
public fun <A : Annotation, T> Collector<T>.lazyCollectWithAnnotation(): Lazy<List<Pair<A, T>>> = notApplied()

/** [collectAsMap], created on first access. */
public fun <A : Annotation, T> Collector<T>.lazyCollectAsMap(): Lazy<Map<A, T>> = notApplied()
