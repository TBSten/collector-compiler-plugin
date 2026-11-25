package edge.spelling.shadowed

import defs.*

// a class of the package wins over a star import
annotation class Spelled

@Spelled
val shadowedByPackage = "not collected: edge.spelling.shadowed.Spelled"
