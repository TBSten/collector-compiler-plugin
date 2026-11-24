package me.tbsten.collector.compiler

/**
 * Reversible escaping of a fully-qualified name into a readable identifier.
 *
 * `.` → `__`, `_` → `_u`, any other character that is not an ASCII letter or digit → `_xHHHH`.
 * The character right after `_` always decides the meaning, so decoding is unambiguous.
 * A trailing `_k<digits>` is a disambiguator (see [CollectorNames.holder]) and is dropped by [decode].
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

    fun decode(value: String): String? = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '_') {
                append(c); i++; continue
            }
            if (i + 1 >= value.length) return null
            when (value[i + 1]) {
                '_' -> { append('.'); i += 2 }
                'u' -> { append('_'); i += 2 }
                'x' -> {
                    if (i + 6 > value.length) return null
                    val code = value.substring(i + 2, i + 6).toIntOrNull(16) ?: return null
                    append(code.toChar()); i += 6
                }
                'k' -> {
                    val rest = value.substring(i + 2)
                    if (rest.isEmpty() || !rest.all { it in '0'..'9' }) return null
                    return@buildString
                }
                else -> return null
            }
        }
    }
}
