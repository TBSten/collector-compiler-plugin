plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("me.tbsten.collector") apply false
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin.get() apply false
}

configure(subprojects.filter { !it.name.startsWith("compose-") }) {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    if (name != "extlib") apply(plugin = "me.tbsten.collector")
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvm()
        js { nodejs() }
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs { nodejs() }
        linuxX64()
        macosArm64()
        sourceSets.getByName("commonTest").dependencies { implementation(kotlin("test")) }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
    }
}

configure(subprojects.filter { it.name.startsWith("compose-") }) {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    apply(plugin = "me.tbsten.collector")
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvm()
        sourceSets.getByName("commonMain").dependencies { implementation("org.jetbrains.compose.runtime:runtime:1.5.12") }
        sourceSets.getByName("commonTest").dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
    }
}
