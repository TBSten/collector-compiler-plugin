plugins {
    kotlin("multiplatform")
}

kotlin {
    explicitApi()
    jvm()
    js { nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
    linuxX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
}
