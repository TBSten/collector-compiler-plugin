package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.ClassConversion
import me.tbsten.collector.compiler.ClassConversionFacts
import me.tbsten.collector.compiler.CollectorPluginKey
import me.tbsten.collector.compiler.selectClassConversion
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
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
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name

/** Builds the bodies of `as_<C>()`: the expression that turns an item declaration into a value of T. */
internal class ItemConversion(private val context: IrPluginContext) {
    private val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)
    private val bridges = mutableListOf<Pair<IrDeclarationContainer, IrSimpleFunction>>()

    /** Gives the holder member [holderFn] the body `return <target as T>`. */
    fun fill(holderFn: IrSimpleFunction, target: IrDeclaration) {
        val bridge = bridgeFor(holderFn, target)
        holderFn.body = DeclarationIrBuilder(context, holderFn.symbol).irBlockBody {
            +irReturn(if (bridge != null) bridgeCall(bridge) else convert(holderFn.returnType, target))
        }
    }

    /** Adds the bridges [fill] created to their containers. Call once, when no declaration list is being walked. */
    fun addBridges() {
        bridges.forEach { (container, bridge) -> container.declarations += bridge }
        bridges.clear()
    }

    private fun IrDeclaration.isPrivate(): Boolean =
        (this as? IrDeclarationWithVisibility)?.visibility == DescriptorVisibilities.PRIVATE

    /**
     * Private declarations can not be reached from the holder (JVM access checks, file-local klib signatures), so the
     * conversion is done next to them:
     * - a private declaration gets an internal bridge in its own container (the file, or the object it is a member of);
     * - if a class on the way is private, an internal top-level bridge in the same file calls on.
     */
    private fun bridgeFor(holderFn: IrSimpleFunction, target: IrDeclaration): IrSimpleFunction? {
        val type = holderFn.returnType
        var bridge: IrSimpleFunction? = null
        if (target.isPrivate()) {
            bridge = newBridge(holderFn, target.parent as IrDeclarationContainer, target) { convert(type, target) }
        }
        val classes = generateSequence(target.parent as? IrClass) { it.parent as? IrClass }
        if (classes.any { it.isPrivate() }) {
            val inner = bridge
            bridge = newBridge(holderFn, target.file, target) {
                if (inner != null) bridgeCall(inner) else convert(type, target)
            }
        }
        return bridge
    }

    private fun newBridge(
        holderFn: IrSimpleFunction,
        container: IrDeclarationContainer,
        target: IrDeclaration,
        body: IrBuilderWithScope.() -> IrExpression,
    ): IrSimpleFunction {
        val level = if (container is IrFile) "file" else "member"
        val bridge = context.irFactory.buildFun {
            name = Name.identifier("collector\$bridge\$" + level + "\$" + holderFn.name.asString() + "\$" + (target as IrDeclarationWithName).name.asString())
            origin = IrDeclarationOrigin.GeneratedByPlugin(CollectorPluginKey)
            visibility = DescriptorVisibilities.INTERNAL
            returnType = holderFn.returnType
        }
        bridge.parent = container
        if (container is IrClass) {
            bridge.parameters += bridge.buildReceiverParameter {
                kind = IrParameterKind.DispatchReceiver
                type = container.defaultType
            }
        }
        bridges += container to bridge
        bridge.body = DeclarationIrBuilder(context, bridge.symbol).irBlockBody { +irReturn(body()) }
        return bridge
    }

    private fun IrBuilderWithScope.bridgeCall(bridge: IrSimpleFunction): IrExpression =
        irCall(bridge).apply {
            if (bridge.dispatchReceiverParameter != null) arguments[0] = irGetObject(bridge.parentAsClass.symbol)
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

    /** The constructor `Foo()` calls: one without parameters if there is one, as overload resolution prefers it. */
    private fun constructorWithoutArguments(target: IrClass): IrConstructor? {
        val candidates = callableConstructors(target).filter { c -> c.parameters.all { it.defaultValue != null } }
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
