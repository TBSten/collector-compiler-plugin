package me.tbsten.collector.compiler

/**
 * How a class or an object becomes a value of the T of `Collector<T>` (spec section 04), or why it does not.
 *
 * FIR decides with [selectClassConversion] whether `as_<C>()` exists, and IR builds its body from the rule the same
 * function selects. Both sides answer the [ClassConversionFacts] from the declaration itself, so they can not pick
 * different rules for a T that several rules fit (such as `Any`).
 */
internal sealed interface ClassConversion {
    enum class Rule : ClassConversion {
        /** the object itself */
        OBJECT_ITSELF,

        /** `Foo::class` */
        CLASS_REFERENCE,

        /** `{ Foo }` for an object Foo */
        OBJECT_PROVIDER,

        /** `Foo()` */
        NEW_INSTANCE,

        /** `{ a, b -> Foo(a, b) }` */
        CONSTRUCTOR_REFERENCE,
    }

    enum class Rejection : ClassConversion {
        OBJECT_DOES_NOT_FIT,
        NOT_INSTANTIABLE,
        NO_CONSTRUCTOR_WITHOUT_ARGUMENTS,
        CLASS_DOES_NOT_FIT,
    }
}

/**
 * What [selectClassConversion] asks about a class and T. Each answer is only computed when the rule order needs it.
 *
 * A "callable" constructor is public or internal: the holder calls it from another class of the same module, so
 * private and protected constructors are out of reach.
 */
internal interface ClassConversionFacts {
    val isObject: Boolean

    /** The class is a subtype of T. */
    val isSubtypeOfT: Boolean

    /** T takes the class reference. */
    val classReferenceFitsT: Boolean

    /** T is a function type without parameters that can return the object. */
    val providerFitsT: Boolean

    /** A plain class: not an interface, annotation or enum class, and not inner, abstract or sealed. */
    val canBeInstantiated: Boolean

    /** A callable constructor can be called without arguments. */
    val hasConstructorWithoutArguments: Boolean

    /** T is a function type whose parameters and result match a callable constructor. */
    val constructorFitsT: Boolean
}

internal fun selectClassConversion(facts: ClassConversionFacts): ClassConversion = when {
    facts.isObject -> when {
        facts.isSubtypeOfT -> ClassConversion.Rule.OBJECT_ITSELF
        facts.classReferenceFitsT -> ClassConversion.Rule.CLASS_REFERENCE
        facts.providerFitsT -> ClassConversion.Rule.OBJECT_PROVIDER
        else -> ClassConversion.Rejection.OBJECT_DOES_NOT_FIT
    }
    // Like an object, a class is a value of T first; its class reference is for a T that asks for one, and what is
    // left for a T such as Any when no instance can be made.
    facts.canBeInstantiated && facts.isSubtypeOfT && facts.hasConstructorWithoutArguments -> ClassConversion.Rule.NEW_INSTANCE
    facts.classReferenceFitsT -> ClassConversion.Rule.CLASS_REFERENCE
    !facts.canBeInstantiated -> ClassConversion.Rejection.NOT_INSTANTIABLE
    facts.isSubtypeOfT -> ClassConversion.Rejection.NO_CONSTRUCTOR_WITHOUT_ARGUMENTS
    facts.constructorFitsT -> ClassConversion.Rule.CONSTRUCTOR_REFERENCE
    else -> ClassConversion.Rejection.CLASS_DOES_NOT_FIT
}
