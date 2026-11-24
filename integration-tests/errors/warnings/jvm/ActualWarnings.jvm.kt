// Each line marked `// W: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.

// The item is the expect declaration, declared with the common source set: what only the actual adds is not collected.
@OnExpect @OnActualOnly actual fun annotatedExpect(): String = "" // W: ITEM_ANNOTATION_ONLY_ON_ACTUAL

// the actual of a plain expect declaration is an item of its own
@OnActualOnly actual fun plainExpect(): String = ""
