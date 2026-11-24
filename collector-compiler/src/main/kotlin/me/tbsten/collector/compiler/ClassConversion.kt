package me.tbsten.collector.compiler

/**
 * How a class or an object becomes a value of the T of `Collector<T>` (spec section 04), or why it does not.
 *
 * FIR decides with [selectClassConversion] whether `as_<C>()` exists, and IR builds its body from the rule the same
 * function selects, so the two sides share the order in which the rules are tried.
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

/** What [selectClassConversion] asks about a class and T. Each answer is only computed when the rule order needs it. */
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
    facts.classReferenceFitsT -> ClassConversion.Rule.CLASS_REFERENCE
    !facts.canBeInstantiated -> ClassConversion.Rejection.NOT_INSTANTIABLE
    facts.isSubtypeOfT ->
        if (facts.hasConstructorWithoutArguments) ClassConversion.Rule.NEW_INSTANCE else ClassConversion.Rejection.NO_CONSTRUCTOR_WITHOUT_ARGUMENTS
    facts.constructorFitsT -> ClassConversion.Rule.CONSTRUCTOR_REFERENCE
    else -> ClassConversion.Rejection.CLASS_DOES_NOT_FIT
}
