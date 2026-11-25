// More items than one chunk of the generated vararg holds (ItemVarargs.CHUNK_SIZE in the compiler), so that every
// target compiles and runs the chunked form. The JVM limits themselves need thousands of items and are tested in
// the compiler's own tests.
val itemCount = 1100

val generateItems by tasks.registering {
    val directory = layout.buildDirectory.dir("generated/items")
    val count = itemCount
    inputs.property("itemCount", count)
    outputs.dir(directory)
    doLast {
        val file = directory.get().file("Items.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("package many")
                appendLine()
                repeat(count) { appendLine("@Entry($it) object O${it.toString().padStart(4, '0')}") }
            },
        )
    }
}

kotlin { sourceSets.commonMain { kotlin.srcDir(generateItems) } }
