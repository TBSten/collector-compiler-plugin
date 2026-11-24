package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.ItemIdentity
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol

/** The declarations of this module that can be items, to find the one a holder stands for. */
internal class ItemDeclarations(private val module: IrModuleFragment) {
    private class Candidate(val kind: ItemIdentity.Kind, val fqName: String, val declaration: IrDeclaration)

    private val candidates: Map<Pair<ItemIdentity.Kind, String>, List<IrDeclaration>> by lazy {
        buildList { module.files.forEach { file -> file.declarations.forEach { visit(it, file.packageFqName.asString()) } } }
            .groupBy({ it.kind to it.fqName }, { it.declaration })
    }

    private fun MutableList<Candidate>.visit(declaration: IrDeclaration, prefix: String) {
        if (declaration.origin is IrDeclarationOrigin.GeneratedByPlugin) return
        val (kind, name) = when (declaration) {
            is IrClass -> ItemIdentity.Kind.CLASS to declaration.name
            is IrSimpleFunction -> ItemIdentity.Kind.FUNCTION to declaration.name
            is IrProperty -> ItemIdentity.Kind.PROPERTY to declaration.name
            is IrEnumEntry -> ItemIdentity.Kind.ENUM_ENTRY to declaration.name
            else -> return
        }
        val fqName = if (prefix.isEmpty()) name.asString() else "$prefix.$name"
        add(Candidate(kind, fqName, declaration))
        if (declaration is IrClass && !declaration.isInner) declaration.declarations.forEach { visit(it, fqName) }
    }

    /**
     * The declaration [identity] names, or null when this module has none.
     *
     * Several declarations can share a kind and an FQN (overloads, or private declarations of two files); the item is
     * the one that carries one of [annotations], the annotations its holder collects it through. None carries one when
     * only the expect declaration was annotated: then the name has to be enough.
     */
    fun find(identity: ItemIdentity, annotations: List<IrClassSymbol>): IrDeclaration? {
        val named = candidates[identity.kind to identity.fqName] ?: return null
        val annotated = named.filter { declaration -> annotations.any { declaration.annotationsOfType(it).isNotEmpty() } }
        return (annotated.ifEmpty { named }).singleOrNull()
            ?: error("Collector: ${named.size} declarations are named ${identity.fqName}; annotate the one to collect")
    }
}
