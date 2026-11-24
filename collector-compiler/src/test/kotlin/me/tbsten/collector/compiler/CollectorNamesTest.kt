package me.tbsten.collector.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the names other modules (and other plugin versions) read: changing one of them changes the hint format. */
class CollectorNamesTest {
    private val route = ClassId.topLevel(FqName("com.example.Route"))
    private val collector = ClassId.topLevel(FqName("com.example.RouteCollector"))

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
    }

    @Test
    fun holderNames() {
        val holder = CollectorNames.holder("com.example.Home")
        assertEquals("Item_com__example__Home", holder.shortClassName.asString())
        assertEquals("com.example.Home", CollectorNames.decodeHolder(holder))
        assertEquals("as_com__example__RouteCollector", CollectorNames.asFunction(collector).asString())
    }

    @Test
    fun hintFileNames() {
        assertEquals("collectorFor_com__example__RouteCollector", CollectorNames.collectorForHintFile(collector))
        assertEquals("collectorKey_com__example__RouteCollector", CollectorNames.collectorKeyHintFile(collector))
        assertEquals("itemHint_com__example__Home", CollectorNames.itemHintFile(CollectorNames.holder("com.example.Home")))
        assertEquals("itemHint_com__example__home_k1", CollectorNames.itemHintFile(CollectorNames.holder("com.example.home", 1)))
    }
}
