package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.name.CallableId

/**
 * The items of this module and the hints it declares: what [CollectorHintGenerator] generates.
 *
 * Both are computed once per session, on first use, and shared by everything that needs them, so the generator
 * itself keeps no state. [moduleName] is the name of the module being compiled, which is part of what an item is
 * (see [me.tbsten.collector.compiler.ItemIdentity]); a session only knows the name of its own source set.
 */
class CollectorItemIndex(session: FirSession, private val moduleName: String) : FirExtensionSessionComponent(session) {
    internal val own: OwnItems by session.firCachesFactory.createLazyValue {
        val inherited = session.moduleData.allDependsOnDependencies.map { it.session.collectorItemIndex.own }
        ItemScanner(session, moduleName, session.collectorIndex).scan(inherited)
    }

    internal val hints: Map<CallableId, List<HintSpec>> by session.firCachesFactory.createLazyValue {
        hintSpecs(session.collectorIndex.ownCollectors, own)
    }
}

internal val FirSession.collectorItemIndex: CollectorItemIndex by FirSession.sessionComponentAccessor()
