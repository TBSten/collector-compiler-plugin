kotlin {
    sourceSets.commonMain {
        dependencies {
            implementation(project(":items1")); implementation(project(":items2")); implementation(project(":oldhints"))
        }
        // -Pcollector.errorCases=warnings compiles the warning cases instead (see scripts/error-check.sh)
        if (providers.gradleProperty("collector.errorCases").orNull == "warnings") kotlin.setSrcDirs(listOf("warnings"))
    }
}
