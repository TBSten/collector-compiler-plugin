package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

/**
 * Reads hints the way a call site sees them: those of this module and of everything on its classpath.
 *
 * Only for checkers. Hint generation must not come here: looking up `collector.hints` through the session's symbol
 * provider asks the generator, which would recurse.
 */
class CollectorHints(session: FirSession) : FirExtensionSessionComponent(session) {
    private fun hints(id: CallableId): List<FirNamedFunctionSymbol> =
        session.symbolProvider.getTopLevelFunctionSymbols(id.packageName, id.callableName)

    private fun FirNamedFunctionSymbol.parameterClassId(index: Int): ClassId? =
        valueParameterSymbols.getOrNull(index)?.resolvedReturnType?.classId

    private val annotationsByCollector: FirCache<ClassId, List<ClassId>, Nothing?> =
        session.firCachesFactory.createCache { collector ->
            hints(CollectorNames.collectorKeyHint(collector)).mapNotNull { it.parameterClassId(0) }.distinct()
        }

    /** Annotations X the collector [collector] collects, read from its `collectorKey_` hints. */
    internal fun annotationsOf(collector: ClassId): List<ClassId> = annotationsByCollector.getValue(collector)

    /** FQNs of the items the call site sees for [collector], with the same rules as the IR rewrite. */
    internal fun itemsOf(collector: ClassId): List<String> {
        val asName = CollectorNames.asFunction(collector)
        return annotationsOf(collector)
            .flatMap { x -> hints(CollectorNames.itemHint(x)) }
            .mapNotNull { it.parameterClassId(0) }
            .distinct()
            .filter { holderId ->
                val holder = session.symbolProvider.getClassLikeSymbolByClassId(holderId) as? FirRegularClassSymbol ?: return@filter false
                session.declaredMemberScope(holder, null).getFunctions(asName).isNotEmpty()
            }
            .map { CollectorNames.decodeHolder(it) }
            .sorted()
    }
}

internal val FirSession.collectorHints: CollectorHints by FirSession.sessionComponentAccessor()
