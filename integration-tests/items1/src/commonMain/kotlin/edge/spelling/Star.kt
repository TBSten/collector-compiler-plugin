package edge.spelling

import defs.*

@Spelled
val byStarImport = "star import"

@SpelledAlias
val byTypeAlias = "type alias"

@Spellings.Nested
val nestedByOuterName = "outer name"

object Scope {
    // hides defs.Spelled in this object
    annotation class Spelled

    @Spelled
    val shadowedByNestedClass = "not collected: edge.spelling.Scope.Spelled"

    @defs.Spelled
    val qualifiedInScope = "qualified"
}
