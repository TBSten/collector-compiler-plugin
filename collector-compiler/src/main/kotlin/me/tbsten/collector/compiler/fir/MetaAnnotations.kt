package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.name.ClassId

// An annotation class annotated with X is a meta-annotation: it counts as X wherever it is used, like a Compose
// multipreview. These functions expand meta-annotations once annotations are resolved (member generation and
// checkers); ItemScanner does the same before resolution, from source.

/** The [x] annotations among [annotations], with meta-annotations expanded in place (source order, depth first). */
internal fun annotationsOfType(
    session: FirSession,
    annotations: List<FirAnnotation>,
    x: ClassId,
    path: Set<ClassId> = emptySet(),
): List<FirAnnotation> =
    annotations.flatMap { annotation ->
        val id = annotation.annotationTypeRef.coneTypeOrNull?.classId ?: return@flatMap emptyList()
        if (id == x) return@flatMap listOf(annotation)
        if (id in path || id.packageFqName.asString().startsWith("kotlin")) return@flatMap emptyList()
        val meta = (session.symbolProvider.getClassLikeSymbolByClassId(id) as? FirRegularClassSymbol)
            ?.takeIf { it.classKind == ClassKind.ANNOTATION_CLASS } ?: return@flatMap emptyList()
        annotationsOfType(session, meta.resolvedAnnotationsWithArguments, x, path + id)
    }

/** The annotations on [declaration] itself (no use-site targets), and those reached through meta-annotations. */
internal fun expandedAnnotationIds(session: FirSession, declaration: FirDeclaration): Set<ClassId> {
    val result = mutableSetOf<ClassId>()
    fun visit(ids: List<ClassId>, path: Set<ClassId>) {
        for (id in ids) {
            result += id
            if (id in path || id.packageFqName.asString().startsWith("kotlin")) continue
            val meta = session.symbolProvider.getClassLikeSymbolByClassId(id) as? FirRegularClassSymbol ?: continue
            if (meta.classKind != ClassKind.ANNOTATION_CLASS) continue
            visit(meta.resolvedAnnotationsWithArguments.mapNotNull { it.toAnnotationClassId(session) }, path + id)
        }
    }
    visit(declaration.annotations.filter { it.annotatesDeclaration() }.mapNotNull { it.toAnnotationClassId(session) }, emptySet())
    return result
}
