// Warning cases: compiled on their own, because warnings are not printed when compilation fails.
// Each line marked `// W: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor
import me.tbsten.collector.collectAsList

@Conv val number: Int = 1 // W: ITEM_NOT_CONVERTIBLE

@CollectorFor<Conv>() @CollectorFor<Conv>() object Twice : Collector<Shape> // W: COLLECTOR_FOR_DUPLICATE

// spec 06+: an enum entry has no KClass of its own, and an enum class has no instance
enum class EntryForKClass { @defs.EnumKind ENTRY } // W: ITEM_NOT_CONVERTIBLE

@defs.Palette enum class EnumForInstance : defs.Tinted { ; override val hex get() = "" } // W: ITEM_NOT_CONVERTIBLE

// spec P11: items2 uses @Unseen but could not see this Collector when it was compiled
@CollectorFor<ext.Unseen>() object UnseenCollector : Collector<Any>

fun missed() {
    UnseenCollector.collectAsList() // W: COLLECT_MISSED_ITEMS
}

// :defs and :items1 saw the Collector of @Weighted that :defs declares, but not this one, which came later: their
// items have holders, without a conversion for it
@CollectorFor<defs.Weighted>() object LateWeightedCollector : Collector<Any>

@defs.Weighted("here") val weightedHere = "seen by both Collectors"

fun unseen() {
    LateWeightedCollector.collectAsList() // W: COLLECT_MISSED_ITEMS
    defs.WeightedCollector.collectAsList() // every module saw this one
}

// A KFunction type is no function type for a Collector: its values are function references, and the collected value
// of a function is a lambda.
annotation class Reflected

@CollectorFor<Reflected>() object ReflectedCollector : Collector<kotlin.reflect.KFunction1<Int, Int>>

@Reflected fun increment(a: Int): Int = a + 1 // W: ITEM_NOT_CONVERTIBLE

// An annotation with SOURCE retention is not kept on a compiled annotation class: a module that uses the
// meta-annotation does not see it, and collects nothing through it.
@Retention(AnnotationRetention.SOURCE) annotation class SourceOnly

@CollectorFor<SourceOnly>() object SourceOnlyCollector : Collector<Any>

@SourceOnly annotation class ThroughSourceOnly // W: META_ANNOTATION_SOURCE_RETENTION

@SourceOnly internal annotation class ThroughSourceOnlyInThisModule // only used where its source is read

@ThroughSourceOnly @ThroughSourceOnlyInThisModule val sourceOnly = "collected in this module"

// X is built again, with its arguments, where a meta-annotation is used. A module that uses a public one refers to
// what is internal here, which the compiler rejects when it verifies IR.
annotation class LinkedTo(val to: kotlin.reflect.KClass<*>)

@CollectorFor<LinkedTo>() object LinkedToCollector : Collector<Any>

internal class InternalTarget

@PublishedApi internal class PublishedTarget

@LinkedTo(InternalTarget::class) annotation class LinkedToInternal // W: META_ANNOTATION_INTERNAL_ARGUMENT

@LinkedTo(InternalTarget::class) internal annotation class InternallyLinkedToInternal // only used in this module

@LinkedTo(PublishedTarget::class) annotation class LinkedToPublished // the verification lets @PublishedApi through

internal annotation class InternalLink

@CollectorFor<InternalLink>() internal object InternalLinkCollector : Collector<Any>

@InternalLink annotation class ThroughInternalLink // W: META_ANNOTATION_INTERNAL_ARGUMENT

@LinkedToInternal @InternallyLinkedToInternal @LinkedToPublished @ThroughInternalLink val linkedToInternal = "collected in this module"
