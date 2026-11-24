package conv

import defs.Conv
import defs.Route
import defs.Router
import defs.Shape

@Conv object Circle : Shape { override val id = "circle" }

@Conv class Square : Shape { override val id = "square" }

@Conv class Named(name: String) : Shape { override val id = "named:$name" }

// two constructors with one parameter: (String) -> Shape takes the one whose parameter is a String
@Conv class Sized(size: Int) : Shape {
    constructor(name: String) : this(name.length)

    override val id = "sized:$size"
}

@Conv interface Polygon : Shape

@Conv val defaultShape: Shape = object : Shape { override val id = "default" }

object Shapes {
    @Conv val triangle: Shape = object : Shape { override val id = "triangle" }

    @Conv fun make(name: String): Shape = object : Shape { override val id = "made:$name" }
}

class WithCompanion {
    @Conv companion object : Shape { override val id = "companion" }
}

@Conv private object Hidden : Shape { override val id = "hidden" }

private object PrivateHost {
    @Conv val inner: Shape = object : Shape { override val id = "private-host" }
}

object Host {
    @Conv private val secret: Shape = object : Shape { override val id = "secret" }
}

// names that differ only in case get separate holders (Item_conv__Dup / Item_conv__dup_k1)
@Conv object Dup : Shape { override val id = "Dup" }

@Conv val dup: Shape = object : Shape { override val id = "dup" }

// an overload that is not collected, declared first: the item is the annotated function
fun stamped(width: Int, height: Int): Shape = object : Shape { override val id = "stamped:${width}x$height" }

@Conv fun stamped(name: String): Shape = object : Shape { override val id = "stamped:$name" }

@Route fun Router.users() { routes += "users" }

@Route fun health(router: Router) { router.routes += "health" }
