package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.name.CallableId

/**
 * The items of this module and the hints it declares: what [CollectorHintGenerator] generates.
 *
 * Both are computed once per session, on first use, and shared by everything that needs them, so the generator
 * itself keeps no state.
 */
class CollectorItemIndex(session: FirSession) : FirExtensionSessionComponent(session) {
    internal val own: OwnItems by session.firCachesFactory.createLazyValue {
        ItemScanner(session).scan(session.collectorIndex.targetAnnotations)
    }

    internal val hints: Map<CallableId, List<HintSpec>> by session.firCachesFactory.createLazyValue {
        hintSpecs(session.collectorIndex.ownCollectors, own)
    }
}

internal val FirSession.collectorItemIndex: CollectorItemIndex by FirSession.sessionComponentAccessor()
