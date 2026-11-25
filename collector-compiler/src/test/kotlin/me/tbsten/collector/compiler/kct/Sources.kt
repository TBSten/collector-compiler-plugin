package me.tbsten.collector.compiler.kct

/** `Box.kt` with the usual imports and `fun box(): Any? = <body>`. */
fun boxFile(body: String, imports: String = "") = "Box.kt" to """
    import defs.*
    import me.tbsten.collector.*
    $imports
    fun box(): Any? = $body
"""
