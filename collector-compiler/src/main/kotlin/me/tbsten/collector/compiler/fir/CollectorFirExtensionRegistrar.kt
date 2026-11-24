package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class CollectorFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // session components first: the generator reads them
        +::CollectorIndex
        +::CollectorItemIndex
        +::CollectorHintGenerator
    }
}
