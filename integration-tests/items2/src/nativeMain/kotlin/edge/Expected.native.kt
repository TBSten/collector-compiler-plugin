package edge

import defs.Tinted

actual object PlatformTint : Tinted { override val hex = "native" }

@defs.Palette
actual fun tintOnBoth(): Tinted = PlatformTint

@defs.Palette
actual fun tintOnActual(): Tinted = PlatformTint
