package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isObject

/**
 * Phase 1: gives bodies to what FIR declared in `collector.hints` for this module. Hints get an empty body (only
 * their signature matters), holders get their constructor and their `as_<C>()` members.
 */
internal class HintBodyGenerator(
    private val context: IrPluginContext,
    private val symbols: CollectorSymbols,
    private val module: IrModuleFragment,
) {
    private val conversion = ItemConversion(context)

    /** FQN (as computed on the FIR side) → declaration, for every declaration of this module. */
    private val items: Map<String, IrDeclaration> by lazy {
        collectItemDeclarations().also { debugLog { "IR items in module ${module.name}: ${it.keys}" } }
    }

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
        conversion.addBridges()
    }

    private fun fillHolder(holder: IrClass) {
        holder.constructors.forEach { ctor ->
            if (ctor.body != null) return@forEach
            ctor.body = DeclarationIrBuilder(context, ctor.symbol).irBlockBody {
                +irDelegatingConstructorCall(symbols.anyConstructor.owner)
                +IrInstanceInitializerCallImpl(startOffset, endOffset, holder.symbol, context.irBuiltIns.unitType)
            }
        }
        val fqName = CollectorNames.decodeHolderName(holder.name.asString())
        val target = items[fqName]
        debugLog { "fill holder ${holder.name} -> $fqName -> ${target?.let { it::class.simpleName }}" }
        holder.functions.filter { it.isCollectorGenerated() && it.body == null }.forEach { fn ->
            target ?: error("Collector: declaration $fqName not found")
            conversion.fill(fn, target)
        }
    }

    private fun collectItemDeclarations(): Map<String, IrDeclaration> {
        val result = mutableMapOf<String, IrDeclaration>()
        fun visit(decl: IrDeclaration, prefix: String) {
            val name = when (decl) {
                is IrClass -> decl.name.asString()
                is IrSimpleFunction -> decl.name.asString()
                is IrProperty -> decl.name.asString()
                else -> return
            }
            if (decl.origin is IrDeclarationOrigin.GeneratedByPlugin) return
            val fq = if (prefix.isEmpty()) name else "$prefix.$name"
            result.putIfAbsent(fq, decl)
            if (decl is IrClass && !decl.isInner) decl.declarations.forEach { visit(it, fq) }
        }
        for (file in module.files) {
            val pkg = file.packageFqName.asString()
            file.declarations.forEach { visit(it, pkg) }
        }
        return result
    }
}
