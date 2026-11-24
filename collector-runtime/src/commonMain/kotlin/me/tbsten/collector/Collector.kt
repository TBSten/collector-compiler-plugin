package me.tbsten.collector

/**
 * Declares that declarations annotated with [X] are collected as [T] by the annotated `object`.
 *
 * ```
 * annotation class ApiRoutes
 *
 * @CollectorFor<ApiRoutes>()
 * object ApiRoutesCollector : Collector<Route.() -> Unit>
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class CollectorFor<X : Annotation>(
    /** Optional tag. Collectors with the same tag can be collected together with `collectAsList<T>(tag = ...)`. */
    val tag: String = "",
)

/**
 * Marker interface for a collector `object`. [T] is the type each collected declaration is converted to.
 */
public interface Collector<T>
