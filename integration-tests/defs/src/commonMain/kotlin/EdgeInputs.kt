package defs

import kotlin.reflect.KClass
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

// spec 06+: enum classes and enum entries
interface Tinted { val hex: String }

annotation class Palette

@CollectorFor<Palette>()
object PaletteCollector : Collector<Tinted>

@CollectorFor<Palette>()
object PaletteFactoryCollector : Collector<() -> Tinted>

annotation class EnumKind

@CollectorFor<EnumKind>()
object EnumKindCollector : Collector<KClass<out Enum<*>>>

// spec 06+: only annotations on the property itself (@X, @property:X) count, not @get:X or @field:X
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FIELD)
annotation class Setting

@CollectorFor<Setting>()
object SettingCollector : Collector<String>
