kotlin { sourceSets.commonMain.dependencies { implementation(project(":items1")); implementation(project(":items2")) } }

tasks.named<Test>("jvmTest") {
    systemProperty("extraEntries", providers.gradleProperty("extraEntries").getOrElse(""))
}
