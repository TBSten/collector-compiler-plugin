package me.tbsten.collector.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Every name the plugin shares between its FIR and IR parts, and with the hints other modules read. */
internal object CollectorNames {
    /** The compiler plugin id: the same value in the command line processor, the registrar and the Gradle plugin. */
    const val PLUGIN_ID = "me.tbsten.collector"

    val RUNTIME_PACKAGE = FqName("me.tbsten.collector")
    val HINTS_PACKAGE = FqName("collector.hints")

    val COLLECTOR_FOR = ClassId(RUNTIME_PACKAGE, Name.identifier("CollectorFor"))
    val COLLECTOR = ClassId(RUNTIME_PACKAGE, Name.identifier("Collector"))

    const val COLLECTOR_FOR_PREFIX = "collectorFor_"
    const val COLLECTOR_KEY_PREFIX = "collectorKey_"
    const val ITEM_PREFIX = "item_"
    const val HOLDER_PREFIX = "Item_"
    const val AS_PREFIX = "as_"
    private const val ITEM_FILE_PREFIX = "itemHint_"

    /** Parameter names of the hints: the annotation X, the Collector C and the item's holder. */
    val ANNOTATION_PARAMETER = Name.identifier("a")
    val COLLECTOR_PARAMETER = Name.identifier("c")
    val HOLDER_PARAMETER = Name.identifier("i")

    /** The name of the `collectorFor_` hints for the annotation [annotationFqName]. */
    fun collectorForHintName(annotationFqName: String): Name =
        Name.identifier(COLLECTOR_FOR_PREFIX + HintNameEncoding.encode(annotationFqName))

    fun collectorForHint(annotation: ClassId): CallableId =
        CallableId(HINTS_PACKAGE, collectorForHintName(annotation.asFqNameString()))

    fun collectorKeyHint(collector: ClassId): CallableId =
        CallableId(HINTS_PACKAGE, Name.identifier(COLLECTOR_KEY_PREFIX + HintNameEncoding.encode(collector.asFqNameString())))

    fun itemHint(annotation: ClassId): CallableId =
        CallableId(HINTS_PACKAGE, Name.identifier(ITEM_PREFIX + HintNameEncoding.encode(annotation.asFqNameString())))

    // Hints are top-level functions of synthetic files. The file names end up as JVM facade classes and klib files,
    // so they are part of the hint format too.

    /** The file of the `collectorFor_` hints of [collector]. */
    fun collectorForHintFile(collector: ClassId): String = COLLECTOR_FOR_PREFIX + HintNameEncoding.encode(collector.asFqNameString())

    /** The file of the `collectorKey_` hints of [collector]. */
    fun collectorKeyHintFile(collector: ClassId): String = collectorKeyHint(collector).callableName.asString()

    /** The file of the `item_` hints of the item [declarationFqName]. */
    fun itemHintFile(declarationFqName: String): String = ITEM_FILE_PREFIX + HintNameEncoding.encode(declarationFqName)

    fun holder(declarationFqName: String): ClassId =
        ClassId(HINTS_PACKAGE, Name.identifier(HOLDER_PREFIX + HintNameEncoding.encode(declarationFqName)))

    /** The declaration FQN a holder class stands for. */
    fun decodeHolder(holder: ClassId): String = decodeHolderName(holder.shortClassName.asString())

    fun decodeHolderName(name: String): String =
        name.removePrefix(HOLDER_PREFIX).let { HintNameEncoding.decode(it) ?: it }

    fun asFunction(collector: ClassId): Name =
        Name.identifier(AS_PREFIX + HintNameEncoding.encode(collector.asFqNameString()))

    val COLLECTOR_INTERNALS = ClassId(FqName("me.tbsten.collector.internal"), Name.identifier("CollectorInternals"))
}
