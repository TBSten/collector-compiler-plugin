package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isObject

/**
 * Phase 1: gives bodies to what FIR declared in `collector.hints` for this module. Hints get an empty body (only
 * their signature matters), holders get their constructor and their `as_<C>()` and `annotations_<X>()` members.
 */
internal class HintBodyGenerator(
    private val context: IrPluginContext,
    private val symbols: CollectorSymbols,
    private val module: IrModuleFragment,
) {
    private val bridges = ItemBridges(context)
    private val conversion = ItemConversion(context, bridges)
    private val annotations = ItemAnnotations(context, symbols)
    private val declarations = ItemDeclarations(module)

    fun generate() {
        for (file in module.files) {
            if (file.packageFqName != CollectorNames.HINTS_PACKAGE) continue
            for (declaration in file.declarations) {
                if (!declaration.isCollectorGenerated()) continue
                when (declaration) {
                    is IrSimpleFunction -> if (declaration.body == null) {
                        declaration.body = DeclarationIrBuilder(context, declaration.symbol).irBlockBody { }
                    }
                    is IrClass -> if (declaration.isObject) fillHolder(declaration)
                    else -> Unit
                }
            }
        }
        bridges.addAll()
    }

    private fun fillHolder(holder: IrClass) {
        holder.constructors.forEach { ctor ->
            if (ctor.body != null) return@forEach
            ctor.body = DeclarationIrBuilder(context, ctor.symbol).irBlockBody {
                +irDelegatingConstructorCall(symbols.anyConstructor.owner)
                +IrInstanceInitializerCallImpl(startOffset, endOffset, holder.symbol, context.irBuiltIns.unitType)
            }
        }
        val pending = holder.functions.filter { it.isCollectorGenerated() && it.body == null }.toList()
        if (pending.isEmpty()) return
        val identity = holder.itemIdentity() ?: error("Collector: the holder ${holder.name} does not record its item")
        val (annotationFunctions, conversionFunctions) = pending.partition { it.name.asString().startsWith(CollectorNames.ANNOTATIONS_PREFIX) }
        val target = declarations.find(identity, annotationFunctions.map { annotations.annotationClass(it) })
            ?: error("Collector: declaration ${identity.fqName} not found")
        debugLog { "fill holder ${holder.name} -> ${identity.fqName} -> ${target::class.simpleName}" }
        annotationFunctions.forEach { annotations.fill(it, target) }
        conversionFunctions.forEach { conversion.fill(it, target) }
    }
}
