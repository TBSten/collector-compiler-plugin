package edge

import defs.Tinted

@defs.Palette // annotating the actual as well still gives one item
actual object PlatformTint : Tinted { override val hex = "jvm" }
