package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectFunction
import me.tbsten.collector.compiler.CollectKind
import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId

/**
 * Phase 3: replaces each `collect*()` call with an expression that lists the items of its Collector, for example
 * `listOf(Item_a.as_C(), Item_b.as_C())`. The `lazyCollect*()` forms wrap the same expression in `lazy { }`.
 */
internal class CollectCallTransformer(
    private val context: IrPluginContext,
    private val symbols: CollectorSymbols,
    private val discovery: HintDiscovery,
) : IrElementTransformerVoidWithContext() {
    private val varargs = ItemVarargs(context, symbols)

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid(this)
        val callee = expression.symbol.owner
        val function = CollectFunction.of(callee.callableIdOrNull()) ?: return expression
        val byTag = callee.parameters.firstOrNull()?.kind == IrParameterKind.Regular
        val entries = if (byTag) {
            val tag = constantString(expression.arguments[0])
                ?: error("Collector: the tag of ${function.name}() must be a constant (checked on the FIR side)")
            discovery.entriesForTag(tag)
        } else {
            // `XCollector.f()` or the implicit receiver of `with(XCollector) { f() }`: the object type decides
            val collector = expression.arguments[0]?.type?.classOrNull?.owner?.takeIf { it.isObject }
                ?: error("Collector: the receiver of ${function.name}() must be a @CollectorFor object (checked on the FIR side)")
            discovery.entriesFor(collector)
        }
        debugLog { "call site ${currentFile.name}: ${function.name} -> ${entries.map { it.identity.fqName }}" }

        val builder = DeclarationIrBuilder(context, currentScope!!.scope.scopeOwnerSymbol, expression.startOffset, expression.endOffset)
        val typeArguments = expression.typeArguments.map { it ?: error("Collector: no type argument on ${function.name}()") }
        if (!function.lazy) return build(builder, function.kind, typeArguments, expression.type, entries)
        val valueType = (expression.type as IrSimpleType).arguments.first().typeOrFail
        return lazyOf(builder, valueType, expression.type) { build(it, function.kind, typeArguments, valueType, entries) }
    }

    /** A constant string argument: a literal, a `const val`, or those joined with `+` or in a string template. */
    private fun constantString(expression: IrExpression?): String? {
        return when (expression) {
            is IrConst -> expression.value as? String
            is IrStringConcatenation -> expression.arguments.map { constantString(it) ?: return null }.joinToString("")
            is IrCall -> {
                val callee = expression.symbol.owner
                val property = callee.correspondingPropertySymbol?.owner
                when {
                    property?.isConst == true -> constantString(property.backingField?.initializer?.expression)
                    callee.name.asString() == "plus" && callee.parent == context.irBuiltIns.stringClass.owner && expression.arguments.size == 2 -> {
                        val left = constantString(expression.arguments[0]) ?: return null
                        val right = constantString(expression.arguments[1]) ?: return null
                        left + right
                    }
                    else -> null
                }
            }
            is IrGetField -> constantString(expression.symbol.owner.initializer?.expression)
            else -> null
        }
    }

    /** `Item_x.as_C()` */
    private fun value(entry: CollectedEntry): ElementBuilder = { b ->
        b.irCall(entry.asFunction).apply { arguments[0] = b.irGetObject(entry.holder) }
    }

    /** [typeArguments] are those of the call: `<T>`, or `<A, T>` for the kinds with annotations. */
    private fun build(
        b: IrBuilderWithScope,
        kind: CollectKind,
        typeArguments: List<IrType>,
        resultType: IrType,
        entries: List<CollectedEntry>,
    ): IrExpression {
        val elementType = typeArguments.last()
        val values = entries.map { value(it) }
        return when (kind) {
            CollectKind.AS_LIST -> varargCall(b, symbols.listOf, elementType, resultType, values)
            CollectKind.AS_SET -> varargCall(b, symbols.setOf, elementType, resultType, values)
            CollectKind.AS_ARRAY -> varargCall(b, symbols.arrayOf, elementType, resultType, values)
            CollectKind.AS_SEQUENCE -> {
                val factoryType = context.irBuiltIns.functionN(0).typeWith(elementType)
                val factories = values.map<ElementBuilder, ElementBuilder> { value ->
                    { inner -> inner.irLambda(factoryType) { lambda, _ -> value(lambda) } }
                }
                internalsCall(b, symbols.sequenceOfFactories, resultType, listOf(elementType), varargs.build(b, factoryType, factories))
            }
            // the count is checked on the FIR side
            CollectKind.SINGLE -> values.singleOrNull()?.invoke(b) ?: error("Collector: collectSingle() needs exactly one item")
            CollectKind.SINGLE_OR_NULL -> values.singleOrNull()?.invoke(b) ?: b.irNull(resultType)
            CollectKind.WITH_ANNOTATION -> withAnnotations(b, typeArguments, entries)
            CollectKind.AS_MAP -> internalsCall(b, symbols.uniqueMapOf, resultType, typeArguments, withAnnotations(b, typeArguments, entries))
        }
    }

    /** `withAnnotations(h1.annotations_X() to h1.as_C(), …)`: one pair per annotation of each item. */
    private fun withAnnotations(b: IrBuilderWithScope, typeArguments: List<IrType>, entries: List<CollectedEntry>): IrExpression {
        val (a, t) = typeArguments
        val listOfA = context.irBuiltIns.listClass.typeWith(a)
        val pairType = symbols.pair.typeWith(listOfA, t)
        val pairs = entries.flatMap { e ->
            e.annotations.mapNotNull<ClassId, ElementBuilder> { x ->
                val annotationsFn = e.holder.owner.functions.firstOrNull { it.name == CollectorNames.annotationsFunction(x) } ?: return@mapNotNull null
                val pair: ElementBuilder = { inner ->
                    // not irCallConstructor(): it types the call as Pair<A, B>, with the type parameters of Pair itself
                    inner.irCall(symbols.pair.owner.primaryConstructor!!.symbol, pairType).apply {
                        this.typeArguments[0] = listOfA
                        this.typeArguments[1] = t
                        arguments[0] = inner.irCall(annotationsFn.symbol).apply { arguments[0] = inner.irGetObject(e.holder) }
                        arguments[1] = value(e)(inner)
                    }
                }
                pair
            }
        }
        val resultType = context.irBuiltIns.listClass.typeWith(symbols.pair.typeWith(a, t))
        return internalsCall(b, symbols.withAnnotations, resultType, typeArguments, varargs.build(b, pairType, pairs))
    }

    /** `CollectorInternals.<function>(<argument>)` */
    private fun internalsCall(
        b: IrBuilderWithScope, function: IrSimpleFunctionSymbol, resultType: IrType, typeArguments: List<IrType>, argument: IrExpression,
    ): IrExpression =
        b.irCall(function, resultType, typeArguments = typeArguments).apply {
            arguments[0] = b.irGetObject(symbols.internals)
            arguments[1] = argument
        }

    private fun varargCall(
        b: IrBuilderWithScope, function: IrSimpleFunctionSymbol, elementType: IrType, resultType: IrType, values: List<ElementBuilder>,
    ): IrExpression =
        b.irCall(function, resultType, typeArguments = listOf(elementType)).apply {
            arguments[0] = varargs.build(b, elementType, values)
        }

    /** `lazy { <body> }` */
    private fun lazyOf(b: IrBuilderWithScope, valueType: IrType, lazyType: IrType, body: (IrBuilderWithScope) -> IrExpression): IrExpression {
        val initializerType = context.irBuiltIns.functionN(0).typeWith(valueType)
        return b.irCall(symbols.lazy, lazyType, typeArguments = listOf(valueType)).apply {
            arguments[0] = b.irLambda(initializerType) { lambda, _ -> body(lambda) }
        }
    }
}
