package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.ItemIdentity
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

/**
 * The items of this module and the hints it declares: what [CollectorHintGenerator] generates.
 *
 * Both are computed once per session, on first use, and shared by everything that needs them, so the generator
 * itself keeps no state. [moduleName] is the name of the module being compiled, which is part of what an item is
 * (see [me.tbsten.collector.compiler.ItemIdentity]); a session only knows the name of its own source set.
 */
class CollectorItemIndex(session: FirSession, private val moduleName: String) : FirExtensionSessionComponent(session) {
    /** The items of the source sets this one depends on (commonMain for jvmMain). */
    private val inherited: List<OwnItems> by session.firCachesFactory.createLazyValue {
        session.moduleData.allDependsOnDependencies.map { it.session.collectorItemIndex.own }
    }

    internal val own: OwnItems by session.firCachesFactory.createLazyValue {
        ItemScanner(session, moduleName, session.collectorIndex).scan(inherited)
    }

    private val expectItems: Map<Pair<ItemIdentity.Kind, String>, ItemDef> by session.firCachesFactory.createLazyValue {
        (inherited.flatMap { it.items } + own.items)
            .filter { (it.declaration as? FirMemberDeclaration)?.status?.isExpect == true }
            .associateBy { it.identity.kind to it.identity.fqName }
    }

    /**
     * The item of the `expect` declaration with this kind and FQN, when it is one: its `actual` declaration belongs
     * to that item and is none of its own (spec 06+).
     */
    internal fun expectItem(kind: ItemIdentity.Kind, fqName: String): ItemDef? = expectItems[kind to fqName]

    /**
     * The Collectors of this module and their annotations that only their file can name -> the marker interface
     * that stands for each in the parameters of hints (see [CollectorNames.privateMarker]).
     */
    internal val privateMarkers: Map<ClassId, ClassId> by session.firCachesFactory.createLazyValue {
        session.collectorIndex.ownCollectors.flatMap { listOf(it.collector, it.annotation) }.distinct()
            .filter { session.isFilePrivate(it) }
            .associateWith { CollectorNames.privateMarker(it, moduleName) }
    }

    internal val hints: Map<CallableId, List<HintSpec>> by session.firCachesFactory.createLazyValue {
        hintSpecs(session.collectorIndex.ownCollectors, own, privateMarkers)
    }
}

internal val FirSession.collectorItemIndex: CollectorItemIndex by FirSession.sessionComponentAccessor()
