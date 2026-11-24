package me.tbsten.collector.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HintNameEncodingTest {
    @Test
    fun encodesDesignExample() {
        assertEquals("com__example__Api_uRoutes", HintNameEncoding.encode("com.example.Api_Routes"))
    }

    @Test
    fun roundTrips() {
        listOf("a.b_c", "a_b.c", "com.example.Api_Routes", "pkg.Outer.Inner", "x.`weird name`", "日本.語", "a__b", "_")
            .forEach { assertEquals(it, HintNameEncoding.decode(HintNameEncoding.encode(it))) }
    }

    @Test
    fun dotAndUnderscoreDoNotCollide() {
        assertNotEquals(HintNameEncoding.encode("a.b_c"), HintNameEncoding.encode("a_b.c"))
    }

    @Test
    fun outputIsAPlainIdentifier() {
        val encoded = HintNameEncoding.encode("x.`weird name`-日本")
        assert(encoded.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }) { encoded }
    }

    @Test
    fun disambiguatorIsDroppedOnDecode() {
        assertEquals("a.k5", CollectorNames.decodeHolderName("Item_" + HintNameEncoding.encode("a.k5")))
        assertEquals("a.Foo", CollectorNames.decodeHolderName(CollectorNames.holder("a.Foo", 2).shortClassName.asString()))
        assertEquals("a.foo_k1", CollectorNames.decodeHolderName(CollectorNames.holder("a.foo_k1").shortClassName.asString()))
    }
}
