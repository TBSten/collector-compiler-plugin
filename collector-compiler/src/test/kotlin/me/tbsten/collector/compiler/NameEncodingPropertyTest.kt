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
import me.tbsten.collector.compiler.kct.knownBug
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Test plan PBT-1 (G1) and PBT-2 (G4, G5): the name escaping used for every hint. */
class NameEncodingPropertyTest {
    /** Any string: Unicode, symbols, spaces, and the characters the escaping itself uses. */
    private val anyName: Arb<String> = Arb.choice(
        Arb.string(0..40, Arb.codepoints()),
        Arb.list(Arb.element("a", "Z", "0", ".", "_", "_u", "_x", "__", "\$", " ", "日", "`", "_k1"), 0..20).map { it.joinToString("") },
    ).withEdgecases("", "_", "__", "_k", "_k12", "a._", "日本語")

    /** Realistic FQNs: dot-separated identifiers. */
    private val fqName = Arb.list(Arb.string(1..12, Codepoint.az()), 1..8).map { it.joinToString(".") }

    @Test
    fun pbt1_roundTrip() = runBlocking<Unit> {
        checkAll(1000, PropTestConfig(seed = 1), anyName) { name ->
            assertEquals(name, HintNameEncoding.decode(HintNameEncoding.encode(name)))
        }
    }

    @Test
    fun pbt1_injectiveAndIdentifier() = runBlocking<Unit> {
        checkAll(1000, PropTestConfig(seed = 2), anyName, anyName) { a, b ->
            val ea = HintNameEncoding.encode(a)
            assertTrue(ea.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }, "not an identifier: $ea")
            if (a != b) assertNotEquals(ea, HintNameEncoding.encode(b))
        }
    }

    /** The disambiguator suffix of holders is dropped by decode and never produced by encode. */
    @Test
    fun pbt1_disambiguator() = runBlocking<Unit> {
        checkAll(500, PropTestConfig(seed = 3), fqName, Arb.int(1..999)) { fq, k ->
            assertEquals(fq, CollectorNames.decodeHolder(CollectorNames.holder(fq, k)))
            assertEquals(fq, CollectorNames.decodeHolder(CollectorNames.holder(fq)))
        }
    }

    /** PBT-2: holder file names (`<name>.class`) stay within 255 bytes for long but legal FQNs. */
    @Test
    fun pbt2_fileNameLength() = runBlocking<Unit> {
        val longFq = Arb.list(Arb.string(1..30, Arb.codepoints()), 1..12).map { it.joinToString(".") }
        knownBug("G5/PBT-2: holder names have no length limit") {
            runBlocking {
                checkAll(300, PropTestConfig(seed = 4), longFq) { fq ->
                    val file = CollectorNames.holder(fq).shortClassName.asString() + ".class"
                    assertTrue(file.toByteArray().size <= 255, "${file.length} chars for $fq")
                }
            }
        }
    }
}
