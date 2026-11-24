package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.CollectorPluginKey
import me.tbsten.collector.compiler.HintMessage
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCopy
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Declares the hints, the holder objects and the missed markers of this module in `collector.hints`. Only signatures are generated here;
 * the bodies are filled in IR.
 *
 * What to generate comes from the session components [CollectorIndex] and [CollectorItemIndex].
 */
@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class CollectorHintGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private val collectors get() = session.collectorIndex
    private val items get() = session.collectorItemIndex

    override fun hasPackage(packageFqName: FqName): Boolean = packageFqName == CollectorNames.HINTS_PACKAGE

    override fun getTopLevelClassIds(): Set<ClassId> = items.own.byHolder.keys + items.own.missedMarkers.keys

    override fun getTopLevelCallableIds(): Set<CallableId> = items.hints.keys

    /** Holders and missed markers are hidden from user code; their message says what they stand for. */
    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        val (kind, fields) = items.own.missedMarkers[classId]?.let { ClassKind.INTERFACE to listOf(HintMessage.MISSED to it) }
            ?: items.own.byHolder[classId]?.let { ClassKind.OBJECT to it.identity.toFields() }
            ?: return null
        val cls = createTopLevelClass(classId, CollectorPluginKey, kind)
        cls.replaceAnnotations(listOf(hiddenMarker(HintMessage.of(fields))))
        return cls.symbol
    }

    /** The item [cls] is the holder of, or null when [cls] is not a holder of this module. */
    private fun itemOf(cls: FirClassSymbol<*>): ItemDef? =
        if (cls.isCollectorGenerated()) items.own.byHolder[cls.classId] else null

    private fun FirClassSymbol<*>.isCollectorGenerated(): Boolean =
        (origin as? FirDeclarationOrigin.Plugin)?.key == CollectorPluginKey

    /** The visible Collectors this item can be converted for (spec 04); the others are left to the checker's warning. */
    private fun convertibleCollectors(item: ItemDef): List<CollectorDef> =
        item.annotations.flatMap { collectors.collectorsFor(it) }.distinctBy { it.collector }.filter { c ->
            val t = c.elementType ?: return@filter false
            val problem = conversionProblem(session, item.declaration, t)
            if (problem != null) debugLog { "${item.identity.fqName} is not converted for ${c.collector}: $problem" }
            problem == null
        }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        val item = itemOf(classSymbol) ?: return emptySet()
        return buildSet {
            add(SpecialNames.INIT)
            convertibleCollectors(item).forEach { add(CollectorNames.asFunction(it.collector)) }
            item.annotations.forEach { add(CollectorNames.annotationsFunction(it)) }
        }
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        itemOf(context.owner) ?: return emptyList()
        return listOf(createDefaultPrivateConstructor(context.owner, CollectorPluginKey).symbol)
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> =
        if (context != null) generateHolderMembers(callableId, context.owner) else generateHints(callableId)

    /**
     * The members of a holder: `annotations_<enc(X)>(): List<X>` for each collected annotation X on the item, and
     * `as_<enc(C)>(): T` for each Collector C the item converts for.
     */
    private fun generateHolderMembers(callableId: CallableId, owner: FirClassSymbol<*>): List<FirNamedFunctionSymbol> {
        val item = itemOf(owner) ?: return emptyList()
        item.annotations.firstOrNull { CollectorNames.annotationsFunction(it) == callableId.callableName }?.let { x ->
            return listOf(annotationsFunction(owner, item, x))
        }
        return convertibleCollectors(item)
            .filter { CollectorNames.asFunction(it.collector) == callableId.callableName }
            .mapNotNull { c ->
                val type = c.elementType ?: return@mapNotNull null
                createMemberFunction(owner, CollectorPluginKey, callableId.callableName, type).symbol
            }
    }

    /**
     * The body (IR) lists the X annotations of the item; the same annotations are copied onto the function so call
     * sites can compare their arguments (collectAsMap keys).
     */
    private fun annotationsFunction(owner: FirClassSymbol<*>, item: ItemDef, x: ClassId): FirNamedFunctionSymbol {
        // the function is named after X, and typed with the class X is in this module
        val annotationClass = collectors.annotationClassOf(x)
        val type = StandardClassIds.List.constructClassLikeType(arrayOf(annotationClass.constructClassLikeType()))
        val function = createMemberFunction(owner, CollectorPluginKey, CollectorNames.annotationsFunction(x), type)
        val copies = annotationsOfType(session, item.declaration.annotations.filter { it.annotatesDeclaration() }, annotationClass)
            .map { buildAnnotationCopy(it) { useSiteTarget = null } }
        function.replaceAnnotations(copies)
        return function.symbol
    }

    private fun generateHints(callableId: CallableId): List<FirNamedFunctionSymbol> {
        if (callableId.packageName != CollectorNames.HINTS_PACKAGE) return emptyList()
        val unit = session.builtinTypes.unitType.coneType
        return items.hints[callableId].orEmpty().map { hint ->
            val function = createTopLevelFunction(CollectorPluginKey, callableId, unit, containingFileName = hint.fileName) {
                hint.parameters.forEach { (name, type) -> valueParameter(name, type.constructClassLikeType()) }
            }
            function.replaceAnnotations(function.annotations + hiddenMarker(HintMessage.of(hint.fields)))
            function.symbol
        }
    }
}
