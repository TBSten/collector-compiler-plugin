package edge

import defs.Tinted

actual object PlatformTint : Tinted { override val hex = "native" }

@defs.Palette
actual fun tintOnBoth(): Tinted = PlatformTint

@defs.Palette
actual fun tintOnActual(): Tinted = PlatformTint

actual fun platformOnExpectOnly(): String = "expect only"

@defs.Platform("on both")
actual fun platformOnBoth(): String = "both"
