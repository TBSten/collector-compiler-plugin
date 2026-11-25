package me.tbsten.collector.compiler.kct

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test plan PBT-3, PBT-4, PBT-5: random items over several modules, compared with a reference model of the spec
 * (visible items → those T accepts → FQN order). Each sample compiles 2–4 modules, so the counts are kept small (about a minute in all).
 */
class ModelPropertyTest {
    enum class Kind { OBJECT, CLASS, VAL, FUN }
    enum class Visibility(val keyword: String) { PUBLIC(""), INTERNAL("internal "), PRIVATE("private ") }

    data class Item(val kind: Kind, val visibility: Visibility, val pkg: String, val name: String, val module: Int) {
        val fqName get() = "$pkg.$name"
        fun source(): String {
            val v = visibility.keyword
            val label = "override val label = \"$fqName\""
            return when (kind) {
                Kind.OBJECT -> "@defs.E ${v}object $name : defs.Base { $label }"
                Kind.CLASS -> "@defs.E ${v}class $name : defs.Base { $label }"
                Kind.VAL -> "@defs.E ${v}val $name: defs.Base = object : defs.Base { $label }"
                Kind.FUN -> "@defs.E ${v}fun $name(): defs.Base = object : defs.Base { $label }"
            }
        }
    }

    private val itemArb = Arb.bind(
        Arb.element(Kind.entries), Arb.element(Visibility.entries), Arb.element("p", "p.sub", "q", "a.b"),
        Arb.string(1..6, Codepoint.az()), Arb.int(0..2),
    ) { kind, vis, pkg, name, module -> Item(kind, vis, pkg, name.replaceFirstChar { it.uppercase() } + kind.name.first(), module) }

    /** Distinct names, ignoring case (same-named items are D7, case-only differences are G4 — tested elsewhere). */
    private val itemsArb = Arb.list(itemArb, 1..10)

    private fun List<Item>.unique() = distinctBy { it.name.lowercase() }

    private val defsFile = "Defs.kt" to """
        package defs
        import me.tbsten.collector.*
        annotation class E
        interface Base { val label: String }
        @CollectorFor<E>() object Values : Collector<Base>
        @CollectorFor<E>() object Factories : Collector<() -> Base>
    """

    /** The spec as a model: Values takes objects, classes and vals; Factories takes objects, classes and functions. */
    private fun model(items: List<Item>): List<List<String>> = listOf(
        items.filter { it.kind != Kind.FUN }.map { it.fqName }.sorted(),
        items.filter { it.kind != Kind.VAL }.map { it.fqName }.sorted(),
    )

    /** Compiles the items into modules (file and declaration order from [random]) and returns `box()` of main. */
    private fun run(items: List<Item>, random: Random, body: String): Any? {
        val defs = compile("defs", files = arrayOf(defsFile)).requireOk()
        val modules = items.groupBy { it.module }.toSortedMap().map { (module, moduleItems) ->
            // file names are unique across modules: two modules with p/F0.kt would both define the class p.F0Kt
            moduleItems.shuffled(random).groupBy { it.pkg to random.nextInt(2) }.entries.mapIndexed { i, (key, its) ->
                "M${module}F$i.kt" to "package ${key.first}\n" + its.joinToString("\n") { it.source() }
            }
        }
        val compiled = modules.shuffled(random).mapIndexed { i, files ->
            compile("items$i", listOf(defs), files = files.toTypedArray()).requireOk()
        }
        return compile("main", compiled.ifEmpty { listOf(defs) }, files = arrayOf(boxFile(body))).box()
    }

    private val listBody = "listOf(Values.collectAsList().map { it.label }, Factories.collectAsList().map { it().label })"

    /** PBT-3: what is collected equals the model. */
    @Test
    fun pbt3_matchesModel() = runBlocking<Unit> {
        checkAll(30, PropTestConfig(seed = 30), itemsArb) { generated ->
            val items = generated.unique()
            assertEquals(model(items), run(items, Random(0), listBody), "items: $items")
        }
    }

    /** PBT-4: splitting into files, declaration order and module order don't change the result. */
    @Test
    fun pbt4_orderInvariance() = runBlocking<Unit> {
        checkAll(15, PropTestConfig(seed = 40), itemsArb, Arb.long()) { generated, seed ->
            val items = generated.unique()
            assertEquals(run(items, Random(0), listBody), run(items, Random(seed), listBody), "items: $items, seed $seed")
        }
    }

    /** PBT-5: every collect function agrees with collectAsList. */
    @Test
    fun pbt5_collectFunctionsAgree() = runBlocking<Unit> {
        checkAll(15, PropTestConfig(seed = 50), itemsArb) { generated ->
            val items = generated.unique()
            val result = run(
                items, Random(0),
                """run {
                    val list = Values.collectAsList()
                    fun labels(xs: Iterable<Base>) = xs.map { it.label }
                    listOf(
                        labels(list),
                        labels(Values.collectAsSet()),
                        labels(Values.collectAsSequence().toList()),
                        labels(Values.collectAsArray().toList()),
                        labels(Values.lazyCollectAsList().value),
                        labels(Values.lazyCollectAsSet().value),
                        labels(Values.lazyCollectAsSequence().value.toList()),
                        labels(Values.lazyCollectAsArray().value.toList()),
                    )
                }""",
            ) as List<*>
            // labels are distinct, so the Set keeps every item
            result.forEach { assertEquals(result[0], it, "items: $items") }
            assertEquals(model(items)[0], result[0])
        }
    }
}
