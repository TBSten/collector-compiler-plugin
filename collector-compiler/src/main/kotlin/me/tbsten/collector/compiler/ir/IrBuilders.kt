package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.CollectorPluginKey
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnnotationWithEqualFqName
import org.jetbrains.kotlin.ir.util.isKSuspendFunction
import org.jetbrains.kotlin.ir.util.isSuspendFunction
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
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
 * `{ p1, …, pn -> <body> }` as a lambda of the function type [type]. The parameters come from [type] (the first one
 * is the receiver when [type] is an extension function type), and a non-Unit result is dropped when [type] returns
 * Unit. Type annotations such as `@Composable` are copied onto the lambda. Its parent is the declaration this builder
 * builds in, so lambdas nest correctly.
 */
internal fun IrBuilderWithScope.irLambda(
    type: IrType,
    body: (IrBuilderWithScope, parameters: List<IrValueParameter>) -> IrExpression,
): IrExpression {
    val typeArguments = (type as IrSimpleType).arguments.map { it.typeOrFail }
    val returnType = typeArguments.last()
    val lambda = context.irFactory.buildFun {
        name = SpecialNames.ANONYMOUS
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        visibility = DescriptorVisibilities.LOCAL
        this.returnType = returnType
        isSuspend = type.isSuspendFunction() || type.isKSuspendFunction()
    }
    lambda.parent = scope.getLocalDeclarationParent()
    // @ExtensionFunctionType only belongs on the type
    lambda.annotations = type.annotations
        .filter { !it.isAnnotationWithEqualFqName(StandardNames.FqNames.extensionFunctionType) }
        .map { it.deepCopyWithSymbols(lambda) }
    val extension = type.hasAnnotation(StandardNames.FqNames.extensionFunctionType)
    val parameters = typeArguments.dropLast(1).mapIndexed { i, parameterType ->
        lambda.addValueParameter {
            kind = if (i == 0 && extension) IrParameterKind.ExtensionReceiver else IrParameterKind.Regular
            name = if (i == 0 && extension) SpecialNames.THIS else Name.identifier("p$i")
            this.type = parameterType
        }
    }
    lambda.body = DeclarationIrBuilder(context, lambda.symbol, startOffset, endOffset).irBlockBody {
        val value = body(this, parameters)
        if (returnType.isUnit() && !value.type.isUnit()) {
            +value
            +irReturn(irUnit())
        } else {
            +irReturn(value)
        }
    }
    return IrFunctionExpressionImpl(startOffset, endOffset, type, lambda, IrStatementOrigin.LAMBDA)
}
