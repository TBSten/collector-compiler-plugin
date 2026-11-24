package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.fir.checkers.CollectorCheckers
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class CollectorFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // session components first: the generator and the checkers read them
        +::CollectorIndex
        +::CollectorItemIndex
        +::CollectorHints
        +::CollectorHintGenerator
        +::CollectorCheckers
        registerDiagnosticContainers(CollectorErrors)
    }
}
