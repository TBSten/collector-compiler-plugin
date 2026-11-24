package me.tbsten.collector.compiler

/**
 * What an item is: a declaration, and the module that declares it.
 *
 * FIR writes it into the deprecation message of the item's holder; call sites (FIR and IR) and the IR phase that fills
 * the holder read it back from there. The name of a holder is derived from it, but is never decoded.
 */
internal data class ItemIdentity(
    val kind: Kind,
    /** The name of the module (`-module-name`) that declares the item. */
    val module: String,
    val packageName: String,
    /**
     * The source file, for top-level functions and properties; null for everything else. Two modules may declare the
     * same top-level function in the same package as long as the files differ (on the JVM the file is their class).
     */
    val fileName: String?,
    /** The name inside the package: `name`, or `Outer.Inner.name` for a nested class or a member. */
    val relativeName: String,
) : Comparable<ItemIdentity> {
    enum class Kind { CLASS, FUNCTION, PROPERTY, ENUM_ENTRY }

    /** The name shown to users, and the order of the items of a Collector. */
    val fqName: String get() = if (packageName.isEmpty()) relativeName else "$packageName.$relativeName"

    /**
     * Like a class id: `com/example/Outer.name`, or `com/example/File.kt/name` for a top-level function or property.
     * Unlike [fqName] it tells packages, files and classes apart.
     */
    val path: String
        get() = buildString {
            if (packageName.isNotEmpty()) append(packageName.replace('.', '/')).append('/')
            if (fileName != null) append(fileName).append('/')
            append(relativeName)
        }

    /** By [fqName]; items with the same FQN (possible across modules) keep a fixed order too. */
    override fun compareTo(other: ItemIdentity): Int = ORDER.compare(this, other)

    /** The lines that record this identity in a hint message (see [HintMessage]). */
    fun toFields(): List<Pair<String, String>> = buildList {
        add(KIND to kind.name.lowercase())
        add(MODULE to module)
        add(PACKAGE to packageName)
        if (fileName != null) add(FILE to fileName)
        add(NAME to relativeName)
    }

    companion object {
        private const val KIND = "item"
        private const val MODULE = "module"
        private const val PACKAGE = "package"
        private const val FILE = "file"
        private const val NAME = "name"

        private val ORDER = compareBy<ItemIdentity>({ it.fqName }, { it.module }, { it.path }, { it.kind })

        /** The identity recorded in [fields], or null when they do not describe an item. */
        fun fromFields(fields: Map<String, String>): ItemIdentity? {
            val kind = fields[KIND]?.let { name -> Kind.entries.firstOrNull { it.name.lowercase() == name } } ?: return null
            return ItemIdentity(
                kind = kind,
                module = fields[MODULE] ?: return null,
                packageName = fields[PACKAGE] ?: return null,
                fileName = fields[FILE],
                relativeName = fields[NAME] ?: return null,
            )
        }
    }
}

/**
 * The message of the `@Deprecated(level = HIDDEN)` on everything the plugin generates in `collector.hints`. The first
 * line records the hint format; holders and missed markers add `key: value` lines about what they stand for.
 */
internal object HintMessage {
    fun of(fields: List<Pair<String, String>>): String =
        (listOf(CollectorNames.HINT_MESSAGE) + fields.map { (key, value) -> "$key$SEPARATOR$value" }).joinToString("\n")

    /** The `key: value` lines of [message]; names can not contain line breaks, so a value is the rest of its line. */
    fun fields(message: String): Map<String, String> =
        message.lineSequence().drop(1)
            .mapNotNull { line -> line.split(SEPARATOR, limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()

    /** The field of a missed marker: one of the declarations that were not collected. */
    const val MISSED = "missed"

    private const val SEPARATOR = ": "
}
