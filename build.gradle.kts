// gradle.properties (GROUP / VERSION_NAME) is the single source of the Maven coordinates:
// every module is GROUP:<project name>:VERSION_NAME.
allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}
