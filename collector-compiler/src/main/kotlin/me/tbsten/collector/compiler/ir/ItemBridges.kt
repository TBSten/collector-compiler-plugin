package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorPluginKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
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
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name

/**
 * Bridges: internal functions the plugin adds next to an item, for what its holder can not do from its own file.
 *
 * A holder lives in `collector.hints`, in a file of its own, and can not reach private declarations (JVM access
 * checks, file-local klib signatures). A bridge does the work where the item is declared, and the holder only calls it.
 */
internal class ItemBridges(private val context: IrPluginContext) {
    private val pending = mutableListOf<Pair<IrDeclarationContainer, IrSimpleFunction>>()

    /**
     * A bridge for the holder member [holderFn] that returns [body], built next to [target], or null when none is
     * needed: the holder can then evaluate [body] itself.
     *
     * - When [target] is private, the bridge is declared where [target] is: in its file, or in the object it is a
     *   member of.
     * - When a class around [target] is private, the holder can not reach a bridge inside it either, so a bridge in the
     *   file calls on.
     */
    fun bridgeFor(holderFn: IrSimpleFunction, target: IrDeclaration, body: IrBuilderWithScope.() -> IrExpression): IrSimpleFunction? {
        var bridge: IrSimpleFunction? = null
        if (target.isPrivate()) {
            bridge = newBridge(holderFn, target.parent as IrDeclarationContainer, target, body)
        }
        val hidden = generateSequence(target.parent as? IrClass) { it.parent as? IrClass }.any { it.isPrivate() }
        if (hidden) {
            val inner = bridge
            bridge = newBridge(holderFn, target.file, target) { if (inner != null) call(inner) else body() }
        }
        return bridge
    }

    /** `bridge()`, on its object when it is a member. */
    fun IrBuilderWithScope.call(bridge: IrSimpleFunction): IrExpression =
        irCall(bridge).apply {
            if (bridge.dispatchReceiverParameter != null) arguments[0] = irGetObject(bridge.parentAsClass.symbol)
        }

    /** Adds the bridges created so far to their containers. Call once, when no declaration list is being walked. */
    fun addAll() {
        pending.forEach { (container, bridge) -> container.declarations += bridge }
        pending.clear()
    }

    private fun IrDeclaration.isPrivate(): Boolean =
        (this as? IrDeclarationWithVisibility)?.visibility == DescriptorVisibilities.PRIVATE

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
        pending += container to bridge
        bridge.body = DeclarationIrBuilder(context, bridge.symbol).irBlockBody { +irReturn(body()) }
        return bridge
    }
}
