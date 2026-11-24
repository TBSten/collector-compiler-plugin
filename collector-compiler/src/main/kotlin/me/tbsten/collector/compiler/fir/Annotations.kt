package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId

/** X of a `@CollectorFor<X>` annotation, whether or not its type arguments are resolved yet. */
internal fun FirAnnotation.collectorForTarget(): ClassId? =
    annotationTypeRef.coneTypeOrNull?.typeArguments?.firstOrNull()?.type?.classId
        ?: (typeArguments.firstOrNull() as? FirTypeProjectionWithVariance)?.typeRef?.coneTypeOrNull?.classId
