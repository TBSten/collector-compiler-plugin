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
    fun differentNamesNeverCollide() {
        val names = listOf("a.b_c", "a_b.c", "a.b.c", "a__b", "a_u", "a._", "a_", "_", "", "x.`weird name`", "日本.語", "a_x0041", "aA")
        assertEquals(names.size, names.map { HintNameEncoding.encode(it) }.toSet().size)
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

    /** `_k<n>` marks a disambiguated holder; a name that ends in `_k1` itself must not look like one. */
    @Test
    fun disambiguatorDoesNotCollideWithNames() {
        assertEquals("Item_a__Foo_k2", CollectorNames.holder("a.Foo", 2).shortClassName.asString())
        assertNotEquals(CollectorNames.holder("a.foo", 1), CollectorNames.holder("a.foo_k1"))
    }
}
