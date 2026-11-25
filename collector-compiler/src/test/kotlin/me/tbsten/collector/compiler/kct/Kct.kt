package me.tbsten.collector.compiler.kct

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import me.tbsten.collector.compiler.CollectorCommandLineProcessor
import me.tbsten.collector.compiler.CollectorCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Small harness over kctfork (kotlin-compile-testing) for the Collector plugin.
 *
 * A "module" is one kctfork compilation. Upstream modules are passed as [Module.deps]; their output directories go
 * on the classpath, so their hints are read the same way as from a jar.
 */
class Module(
    val name: String,
    val files: Map<String, String>,
    val deps: List<Compiled> = emptyList(),
    val plugin: Boolean = true,
    /** -Xverify-ir=error; turned off only to look past an IR validation bug that is reported separately. */
    val verifyIr: Boolean = true,
)

class Compiled(val module: Module, val result: JvmCompilationResult, val messages: String) {
    val ok: Boolean get() = result.exitCode == KotlinCompilation.ExitCode.OK
    val outputDir: File get() = result.outputDirectory

    /** Every output directory this module needs at run time (itself and all upstream modules). */
    val runtimeDirs: List<File> get() = (listOf(outputDir) + module.deps.flatMap { it.runtimeDirs }).distinct()

    fun requireOk(): Compiled {
        if (!ok) fail("Compilation of ${module.name} failed:\n$messages")
        return this
    }

    /** Runs the top-level `fun box(): Any?` declared in [file] (`Box.kt` → `BoxKt`) and returns its result. */
    fun box(file: String = "Box.kt"): Any? {
        requireOk()
        val className = file.removeSuffix(".kt").replaceFirstChar { it.uppercase() } + "Kt"
        val loader = java.net.URLClassLoader(runtimeDirs.map { it.toURI().toURL() }.toTypedArray(), Compiled::class.java.classLoader)
        val cls = loader.loadClass(className)
        return try {
            cls.getMethod("box").invoke(null)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }

    /** Diagnostics reported by the compiler, as `<file>:<line> <e|w> <ID>` (only those with a plugin-style ID). */
    val diagnostics: Set<String>
        get() = DIAGNOSTIC.findAll(messages).map { m ->
            val (sev, path, line, id) = m.destructured
            "${File(path).name}:$line $sev $id"
        }.toSet()

    companion object {
        private val DIAGNOSTIC = Regex("""^([ew]): (?:file://)?(\S+?\.kt):(\d+):\d+ \[([A-Z_]+)]""", RegexOption.MULTILINE)
    }
}

@OptIn(ExperimentalCompilerApi::class)
fun compile(module: Module): Compiled {
    val out = ByteArrayOutputStream()
    val dir = Files.createTempDirectory("kct-${module.name}").toFile()
    val result = KotlinCompilation().apply {
        workingDir = dir
        moduleName = module.name
        sources = module.files.map { (path, src) ->
            if (path.endsWith(".java")) SourceFile.java(path, src.trimIndent()) else SourceFile.kotlin(path, src.trimIndent())
        }
        if (module.plugin) {
            compilerPluginRegistrars = listOf(CollectorCompilerPluginRegistrar())
            commandLineProcessors = listOf(CollectorCommandLineProcessor())
        }
        inheritClassPath = true
        classpaths = module.deps.flatMap { it.runtimeDirs }.distinct()
        messageOutputStream = out
        verbose = false
        // -Xverify-ir=error: malformed IR from the plugin fails the compilation instead of passing unnoticed (off by default on JVM).
        kotlincArguments = kotlincArguments + listOf("-Xrender-internal-diagnostic-names") +
            (if (module.verifyIr) listOf("-Xverify-ir=error") else emptyList())
        // the runtime is built for the JVM running the tests; inline functions need the same target
        jvmTarget = Runtime.version().feature().toString()
    }.compile()
    return Compiled(module, result, out.toString())
}

fun compile(name: String = "main", deps: List<Compiled> = emptyList(), plugin: Boolean = true, vararg files: Pair<String, String>) =
    compile(Module(name, files.toMap(), deps, plugin))

/** Compiles a single-module source set that must succeed and returns `box()` of `Box.kt`. */
fun box(vararg files: Pair<String, String>, deps: List<Compiled> = emptyList()): Any? =
    compile("main", deps, true, *files).box()

/**
 * Compiles [files] and checks that the reported diagnostics are exactly the markers at the end of lines:
 * `// E: ID` (error), `// W: ID` (warning) — the same convention as integration-tests/scripts/error-check.sh — and
 * `// E|W: ID`, which accepts either severity. The last one is for ITEM_NOT_CONVERTIBLE, whose severity is still
 * undecided (spec/impl difference D1), so tests don't pin it.
 */
fun assertDiagnostics(vararg files: Pair<String, String>, deps: List<Compiled> = emptyList()): Compiled {
    val compiled = compile("main", deps, true, *files)
    val markers = files.flatMap { (path, src) ->
        src.trimIndent().lines().mapIndexedNotNull { i, line ->
            MARKER.find(line)?.let { Triple("${File(path).name}:${i + 1}", it.groupValues[1], it.groupValues[2]) }
        }
    }
    val anySeverity = markers.filter { it.second == "E|W" }.mapTo(mutableSetOf()) { "${it.first} ${it.third}" }
    val expected = markers.mapTo(mutableSetOf()) { (at, sev, id) -> if (sev == "E|W") "$at * $id" else "$at ${sev.lowercase()} $id" }
    val actual = compiled.diagnostics.mapTo(mutableSetOf()) { d ->
        val (at, sev, id) = d.split(" ")
        if ("$at $id" in anySeverity) "$at * $id" else "$at $sev $id"
    }
    assertEquals(expected.sorted().joinToString("\n"), actual.sorted().joinToString("\n"), "Diagnostics differ. Compiler output:\n${compiled.messages}")
    if (anySeverity.isEmpty()) {
        val expectFailure = markers.any { it.second == "E" }
        if (expectFailure == compiled.ok) fail("Expected compilation to ${if (expectFailure) "fail" else "succeed"}:\n${compiled.messages}")
    }
    return compiled
}

private val MARKER = Regex("""// (E|W|E\|W): ([A-Z_]+)\s*$""")

/**
 * Runs a check of a spec-decided behavior that the implementation does not follow yet and whose direction is still
 * undecided ([id] is one of D1–D11 in the spec's 08-8). It reports whether the implementation matches the spec but
 * never fails the build, so the decision is not pinned by the test suite.
 */
fun undecided(id: String, block: () -> Unit) {
    val failure = runCatching(block).exceptionOrNull()
    if (failure == null) println("Undecided $id: the implementation matches the spec")
    else println("Undecided $id: differs from the spec: " + failure.message?.lineSequence()?.filter { it.isNotBlank() }?.take(4)?.joinToString(" / "))
}

/**
 * A test that reproduces an open implementation bug. It must currently fail; once the bug is fixed it starts to pass,
 * and this fails so the wrapper is removed and the test guards the fix.
 */
fun knownBug(bug: String, block: () -> Unit) {
    val failure = runCatching(block).exceptionOrNull()
        ?: fail("Known bug no longer reproduces ($bug): remove knownBug() so the test guards the fix.")
    println("Known bug reproduces ($bug): " + failure.message?.lineSequence()?.filter { it.isNotBlank() }?.take(4)?.joinToString(" / "))
}
