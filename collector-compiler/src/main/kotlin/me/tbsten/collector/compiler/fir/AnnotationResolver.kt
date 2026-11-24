package me.tbsten.collector.compiler.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvedImport
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.java.javaSymbolProvider
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.scopes.defaultImportsProvider
import org.jetbrains.kotlin.fir.scopes.impl.DefaultImportPriority
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Tells which class an annotation names before the compiler has resolved it.
 *
 * Hints are declared before the phase that resolves annotations, when `@Entry` on a declaration is still only a
 * name. This class looks the name up the way the compiler will, in this order:
 *
 * 1. the classes nested in the classes around the declaration and in their companions, innermost first;
 * 2. explicit imports (`import p.Entry`, `import p.Entry as E`);
 * 3. the package of the file;
 * 4. star imports;
 * 5. default imports (`kotlin.*` and the like);
 * 6. for `a.b.Entry`, the class `Entry` of the package `a.b`.
 *
 * Classes are only read from the sources of this module (Kotlin and Java) and from its dependencies, never through the
 * session's whole symbol provider: that one asks the hint generator, which is what is running.
 *
 * Two things are left out: classes nested in a supertype of a surrounding class, and the annotations on an annotation
 * class declared in a Java source of this module (see [annotationsOn]). `CollectedItemChecker` compares every result
 * with what the compiler resolved in the end, so what this class gets wrong is a compile error and not an item that
 * is silently left out.
 */
@OptIn(SymbolInternals::class)
internal class AnnotationResolver(private val session: FirSession) {
    private val fileScopes = HashMap<FirFile, FileScope>()
    private val expanded = HashMap<ClassId, Result>()
    private val metaAnnotations = HashMap<ClassId, List<ClassId>>()

    /** A cached answer, which may be "nothing". */
    private class Result(val id: ClassId?)

    /**
     * The class or type alias [annotation] names, or null when it names none. [containers] are the classes around
     * the annotated declaration, outermost first.
     */
    fun resolve(annotation: FirAnnotation, file: FirFile, containers: List<FirRegularClass>): ClassId? {
        annotation.annotationTypeRef.coneTypeOrNull?.let { return it.classId }
        val reference = annotation.annotationTypeRef as? FirUserTypeRef ?: return null
        return resolve(reference.qualifier.map { it.name }, file, containers)
    }

    /** The class [id] is: itself, or what it stands for when it is a type alias. Null when there is no such class. */
    fun expand(id: ClassId): ClassId? = expanded.getOrPut(id) { Result(expand(id, mutableSetOf())) }.id

    private fun expand(id: ClassId, seen: MutableSet<ClassId>): ClassId? {
        val alias = when (val declaration = classLike(id)) {
            null -> return null
            is FirTypeAlias -> declaration
            else -> return id
        }
        if (!seen.add(id)) return null
        val target = alias.expandedTypeRef
        val next = when (val type = target.coneTypeOrNull) {
            null -> {
                // an alias of this module whose right-hand side is not resolved yet either
                val file = session.firProvider.getFirClassifierContainerFileIfAny(id) ?: return null
                resolve((target as? FirUserTypeRef)?.qualifier?.map { it.name } ?: return null, file, containersOf(id))
            }
            else -> (type as? ConeClassLikeType)?.lookupTag?.classId
        }
        return next?.let { expand(it, seen) }
    }

    /**
     * The annotations on the annotation class [id] (type aliases expanded), or nothing when [id] is not an annotation
     * class: what makes [id] a meta-annotation.
     */
    fun annotationsOn(id: ClassId): List<ClassId> = metaAnnotations.getOrPut(id) {
        val declaration = classLike(id) as? FirRegularClass
        if (declaration?.classKind != ClassKind.ANNOTATION_CLASS) return@getOrPut emptyList()
        // The annotations of a Java source are built when they are first read, by looking classes up in the whole
        // session. That asks the hint generator, which is what is asking. They are left unread here, and
        // CollectedItemChecker reports the declarations for which it mattered.
        if (declaration.origin == FirDeclarationOrigin.Java.Source) return@getOrPut emptyList()
        // null for a class of a dependency, whose annotations are resolved
        val file = session.firProvider.getFirClassifierContainerFileIfAny(id)
        val containers = if (file != null) containersOf(id) else emptyList()
        declaration.annotations.mapNotNull { annotation ->
            val named = if (file != null) resolve(annotation, file, containers) else annotation.annotationTypeRef.coneTypeOrNull?.classId
            named?.let { expand(it) }
        }
    }

    /** Whether [id] is an annotation class. */
    fun isAnnotationClass(id: ClassId): Boolean = (classLike(id) as? FirRegularClass)?.classKind == ClassKind.ANNOTATION_CLASS

    private fun resolve(names: List<Name>, file: FirFile, containers: List<FirRegularClass>): ClassId? {
        val first = names.firstOrNull() ?: return null
        val inScope = inScope(first, file, containers)?.let { outer -> nested(outer, names.drop(1), file) }
        return inScope ?: qualified(names, file)
    }

    /** [outer] followed by the nested classes [names], when they all exist. */
    private fun nested(outer: ClassId, names: List<Name>, file: FirFile): ClassId? =
        names.fold(outer as ClassId?) { id, name -> id?.createNestedClassId(name)?.takeIf { visible(it, file) } }

