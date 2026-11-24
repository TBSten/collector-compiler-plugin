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
}

tasks.test {
    // CollectorNamesTest checks the plugin id in the sources against gradle.properties
    systemProperty("collector.compilerPluginId", providers.gradleProperty("COMPILER_PLUGIN_ID").get())
}
