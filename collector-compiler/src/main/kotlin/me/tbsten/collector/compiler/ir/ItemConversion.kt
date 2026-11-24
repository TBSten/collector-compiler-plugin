package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorPluginKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name

/** Builds the bodies of `as_<C>()`: the expression that turns an item declaration into a value of T. */
internal class ItemConversion(private val context: IrPluginContext) {
    private val bridges = mutableListOf<Pair<IrDeclarationContainer, IrSimpleFunction>>()

    /** Gives the holder member [holderFn] the body `return <target as T>`. */
    fun fill(holderFn: IrSimpleFunction, target: IrDeclaration) {
        val bridge = if (target.isPrivate()) createBridge(holderFn, target) else null
        holderFn.body = DeclarationIrBuilder(context, holderFn.symbol).irBlockBody {
            +irReturn(if (bridge != null) irCall(bridge) else convert(this, holderFn.returnType, target))
        }
    }

    /** Adds the bridges [fill] created to their containers. Call once, when no declaration list is being walked. */
    fun addBridges() {
        bridges.forEach { (container, bridge) -> container.declarations += bridge }
        bridges.clear()
    }

    private fun IrDeclaration.isPrivate(): Boolean =
        (this as? IrDeclarationWithVisibility)?.visibility == DescriptorVisibilities.PRIVATE

    /** JVM can not reach a private top-level declaration from another class, so convert inside the same file. */
    private fun createBridge(holderFn: IrSimpleFunction, target: IrDeclaration): IrSimpleFunction {
        val file = target.parent as? IrFile ?: error("Collector: private nested declarations are not supported yet")
        val bridge = context.irFactory.buildFun {
            name = Name.identifier("collector\$bridge\$" + holderFn.name.asString() + "\$" + (target as IrDeclarationWithName).name.asString())
            origin = IrDeclarationOrigin.GeneratedByPlugin(CollectorPluginKey)
            visibility = DescriptorVisibilities.INTERNAL
            returnType = holderFn.returnType
        }
        bridge.parent = file
        bridges += file to bridge
        bridge.body = DeclarationIrBuilder(context, bridge.symbol).irBlockBody {
            +irReturn(convert(this, bridge.returnType, target))
        }
        return bridge
    }

    /** Turns [target] into a value of [t]. */
    private fun convert(builder: IrBuilderWithScope, t: IrType, target: IrDeclaration): IrExpression = with(builder) {
        when (target) {
            is IrClass -> when {
                target.isObject -> irGetObject(target.symbol)
                else -> irCall(target.primaryConstructor ?: target.constructors.first())
            }
            is IrProperty -> irCall(target.getter!!)
            is IrSimpleFunction -> irLambda(t) { it.irCall(target.symbol) }
            else -> error("Collector: unsupported $target")
        }
    }
}
