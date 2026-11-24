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
