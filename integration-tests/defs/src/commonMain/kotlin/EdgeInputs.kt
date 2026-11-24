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

// meta-annotations (like Compose multipreviews): an annotation class carrying X counts as X where it is used
annotation class Labeled(val label: String)

@CollectorFor<Labeled>()
object LabeledCollector : Collector<String>

/** A meta-annotation from a dependency (read from metadata / klib by the item module). */
@Labeled("from-defs")
annotation class DefsLabel

// two modules, one FQN: `edge.twice.c` is a property of package edge.twice in one module and of object edge.twice in
// the other
annotation class Twice

@CollectorFor<Twice>()
object TwiceCollector : Collector<String>

// two modules, one FQN: the top-level property `edge.module` of two modules (the JVM allows it when the files differ)
annotation class PerModule

@CollectorFor<PerModule>()
object PerModuleCollector : Collector<String>

// a declaration whose name is longer than a file name can be once package, file and module are added to it
annotation class LongName

@CollectorFor<LongName>()
object LongNameCollector : Collector<String>

// an annotation with default values, on an item of its own module and with a private class as an argument: the
// annotation is built where it was written
annotation class Weighted(val name: String, val weight: Int = 1, val kind: KClass<*> = Any::class)

@CollectorFor<Weighted>()
object WeightedCollector : Collector<String>

@Weighted("near") val weightedNear = "near"
