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

    @Test
    fun shortNamesAreKeptAsTheyAre() {
        assertEquals("com__example__Home", HintNameEncoding.bounded("com__example__Home", 120))
        assertEquals("a".repeat(120), HintNameEncoding.bounded("a".repeat(120), 120))
    }

    /** A long name keeps its beginning and ends in a hash of the whole name (test plan G5). */
    @Test
    fun longNamesAreShortenedToTheLimit() {
        val long = "com__example__" + "Screen".repeat(30)
        val bounded = HintNameEncoding.bounded(long, 120)
        assertEquals(120, bounded.length)
        assert(bounded.startsWith("com__example__ScreenScreen")) { bounded }
        assert(Regex(".*_z[0-9a-f]{16}").matches(bounded)) { bounded }
        assertEquals(bounded, HintNameEncoding.bounded(long, 120))
    }

    @Test
    fun shortenedNamesOfDifferentNamesDiffer() {
        val prefix = "a".repeat(200)
        assertNotEquals(HintNameEncoding.bounded(prefix + "x", 120), HintNameEncoding.bounded(prefix + "y", 120))
        // an escape cut in half leaves no `_` before the marker, and still no collision
        val escaped = "a".repeat(101) + "__" + "b".repeat(100)
        assert(!HintNameEncoding.bounded(escaped, 120).contains("__z")) { HintNameEncoding.bounded(escaped, 120) }
    }
}
