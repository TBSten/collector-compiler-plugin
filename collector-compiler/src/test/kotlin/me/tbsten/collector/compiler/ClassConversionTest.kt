package me.tbsten.collector.compiler

import me.tbsten.collector.compiler.ClassConversion.Rejection
import me.tbsten.collector.compiler.ClassConversion.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** The order of the conversion rules for classes and objects (spec section 04). */
class ClassConversionTest {
    private class Facts(
        override val isObject: Boolean = false,
        override val isSubtypeOfT: Boolean = false,
        override val classReferenceFitsT: Boolean = false,
        override val providerFitsT: Boolean = false,
        override val canBeInstantiated: Boolean = true,
        override val hasConstructorWithoutArguments: Boolean = false,
        override val constructorFitsT: Boolean = false,
    ) : ClassConversionFacts

    @Test
    fun objects() {
        assertEquals(Rule.OBJECT_ITSELF, selectClassConversion(Facts(isObject = true, isSubtypeOfT = true, classReferenceFitsT = true, providerFitsT = true)))
        assertEquals(Rule.CLASS_REFERENCE, selectClassConversion(Facts(isObject = true, classReferenceFitsT = true, providerFitsT = true)))
        assertEquals(Rule.OBJECT_PROVIDER, selectClassConversion(Facts(isObject = true, providerFitsT = true)))
        assertEquals(Rejection.OBJECT_DOES_NOT_FIT, selectClassConversion(Facts(isObject = true)))
    }

    @Test
    fun classes() {
        assertEquals(Rule.CLASS_REFERENCE, selectClassConversion(Facts(classReferenceFitsT = true, isSubtypeOfT = true, hasConstructorWithoutArguments = true)))
        assertEquals(Rule.NEW_INSTANCE, selectClassConversion(Facts(isSubtypeOfT = true, hasConstructorWithoutArguments = true, constructorFitsT = true)))
        assertEquals(Rule.CONSTRUCTOR_REFERENCE, selectClassConversion(Facts(constructorFitsT = true)))
    }

    @Test
    fun classesThatDoNotConvert() {
        assertEquals(Rejection.NOT_INSTANTIABLE, selectClassConversion(Facts(canBeInstantiated = false, isSubtypeOfT = true)))
        assertEquals(Rejection.NO_CONSTRUCTOR_WITHOUT_ARGUMENTS, selectClassConversion(Facts(isSubtypeOfT = true, constructorFitsT = true)))
        assertEquals(Rejection.CLASS_DOES_NOT_FIT, selectClassConversion(Facts()))
    }

    /** A class reference wins even for classes that can not be instantiated (interfaces, abstract classes). */
    @Test
    fun classReferenceDoesNotNeedAnInstance() {
        assertEquals(Rule.CLASS_REFERENCE, selectClassConversion(Facts(classReferenceFitsT = true, canBeInstantiated = false)))
    }
}
