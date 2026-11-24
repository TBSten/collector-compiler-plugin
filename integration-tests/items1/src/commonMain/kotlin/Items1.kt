package items1

import defs.Entry
import defs.Greeter
import ext.Ext

@Entry
object A : Greeter { override val name = "a" }

@Ext
private fun hello(): String = "hello"

@defs.SingleEntry
object OnlyOne : Greeter { override val name = "only" }
