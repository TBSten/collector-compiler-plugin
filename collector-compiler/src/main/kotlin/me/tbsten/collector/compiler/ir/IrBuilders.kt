package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorPluginKey
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.SpecialNames

/** Whether FIR generated this declaration for the plugin (see [CollectorPluginKey]). */
internal fun IrDeclaration.isCollectorGenerated(): Boolean =
    (origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == CollectorPluginKey

/** The callable id of a top-level function, or null for members and local functions. */
internal fun IrSimpleFunction.callableIdOrNull(): CallableId? {
    val parentPkg = (parent as? IrPackageFragment)?.packageFqName ?: return null
    return CallableId(parentPkg, name)
}

/**
 * `{ <body> }` as a lambda of the function type [type]. Type annotations (e.g. `@Composable`) are copied onto the
 * lambda. Its parent is the declaration this builder builds in, so lambdas nest correctly.
 */
internal fun IrBuilderWithScope.irLambda(type: IrType, body: (IrBuilderWithScope) -> IrExpression): IrExpression {
    val returnType = (type as IrSimpleType).arguments.last().typeOrFail
    val lambda = context.irFactory.buildFun {
        name = SpecialNames.ANONYMOUS
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        visibility = DescriptorVisibilities.LOCAL
        this.returnType = returnType
    }
    lambda.parent = scope.getLocalDeclarationParent()
    lambda.annotations = type.annotations.map { it.deepCopyWithSymbols(lambda) }
    lambda.body = DeclarationIrBuilder(context, lambda.symbol, startOffset, endOffset).irBlockBody {
        +irReturn(body(this))
    }
    return IrFunctionExpressionImpl(startOffset, endOffset, type, lambda, IrStatementOrigin.LAMBDA)
}
