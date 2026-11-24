package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildEnumEntryDeserializedAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind

private val MESSAGE = Name.identifier("message")
private val LEVEL = Name.identifier("level")
private val HIDDEN = Name.identifier("HIDDEN")

/** `@Deprecated(HINT_MESSAGE, level = HIDDEN)`: keeps a hint out of completion and records the hint format. */
internal fun hintFormatMarker(): FirAnnotation = buildAnnotation {
    annotationTypeRef = StandardClassIds.Annotations.Deprecated.constructClassLikeType().toFirResolvedTypeRef()
    argumentMapping = buildAnnotationArgumentMapping {
        mapping[MESSAGE] = buildLiteralExpression(null, ConstantValueKind.String, CollectorNames.HINT_MESSAGE, setType = true)
        mapping[LEVEL] = buildEnumEntryDeserializedAccessExpression {
            enumClassId = StandardClassIds.DeprecationLevel
            enumEntryName = HIDDEN
        }
    }
}

/** The hint format a hint was written with (see [CollectorNames.HINT_FORMAT]), or null when it records none. */
@OptIn(SymbolInternals::class)
internal fun FirCallableSymbol<*>.hintFormat(): Int? {
    // Raw annotations without type-alias expansion: this runs while hints are generated, and expanding types would go
    // back through the symbol providers into the generator. Hint annotations are always written with their real class.
    val marker = fir.annotations.firstOrNull { it.annotationTypeRef.coneTypeOrNull?.classId == StandardClassIds.Annotations.Deprecated }
        ?: return null
    val message = (marker.argumentMapping.mapping[MESSAGE] as? FirLiteralExpression)?.value as? String ?: return null
    return CollectorNames.hintFormatOf(message)
}

internal fun FirCallableSymbol<*>.hasCurrentHintFormat(): Boolean = hintFormat() == CollectorNames.HINT_FORMAT

/** `collectorKey_… in <module> (format 0)`, for diagnostics. */
internal fun FirCallableSymbol<*>.describeHint(): String =
    "$name in ${moduleData.name} (${hintFormat()?.let { "format $it" } ?: "no format"})"
