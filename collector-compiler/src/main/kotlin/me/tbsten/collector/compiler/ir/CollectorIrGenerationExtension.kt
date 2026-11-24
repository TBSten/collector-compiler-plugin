package me.tbsten.collector.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * The IR part of the plugin, in three phases:
 *
 * 1. [HintBodyGenerator] gives bodies to the hints and holders FIR declared for this module.
 * 2. [HintDiscovery] finds, for a call site, the items its Collector collects, from the hints of this module and of
 *    its dependencies.
 * 3. [CollectCallTransformer] replaces each `collect*()` call with those items.
 *
 * Phases 2 and 3 run file by file. They do not depend on phase 1: call sites refer to holder members by symbol, and
 * those exist since FIR.
 */
class CollectorIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = CollectorSymbols(pluginContext)
        HintBodyGenerator(pluginContext, symbols, moduleFragment).generate()
        for (file in moduleFragment.files) {
            // a finder per file: its lookups are recorded for incremental compilation as made from this file
            val discovery = HintDiscovery(pluginContext.finderForSource(file))
            file.transform(CollectCallTransformer(pluginContext, symbols, discovery), null)
        }
    }
}
