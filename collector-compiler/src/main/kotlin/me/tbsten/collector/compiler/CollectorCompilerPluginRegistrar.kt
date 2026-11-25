package me.tbsten.collector.compiler

import me.tbsten.collector.compiler.fir.CollectorFirExtensionRegistrar
import me.tbsten.collector.compiler.ir.CollectorIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class CollectorCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = CollectorNames.PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // items are named after their module; without a name (not passed by some tools) they only differ by declaration
        val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME].orEmpty()
        FirExtensionRegistrarAdapter.registerExtension(CollectorFirExtensionRegistrar(moduleName))
        IrGenerationExtension.registerExtension(CollectorIrGenerationExtension())
    }
}
