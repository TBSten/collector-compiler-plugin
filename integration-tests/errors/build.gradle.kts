kotlin {
    // -Pcollector.errorCases=warnings compiles the warning cases instead (see scripts/error-check.sh)
    val warningCases = providers.gradleProperty("collector.errorCases").orNull == "warnings"
    sourceSets.commonMain {
        dependencies {
            implementation(project(":items1")); implementation(project(":items2")); implementation(project(":oldhints"))
        }
        if (warningCases) kotlin.setSrcDirs(listOf("warnings"))
    }
    // the cases that need a Java source
    sourceSets.jvmMain { if (warningCases) kotlin.setSrcDirs(emptyList<String>()) }
}
