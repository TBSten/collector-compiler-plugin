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
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId

/** A `@CollectorFor<X>` on an object. */
internal data class CollectorDef(
    val collector: ClassId,
    /**
     * X as the module of the Collector knows it, type aliases expanded. The hints of the Collector and of its items
     * are named after it. Another module may see another class behind it (an `expect` annotation with an
     * `actual typealias`): see [CollectorIndex.annotationClassOf].
     */
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
    internal val annotationResolver = AnnotationResolver(session)

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(COLLECTOR_FOR_PREDICATE)
    }

    /**
     * The objects of this module the compiler found `@CollectorFor` on early enough for hints. It only finds the
     * annotation under its own name: `CollectorForChecker` reports an object that is missing here.
     */
    private val annotatedObjects: List<FirRegularClassSymbol> by session.firCachesFactory.createLazyValue {
        session.predicateBasedProvider.getSymbolsByPredicate(COLLECTOR_FOR_PREDICATE)
            .filterIsInstance<FirRegularClassSymbol>()
            .filter { it.classKind == ClassKind.OBJECT } // others are reported by CollectorForChecker
    }

    internal fun isRegistered(collector: ClassId): Boolean = annotatedObjects.any { it.classId == collector }

    // The annotations are read unresolved (`fir`): this runs while declarations are generated, before the phase that
    // resolves them.
    @OptIn(SymbolInternals::class)
    internal val ownCollectors: List<CollectorDef> by session.firCachesFactory.createLazyValue {
        annotatedObjects
            .flatMap { cls ->
                cls.fir.annotations
                    .filter { it.annotationTypeRef.coneTypeOrNull?.classId == CollectorNames.COLLECTOR_FOR }
                    .mapNotNull { ann ->
                        // the type argument is resolved by now, but an alias of this module is not expanded yet
                        val x = ann.collectorForTarget()?.let { annotationResolver.expand(it) ?: it }
                        debugLog { "own @CollectorFor on ${cls.classId} -> X=$x" }
                        x?.let { CollectorDef(cls.classId, it, cls, ann.collectorForTag()) }
                    }
            }
            .distinct()
    }

    /** Every `collectorFor_` hint on the classpath, of any hint format. */
    private val classpathCollectorHints: List<FirNamedFunctionSymbol> by session.firCachesFactory.createLazyValue {
        val provider = session.dependenciesSymbolProvider
        val names = provider.symbolNamesProvider.getTopLevelCallableNamesInPackage(CollectorNames.HINTS_PACKAGE).orEmpty()
        names.filter { it.asString().startsWith(CollectorNames.COLLECTOR_FOR_PREFIX) }
            .flatMap { name -> provider.getTopLevelFunctionSymbols(CollectorNames.HINTS_PACKAGE, name) }
    }

    /** `collectorFor_` hints written by another hint format: they are not read, and items they concern are reported. */
    internal val incompatibleCollectorHints: List<FirNamedFunctionSymbol> by session.firCachesFactory.createLazyValue {
        classpathCollectorHints.filter { !it.hasCurrentHintFormat() }
    }

    internal val classpathCollectors: List<CollectorDef> by session.firCachesFactory.createLazyValue {
        val provider = session.dependenciesSymbolProvider
        classpathCollectorHints.filter { it.hasCurrentHintFormat() }
            .mapNotNull { fn ->
                val x = fn.hintAnnotation() ?: return@mapNotNull null
                val cId = fn.hintCollector(parameter = 1) ?: return@mapNotNull null
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

    internal fun collectorsFor(annotation: ClassId): List<CollectorDef> = collectorsByAnnotation[annotation].orEmpty()

    /**
     * The annotation class X is in this module. Almost always X itself; for an `expect` annotation whose `actual` is
     * a type alias, the class behind the alias in the modules that see the alias.
     */
    internal fun annotationClassOf(annotation: ClassId): ClassId = annotationResolver.expand(annotation) ?: annotation

    private val targetsByClass: Map<ClassId, Set<ClassId>> by session.firCachesFactory.createLazyValue {
        collectorsByAnnotation.keys.groupBy { annotationClassOf(it) }.mapValues { (_, xs) -> xs.toSet() }
    }

    /** The X's of the visible Collectors that the annotation class [annotationClass] is: empty when none collects it. */
    internal fun targetsOf(annotationClass: ClassId): Set<ClassId> = targetsByClass[annotationClass].orEmpty()

    private companion object {
        val COLLECTOR_FOR_PREDICATE = LookupPredicate.create { annotated(CollectorNames.COLLECTOR_FOR.asSingleFqName()) }
    }
}

internal val FirSession.collectorIndex: CollectorIndex by FirSession.sessionComponentAccessor()
