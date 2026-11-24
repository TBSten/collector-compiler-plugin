package me.tbsten.collector.compiler

import me.tbsten.collector.compiler.fir.CollectorFirExtensionRegistrar
import me.tbsten.collector.compiler.ir.CollectorIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class CollectorCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = CollectorNames.PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(CollectorFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(CollectorIrGenerationExtension())
    }
}
