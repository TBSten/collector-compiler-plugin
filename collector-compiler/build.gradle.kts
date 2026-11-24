plugins {
    id("buildsrc.convention.kotlin-jvm")
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(kotlin("test"))
    // kctfork: compiles small sources in-process with the plugin (diagnostic and run tests). It brings its own
    // kotlin-compiler-embeddable, so the compiler is pinned to the version the plugin supports.
    testImplementation(libs.kctfork)
    testImplementation(project(":collector-runtime"))
    // property-based tests (test plan PBT-*): generators and shrinking only, run from kotlin.test
    testImplementation(libs.kotest.property)
}

configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-")) useVersion(libs.versions.kotlin.get())
    }
}

tasks.test {
    // Each kctfork compilation spins up a compiler; the suite is CPU-bound and benefits from a larger heap.
    maxHeapSize = "2g"
    // golden files are rewritten with -Pcollector.updateGolden
    systemProperty("collector.updateGolden", providers.gradleProperty("collector.updateGolden").isPresent.toString())
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

tasks.test {
    // CollectorNamesTest checks the plugin id in the sources against gradle.properties
    systemProperty("collector.compilerPluginId", providers.gradleProperty("COMPILER_PLUGIN_ID").get())
}
