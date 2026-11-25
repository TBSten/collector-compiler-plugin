// Meta-annotations are checked like the annotation they carry.
// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape
import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

@Conv annotation class ConvMeta // a meta-annotation, not an item: no diagnostic

private val shape = object : Shape { override val id = "meta" }

class MetaHolder {
    @ConvMeta val member: Shape = shape // E: ITEM_NOT_ALLOWED_HERE
}

@ConvMeta fun metaOverload(): Shape = shape // E: ITEM_NAME_CLASH
@Conv fun metaOverload(name: String): Shape = shape // E: ITEM_NAME_CLASH

// The arguments of a collected annotation on a meta-annotation are built again in the file of each item, which
// can not see what is private to the file of the meta-annotation.
annotation class Linked(val to: kotlin.reflect.KClass<*> = Any::class, val level: LinkLevel = LinkLevel.LOW)

enum class LinkLevel { LOW }

@CollectorFor<Linked>() object LinkedCollector : Collector<Any>

private class HiddenTarget

@Linked(HiddenTarget::class) annotation class LinkedToHidden // E: META_ANNOTATION_PRIVATE_ARGUMENT

@Linked(to = Array<HiddenTarget>::class) annotation class LinkedToArrayOfHidden // E: META_ANNOTATION_PRIVATE_ARGUMENT

// what is public can be reached from everywhere, and a private meta-annotation is only used in this file
@Linked(LinkedCollector::class, LinkLevel.LOW) annotation class LinkedToPublic

@Linked(HiddenTarget::class) private annotation class PrivatelyLinked

@PrivatelyLinked @LinkedToPublic val linked = "linked"

