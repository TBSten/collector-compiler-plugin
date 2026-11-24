// spec 06+: collected declarations of one package share FQNs across files, private ones included.
// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
package clash

import defs.Conv
import defs.Shape

private val any = object : Shape { override val id = "a" }

@Conv private val samePrivateName: Shape = any // E: ITEM_NAME_CLASH

@Conv fun functionAndProperty(): Shape = any // E: ITEM_NAME_CLASH

@Conv private val onlyHere: Shape = any
