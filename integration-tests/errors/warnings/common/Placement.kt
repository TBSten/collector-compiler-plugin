// A collected annotation where nothing can be collected is a warning, and the declaration is left out. It must not
// stop the build: the annotation need not be the project's own (`@Serializable` on a member of a class is right).
// Each line marked `// W: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.Conv
import defs.Shape

private val any = object : Shape { override val id = "any" }

class Holder {
    @Conv val member: Shape = any // W: ITEM_NOT_ALLOWED_HERE
    @Conv fun memberFunction(name: String): Shape = any // W: ITEM_NOT_ALLOWED_HERE
    @Conv inner class Inner : Shape by any // W: ITEM_NOT_ALLOWED_HERE

    // reported on the name of the declaration, not on the annotations before it
    @Conv
    val onTheNextLine: Shape = any // W: ITEM_NOT_ALLOWED_HERE
}

fun localDeclarations() {
    @Conv val local: Shape = any // W: ITEM_NOT_ALLOWED_HERE
    @Conv class LocalClass : Shape by any // W: ITEM_NOT_ALLOWED_HERE
    println(local)
}

fun parameter(@Conv shape: Shape) = shape // W: ITEM_NOT_ALLOWED_HERE

// a meta-annotation is checked like the annotation it carries
@Conv annotation class ConvMeta

class MetaHolder {
    @ConvMeta val member: Shape = any // W: ITEM_NOT_ALLOWED_HERE
}
