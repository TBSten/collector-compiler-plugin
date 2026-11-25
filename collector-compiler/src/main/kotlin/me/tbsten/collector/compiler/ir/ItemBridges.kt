package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorPluginKey
import me.tbsten.collector.compiler.HintNameEncoding
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
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name

/**
 * Bridges: internal functions the plugin adds next to an item, for what its holder can not do from its own file.
 *
 * A holder lives in `collector.hints`, in a synthetic file. It can not reach private declarations (JVM access checks,
 * file-local klib signatures), and code that belongs to the item's file is out of place in it. A bridge does the work
 * where the item is declared, and the holder only calls it.
 */
internal class ItemBridges(private val context: IrPluginContext) {
    private val pending = mutableListOf<Pair<IrDeclarationContainer, IrSimpleFunction>>()

    /**
     * A bridge for the holder member [holderFn] that returns [body], built next to [target], or null when none is
     * needed: the holder can then evaluate [body] itself.
     *
     * - With [always], or when [target] is private, the bridge is declared where [target] is: in its object, or else
     *   in its file.
     * - When a class around [target] is private, the holder can not reach a bridge inside it either, so a bridge in the
     *   file calls on.
     */
    fun bridgeFor(
        holderFn: IrSimpleFunction,
        target: IrDeclaration,
        always: Boolean = false,
        body: IrBuilderWithScope.() -> IrExpression,
    ): IrSimpleFunction? {
        val owner = target.parent as? IrClass
        var bridge: IrSimpleFunction? = null
        if (always || target.isPrivate()) {
            // a class that is not an object has no instance to call a member bridge on
            bridge = newBridge(holderFn, owner?.takeIf { it.isObject } ?: target.file, target, body)
        }
        val hidden = generateSequence(owner) { it.parent as? IrClass }.any { it.isPrivate() }
        if (hidden && bridge?.parent !is IrFile) {
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
        val bridge = context.irFactory.buildFun {
            name = bridgeName(holderFn, container, target)
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

    /**
     * `collector$bridge$<declaration>$<hash>`. The hash stands for the holder member (a holder is one item, a member
     * one Collector or annotation) and the level, which together make the name unique; spelling them out would make
     * the classes nested in a bridge (the class of a suspend lambda) longer than a file name can be.
     */
    private fun bridgeName(holderFn: IrSimpleFunction, container: IrDeclarationContainer, target: IrDeclaration): Name {
        val level = if (container is IrFile) "file" else "member"
        val declaration = HintNameEncoding.bounded(HintNameEncoding.encode((target as IrDeclarationWithName).name.asString()), MAX_DECLARATION_NAME)
        val hash = HintNameEncoding.hash("${holderFn.parentAsClass.name}/${holderFn.name}/$level")
        return Name.identifier("collector\$bridge\$$declaration\$$hash")
    }

    private companion object {
        const val MAX_DECLARATION_NAME = 60
    }
}
