package me.tbsten.collector.compiler.kct

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test plan PBT-7: however the annotation is referenced, the item is collected exactly when Kotlin resolves the
 * reference to the collected annotation `p.Entry`. A distractor `q.Entry` (no Collector) may be imported too.
 */
class ImportStylePropertyTest {
    enum class Target { NONE, STAR, EXPLICIT, ALIAS }
    enum class Distractor { NONE, STAR, EXPLICIT, SAME_PACKAGE }
    enum class Reference { SIMPLE, QUALIFIED, ALIAS }

    data class Case(val target: Target, val distractor: Distractor, val reference: Reference) {
        /** Kotlin's resolution: explicit imports, then the file's package, then star imports. Null = does not compile. */
        fun resolvesTo(): String? = if (target == Target.EXPLICIT && distractor == Distractor.EXPLICIT) null /* conflicting imports */ else when (reference) {
            Reference.QUALIFIED -> "p"
            Reference.ALIAS -> if (target == Target.ALIAS) "p" else null
            Reference.SIMPLE -> when {
                target == Target.EXPLICIT -> "p"
                distractor == Distractor.EXPLICIT -> "q"
                distractor == Distractor.SAME_PACKAGE -> "q"
                target == Target.STAR && distractor == Distractor.STAR -> null // ambiguous
                target == Target.STAR -> "p"
                else -> null
            }
        }

        fun source(pkg: String): String = buildString {
            appendLine("package $pkg")
            when (target) {
                Target.NONE -> {}
                Target.STAR -> appendLine("import p.*")
                Target.EXPLICIT -> appendLine("import p.Entry")
                Target.ALIAS -> appendLine("import p.Entry as PEntry")
            }
            when (distractor) {
                Distractor.NONE, Distractor.SAME_PACKAGE -> {}
                Distractor.STAR -> appendLine("import q.*")
                Distractor.EXPLICIT -> appendLine("import q.Entry")
            }
            if (distractor == Distractor.SAME_PACKAGE) appendLine("annotation class Entry")
            val ref = when (reference) { Reference.SIMPLE -> "Entry"; Reference.QUALIFIED -> "p.Entry"; Reference.ALIAS -> "PEntry" }
            appendLine("@$ref object Item { override fun toString() = \"$pkg\" }")
        }
    }

    private val caseArb = Arb.bind(Arb.element(Target.entries), Arb.element(Distractor.entries), Arb.element(Reference.entries), ::Case)

    private val upstream by lazy {
        compile("ann", files = arrayOf(
            "P.kt" to "package p\nimport me.tbsten.collector.*\nannotation class Entry\n@CollectorFor<Entry>() object PCollector : Collector<Any>",
            "Q.kt" to "package q\nannotation class Entry",
        )).requireOk()
    }

    /** Compiles every case as its own package in one compilation and checks which ones were collected. */
    private fun check(cases: List<Case>) {
        val valid = cases.filter { it.resolvesTo() != null }
        val files = valid.mapIndexed { i, c -> "U$i.kt" to c.source("u$i") }
        val expected = valid.indices.filter { valid[it].resolvesTo() == "p" }.map { "u$it" }.sorted()
        val actual = box(*files.toTypedArray(), "Box.kt" to "import me.tbsten.collector.*\nfun box() = p.PCollector.collectAsList().map { it.toString() }.sorted()", deps = listOf(upstream))
        assertEquals(expected, actual, valid.mapIndexed { i, c -> "u$i: $c" }.joinToString("\n"))
    }

    /** Without a distractor every spelling that compiles must be collected. */
    @Test
    fun pbt7_spellings() = runBlocking<Unit> {
        checkAll(20, PropTestConfig(seed = 70), Arb.list(caseArb, 1..12)) { cases ->
            check(cases.map { it.copy(distractor = Distractor.NONE) })
        }
    }

    /** With a distractor the reference must follow Kotlin's resolution order (G7). */
    @Test
    fun pbt7_withDistractors() {
        runBlocking {
            checkAll(20, PropTestConfig(seed = 71), Arb.list(caseArb, 1..12)) { cases -> check(cases) }
        }
    }
}
