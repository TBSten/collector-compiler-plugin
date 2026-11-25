package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.HintMessage
import me.tbsten.collector.compiler.ItemIdentity
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildEnumEntryDeserializedAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind

private val MESSAGE = Name.identifier("message")
private val LEVEL = Name.identifier("level")
private val HIDDEN = Name.identifier("HIDDEN")

/**
 * `@Deprecated(message, level = HIDDEN)`: keeps a generated declaration out of user code and records the hint format,
 * and for holders and missed markers what they stand for (see [HintMessage]).
 */
internal fun hiddenMarker(message: String = CollectorNames.HINT_MESSAGE): FirAnnotation = buildAnnotation {
    annotationTypeRef = StandardClassIds.Annotations.Deprecated.constructClassLikeType().toFirResolvedTypeRef()
    argumentMapping = buildAnnotationArgumentMapping {
        mapping[MESSAGE] = buildLiteralExpression(null, ConstantValueKind.String, message, setType = true)
        mapping[LEVEL] = buildEnumEntryDeserializedAccessExpression {
            enumClassId = StandardClassIds.DeprecationLevel
            enumEntryName = HIDDEN
        }
    }
}

/** The message of the [hiddenMarker] on a generated declaration, or null when it has none. */
@OptIn(SymbolInternals::class)
internal fun FirBasedSymbol<*>.hintMessage(): String? {
    // Raw annotations without type-alias expansion: this runs while hints are generated, and expanding types would go
    // back through the symbol providers into the generator. Hint annotations are always written with their real class.
    val marker = fir.annotations.firstOrNull { it.annotationTypeRef.coneTypeOrNull?.classId == StandardClassIds.Annotations.Deprecated }
        ?: return null
    return (marker.argumentMapping.mapping[MESSAGE] as? FirLiteralExpression)?.value as? String
}

/** The hint format a hint was written with (see [CollectorNames.HINT_FORMAT]), or null when it records none. */
internal fun FirCallableSymbol<*>.hintFormat(): Int? = hintMessage()?.let { CollectorNames.hintFormatOf(it) }

/** The item a holder stands for, or null when [this] is not a holder this plugin can read. */
internal fun FirClassLikeSymbol<*>.itemIdentity(): ItemIdentity? =
    hintMessage()?.let { ItemIdentity.fromFields(HintMessage.fields(it)) }

/**
 * X of a `collectorFor_` or `collectorKey_` hint as the module of the Collector knows it: the class id its message
 * records, or else the type of the parameter `a`.
 */
internal fun FirCallableSymbol<*>.hintAnnotation(): ClassId? =
    hintMessage()?.let { HintMessage.fields(it)[HintMessage.ANNOTATION] }?.let { ClassId.fromString(it) }
        ?: (this as? FirNamedFunctionSymbol)?.valueParameterSymbols?.firstOrNull()?.resolvedReturnType?.classId

/**
 * The Collector of a `collectorFor_` or `collectorTag_` hint, whose parameter [parameter] is `c`: the class id its
 * message records, or else the type of that parameter (a marker stands there for a private Collector).
 */
internal fun FirCallableSymbol<*>.hintCollector(parameter: Int): ClassId? =
    hintMessage()?.let { HintMessage.fields(it)[HintMessage.COLLECTOR] }?.let { ClassId.fromString(it) }
        ?: (this as? FirNamedFunctionSymbol)?.valueParameterSymbols?.getOrNull(parameter)?.resolvedReturnType?.classId

/** The Collectors that were visible where the item of the holder [this] is declared (see [HintMessage.COLLECTOR]). */
internal fun FirClassLikeSymbol<*>.visibleCollectors(): Set<ClassId> =
    hintMessage()?.let { message -> HintMessage.values(message, HintMessage.COLLECTOR).mapTo(mutableSetOf()) { ClassId.fromString(it) } }.orEmpty()

/** The declaration a missed marker names, or null when [this] is not a missed marker this plugin can read. */
internal fun FirClassLikeSymbol<*>.missedDeclaration(): String? =
    hintMessage()?.let { HintMessage.fields(it)[HintMessage.MISSED] }

internal fun FirCallableSymbol<*>.hasCurrentHintFormat(): Boolean = hintFormat() == CollectorNames.HINT_FORMAT

/** `collectorKey_… in <module> (format 0)`, for diagnostics. */
internal fun FirCallableSymbol<*>.describeHint(): String =
    "$name in ${moduleData.name} (${hintFormat()?.let { "format $it" } ?: "no format"})"
