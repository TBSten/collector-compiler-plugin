package me.tbsten.collector.compiler

/**
 * Escaping of a class id (`com/example/Outer.Inner`), a tag or a module name into a readable identifier made of
 * ASCII letters, digits and `_`.
 *
 * `/` → `__`, `.` → `_d`, `_` → `_u`, `-` → `_h`, `:` → `_c`, and any other character that is not an ASCII letter or
 * digit → `_xHHHH`. The character right after `_` always decides the meaning, so two different values never give the
 * same name. `_i` and `_k` are never produced: they join the parts of a holder's name (see [CollectorNames.holder]).
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
}
