package me.tbsten.collector.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrElementBase
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

/** Builds the bodies of `annotations_<X>()`: the X annotations of an item as a list. */
internal class ItemAnnotations(private val context: IrPluginContext, private val symbols: CollectorSymbols) {
    /**
     * Gives the holder member [holderFn], declared as `annotations_<X>(): List<X>`, the body `return listOf(X(…), …)`:
     * copies of the X annotations on [target], in source order.
     */
    fun fill(holderFn: IrSimpleFunction, target: IrDeclaration) {
        val elementType = (holderFn.returnType as IrSimpleType).arguments.single().typeOrFail
        val annotations = target.annotations.filter { it.type.classOrNull == elementType.classOrNull }
        holderFn.body = DeclarationIrBuilder(context, holderFn.symbol).irBlockBody {
            +irReturn(
                irCall(symbols.listOf, holderFn.returnType, typeArguments = listOf(elementType)).apply {
                    arguments[0] = irVararg(elementType, annotations.map { it.deepCopyWithSymbols(holderFn).withoutOffsets() })
                },
            )
        }
    }

    /**
     * The copied annotation keeps offsets of the item's file, which mean nothing in the holder's file (the JVM
     * line-number mapper asserts on them), so drop them.
     */
    private fun <E : IrElement> E.withoutOffsets(): E = apply {
        acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                (element as? IrElementBase)?.let {
                    it.startOffset = UNDEFINED_OFFSET
                    it.endOffset = UNDEFINED_OFFSET
                }
                element.acceptChildrenVoid(this)
            }
        })
    }
}
