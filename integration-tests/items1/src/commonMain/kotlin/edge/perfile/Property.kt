package edge.perfile

import defs.PerFile

// a property this time: in another file it does not clash with the functions either
@PerFile
private val preview: () -> String = { "a property" }
