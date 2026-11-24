plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("me.tbsten.collector") apply false
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin.get() apply false
}

subprojects {
    // Malformed IR from the plugin fails the build instead of passing unnoticed (the JVM backend does not check by default).
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions.freeCompilerArgs.addAll("-Xverify-ir=error", "-Xverify-ir-visibility")
    }
}

configure(subprojects.filter { !it.name.startsWith("compose-") }) {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    // extlib: an "external" library; oldhints: hand-written hints of other plugin versions
    if (name != "extlib" && name != "oldhints") apply(plugin = "me.tbsten.collector")
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
