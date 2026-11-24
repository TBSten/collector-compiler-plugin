package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.CollectorPluginKey
import me.tbsten.collector.compiler.debugLog
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
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

/**
 * Declares the hints and the holder objects of this module in `collector.hints`. Only signatures are generated here;
 * the bodies are filled in IR.
 *
 * What to generate comes from the session components [CollectorIndex] and [CollectorItemIndex].
 */
@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class CollectorHintGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private val collectors get() = session.collectorIndex
    private val items get() = session.collectorItemIndex

    override fun hasPackage(packageFqName: FqName): Boolean = packageFqName == CollectorNames.HINTS_PACKAGE

    override fun getTopLevelClassIds(): Set<ClassId> = items.own.byHolder.keys

    override fun getTopLevelCallableIds(): Set<CallableId> = items.hints.keys

    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        if (classId !in items.own.byHolder) return null
        return createTopLevelClass(classId, CollectorPluginKey, ClassKind.OBJECT).symbol
    }

    /** The item [cls] is the holder of, or null when [cls] is not a holder of this module. */
    private fun itemOf(cls: FirClassSymbol<*>): ItemDef? =
        if (cls.isCollectorGenerated()) items.own.byHolder[cls.classId] else null

    private fun FirClassSymbol<*>.isCollectorGenerated(): Boolean =
        (origin as? FirDeclarationOrigin.Plugin)?.key == CollectorPluginKey

    /** The visible Collectors this item can be converted for (spec 04); the others are left to the checker's warning. */
    private fun convertibleCollectors(item: ItemDef): List<CollectorDef> =
        collectors.collectorsFor(item.annotation).filter { c ->
            val t = c.elementType ?: return@filter false
            val problem = conversionProblem(session, item.declaration, t)
            if (problem != null) debugLog { "${item.fqName} is not converted for ${c.collector}: $problem" }
            problem == null
        }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        val item = itemOf(classSymbol) ?: return emptySet()
        return buildSet {
            add(SpecialNames.INIT)
            convertibleCollectors(item).forEach { add(CollectorNames.asFunction(it.collector)) }
        }
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        itemOf(context.owner) ?: return emptyList()
        return listOf(createDefaultPrivateConstructor(context.owner, CollectorPluginKey).symbol)
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> =
        if (context != null) generateHolderMembers(callableId, context.owner) else generateHints(callableId)

    /** `as_<enc(C)>(): T` on a holder, for each Collector C the item converts for. */
    private fun generateHolderMembers(callableId: CallableId, owner: FirClassSymbol<*>): List<FirNamedFunctionSymbol> {
        val item = itemOf(owner) ?: return emptyList()
        return convertibleCollectors(item)
            .filter { CollectorNames.asFunction(it.collector) == callableId.callableName }
            .mapNotNull { c ->
                val type = c.elementType ?: return@mapNotNull null
                createMemberFunction(owner, CollectorPluginKey, callableId.callableName, type).symbol
            }
    }

    private fun generateHints(callableId: CallableId): List<FirNamedFunctionSymbol> {
        if (callableId.packageName != CollectorNames.HINTS_PACKAGE) return emptyList()
        val unit = session.builtinTypes.unitType.coneType
        return items.hints[callableId].orEmpty().map { hint ->
            createTopLevelFunction(CollectorPluginKey, callableId, unit, containingFileName = hint.fileName) {
                hint.parameters.forEach { (name, type) -> valueParameter(name, type.constructClassLikeType()) }
            }.symbol
        }
    }
}
