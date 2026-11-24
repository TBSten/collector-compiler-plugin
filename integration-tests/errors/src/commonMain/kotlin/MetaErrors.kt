// Meta-annotations are checked like the annotation they carry.
// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape

@Conv annotation class ConvMeta // a meta-annotation, not an item: no diagnostic

private val shape = object : Shape { override val id = "meta" }

class MetaHolder {
    @ConvMeta val member: Shape = shape // E: ITEM_NOT_ALLOWED_HERE
}

@ConvMeta fun metaOverload(): Shape = shape // E: ITEM_NAME_CLASH
@Conv fun metaOverload(name: String): Shape = shape // E: ITEM_NAME_CLASH
