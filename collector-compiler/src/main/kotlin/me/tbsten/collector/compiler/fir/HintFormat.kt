package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildEnumEntryDeserializedAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
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
