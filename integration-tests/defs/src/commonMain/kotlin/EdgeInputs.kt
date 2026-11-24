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

// two modules, one FQN: `edge.twice.c` is a property of package edge.twice in one module and an object nested in
// object edge.twice in the other. (Not a property on both sides: Kotlin/Native gives a property `c` of the package and
// one of the object the same symbol, so the two modules could not be linked into one binary.)
annotation class Twice

@CollectorFor<Twice>()
object TwiceCollector : Collector<Any>

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

// The ways to write an annotation at an item: imports, an import alias, a type alias, a nested class
annotation class Spelled

object Spellings { annotation class Nested }

typealias SpelledAlias = Spelled

@CollectorFor<Spelled>()
object SpelledCollector : Collector<String>

@CollectorFor<Spellings.Nested>()
object NestedSpelledCollector : Collector<String>

// the same name in several files of a package, like the `private fun Preview()` of every screen
annotation class PerFile

@CollectorFor<PerFile>()
object PerFileCollector : Collector<() -> String>

// an annotation with a value on expect declarations
annotation class Platform(val name: String)

@CollectorFor<Platform>()
object PlatformCollector : Collector<() -> String>
