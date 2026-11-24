// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
package clash

import defs.Conv
import defs.Shape

private val any = object : Shape { override val id = "b" }

@Conv private fun samePrivateName(): Shape = any // E: ITEM_NAME_CLASH

@Conv val functionAndProperty: Shape = any // E: ITEM_NAME_CLASH

private val onlyHere: Shape = any // not collected, so no clash
