package items2

import defs.*

@Entry
object B : Greeter { override val name = "b" }

@defs.Entry
class C : Greeter { override val name = "c" }

@ext.Ext
fun world(): String = "world"
