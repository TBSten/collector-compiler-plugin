package conv

import defs.Loose

// Items of Collector<Any>: both the declaration and its KClass are an Any (see ConversionTest).

@Loose object LooseObject { override fun toString() = "LooseObject" }

@Loose class LooseClass { override fun toString() = "LooseClass()" }

// the constructor without parameters is called, not the one whose parameters all have defaults
@Loose class LooseTwo(private val n: Int = 1) {
    constructor() : this(7)

    override fun toString() = "LooseTwo($n)"
}

// a vararg needs no argument either
@Loose class LooseVararg(vararg val parts: String) { override fun toString() = "LooseVararg(${parts.size})" }

// no instance can be made from outside, so these are collected as their class
@Loose interface LooseInterface

@Loose class LoosePrivate private constructor()

@Loose open class LooseProtected protected constructor()
