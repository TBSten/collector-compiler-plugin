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
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.FirEnumEntryDeserializedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.FirWrappedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.references.toResolvedEnumEntrySymbol
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.renderReadableWithFqNames
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
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

    /**
     * A readable form of an annotation and its arguments, e.g. `Route(path = "/home")`, that is the same text for the
     * same arguments however they are written (a literal or a const val, `[…]` or `arrayOf(…)`) and wherever they are
     * read from (source, or a compiled module).
     *
     * Only what is written is compared: an argument left to its default value and the same value written out are
     * two keys here. The generated code checks the keys again when it builds the map.
     */
    context(context: CheckerContext)
    private fun annotationKey(annotation: FirAnnotation): String =
        annotationKey(annotation.annotationTypeRef.coneTypeOrNull?.classId, annotation.argumentMapping.mapping)

    context(context: CheckerContext)
    private fun annotationKey(annotationClass: ClassId?, arguments: Map<Name, FirExpression>): String {
        // a compiled annotation has `xs = []` for a vararg without arguments, and a written one has nothing
        val varargs = (annotationClass?.let { context.session.symbolProvider.getClassLikeSymbolByClassId(it) } as? FirRegularClassSymbol)
            ?.primaryConstructorIfAny(context.session)?.valueParameterSymbols.orEmpty().filter { it.isVararg }.map { it.name }
        val keys = arguments.mapValues { (_, value) -> argumentKey(value) } + (varargs - arguments.keys).associateWith { "[]" }
        val rendered = keys.entries.sortedBy { it.key.asString() }.joinToString { (name, value) -> "${name.asString()} = $value" }
        return "${annotationClass?.shortClassName?.asString() ?: "?"}($rendered)"
    }

    context(context: CheckerContext)
    private fun argumentKey(expression: FirExpression): String = when (expression) {
        is FirLiteralExpression -> if (expression.value is String) "\"${expression.value}\"" else expression.value.toString()
        is FirAnnotation -> annotationKey(expression)
        is FirWrappedArgumentExpression -> argumentKey(expression.expression)
        is FirCollectionLiteral -> arrayKey(expression.argumentList.arguments)
        is FirVarargArgumentsExpression -> arrayKey(expression.arguments)
        is FirGetClassCall -> expression.resolvedType.typeArguments.firstOrNull()?.type?.let { "${it.renderReadableWithFqNames()}::class" }
        is FirEnumEntryDeserializedAccessExpression -> "${expression.enumClassId.asFqNameString()}.${expression.enumEntryName}"
        is FirPropertyAccessExpression -> expression.calleeReference.toResolvedEnumEntrySymbol()?.let { "${it.callableId?.asSingleFqName()}" }
        is FirFunctionCall -> {
            val constructor = expression.calleeReference.toResolvedConstructorSymbol()
            val arguments = expression.argumentList.arguments
            when {
                // an annotation as an argument is a call of its constructor in source
                constructor != null -> annotationKey(
                    constructor.resolvedReturnType.classId,
                    expression.resolvedArgumentMapping.orEmpty().entries.associate { (argument, parameter) -> parameter.name to argument },
                )
                // arrayOf(…), intArrayOf(…): a call whose arguments are one vararg
                else -> (arguments.singleOrNull() as? FirVarargArgumentsExpression)?.let { arrayKey(it.arguments) }
            }
        }
        else -> null
    } ?: constantString(expression)?.let { "\"$it\"" } ?: expression.render()

    context(context: CheckerContext)
    private fun arrayKey(elements: List<FirExpression>): String = elements.joinToString(prefix = "[", postfix = "]") { argumentKey(it) }
}
