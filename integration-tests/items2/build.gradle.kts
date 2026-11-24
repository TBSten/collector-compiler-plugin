kotlin {
    sourceSets.commonMain.dependencies { api(project(":defs")) }
    // edge/Expected.kt: expect/actual objects
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}
