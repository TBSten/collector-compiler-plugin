package me.tbsten.collector.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

/** What a `collect*()` placeholder of the runtime returns. Each kind also exists as `lazyCollect*()`. */
internal enum class CollectKind(val functionName: String) {
    AS_LIST("collectAsList"),
    AS_SET("collectAsSet"),
    AS_SEQUENCE("collectAsSequence"),
    AS_ARRAY("collectAsArray"),
    SINGLE("collectSingle"),
    SINGLE_OR_NULL("collectSingleOrNull"),
    WITH_ANNOTATION("collectWithAnnotation"),
    AS_MAP("collectAsMap"),
    ;

    /** The result pairs each item with its annotation, so the call has the type arguments `<A, T>`. */
    val withAnnotations: Boolean get() = this == WITH_ANNOTATION || this == AS_MAP
}

/** A `collect*()` / `lazyCollect*()` placeholder of the runtime: FIR checks calls to it and IR replaces them. */
internal data class CollectFunction(val kind: CollectKind, val lazy: Boolean) {
    val name: String
        get() = if (lazy) "lazy" + kind.functionName.replaceFirstChar { it.uppercase() } else kind.functionName

    companion object {
        val ALL: List<CollectFunction> = CollectKind.entries.flatMap { listOf(CollectFunction(it, lazy = false), CollectFunction(it, lazy = true)) }

        private val BY_NAME: Map<Name, CollectFunction> = ALL.associateBy { Name.identifier(it.name) }

        /** The placeholder [callableId] names, or null when it is any other function. */
        fun of(callableId: CallableId?): CollectFunction? {
            if (callableId == null || callableId.classId != null || callableId.packageName != CollectorNames.RUNTIME_PACKAGE) return null
            return BY_NAME[callableId.callableName]
        }
    }
}
