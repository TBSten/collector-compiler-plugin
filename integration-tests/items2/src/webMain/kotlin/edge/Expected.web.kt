package edge

import defs.Tinted

@defs.Palette // annotating the actual as well still gives one item
actual object PlatformTint : Tinted { override val hex = "web" }

@defs.Palette
actual fun tintOnBoth(): Tinted = PlatformTint

@defs.Palette
actual fun tintOnActual(): Tinted = PlatformTint

actual fun platformOnExpectOnly(): String = "expect only"

@defs.Platform("on both")
actual fun platformOnBoth(): String = "both"
