package me.tbsten.collector.compiler

import me.tbsten.collector.compiler.ItemIdentity.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemIdentityTest {
    private val member = ItemIdentity(Kind.PROPERTY, "com.example", "Registry.first")

    @Test
    fun fqNameJoinsPackageAndName() {
        assertEquals("com.example.Registry.first", member.fqName)
        assertEquals("topLevel", ItemIdentity(Kind.FUNCTION, "", "topLevel").fqName)
    }

    /** What a holder records is what is read back, whatever characters the names contain. */
    @Test
    fun survivesTheHintMessage() {
        val identities = listOf(
            member,
            ItemIdentity(Kind.FUNCTION, "", "topLevel"),
            ItemIdentity(Kind.CLASS, "in.fun", "weird name"),
            ItemIdentity(Kind.ENUM_ENTRY, "日本.語", "Enum.ENTRY: with a colon"),
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

    @Test
    fun ordersByFqName() {
        val items = listOf(
            ItemIdentity(Kind.CLASS, "b", "A"),
            ItemIdentity(Kind.FUNCTION, "a", "z"),
            ItemIdentity(Kind.CLASS, "a", "B.c"),
        )
        assertEquals(listOf("a.B.c", "a.z", "b.A"), items.sorted().map { it.fqName })
    }
}
