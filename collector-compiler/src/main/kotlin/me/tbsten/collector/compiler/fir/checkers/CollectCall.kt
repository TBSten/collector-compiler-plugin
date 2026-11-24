package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectFunction
import me.tbsten.collector.compiler.CollectorNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.unwrapSmartcastExpression
import org.jetbrains.kotlin.fir.references.toResolvedNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

/** A call of a `collect*()` placeholder. */
internal class CollectCall(
    val function: CollectFunction,
    val expression: FirFunctionCall,
    /** The `@CollectorFor` object the call is made on, or null when the receiver is anything else. */
    val collector: FirRegularClassSymbol?,
)

/** [expression] as a [CollectCall], or null when it calls something else. */
context(context: CheckerContext)
internal fun collectCall(expression: FirFunctionCall): CollectCall? {
    val callee = expression.calleeReference.toResolvedNamedFunctionSymbol() ?: return null
    val function = CollectFunction.of(callee.callableId) ?: return null
    val receiver = expression.extensionReceiver?.unwrapSmartcastExpression() as? FirResolvedQualifier
    val collector = (receiver?.qualifierSymbol as? FirRegularClassSymbol)
        ?.takeIf { it.classKind == ClassKind.OBJECT && it.hasAnnotation(CollectorNames.COLLECTOR_FOR, context.session) }
    return CollectCall(function, expression, collector)
}
