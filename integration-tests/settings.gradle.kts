rootProject.name = "collector-integration-tests"

pluginManagement {
    includeBuild("..")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

includeBuild("..")

include(":extlib", ":defs", ":items1", ":items2", ":app")
include(":many")
include(":compose-defs", ":compose-items", ":compose-app")

// JVM-only modules that scripts/ic-check.sh rebuilds incrementally
include(":ic-defs", ":ic-items", ":ic-app")

// Compile-error cases, checked by scripts/error-check.sh (they never compile, so they are opt-in)
if (providers.gradleProperty("collector.errorCases").isPresent) include(":errors", ":oldhints")
