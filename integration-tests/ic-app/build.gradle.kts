plugins { application }

dependencies { "implementation"(project(":ic-items")) }

application { mainClass.set("ic.app.MainKt") }
