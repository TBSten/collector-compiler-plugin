package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectKind
import me.tbsten.collector.compiler.fir.CollectorErrors
import me.tbsten.collector.compiler.fir.VisibleItem
import me.tbsten.collector.compiler.fir.collectorHints
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.platform.isCommon

/**
 * Checks what depends on the full set of items: the item count of `collectSingle()` / `collectSingleOrNull()` and
 * the keys of `collectAsMap()`.
 *
 * Platform kind: the full set of items is only known when compiling for a platform, so metadata (common) compilation
 * is skipped.
 */
internal object CollectResultChecker : FirFunctionCallChecker(MppCheckerKind.Platform) {
    private val CHECKED_KINDS = setOf(CollectKind.SINGLE, CollectKind.SINGLE_OR_NULL, CollectKind.AS_MAP)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val call = collectCall(expression) ?: return
        val kind = call.function.kind
        if (kind !in CHECKED_KINDS) return
        val session = context.session
        if (session.moduleData.platform.isCommon()) return
        if (!call.receiverIsValid || (call.byTag && call.tag == null)) return
        val source = expression.source ?: return
        val items = session.collectorHints.itemsOf(call.collectors.map { it.classId })
        when (kind) {
            CollectKind.SINGLE -> if (items.size != 1) reporter.reportOn(source, CollectorErrors.COLLECT_SINGLE_COUNT, describeCount(items))
            CollectKind.SINGLE_OR_NULL -> if (items.size > 1) reporter.reportOn(source, CollectorErrors.COLLECT_SINGLE_OR_NULL_COUNT, describeCount(items))
            CollectKind.AS_MAP -> {
                val keys = items.flatMap { item -> item.annotations.map { annotationKey(it) to item.fqName } }
                keys.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }.forEach { (key, owners) ->
                    reporter.reportOn(source, CollectorErrors.COLLECT_AS_MAP_DUPLICATE_KEY, "$key (${owners.joinToString()})")
                }
            }
            else -> Unit
        }
    }

    private fun describeCount(items: List<VisibleItem>): String =
        if (items.isEmpty()) "no item was found" else "${items.size} items were found (${items.joinToString { it.fqName }})"

    /** A readable, comparable form of an annotation and its arguments, e.g. `Route(path = "/home")`. */
    private fun annotationKey(annotation: FirAnnotation): String {
        val name = annotation.annotationTypeRef.coneTypeOrNull?.classId?.shortClassName?.asString() ?: "?"
        val arguments = annotation.argumentMapping.mapping.entries.sortedBy { it.key.asString() }
            .joinToString { (key, value) -> "${key.asString()} = ${argumentKey(value)}" }
        return "$name($arguments)"
    }

    private fun argumentKey(expression: FirExpression): String = when (expression) {
        is FirLiteralExpression -> if (expression.value is String) "\"${expression.value}\"" else expression.value.toString()
        is FirAnnotation -> annotationKey(expression)
        else -> constantString(expression)?.let { "\"$it\"" } ?: expression.render()
    }
}
