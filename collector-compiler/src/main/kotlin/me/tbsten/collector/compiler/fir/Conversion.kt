package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.ClassConversion
import me.tbsten.collector.compiler.ClassConversionFacts
import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.selectClassConversion
import org.jetbrains.kotlin.builtins.functions.isSuspendOrKSuspendFunction
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.functionTypeKind
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.StandardClassIds

/** `(P1, …, Pn) -> R`, read from a function type T. */
internal class FunctionSignature(
    val parameters: List<ConeKotlinType>,
    val returnType: ConeKotlinType,
    val composable: Boolean,
    val suspend: Boolean,
)

internal fun functionSignature(session: FirSession, t: ConeKotlinType): FunctionSignature? {
    val kind = t.functionTypeKind(session) ?: return null
    val args = t.typeArguments.map { it.type ?: return null }
    if (args.isEmpty()) return null
    return FunctionSignature(
        parameters = args.dropLast(1),
        returnType = args.last(),
        composable = "Composable" in kind.classNamePrefix,
        suspend = kind.isSuspendOrKSuspendFunction,
    )
}

/**
 * Why [declaration] can not be turned into a value of [t] (spec section 04), or null when it can.
 *
 * `ItemConversion` on the IR side builds the conversion this function accepts.
 */
internal fun conversionProblem(session: FirSession, declaration: FirDeclaration, t: ConeKotlinType): String? {
    fun ConeKotlinType.isSubtypeOf(other: ConeKotlinType) = isSubtypeOf(other, session)
    val signature = functionSignature(session, t)
    return when (declaration) {
        is FirRegularClass -> classConversionProblem(session, declaration, t, signature)
        // an entry is like an object, except that it has no class of its own to reference (spec 06+)
        is FirEnumEntry -> {
            val type = declaration.returnTypeRef.coneTypeOrNull ?: return "the type of ${declaration.name} is not resolved"
            if (type.isSubtypeOf(t)) return null
            if (signature != null && signature.parameters.isEmpty() && !signature.composable && !signature.suspend &&
                (signature.returnType.isUnit || type.isSubtypeOf(signature.returnType))
            ) return null
            "enum entry ${declaration.name} is neither a subtype of T nor () -> it (enum entries have no KClass of their own)"
        }
        is FirProperty -> {
            if (declaration.typeParameters.isNotEmpty()) return "generic properties can not be collected"
            if (declaration.receiverParameter != null) return "extension properties can not be collected"
            val type = declaration.returnTypeRef.coneTypeOrNull ?: return "the type of ${declaration.name} is not resolved"
            if (type.isSubtypeOf(t)) null else "${declaration.name} is not a subtype of T"
        }
        is FirNamedFunction -> {
            if (signature == null) return "a function can only be collected with a function type T"
            if (declaration.typeParameters.isNotEmpty()) return "generic functions can not be collected"
            if (declaration.contextParameters.isNotEmpty()) return "functions with context parameters can not be collected"
            val composable = declaration.hasAnnotation(CollectorNames.COMPOSABLE, session)
            if (composable != signature.composable) {
                return if (composable) "@Composable functions need a @Composable function type T" else "T is @Composable but ${declaration.name} is not"
            }
            if (declaration.isSuspend != signature.suspend) return "suspend modifiers of ${declaration.name} and T differ"
            val params = listOfNotNull(declaration.receiverParameter?.typeRef?.coneTypeOrNull) +
                declaration.valueParameters.map { it.returnTypeRef.coneTypeOrNull }
            if (declaration.receiverParameter != null && params.first() == null) return "the receiver type of ${declaration.name} is not resolved"
            if (!parametersMatch(session, params, signature.parameters)) return "the parameters of ${declaration.name} do not match T"
            val returnType = declaration.returnTypeRef.coneTypeOrNull ?: return "the return type of ${declaration.name} is not resolved"
            if (!signature.returnType.isUnit && !returnType.isSubtypeOf(signature.returnType)) return "the return type of ${declaration.name} does not match T"
            null
        }
        else -> "this kind of declaration can not be collected"
    }
}

private fun classConversionProblem(session: FirSession, declaration: FirRegularClass, t: ConeKotlinType, signature: FunctionSignature?): String? {
    if (declaration.typeParameters.isNotEmpty()) return "generic classes can not be collected"
    return when (selectClassConversion(FirClassConversionFacts(session, declaration, t, signature))) {
        is ClassConversion.Rule -> null
        ClassConversion.Rejection.OBJECT_DOES_NOT_FIT -> "object ${declaration.name} is neither a subtype of T, its KClass nor () -> it"
        ClassConversion.Rejection.NOT_INSTANTIABLE -> "${declaration.name} can not be instantiated, so only KClass<…> can collect it"
        ClassConversion.Rejection.NO_CONSTRUCTOR_WITHOUT_ARGUMENTS ->
            "${declaration.name} has no constructor without arguments; collect it with a function type such as (…) -> ${declaration.name}"
        ClassConversion.Rejection.CLASS_DOES_NOT_FIT -> "${declaration.name} is neither a subtype of T, its KClass nor a matching constructor reference"
    }
}

private class FirClassConversionFacts(
    private val session: FirSession,
    private val declaration: FirRegularClass,
    private val t: ConeKotlinType,
    signature: FunctionSignature?,
) : ClassConversionFacts {
    private val classType = declaration.symbol.classId.constructClassLikeType()

    /** T as a plain function type that can return the class (or Unit, which drops the value). */
    private val function: FunctionSignature? = signature?.takeIf {
        !it.composable && !it.suspend && (it.returnType.isUnit || classType.isSubtypeOf(it.returnType, session))
    }

    // Constructors are read from the source declaration: this runs while holder members are generated.
    @OptIn(DirectDeclarationsAccess::class)
    private val constructors: List<FirConstructor> by lazy {
        declaration.declarations.filterIsInstance<FirConstructor>().filter { it.visibility != Visibilities.Private }
    }

    override val isObject: Boolean get() = declaration.classKind == ClassKind.OBJECT

    override val isSubtypeOfT: Boolean get() = classType.isSubtypeOf(t, session)

    override val classReferenceFitsT: Boolean
        get() = StandardClassIds.KClass.constructClassLikeType(arrayOf(classType)).isSubtypeOf(t, session)

    override val providerFitsT: Boolean get() = function != null && function.parameters.isEmpty()

    override val canBeInstantiated: Boolean
        get() = declaration.classKind == ClassKind.CLASS && !declaration.isInner &&
            declaration.modality != Modality.ABSTRACT && declaration.modality != Modality.SEALED

    override val hasConstructorWithoutArguments: Boolean
        get() = constructors.any { c -> c.valueParameters.all { it.defaultValue != null } }

    override val constructorFitsT: Boolean
        get() = function != null && constructors.any { c ->
            parametersMatch(session, c.valueParameters.map { it.returnTypeRef.coneTypeOrNull }, function.parameters)
        }
}

/** Every argument of type [given] can be passed to the matching parameter of [declared]. */
private fun parametersMatch(session: FirSession, declared: List<ConeKotlinType?>, given: List<ConeKotlinType>): Boolean =
    declared.size == given.size && declared.zip(given).all { (d, g) -> d != null && g.isSubtypeOf(d, session) }
