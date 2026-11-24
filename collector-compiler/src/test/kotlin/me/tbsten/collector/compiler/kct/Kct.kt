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
        sources = module.files.map { (path, src) -> SourceFile.kotlin(path, src.trimIndent()) }
        if (module.plugin) {
            compilerPluginRegistrars = listOf(CollectorCompilerPluginRegistrar())
            commandLineProcessors = listOf(CollectorCommandLineProcessor())
        }
        inheritClassPath = true
        classpaths = module.deps.flatMap { it.runtimeDirs }.distinct()
        messageOutputStream = out
        verbose = false
        kotlincArguments = kotlincArguments + listOf("-Xrender-internal-diagnostic-names")
    }.compile()
    return Compiled(module, result, out.toString())
}

fun compile(name: String = "main", deps: List<Compiled> = emptyList(), plugin: Boolean = true, vararg files: Pair<String, String>) =
    compile(Module(name, files.toMap(), deps, plugin))

/** Compiles a single-module source set that must succeed and returns `box()` of `Box.kt`. */
fun box(vararg files: Pair<String, String>, deps: List<Compiled> = emptyList()): Any? =
    compile("main", deps, true, *files).box()

/**
 * Compiles [files] and checks that the reported diagnostics are exactly the `// E: ID` / `// W: ID` markers at the
 * end of lines (same convention as integration-tests/scripts/error-check.sh).
 */
fun assertDiagnostics(vararg files: Pair<String, String>, deps: List<Compiled> = emptyList()): Compiled {
    val compiled = compile("main", deps, true, *files)
    val expected = files.flatMap { (path, src) ->
        src.trimIndent().lines().mapIndexedNotNull { i, line ->
            MARKER.find(line)?.let { "${File(path).name}:${i + 1} ${it.groupValues[1].lowercase()} ${it.groupValues[2]}" }
        }
    }.toSet()
    val actual = compiled.diagnostics
    assertEquals(expected.sorted().joinToString("\n"), actual.sorted().joinToString("\n"), "Diagnostics differ. Compiler output:\n${compiled.messages}")
    val expectFailure = expected.any { it.contains(" e ") }
    if (expectFailure == compiled.ok) fail("Expected compilation to ${if (expectFailure) "fail" else "succeed"}:\n${compiled.messages}")
    return compiled
}

private val MARKER = Regex("""// ([EW]): ([A-Z_]+)\s*$""")
