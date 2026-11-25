// An annotation class of a Java source of this module is not looked into: what it carries is an error, not an item
// that is silently left out. The same annotation class from another module is read like any other (JavaAnnotationJvmTest in :app).
import defs.Conv
import defs.Shape
import errors.JavaMulti
import errors.JavaPlain

private val shape = object : Shape { override val id = "java" }

@JavaMulti val throughJava: Shape = shape // E: META_ANNOTATION_IN_JAVA_SOURCE

// carrying nothing, it is harmless
@JavaPlain @Conv val plainAndDirect: Shape = shape
@JavaPlain val plain: Shape = shape
