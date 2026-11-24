package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorNames
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * The declarations of the standard library and of collector-runtime that generated code calls. Each is looked up at
 * most once per module, and only when something needs it.
 */
internal class CollectorSymbols(context: IrPluginContext) {
    private val finder = context.finderForBuiltins()

    val anyConstructor: IrConstructorSymbol by lazy { context.irBuiltIns.anyClass.constructors.single() }

    val listOf: IrSimpleFunctionSymbol by lazy { varargFunction("kotlin.collections", "listOf") }
    val setOf: IrSimpleFunctionSymbol by lazy { varargFunction("kotlin.collections", "setOf") }
    val arrayOf: IrSimpleFunctionSymbol by lazy { varargFunction("kotlin", "arrayOf") }

    /** `lazy(initializer)` */
    val lazy: IrSimpleFunctionSymbol by lazy {
        function(CallableId(FqName("kotlin"), Name.identifier("lazy"))) { it.owner.parameters.size == 1 }
    }

    val internals: IrClassSymbol by lazy {
        finder.findClass(CollectorNames.COLLECTOR_INTERNALS) ?: notFound(CollectorNames.COLLECTOR_INTERNALS.asFqNameString())
    }
    val sequenceOfFactories: IrSimpleFunctionSymbol by lazy { internalsFunction("sequenceOfFactories") }

    /** The overload of [pkg].[name] with a single vararg parameter. */
    private fun varargFunction(pkg: String, name: String): IrSimpleFunctionSymbol =
        function(CallableId(FqName(pkg), Name.identifier(name))) { it.owner.parameters.singleOrNull()?.isVararg == true }

    private fun internalsFunction(name: String): IrSimpleFunctionSymbol =
        function(CallableId(CollectorNames.COLLECTOR_INTERNALS, Name.identifier(name))) { true }

    private fun function(id: CallableId, filter: (IrSimpleFunctionSymbol) -> Boolean): IrSimpleFunctionSymbol =
        finder.findFunctions(id).singleOrNull(filter) ?: notFound(id.asSingleFqName().asString())

    private fun notFound(fqName: String): Nothing =
        error("Collector: $fqName not found; the collector-runtime on the classpath must match the compiler plugin")
}
