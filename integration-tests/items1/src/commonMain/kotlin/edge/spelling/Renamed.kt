package edge.spelling.renamed

import defs.Spelled as S
import defs.Spellings.Nested

@S
val byImportAlias = "import alias"

@Nested
val nestedByImport = "import of the nested class"
