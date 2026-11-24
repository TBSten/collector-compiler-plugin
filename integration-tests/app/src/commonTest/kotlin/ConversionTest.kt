import defs.LooseCollector
import defs.RouteCollector
import defs.RouteFunctionCollector
import defs.Router
import defs.ShapeClassCollector
import defs.ShapeCollector
import defs.ShapeFactoryCollector
import defs.ShapeProviderCollector
import me.tbsten.collector.collectAsList
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionTest {
    @Test
    fun instances() = assertEquals(
        listOf("circle", "Dup", "hidden", "secret", "private-host", "triangle", "square", "companion", "default", "dup"),
        ShapeCollector.collectAsList().map { it.id },
    )

    @Test
    fun factories() = assertEquals(
        listOf("named:x", "made:x", "sized:1"),
        ShapeFactoryCollector.collectAsList().map { it("x").id },
    )

    @Test
    fun classReferences() = assertEquals(
        listOf("Circle", "Dup", "Hidden", "Named", "Polygon", "Sized", "Square", "Companion"),
        ShapeClassCollector.collectAsList().map { it.simpleName },
    )

    @Test
    fun providers() = assertEquals(
        listOf("circle", "Dup", "hidden", "square", "companion"),
        ShapeProviderCollector.collectAsList().map { it().id },
    )

    /** With T = Any a class is an instance when one can be made, and its class reference otherwise. */
    @Test
    fun anyTakesAnInstanceOrElseTheClass() = assertEquals(
        listOf("LooseClass()", "LooseInterface::class", "LooseObject", "LoosePrivate::class", "LooseProtected::class", "LooseTwo(7)"),
        LooseCollector.collectAsList().map { if (it is KClass<*>) "${it.simpleName}::class" else it.toString() },
    )

    @Test
    fun extensionFunctions() {
        val router = Router()
        RouteCollector.collectAsList().forEach { router.it() }
        RouteFunctionCollector.collectAsList().forEach { it(router) }
        assertEquals(listOf("health", "users", "health", "users"), router.routes)
    }
}
