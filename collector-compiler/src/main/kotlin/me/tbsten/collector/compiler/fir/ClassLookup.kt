package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.java.javaSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.name.ClassId

/**
 * The class or type alias [id]: of this module's sources first (an `actual` wins over its `expect`), Kotlin before
 * Java, then of the dependencies.
 *
 * Never through the session's whole symbol provider: that one asks the hint generator, which may be what is running.
 */
@OptIn(SymbolInternals::class)
internal fun FirSession.classLikeDeclaration(id: ClassId): FirClassLikeDeclaration? =
    firProvider.getFirClassifierByFqName(id)
        // the Java sources compiled with this module; there is no such provider outside the JVM
        ?: javaSymbolProvider?.getClassLikeSymbolByClassId(id)?.fir
        ?: dependenciesSymbolProvider.getClassLikeSymbolByClassId(id)?.fir

/**
 * Whether only one file can name the class or type alias [id]: it is private, or a class around it is.
 *
 * What the plugin generates in `collector.hints` is in files of its own, so it must not name such a class: not on the
 * JVM, where the class is not accessible from another package, and not in IR, which the compiler may verify
 * (`-Xverify-ir`). Read from the modifier as written, so this can be asked before the compiler resolves visibilities.
 */
internal fun FirSession.isFilePrivate(id: ClassId): Boolean =
    id.isLocal || generateSequence(id) { it.outerClassId }.any { classLikeDeclaration(it)?.visibility == Visibilities.Private }
