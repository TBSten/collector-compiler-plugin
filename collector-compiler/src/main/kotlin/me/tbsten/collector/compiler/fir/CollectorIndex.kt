package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId

/** A `@CollectorFor<X>` on an object. */
internal data class CollectorDef(
    val collector: ClassId,
    val annotation: ClassId,
    val symbol: FirClassSymbol<*>,
    /** `tag` of `@CollectorFor`; only known (and only needed) for this module's own Collectors. */
    val tag: String? = null,
) {
    /** T of `Collector<T>`. Only call after the SUPERTYPES phase. */
    val elementType: ConeKotlinType? get() = symbol.collectorElementType()
}

/** T of the `Collector<T>` this class implements, or null when it does not. Only call after the SUPERTYPES phase. */
internal fun FirClassSymbol<*>.collectorElementType(): ConeKotlinType? =
    resolvedSuperTypes.firstOrNull { it.classId == CollectorNames.COLLECTOR }?.typeArguments?.firstOrNull()?.type

/** The Collectors visible from this module: its own `@CollectorFor` objects and the `collectorFor_` hints on the classpath. */
class CollectorIndex(session: FirSession) : FirExtensionSessionComponent(session) {
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(COLLECTOR_FOR_PREDICATE)
    }

    // The annotations are read unresolved (`fir`): this runs while declarations are generated, before the phase that
    // resolves them.
    @OptIn(SymbolInternals::class)
    internal val ownCollectors: List<CollectorDef> by session.firCachesFactory.createLazyValue {
        session.predicateBasedProvider.getSymbolsByPredicate(COLLECTOR_FOR_PREDICATE)
            .filterIsInstance<FirRegularClassSymbol>()
            .filter { it.classKind == ClassKind.OBJECT } // others are reported by CollectorForChecker
            .flatMap { cls ->
                cls.fir.annotations
                    .filter { it.annotationTypeRef.coneTypeOrNull?.classId == CollectorNames.COLLECTOR_FOR }
                    .mapNotNull { ann ->
                        val x = ann.collectorForTarget()
                        debugLog { "own @CollectorFor on ${cls.classId} -> X=$x" }
                        x?.let { CollectorDef(cls.classId, it, cls, ann.collectorForTag()) }
                    }
            }
            .distinct()
    }

    internal val classpathCollectors: List<CollectorDef> by session.firCachesFactory.createLazyValue {
        val provider = session.dependenciesSymbolProvider
        val names = provider.symbolNamesProvider.getTopLevelCallableNamesInPackage(CollectorNames.HINTS_PACKAGE).orEmpty()
        names.filter { it.asString().startsWith(CollectorNames.COLLECTOR_FOR_PREFIX) }
            .flatMap { name -> provider.getTopLevelFunctionSymbols(CollectorNames.HINTS_PACKAGE, name) }
            .mapNotNull { fn ->
                val params = fn.valueParameterSymbols
                val x = params.getOrNull(0)?.resolvedReturnType?.classId ?: return@mapNotNull null
                val cId = params.getOrNull(1)?.resolvedReturnType?.classId ?: return@mapNotNull null
                // not toRegularClassSymbol(): that goes through the generated-declarations provider and recurses
                val c = provider.getClassLikeSymbolByClassId(cId) as? FirRegularClassSymbol ?: return@mapNotNull null
                CollectorDef(c.classId, x, c)
            }
            .distinct()
            .also { debugLog { "classpath collectors: $it" } }
    }

    internal val allCollectors: List<CollectorDef> by session.firCachesFactory.createLazyValue { ownCollectors + classpathCollectors }

    private val collectorsByAnnotation: Map<ClassId, List<CollectorDef>> by session.firCachesFactory.createLazyValue {
        allCollectors.groupBy { it.annotation }
    }

    /** Annotations X that at least one visible Collector collects. */
    internal val targetAnnotations: Set<ClassId> get() = collectorsByAnnotation.keys

    internal fun collectorsFor(annotation: ClassId): List<CollectorDef> = collectorsByAnnotation[annotation].orEmpty()

    private companion object {
        val COLLECTOR_FOR_PREDICATE = LookupPredicate.create { annotated(CollectorNames.COLLECTOR_FOR.asSingleFqName()) }
    }
}

internal val FirSession.collectorIndex: CollectorIndex by FirSession.sessionComponentAccessor()
