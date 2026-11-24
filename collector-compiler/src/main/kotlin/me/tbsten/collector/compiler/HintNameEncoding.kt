package me.tbsten.collector.compiler

/**
 * Escaping of a fully-qualified name into a readable identifier made of ASCII letters, digits and `_`.
 *
 * `.` → `__`, `_` → `_u`, any other character that is not an ASCII letter or digit → `_xHHHH`.
 * The character right after `_` always decides the meaning, so two different names never give the same identifier.
 * `_k` is never produced: it starts the disambiguator of a holder (see [CollectorNames.holder]).
 */
internal object HintNameEncoding {
    fun encode(value: String): String = buildString {
        for (c in value) {
            when {
                c == '.' -> append("__")
                c == '_' -> append("_u")
                (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') -> append(c)
                else -> append("_x").append(c.code.toString(16).padStart(4, '0'))
            }
        }
    }
}
