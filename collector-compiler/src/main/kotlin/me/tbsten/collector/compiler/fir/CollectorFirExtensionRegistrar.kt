package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.fir.checkers.CollectorCheckers
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class CollectorFirExtensionRegistrar(private val moduleName: String) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // session components first: the generator and the checkers read them
        +::CollectorIndex
        +{ session: FirSession -> CollectorItemIndex(session, moduleName) }
        +::CollectorHints
        +::CollectorHintGenerator
        +::CollectorCheckers
        registerDiagnosticContainers(CollectorErrors)
    }
}
