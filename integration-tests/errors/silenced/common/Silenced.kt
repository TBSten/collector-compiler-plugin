// A build that fails on warnings (allWarningsAsErrors) still compiles code that uses the annotation of a Collector
// where nothing can be collected, once that warning is switched off for the module: these cases are compiled with
// -Xwarning-level=ITEM_NOT_ALLOWED_HERE:disabled (errors/build.gradle.kts). Nothing may be reported here.
import defs.Conv
import defs.Shape

private val any = object : Shape { override val id = "any" }

class SilencedHolder {
    @Conv val member: Shape = any
    @Conv inner class Inner : Shape by any
}

fun silencedParameter(@Conv shape: Shape) = shape
