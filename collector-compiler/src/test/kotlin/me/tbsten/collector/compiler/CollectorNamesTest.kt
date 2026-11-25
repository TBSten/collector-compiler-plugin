package me.tbsten.collector.compiler

import me.tbsten.collector.compiler.ItemIdentity.Kind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Pins the names other modules (and other plugin versions) read: changing one of them changes the hint format. */
class CollectorNamesTest {
    private val route = ClassId.topLevel(FqName("com.example.Route"))
    private val collector = ClassId.topLevel(FqName("com.example.RouteCollector"))
    private val nested = ClassId(FqName("com.example"), FqName("Api.Route"), isLocal = false)
    private val home = ItemIdentity(Kind.CLASS, "app", "com.example", null, "Home")

    /** The Gradle plugin passes COMPILER_PLUGIN_ID of gradle.properties; the compiler side has to answer to the same id. */
    @Test
    fun pluginIdMatchesGradleProperties() {
        assertEquals(System.getProperty("collector.compilerPluginId"), CollectorNames.PLUGIN_ID)
    }

    @Test
    fun hintNames() {
        assertEquals("collector.hints", CollectorNames.HINTS_PACKAGE.asString())
        assertEquals("collectorFor_com__example__Route", CollectorNames.collectorForHint(route).callableName.asString())
        assertEquals("collectorKey_com__example__RouteCollector", CollectorNames.collectorKeyHint(collector).callableName.asString())
        assertEquals("item_com__example__Route", CollectorNames.itemHint(route).callableName.asString())
        assertEquals("collectorMissed_com__example__Route", CollectorNames.missedHint(route).callableName.asString())
        assertEquals("collectorTag_my_hplugins", CollectorNames.collectorTagHint("my-plugins").callableName.asString())
    }

    /** `Route` nested in class `Api` is not `Route` in package `com.example.Api`. */
    @Test
    fun nestedClassesAreNotPackages() {
        assertEquals("collectorFor_com__example__Api_dRoute", CollectorNames.collectorForHint(nested).callableName.asString())
        assertNotEquals(
            CollectorNames.itemHint(nested),
            CollectorNames.itemHint(ClassId.topLevel(FqName("com.example.Api.Route"))),
        )
    }

    @Test
    fun holderNames() {
        assertEquals("Item_com__example__Home_in_app", CollectorNames.holder(home).shortClassName.asString())
        assertEquals("Item_com__example__Home_in_app_k1", CollectorNames.holder(home, 1).shortClassName.asString())
        assertEquals(
            "Item_com__example__Screens_dkt__home_in_my_happ_cfeature",
            CollectorNames.holder(ItemIdentity(Kind.FUNCTION, "my-app:feature", "com.example", "Screens.kt", "home")).shortClassName.asString(),
        )
        assertEquals("as_com__example__RouteCollector", CollectorNames.asFunction(collector).asString())
        assertEquals("annotations_com__example__Route", CollectorNames.annotationsFunction(route).asString())
    }

    /** What Kotlin itself keeps apart gets holders of its own: other modules, other files, and classes against packages. */
    @Test
    fun holdersOfDifferentDeclarationsDiffer() {
        val function = ItemIdentity(Kind.FUNCTION, "app", "com.example", "A.kt", "module")
        val holders = listOf(
            function,
            function.copy(module = "lib"),
            function.copy(fileName = "B.kt"),
            ItemIdentity(Kind.CLASS, "app", "a.b", null, "C"),
            ItemIdentity(Kind.CLASS, "app", "a", null, "b.C"),
        ).map { CollectorNames.holder(it) }
        assertEquals(holders.size, holders.toSet().size)
    }

    /** `_k<n>` marks a disambiguated holder; a declaration or a module whose name ends in `_k1` must not look like one. */
    @Test
    fun disambiguatorDoesNotCollideWithNames() {
        val lower = home.copy(relativeName = "home")
        assertNotEquals(CollectorNames.holder(lower, 1), CollectorNames.holder(lower.copy(module = "app_k1")))
        assertNotEquals(CollectorNames.holder(lower, 1), CollectorNames.holder(lower.copy(relativeName = "home_in_app_k1", module = "")))
    }

    @Test
    fun missedMarkerNames() {
        assertEquals("Missed_com__example__Route_in_app", CollectorNames.missedMarker(route, "app").shortClassName.asString())
    }

    @Test
    fun hintFileNames() {
        assertEquals("collectorFor_com__example__RouteCollector", CollectorNames.collectorForHintFile(collector))
        assertEquals("collectorKey_com__example__RouteCollector", CollectorNames.collectorKeyHintFile(collector))
        assertEquals("itemHint_com__example__Home_in_app", CollectorNames.itemHintFile(CollectorNames.holder(home)))
        assertEquals("itemHint_com__example__Home_in_app_k1", CollectorNames.itemHintFile(CollectorNames.holder(home, 1)))
        assertEquals("collectorMissed_com__example__Route_in_app", CollectorNames.missedHintFile(CollectorNames.missedMarker(route, "app")))
    }

    /** Every class, file and member name stays short enough for `Item_…$annotations_…$1.class` to be a file name. */
    @Test
    fun longNamesStayWithinFileNameLimits() {
        val deep = FqName((1..20).joinToString(".") { "segment$it" })
        val longClass = ClassId(deep, FqName("日本語の名前".repeat(10)), isLocal = false)
        val item = ItemIdentity(Kind.CLASS, "group:" + "module-".repeat(20), deep.asString(), null, "日本語の名前".repeat(10))
        val holder = CollectorNames.holder(item, 12).shortClassName.asString()
        val members = listOf(CollectorNames.asFunction(longClass), CollectorNames.annotationsFunction(longClass)).map { it.asString() }
        val files = listOf(
            CollectorNames.itemHintFile(CollectorNames.holder(item, 12)),
            CollectorNames.collectorForHintFile(longClass),
            CollectorNames.collectorKeyHintFile(longClass),
            CollectorNames.missedHintFile(CollectorNames.missedMarker(longClass, item.module)),
        )
        for (member in members) assert("$holder$$member$1.class".length <= 255) { "$holder$$member$1.class" }
        for (file in files) assert("${file}Kt.class".length <= 255) { file }
        assertNotEquals(holder, CollectorNames.holder(item.copy(module = item.module + "x"), 12).shortClassName.asString())
    }
}
