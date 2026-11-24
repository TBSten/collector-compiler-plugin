package me.tbsten.collector.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HintNameEncodingTest {
    @Test
    fun encodesClassIds() {
        assertEquals("com__example__Api_uRoutes", HintNameEncoding.encode("com/example/Api_Routes"))
        assertEquals("com__example__Outer_dInner", HintNameEncoding.encode("com/example/Outer.Inner"))
    }

    @Test
    fun encodesModuleNamesAndTags() {
        assertEquals("my_happ_cfeature_hlogin", HintNameEncoding.encode("my-app:feature-login"))
        assertEquals("plug_x0020ins", HintNameEncoding.encode("plug ins"))
    }

    @Test
    fun differentNamesNeverCollide() {
        val names = listOf(
            "a/b/C", "a/b.C", "a.b/C", "a.b.C", "a/b_C", "a_b/C", "a__b", "a_d", "a_u", "a_h", "a_c", "a-", "a:", "a/_", "a_", "_", "",
            "x/`weird name`", "日本/語", "a_x0041", "aA",
        )
        assertEquals(names.size, names.map { HintNameEncoding.encode(it) }.toSet().size)
    }

    /** A package and an outer class are different things even when they are spelled the same (test plan G2). */
    @Test
    fun packagesAndOuterClassesDoNotCollide() {
        assertNotEquals(HintNameEncoding.encode("a/b/C"), HintNameEncoding.encode("a/b.C"))
    }

    @Test
    fun outputIsAPlainIdentifier() {
        val encoded = HintNameEncoding.encode("x/`weird name`-日本:1.2")
        assert(encoded.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }) { encoded }
    }
}
