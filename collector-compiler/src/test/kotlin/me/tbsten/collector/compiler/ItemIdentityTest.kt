package me.tbsten.collector.compiler

import me.tbsten.collector.compiler.ItemIdentity.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemIdentityTest {
    private val member = ItemIdentity(Kind.PROPERTY, "app", "com.example", null, "Registry.first")
    private val topLevel = ItemIdentity(Kind.FUNCTION, "app", "com.example", "Screens.kt", "home")

    @Test
    fun fqNameJoinsPackageAndName() {
        assertEquals("com.example.Registry.first", member.fqName)
        assertEquals("com.example.home", topLevel.fqName)
        assertEquals("home", topLevel.copy(packageName = "").fqName)
    }

    @Test
    fun pathKeepsPackagesFilesAndClassesApart() {
        assertEquals("com/example/Registry.first", member.path)
        assertEquals("com/example/Screens.kt/home", topLevel.path)
        assertEquals("Screens.kt/home", topLevel.copy(packageName = "").path)
        assertEquals("Home", ItemIdentity(Kind.CLASS, "app", "", null, "Home").path)
    }

    /** What a holder records is what is read back, whatever characters the names contain. */
    @Test
    fun survivesTheHintMessage() {
        val identities = listOf(
            member,
            topLevel,
            ItemIdentity(Kind.FUNCTION, "", "", "a b.kt", "topLevel"),
            ItemIdentity(Kind.CLASS, "group:my-app_debug", "in.fun", null, "weird name"),
            ItemIdentity(Kind.ENUM_ENTRY, "<main>", "日本.語", null, "Enum.ENTRY: with a colon"),
        )
        for (identity in identities) {
            val message = HintMessage.of(identity.toFields())
            assertEquals(identity, ItemIdentity.fromFields(HintMessage.fields(message)))
            assertEquals(CollectorNames.HINT_FORMAT, CollectorNames.hintFormatOf(message))
        }
    }

    @Test
    fun aPlainHintMessageIsNotAnItem() {
        assertNull(ItemIdentity.fromFields(HintMessage.fields(CollectorNames.HINT_MESSAGE)))
        assertNull(ItemIdentity.fromFields(HintMessage.fields(HintMessage.of(listOf(HintMessage.MISSED to "a.b")))))
    }

    @Test
    fun missedMarkersRecordADeclaration() {
        assertEquals("a.b: c", HintMessage.fields(HintMessage.of(listOf(HintMessage.MISSED to "a.b: c")))[HintMessage.MISSED])
    }

    /** Items are listed by FQN; the same FQN in two modules or two files still has one fixed order. */
    @Test
    fun ordersByFqNameThenModuleAndFile() {
        val items = listOf(
            ItemIdentity(Kind.CLASS, "app", "b", null, "A"),
            ItemIdentity(Kind.FUNCTION, "lib", "a", "Z.kt", "z"),
            ItemIdentity(Kind.FUNCTION, "app", "a", "Z2.kt", "z"),
            ItemIdentity(Kind.FUNCTION, "app", "a", "Z1.kt", "z"),
            ItemIdentity(Kind.CLASS, "app", "a", null, "B.c"),
        )
        assertEquals(
            listOf("a.B.c app null", "a.z app Z1.kt", "a.z app Z2.kt", "a.z lib Z.kt", "b.A app null"),
            items.shuffled().sorted().map { "${it.fqName} ${it.module} ${it.fileName}" },
        )
    }
}
