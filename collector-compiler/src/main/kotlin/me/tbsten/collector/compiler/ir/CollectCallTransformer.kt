package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectFunction
import me.tbsten.collector.compiler.CollectKind
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Phase 3: replaces each `collect*()` call with an expression that lists the items of its Collector, for example
 * `listOf(Item_a.as_C(), Item_b.as_C())`. The `lazyCollect*()` forms wrap the same expression in `lazy { }`.
 */
internal class CollectCallTransformer(
    private val context: IrPluginContext,
    private val symbols: CollectorSymbols,
    private val discovery: HintDiscovery,
) : IrElementTransformerVoidWithContext() {
    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid(this)
        val function = CollectFunction.of(expression.symbol.owner.callableIdOrNull()) ?: return expression
        val receiver = expression.arguments[0] as? IrGetObjectValue
            ?: error("Collector: the receiver of ${function.name}() must be a @CollectorFor object (checked on the FIR side)")
        val collector = receiver.symbol.owner
        val elementType = expression.typeArguments[0] ?: error("Collector: no type argument on ${function.name}()")
        val entries = discovery.entriesFor(collector)
        debugLog { "call site ${currentFile.name}: ${function.name} on ${collector.name} -> ${entries.size} item(s)" }

        val builder = DeclarationIrBuilder(context, currentScope!!.scope.scopeOwnerSymbol, expression.startOffset, expression.endOffset)
        if (!function.lazy) return build(builder, function.kind, elementType, expression.type, entries)
        val valueType = (expression.type as IrSimpleType).arguments.first().typeOrFail
        return lazyOf(builder, valueType, expression.type) { build(it, function.kind, elementType, valueType, entries) }
    }

    /** `Item_x.as_C()` */
    private fun value(b: IrBuilderWithScope, entry: CollectedEntry): IrExpression =
        b.irCall(entry.asFunction).apply { arguments[0] = b.irGetObject(entry.holder) }

    private fun build(
        b: IrBuilderWithScope,
        kind: CollectKind,
        elementType: IrType,
        resultType: IrType,
        entries: List<CollectedEntry>,
    ): IrExpression = when (kind) {
        CollectKind.AS_LIST -> varargCall(b, symbols.listOf, elementType, resultType, entries.map { value(b, it) })
        CollectKind.AS_SET -> varargCall(b, symbols.setOf, elementType, resultType, entries.map { value(b, it) })
        CollectKind.AS_ARRAY -> varargCall(b, symbols.arrayOf, elementType, resultType, entries.map { value(b, it) })
        CollectKind.AS_SEQUENCE -> {
            val factoryType = context.irBuiltIns.functionN(0).typeWith(elementType)
            b.irCall(symbols.sequenceOfFactories, resultType, typeArguments = listOf(elementType)).apply {
                arguments[0] = b.irGetObject(symbols.internals)
                arguments[1] = b.irVararg(factoryType, entries.map { e -> b.irLambda(factoryType) { lambda, _ -> value(lambda, e) } })
            }
        }
        // the count is checked on the FIR side
        CollectKind.SINGLE -> entries.singleOrNull()?.let { value(b, it) } ?: error("Collector: collectSingle() needs exactly one item")
        CollectKind.SINGLE_OR_NULL -> entries.singleOrNull()?.let { value(b, it) } ?: b.irNull(resultType)
    }

    private fun varargCall(
        b: IrBuilderWithScope, function: IrSimpleFunctionSymbol, elementType: IrType, resultType: IrType, values: List<IrExpression>,
    ): IrExpression =
        b.irCall(function, resultType, typeArguments = listOf(elementType)).apply {
            arguments[0] = b.irVararg(elementType, values)
        }

    /** `lazy { <body> }` */
    private fun lazyOf(b: IrBuilderWithScope, valueType: IrType, lazyType: IrType, body: (IrBuilderWithScope) -> IrExpression): IrExpression {
        val initializerType = context.irBuiltIns.functionN(0).typeWith(valueType)
        return b.irCall(symbols.lazy, lazyType, typeArguments = listOf(valueType)).apply {
            arguments[0] = b.irLambda(initializerType) { lambda, _ -> body(lambda) }
        }
    }
}
