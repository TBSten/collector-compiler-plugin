package me.tbsten.collector.compiler.ir

import me.tbsten.collector.compiler.ItemIdentity
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol

/** The declarations of this module that can be items, to find the one a holder stands for. */
internal class ItemDeclarations(private val module: IrModuleFragment) {
    private class Candidate(val kind: ItemIdentity.Kind, val fqName: String, val fileName: String, val declaration: IrDeclaration)

    private val candidates: Map<Pair<ItemIdentity.Kind, String>, List<Candidate>> by lazy {
        buildList { module.files.forEach { file -> file.declarations.forEach { visit(it, file.name, file.packageFqName.asString()) } } }
            .groupBy { it.kind to it.fqName }
    }

    private fun MutableList<Candidate>.visit(declaration: IrDeclaration, fileName: String, prefix: String) {
        if (declaration.origin is IrDeclarationOrigin.GeneratedByPlugin) return
        val (kind, name) = when (declaration) {
            is IrClass -> ItemIdentity.Kind.CLASS to declaration.name
            is IrSimpleFunction -> ItemIdentity.Kind.FUNCTION to declaration.name
            is IrProperty -> ItemIdentity.Kind.PROPERTY to declaration.name
            is IrEnumEntry -> ItemIdentity.Kind.ENUM_ENTRY to declaration.name
            else -> return
        }
        val fqName = if (prefix.isEmpty()) name.asString() else "$prefix.$name"
        add(Candidate(kind, fqName, fileName, declaration))
        if (declaration is IrClass && !declaration.isInner) declaration.declarations.forEach { visit(it, fileName, fqName) }
    }

    /**
     * The declaration [identity] names, or null when this module has none.
     *
     * Several declarations can share a kind and an FQN (overloads, or private declarations of two files); the item is
     * the one that carries one of [annotations], the annotations its holder collects it through, in the file the
     * identity records. Neither has to match when only the expect declaration was annotated: the actual declaration
     * is in another file and need not repeat the annotation. Then the name has to be enough.
     */
    fun find(identity: ItemIdentity, annotations: List<IrClassSymbol>): IrDeclaration? {
        val named = candidates[identity.kind to identity.fqName] ?: return null
        val annotated = named.filter { candidate -> annotations.any { candidate.declaration.annotationsOfType(it).isNotEmpty() } }.ifEmpty { named }
        val inFile = annotated.filter { it.fileName == identity.fileName }.ifEmpty { annotated }
        return inFile.singleOrNull()?.declaration
            ?: error("Collector: ${inFile.size} declarations are named ${identity.fqName}; annotate the one to collect")
    }
}
