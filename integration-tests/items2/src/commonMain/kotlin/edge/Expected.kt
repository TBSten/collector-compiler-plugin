package edge

import defs.Palette
import defs.Tinted

// spec 06+: an annotated expect declaration is collected once, as its actual
@Palette
expect object PlatformTint : Tinted

// annotated on both sides, as Kotlin asks for (ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT): still one item
@Palette
expect fun tintOnBoth(): Tinted

// annotated on the actual side only
expect fun tintOnActual(): Tinted

// The annotations of an expect item are the ones on the expect declaration. The actual need not repeat them (Kotlin
// warns about that itself: ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT).
@defs.Platform("on expect only")
expect fun platformOnExpectOnly(): String

@defs.Platform("on both")
expect fun platformOnBoth(): String
