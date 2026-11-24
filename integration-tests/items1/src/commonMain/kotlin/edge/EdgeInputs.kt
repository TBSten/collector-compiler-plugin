package edge

import defs.EnumKind
import defs.Palette
import defs.Setting
import defs.Tinted

@EnumKind
enum class Color(override val hex: String) : Tinted {
    @Palette RED("#f00"),
    GREEN("#0f0"),
    @Palette BLUE("#00f"),
}

@EnumKind
enum class Size { S, M, L }

@Setting val plain = "plain"
@property:Setting val propertyTarget = "property"
@get:Setting val getterTarget = "getter"
@field:Setting val fieldTarget = "field"
