plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("me.tbsten.collector") apply false
}

configure(subprojects) {
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
