package me.tbsten.collector.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * The compiler plugin is built against internal compiler APIs that change between Kotlin releases, so a build with
 * another Kotlin version fails here with an explanation instead of crashing (or miscompiling) inside the compiler.
 */
internal object CollectorKotlinVersionGuard {
    /** Set to `true` in gradle.properties to try another Kotlin version anyway. */
    const val SKIP_PROPERTY = "collector.skipKotlinVersionCheck"

    /** The Kotlin versions this Collector version is built and tested with. */
    val SUPPORTED: Set<String> = setOf(BuildConfig.KOTLIN)

    /** Fails the build when [project] uses a Kotlin version that is not supported, unless [SKIP_PROPERTY] is set. */
    fun check(project: Project) {
        if (project.providers.gradleProperty(SKIP_PROPERTY).orNull == "true") return
        problem(project.getKotlinPluginVersion())?.let { throw GradleException(it) }
    }

    /** Why [kotlinVersion] can not be used, or null when it can. */
    fun problem(kotlinVersion: String): String? {
        if (kotlinVersion in SUPPORTED) return null
        return "Collector ${BuildConfig.VERSION} supports Kotlin ${SUPPORTED.joinToString()}, but this build uses Kotlin " +
            "$kotlinVersion. The Collector compiler plugin depends on compiler internals that change between Kotlin " +
            "versions, so it can fail or produce wrong code with other versions. Use Kotlin ${SUPPORTED.joinToString(" or ")}, " +
            "or set $SKIP_PROPERTY=true in gradle.properties to try anyway."
    }
}
