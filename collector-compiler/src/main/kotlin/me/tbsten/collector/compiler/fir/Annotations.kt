package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Whether [this] annotates the declaration itself. `@get:X`, `@field:X` and other use-site targets annotate something
 * else and are not collected; `@X` and `@property:X` are (spec 06+).
 */
internal fun FirAnnotation.annotatesDeclaration(): Boolean =
    useSiteTarget == null || useSiteTarget == AnnotationUseSiteTarget.PROPERTY

/** X of a `@CollectorFor<X>` annotation, whether or not its type arguments are resolved yet. */
internal fun FirAnnotation.collectorForTarget(): ClassId? =
    annotationTypeRef.coneTypeOrNull?.typeArguments?.firstOrNull()?.type?.classId
        ?: (typeArguments.firstOrNull() as? FirTypeProjectionWithVariance)?.typeRef?.coneTypeOrNull?.classId

private val TAG = Name.identifier("tag")

/** The expression passed as `tag` to a `@CollectorFor` annotation, before or after argument resolution. */
internal fun FirAnnotation.collectorForTagArgument(): FirExpression? {
    argumentMapping.mapping[TAG]?.let { return it }
    val arguments = (this as? FirAnnotationCall)?.argumentList?.arguments ?: return null
    arguments.firstOrNull { it is FirNamedArgumentExpression && it.name == TAG }?.let { return (it as FirNamedArgumentExpression).expression }
    return arguments.firstOrNull { it !is FirNamedArgumentExpression }
}

/** The tag of a `@CollectorFor` annotation when it is a non-empty string literal (the checker reports anything else). */
internal fun FirAnnotation.collectorForTag(): String? =
    collectorForTagArgument()?.stringLiteral()?.takeIf { it.isNotEmpty() }

/**
 * The value of a plain string literal written in source, or null.
 *
 * A const val or `"a" + "b"` is also a [FirLiteralExpression] once the compiler has evaluated it, so the source element is
 * checked too: other modules read the tag before such expressions are evaluated.
 */
internal fun FirExpression.stringLiteral(): String? =
    ((this as? FirLiteralExpression)?.value as? String)?.takeIf { source?.elementType == KtNodeTypes.STRING_TEMPLATE }