    private fun inScope(name: Name, file: FirFile, containers: List<FirRegularClass>): ClassId? {
        for (container in containers.asReversed()) {
            val companion = container.companionObjectSymbol?.classId
            listOfNotNull(companion, container.classId)
                .firstNotNullOfOrNull { owner -> owner.createNestedClassId(name).takeIf { visible(it, file) } }
                ?.let { return it }
        }
        return fileScopes.getOrPut(file) { FileScope(file) }.find(name)
    }

    /** `a.b.Entry` read as a package and a class: the longest package first, as the compiler does. */
    private fun qualified(names: List<Name>, file: FirFile): ClassId? {
        for (packageLength in names.size - 1 downTo 1) {
            val pkg = FqName.fromSegments(names.take(packageLength).map { it.asString() })
            val relative = FqName.fromSegments(names.drop(packageLength).map { it.asString() })
            ClassId(pkg, relative, isLocal = false).takeIf { visible(it, file) }?.let { return it }
        }
        return null
    }

    /**
     * The class or type alias [id]: of this module's sources first (an `actual` wins over its `expect`), Kotlin before
     * Java, then of the dependencies.
     */
    private fun classLike(id: ClassId): FirClassLikeDeclaration? =
        session.firProvider.getFirClassifierByFqName(id)
            // the Java sources compiled with this module; there is no such provider outside the JVM
            ?: session.javaSymbolProvider?.getClassLikeSymbolByClassId(id)?.fir
            ?: session.dependenciesSymbolProvider.getClassLikeSymbolByClassId(id)?.fir

    /** Whether [id] exists and code in [file] can name it. A private class of another file does not hide a class. */
    private fun visible(id: ClassId, file: FirFile): Boolean {
        val declaration = classLike(id) ?: return false
        return declaration.visibility != Visibilities.Private || session.firProvider.getFirClassifierContainerFileIfAny(id) === file
    }

    /** The classes around the class [id] of this module, outermost first. */
    private fun containersOf(id: ClassId): List<FirRegularClass> =
        generateSequence(id.outerClassId) { it.outerClassId }
            .mapNotNull { session.firProvider.getFirClassifierByFqName(it) as? FirRegularClass }
            .toList().asReversed()

    /** What the imports and the package of [file] make of a simple name. */
    private inner class FileScope(private val file: FirFile) {
        private val found = HashMap<Name, Result>()

        /** name in this file -> what explicit imports give it */
        private val explicit = HashMap<Name, MutableList<ClassId>>()

        /** the packages and classes of the star imports */
        private val stars = mutableListOf<Star>()

        /** classes imported under another name: their own name no longer reaches them through a package or a star */
        private val renamed = HashSet<FqName>()

        init {
            for (import in file.imports) {
                val fqName = import.importedFqName ?: continue
                if (import.isAllUnder) {
                    stars += star(fqName, (import as? FirResolvedImport)?.resolvedParentClassId)
                    continue
                }
                val alias = import.aliasName
                if (alias != null) renamed += fqName
                val id = (import as? FirResolvedImport)?.let { resolved ->
                    resolved.resolvedParentClassId?.createNestedClassId(fqName.shortName()) ?: ClassId(resolved.packageFqName, fqName.shortName())
                } ?: classOf(fqName)
                if (id != null && visible(id, file)) explicit.getOrPut(alias ?: fqName.shortName()) { mutableListOf() } += id
            }
        }

        fun find(name: Name): ClassId? = found.getOrPut(name) { Result(lookUp(name)) }.id

        private fun lookUp(name: Name): ClassId? {
            explicit[name]?.firstOrNull()?.let { return it }
            Star(file.packageDirective.packageFqName, null).find(name)?.let { return it }
            stars.firstNotNullOfOrNull { it.find(name) }?.let { return it }
            val defaults = session.defaultImportsProvider
            for (priority in listOf(DefaultImportPriority.HIGH, DefaultImportPriority.LOW)) {
                for (import in priority.getAllDefaultImports(defaults, session.languageVersionSettings).orEmpty()) {
                    val id = when {
                        import.isAllUnder -> ClassId(import.fqName, name)
                        (import.alias ?: import.fqName.shortName()) == name -> ClassId.topLevel(import.fqName)
                        else -> continue
                    }
                    if (id.asSingleFqName() !in defaults.excludedImports && id.asSingleFqName() !in renamed && visible(id, file)) return id
                }
            }
            return null
        }

        /** `import a.b.*`, of a package or of a class. */
        private fun star(fqName: FqName, resolvedClass: ClassId?): Star = when {
            resolvedClass != null -> Star(null, resolvedClass)
            // a package unless a class of that name exists (imports are resolved by now, so this is a fallback)
            else -> classOf(fqName)?.let { Star(null, it) } ?: Star(fqName, null)
        }

        private inner class Star(private val pkg: FqName?, private val owner: ClassId?) {
            fun find(name: Name): ClassId? {
                val id = owner?.createNestedClassId(name) ?: ClassId(pkg!!, name)
                return id.takeIf { it.asSingleFqName() !in renamed && visible(it, file) }
            }
        }

        /** The class an imported name stands for when the import is not resolved: the longest package first. */
        private fun classOf(fqName: FqName): ClassId? {
            val segments = fqName.pathSegments()
            for (packageLength in segments.size - 1 downTo 0) {
                val id = ClassId(
                    FqName.fromSegments(segments.take(packageLength).map { it.asString() }),
                    FqName.fromSegments(segments.drop(packageLength).map { it.asString() }),
                    isLocal = false,
                )
                if (classLike(id) != null) return id
            }
            return null
        }
    }
}
