package me.tbsten.collector.compiler.fir.checkers

import me.tbsten.collector.compiler.CollectFunction
import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.fir.collectorHints
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.unwrapSmartcastExpression
import org.jetbrains.kotlin.fir.references.toResolvedNamedFunctionSymbol
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.resolvedType

/** A call of a `collect*()` placeholder, resolved to the Collectors it reads. */
internal class CollectCall(
    val function: CollectFunction,
    val expression: FirFunctionCall,
    /** The tag of `collectAsList<T>(tag = "…")` and friends; null with a receiver, or when the tag is not a constant. */
    val tag: String?,
    /** The Collectors the call reads: the receiver, or every Collector with the tag. Empty when the receiver is invalid. */
    val collectors: List<FirRegularClassSymbol>,
    val receiverIsValid: Boolean,
) {
    val byTag: Boolean get() = expression.extensionReceiver == null
}

/** [expression] as a [CollectCall], or null when it calls something else. */
context(context: CheckerContext)
internal fun collectCall(expression: FirFunctionCall): CollectCall? {
    val callee = expression.calleeReference.toResolvedNamedFunctionSymbol() ?: return null
    val function = CollectFunction.of(callee.callableId) ?: return null
    val session = context.session
    val receiver = expression.extensionReceiver?.unwrapSmartcastExpression()
    if (receiver == null) {
        val tag = constantString(expression.argumentList.arguments.firstOrNull())
        val collectors = tag?.let { session.collectorHints.collectorsWithTag(it) }.orEmpty()
        return CollectCall(function, expression, tag, collectors, receiverIsValid = true)
    }
    // `XCollector.collect…()` or the implicit receiver of `with(XCollector) { collect…() }`
    val symbol = when {
        receiver is FirResolvedQualifier -> receiver.qualifierSymbol as? FirRegularClassSymbol
        receiver is FirThisReceiverExpression && receiver.isImplicit -> receiver.resolvedType.toRegularClassSymbol(session)
        else -> null
    }
    val collector = symbol?.takeIf { it.classKind == ClassKind.OBJECT && it.hasAnnotation(CollectorNames.COLLECTOR_FOR, session) }
    return CollectCall(function, expression, tag = null, listOfNotNull(collector), receiverIsValid = collector != null)
}

/** A string literal or a reference to a `const val` holding one. */
internal fun constantString(expression: FirExpression?): String? = when (val e = expression?.unwrapSmartcastExpression()) {
    is FirLiteralExpression -> e.value as? String
    is FirPropertyAccessExpression -> {
        val property = e.calleeReference.toResolvedPropertySymbol()
        if (property?.isConst == true) constantString(property.resolvedInitializer) else null
    }
    else -> null
}
