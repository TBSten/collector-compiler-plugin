package edge

import defs.Twice

@Suppress("ClassName")
object twice {
    @Twice object c { override fun toString() = "object edge.twice" }
}
