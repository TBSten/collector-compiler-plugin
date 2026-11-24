package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.ClassConversion
import me.tbsten.collector.compiler.ClassConversionFacts
import me.tbsten.collector.compiler.selectClassConversion
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isSubtypeOf
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.parentAsClass

/** Builds the bodies of `as_<C>()`: the expression that turns an item declaration into a value of T. */
internal class ItemConversion(private val context: IrPluginContext, private val bridges: ItemBridges) {
    private val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)
    /**
     * Gives the holder member [holderFn] the body `return <target as T>`. A private [target], or one inside a private
     * class, is converted by a bridge next to it.
     */
    fun fill(holderFn: IrSimpleFunction, target: IrDeclaration) {
        val type = holderFn.returnType
        val bridge = bridges.bridgeFor(holderFn, target) { convert(type, target) }
        holderFn.body = DeclarationIrBuilder(context, holderFn.symbol).irBlockBody {
            +irReturn(if (bridge != null) with(bridges) { call(bridge) } else convert(type, target))
        }
    }

    /** Turns [target] into a value of [t]. `conversionProblem()` on the FIR side accepted this conversion. */
    private fun IrBuilderWithScope.convert(t: IrType, target: IrDeclaration): IrExpression = when (target) {
        is IrClass -> convertClass(t, target)
        // an entry is like an object, except that it has no class of its own to reference
        is IrEnumEntry -> {
            val enumType = target.parentAsClass.defaultType
            val value = { b: IrBuilderWithScope -> IrGetEnumValueImpl(b.startOffset, b.endOffset, enumType, target.symbol) }
            if (enumType.isSubtypeOf(t, typeSystem)) value(this) else irLambda(t) { b, _ -> value(b) }
        }
        is IrProperty -> {
            val getter = target.getter!!
            irCall(getter.symbol).apply { setObjectReceiver(this@convert, getter) }
        }
        is IrSimpleFunction -> irLambda(t) { b, params ->
            b.irCall(target.symbol).apply {
                val receiverOffset = if (target.dispatchReceiverParameter != null) 1 else 0
                setObjectReceiver(b, target)
                params.forEachIndexed { i, p -> arguments[receiverOffset + i] = b.irGet(p) }
            }
        }
        else -> error("Collector: unsupported $target")
    }

    private fun IrBuilderWithScope.convertClass(t: IrType, target: IrClass): IrExpression =
        when (val conversion = selectClassConversion(IrClassConversionFacts(target, t))) {
            ClassConversion.Rule.OBJECT_ITSELF -> irGetObject(target.symbol)
            ClassConversion.Rule.CLASS_REFERENCE ->
                IrClassReferenceImpl(startOffset, endOffset, classReferenceType(target), target.symbol, target.defaultType)
            ClassConversion.Rule.OBJECT_PROVIDER -> irLambda(t) { b, _ -> b.irGetObject(target.symbol) }
            ClassConversion.Rule.NEW_INSTANCE -> irCall(constructorWithoutArguments(target)!!)
            ClassConversion.Rule.CONSTRUCTOR_REFERENCE -> {
                val parameterTypes = (t as IrSimpleType).arguments.dropLast(1).map { it.typeOrFail }
                val constructor = callableConstructors(target).first { c ->
                    c.parameters.size == parameterTypes.size &&
                        c.parameters.zip(parameterTypes).all { (declared, given) -> given.isSubtypeOf(declared.type, typeSystem) }
                }
                irLambda(t) { b, params ->
                    b.irCall(constructor).apply { params.forEachIndexed { i, p -> arguments[i] = b.irGet(p) } }
                }
            }
            is ClassConversion.Rejection -> error("Collector: ${target.name} can not be collected ($conversion); FIR declares no as_ function for it")
        }

    /** `KClass<Foo>`, the type of `Foo::class`. */
    private fun classReferenceType(target: IrClass): IrType = context.irBuiltIns.kClassClass.typeWith(target.defaultType)

    /** The constructors a holder can call, see [ClassConversionFacts]. */
    private fun callableConstructors(target: IrClass): List<IrConstructor> =
        target.constructors.filter { it.visibility == DescriptorVisibilities.PUBLIC || it.visibility == DescriptorVisibilities.INTERNAL }.toList()

    /**
     * The constructor `Foo()` calls: one without parameters if there is one, as overload resolution prefers it. A
     * parameter that has a default value or is a vararg needs no argument.
     */
    private fun constructorWithoutArguments(target: IrClass): IrConstructor? {
        val candidates = callableConstructors(target).filter { c -> c.parameters.all { it.defaultValue != null || it.varargElementType != null } }
        return candidates.firstOrNull { it.parameters.isEmpty() } ?: candidates.firstOrNull()
    }

    /**
     * The same answers as `FirClassConversionFacts` gives, read from the IR declaration. `as_<C>()` only exists when
     * FIR found a rule that applies, so the two facts that are each the last rule of their branch are taken as true.
     */
    private inner class IrClassConversionFacts(private val target: IrClass, private val t: IrType) : ClassConversionFacts {
        override val isObject: Boolean get() = target.isObject
        override val isSubtypeOfT: Boolean get() = target.defaultType.isSubtypeOf(t, typeSystem)
        override val classReferenceFitsT: Boolean get() = classReferenceType(target).isSubtypeOf(t, typeSystem)
        override val providerFitsT: Boolean get() = true
        override val canBeInstantiated: Boolean
            get() = target.kind == ClassKind.CLASS && !target.isInner &&
                target.modality != Modality.ABSTRACT && target.modality != Modality.SEALED
        override val hasConstructorWithoutArguments: Boolean get() = constructorWithoutArguments(target) != null
        override val constructorFitsT: Boolean get() = true
    }

    /** Members of objects: the receiver is the object itself. */
    private fun IrCall.setObjectReceiver(b: IrBuilderWithScope, callee: IrSimpleFunction) {
        val receiver = callee.dispatchReceiverParameter ?: return
        val owner = receiver.type.classOrNull ?: callee.parentAsClass.symbol
        arguments[0] = b.irGetObject(owner)
    }
}
