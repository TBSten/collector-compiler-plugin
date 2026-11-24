package me.tbsten.collector.compiler

/**
 * Escaping of a class id (`com/example/Outer.Inner`), a tag or a module name into a readable identifier made of
 * ASCII letters, digits and `_`.
 *
 * `/` → `__`, `.` → `_d`, `_` → `_u`, `-` → `_h`, `:` → `_c`, and any other character that is not an ASCII letter or
 * digit → `_xHHHH`. The character right after `_` always decides the meaning, so two different values never give the
 * same name. `_i`, `_k` and `_z` are never produced: they join the parts of a holder's name (see
 * [CollectorNames.holder]) and mark a shortened name (see [bounded]).
 */
internal object HintNameEncoding {
    fun encode(value: String): String = buildString {
        for (c in value) {
            when {
                c == '/' -> append("__")
                c == '.' -> append("_d")
                c == '_' -> append("_u")
                c == '-' -> append("_h")
                c == ':' -> append("_c")
                (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') -> append(c)
                else -> append("_x").append(c.code.toString(16).padStart(4, '0'))
            }
        }
    }

    /**
     * [encoded] when it has at most [max] characters, otherwise its beginning followed by `_z` and a hash of all of
     * it, [max] characters at most.
     *
     * Generated names become file names (class files, and the classes nested in them), which most file systems limit
     * to 255 bytes. A name is only shortened when it has to be, so it stays readable in the usual case.
     */
    fun bounded(encoded: String, max: Int): String {
        if (encoded.length <= max) return encoded
        return encoded.take(max - HASH_SUFFIX_LENGTH).trimEnd('_') + HASH_MARKER + hash(encoded)
    }

    private const val HASH_MARKER = "_z"
    private const val HASH_SUFFIX_LENGTH = 18

    /** 64-bit FNV-1a as 16 hex digits: stable across runs, platforms and compiler versions. */
    private fun hash(value: String): String {
        var hash = -0x340d631b7bdddcdbL
        for (c in value) hash = (hash xor c.code.toLong()) * 0x100000001b3L
        return hash.toULong().toString(16).padStart(16, '0')
    }
}
