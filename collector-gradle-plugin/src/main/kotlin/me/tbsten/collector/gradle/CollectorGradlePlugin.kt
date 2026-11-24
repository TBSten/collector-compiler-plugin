package me.tbsten.collector.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class CollectorGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        // Reacts to each Kotlin plugin when it is applied, so the order of the plugins block does not matter.
        RUNTIME_CONFIGURATION_BY_KOTLIN_PLUGIN.forEach { (kotlinPluginId, configurationName) ->
            target.pluginManager.withPlugin(kotlinPluginId) {
                target.dependencies.add(configurationName, RUNTIME)
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = BuildConfig.GROUP, artifactId = "collector-compiler", version = BuildConfig.VERSION)

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        // @Composable items are wrapped in lambdas, which must happen before the Compose compiler lowers them.
        // The constraint is harmless when the Compose compiler is not applied.
        kotlinCompilation.compileTaskProvider.configure {
            it.compilerOptions.freeCompilerArgs.add("-Xcompiler-plugin-order=${BuildConfig.COMPILER_PLUGIN_ID}>$COMPOSE_PLUGIN_ID")
        }
        return kotlinCompilation.target.project.provider { emptyList() }
    }

    private companion object {
        const val COMPOSE_PLUGIN_ID = "androidx.compose.compiler.plugins.kotlin"
        const val RUNTIME = "${BuildConfig.GROUP}:collector-runtime:${BuildConfig.VERSION}"

        /** Kotlin Gradle plugin id -> the configuration that gets collector-runtime. */
        val RUNTIME_CONFIGURATION_BY_KOTLIN_PLUGIN = mapOf(
            "org.jetbrains.kotlin.multiplatform" to "commonMainImplementation",
            "org.jetbrains.kotlin.jvm" to "implementation",
            "org.jetbrains.kotlin.android" to "implementation",
        )
    }
}
