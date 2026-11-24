import defs.collectedInsideDefsInline
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineTest {
    @Test
    fun inlineFunctionIsResolvedAtLibraryCompileTime() {
        assertEquals(listOf("defs"), collectedInsideDefsInline())
    }
}
