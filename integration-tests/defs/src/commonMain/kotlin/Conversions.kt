package defs

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor
import kotlin.reflect.KClass

/** Collected by every Collector below; each one converts the declarations its T allows (spec 04). */
annotation class Conv

interface Shape { val id: String }

@CollectorFor<Conv>()
object ShapeCollector : Collector<Shape>

@CollectorFor<Conv>()
object ShapeFactoryCollector : Collector<(String) -> Shape>

@CollectorFor<Conv>()
object ShapeClassCollector : Collector<KClass<out Shape>>

@CollectorFor<Conv>()
object ShapeProviderCollector : Collector<() -> Shape>

annotation class Route

class Router { val routes = mutableListOf<String>() }

@CollectorFor<Route>()
object RouteCollector : Collector<Router.() -> Unit>

@CollectorFor<Route>()
object RouteFunctionCollector : Collector<(Router) -> Unit>
