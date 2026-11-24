package me.tbsten.collector.compiler

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.codepoints
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Test plan PBT-1 (G1) and PBT-2 (G4, G5): the name escaping used for every hint. Names are not decoded (the
 * identity of an item is in its holder's @Deprecated message), so PBT-1 checks injectivity instead of a round trip.
 */
class NameEncodingPropertyTest {
    /** Any string: Unicode, symbols, spaces, and the characters the escaping itself uses. */
    private val anyName: Arb<String> = Arb.choice(
        Arb.string(0..40, Arb.codepoints()),
        Arb.list(Arb.element("a", "Z", "0", ".", "_", "_u", "_x", "__", "\$", " ", "日", "`", "_k1"), 0..20).map { it.joinToString("") },
    ).withEdgecases("", "_", "__", "_k", "_k12", "a._", "日本語")

    /** Realistic FQNs: dot-separated identifiers. */
    private val fqName = Arb.list(Arb.string(1..12, Codepoint.az()), 1..8).map { it.joinToString(".") }

    private fun identity(pkg: String, name: String, module: String = "main") =
        ItemIdentity(ItemIdentity.Kind.CLASS, module, pkg, null, name)

    @Test
    fun pbt1_injectiveAndIdentifier() = runBlocking<Unit> {
        checkAll(1000, PropTestConfig(seed = 2), anyName, anyName) { a, b ->
            val ea = HintNameEncoding.encode(a)
            assertTrue(ea.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }, "not an identifier: $ea")
            if (a != b) assertNotEquals(ea, HintNameEncoding.encode(b))
        }
    }

    /** Holder names tell items apart: different identities, and the disambiguator, give different names. */
    @Test
    fun pbt1_holderNamesAreDistinct() = runBlocking<Unit> {
        checkAll(500, PropTestConfig(seed = 3), fqName, fqName, Arb.int(1..999)) { a, b, k ->
            val ia = identity(a.substringBeforeLast('.', ""), a.substringAfterLast('.'))
            val ib = identity(b.substringBeforeLast('.', ""), b.substringAfterLast('.'))
            if (ia != ib) assertNotEquals(CollectorNames.holder(ia), CollectorNames.holder(ib))
            assertNotEquals(CollectorNames.holder(ia), CollectorNames.holder(ia, k))
            assertNotEquals(CollectorNames.holder(ia, k), CollectorNames.holder(ia, k + 1))
        }
    }

    /** PBT-2: holder file names (`<name>.class`) stay within 255 bytes, also for long non-ASCII FQNs. */
    @Test
    fun pbt2_fileNameLength() = runBlocking<Unit> {
        val segment = Arb.string(1..30, Arb.codepoints())
        checkAll(300, PropTestConfig(seed = 4), Arb.list(segment, 1..12), segment, segment) { pkg, name, module ->
            val file = CollectorNames.holder(identity(pkg.joinToString("."), name, module)).shortClassName.asString() + ".class"
            assertTrue(file.toByteArray().size <= 255, "${file.toByteArray().size} bytes for $pkg $name")
        }
    }
}
