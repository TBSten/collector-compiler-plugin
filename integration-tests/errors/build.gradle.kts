kotlin {
    // -Pcollector.errorCases=warnings compiles the warning cases instead, and =silenced the cases of a build that
    // fails on warnings (see scripts/error-check.sh)
    val otherCases = providers.gradleProperty("collector.errorCases").orNull?.takeIf { it == "warnings" || it == "silenced" }
    sourceSets.commonMain {
        dependencies {
            implementation(project(":items1")); implementation(project(":items2")); implementation(project(":oldhints"))
        }
        if (otherCases != null) kotlin.setSrcDirs(listOf("$otherCases/common"))
    }
    // the cases that need a Java source or an actual declaration
    sourceSets.jvmMain { if (otherCases != null) kotlin.setSrcDirs(listOf("$otherCases/jvm")) }
    if (otherCases == "silenced") {
        compilerOptions {
            allWarningsAsErrors.set(true)
            freeCompilerArgs.add("-Xwarning-level=ITEM_NOT_ALLOWED_HERE:disabled")
        }
    }
}
