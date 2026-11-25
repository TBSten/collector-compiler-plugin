package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorPluginKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSpreadElementImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addFakeOverrides
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.name.Name

/** Builds one element of a vararg with the builder of the place the element ends up in: the call site or a chunk. */
internal typealias ElementBuilder = (IrBuilderWithScope) -> IrExpression

/**
 * Builds the vararg argument that lists the items at a call site.
 *
 * A module can have thousands of items, and listing them all in the calling method overflows what the JVM allows
 * for one method (64 KB of code) and for one class (65535 constants). Past [CHUNK_SIZE] elements the vararg is
 * therefore built in chunks, each by a local class of its own whose result is spread into the vararg:
 *
 * ```
 * listOf(*CollectorChunk0().get(), *CollectorChunk1().get(), …)
 * class CollectorChunk0 { fun get(): Array<E> = arrayOf(Item_a.as_C(), …) }
 * ```
 *
 * One instance per file, so the chunk classes of a file get distinct names.
 */
internal class ItemVarargs(private val context: IrPluginContext, private val symbols: CollectorSymbols) {
    private val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)
    private var chunkCount = 0

    fun build(b: IrBuilderWithScope, elementType: IrType, elements: List<ElementBuilder>): IrExpression {
        if (elements.size <= CHUNK_SIZE) return b.irVararg(elementType, elements.map { it(b) })
        val arrayType = context.irBuiltIns.arrayClass.typeWith(elementType)
        val chunks = elements.chunked(CHUNK_SIZE).map { chunk ->
            IrSpreadElementImpl(b.startOffset, b.endOffset, chunk(b, elementType, arrayType, chunk))
        }
        return IrVarargImpl(b.startOffset, b.endOffset, arrayType, elementType, chunks)
    }

    /** `{ class CollectorChunkN { … }; CollectorChunkN().get() }`: the class is declared in a block of its own, right where it is used. */
    private fun chunk(b: IrBuilderWithScope, elementType: IrType, arrayType: IrType, elements: List<ElementBuilder>): IrExpression {
        val chunk = context.irFactory.buildClass {
            name = Name.identifier("CollectorChunk${chunkCount++}")
            origin = IrDeclarationOrigin.GeneratedByPlugin(CollectorPluginKey)
            visibility = DescriptorVisibilities.LOCAL
        }
        chunk.parent = b.scope.getLocalDeclarationParent()
        chunk.superTypes = listOf(context.irBuiltIns.anyType)
        chunk.createThisReceiverParameter()
        val constructor = chunk.addConstructor { isPrimary = true }
        constructor.body = DeclarationIrBuilder(context, constructor.symbol, b.startOffset, b.endOffset).irBlockBody {
            +irDelegatingConstructorCall(symbols.anyConstructor.owner)
            +IrInstanceInitializerCallImpl(startOffset, endOffset, chunk.symbol, context.irBuiltIns.unitType)
        }
        val get = chunk.addFunction("get", arrayType)
        get.body = DeclarationIrBuilder(context, get.symbol, b.startOffset, b.endOffset).irBlockBody {
            +irReturn(
                irCall(symbols.arrayOf, arrayType, typeArguments = listOf(elementType)).apply {
                    arguments[0] = irVararg(elementType, elements.map { it(this@irBlockBody) })
                },
            )
        }
        chunk.addFakeOverrides(typeSystem)
        return b.irBlock(resultType = arrayType) {
            +chunk
            +irCall(get.symbol, arrayType).apply { arguments[0] = irCallConstructor(constructor.symbol, emptyList()) }
        }
    }

    private companion object {
        /** Items per chunk: small enough for one method, large enough to keep the number of classes low. */
        const val CHUNK_SIZE = 500
    }
}
