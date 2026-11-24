package me.tbsten.collector.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrElementBase
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

/** Builds the bodies of `annotations_<X>()`: the X annotations of an item as a list. */
internal class ItemAnnotations(
    private val context: IrPluginContext,
    private val symbols: CollectorSymbols,
    private val bridges: ItemBridges,
) {
    /**
     * Gives the holder member [holderFn], declared as `annotations_<X>(): List<X>`, the body `return <bridge>()`. The
     * bridge, next to [target], returns `listOf(X(…), …)`: the X annotations of the item, in source order, with
     * meta-annotations expanded in place.
     *
     * They are the annotations FIR copied onto [holderFn], which call sites already compare (collectAsMap keys). For
     * an expect declaration those are the ones written on it, which its actual, the [target], need not repeat.
     *
     * The annotations are built where they were written. What their arguments refer to is visible there (a private
     * class, a private annotation class), and the classes the JVM backend generates to implement them end up in a
     * real file: in the holder's file, which has no lines, the default values of an annotation of the same module
     * trip the backend's line number assertion.
     */
    fun fill(holderFn: IrSimpleFunction, target: IrDeclaration) {
        val elementType = (holderFn.returnType as IrSimpleType).arguments.single().typeOrFail
        val x = annotationClass(holderFn)
        val annotations = holderFn.annotationsOfType(x).ifEmpty { target.annotationsOfType(x) }
        val bridge = bridges.bridgeFor(holderFn, target, always = true) {
            irCall(symbols.listOf, holderFn.returnType, typeArguments = listOf(elementType)).apply {
                val parent = scope.getLocalDeclarationParent()
                arguments[0] = irVararg(elementType, annotations.map { it.deepCopyWithSymbols(parent).withoutOffsets() })
            }
        } ?: error("Collector: no bridge for ${holderFn.name}")
        holderFn.body = DeclarationIrBuilder(context, holderFn.symbol).irBlockBody {
            +irReturn(with(bridges) { call(bridge) })
        }
    }

    /** X of the holder member [holderFn], declared as `annotations_<X>(): List<X>`. */
    fun annotationClass(holderFn: IrSimpleFunction): IrClassSymbol =
        (holderFn.returnType as IrSimpleType).arguments.single().typeOrFail.classOrNull
            ?: error("Collector: ${holderFn.name} does not return a list of annotations")

    /**
     * A copied annotation keeps its offsets, which for one that comes from a meta-annotation are those of another
     * file. They are of no use, and the JVM line number mapper asserts on offsets that are not of its file: drop them.
     */
    private fun <E : IrElement> E.withoutOffsets(): E = apply {
        acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                (element as? IrElementBase)?.let {
                    it.startOffset = UNDEFINED_OFFSET
                    it.endOffset = UNDEFINED_OFFSET
                }
                element.acceptChildrenVoid(this)
            }
        })
    }
}

/** The [x] annotations on [this], in source order, with meta-annotations (annotation classes carrying X) expanded in place. */
internal fun IrDeclaration.annotationsOfType(x: IrClassSymbol): List<IrConstructorCall> = annotationsOfType(annotations, x, emptySet())

private fun annotationsOfType(annotations: List<IrConstructorCall>, x: IrClassSymbol, path: Set<String>): List<IrConstructorCall> =
    annotations.flatMap { annotation ->
        if (annotation.type.classOrNull == x) return@flatMap listOf(annotation)
        val fq = annotation.type.classFqName?.asString() ?: return@flatMap emptyList()
        if (fq in path || fq.startsWith("kotlin")) return@flatMap emptyList()
        val meta = annotation.type.classOrNull?.owner?.takeIf { it.kind == ClassKind.ANNOTATION_CLASS } ?: return@flatMap emptyList()
        annotationsOfType(meta.annotations, x, path + fq)
    }
