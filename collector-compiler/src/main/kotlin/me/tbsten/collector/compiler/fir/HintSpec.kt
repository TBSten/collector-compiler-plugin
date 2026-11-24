package me.tbsten.collector.compiler.fir

import me.tbsten.collector.compiler.CollectorNames
import me.tbsten.collector.compiler.HintMessage
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/** One hint to generate: the top-level function `<callableId>(<parameters>)` in the synthetic file [fileName]. */
internal class HintSpec(
    val callableId: CallableId,
    val fileName: String,
    val parameters: List<Pair<Name, ClassId>>,
    /** The `key: value` lines of the hint's message (see [HintMessage]). */
    val fields: List<Pair<String, String>> = emptyList(),
)

/**
 * Every hint this module declares, by callable id. The generator both announces and generates from this one table, so
 * the two can not drift apart.
 *
 * - `collectorFor_<X>(a: X, c: C)` and `collectorKey_<C>(a: X)` for each own Collector C of X: downstream modules
 *   find the Collectors of an annotation, and the annotations of a Collector (type arguments of annotations are not
 *   kept in class files);
 * - `collectorTag_<tag>(c: C)` for each own Collector C declared with a tag: calls by tag find the Collectors;
 * - `item_<X>(i: Item_…)` for each own item and each collected annotation X on it;
 * - `collectorMissed_<X>(a: X, m: Missed_…)` for each annotation X this module uses without seeing a Collector for it.
 */
internal fun hintSpecs(collectors: List<CollectorDef>, items: OwnItems): Map<CallableId, List<HintSpec>> = buildList {
    // @CollectorFor<X> repeated with two tags is two definitions, and one pair of hints
    val pairs = collectors.distinctBy { it.annotation to it.collector }
    for (c in pairs) {
        add(
            HintSpec(
                CollectorNames.collectorForHint(c.annotation), CollectorNames.collectorForHintFile(c.collector),
                listOf(CollectorNames.ANNOTATION_PARAMETER to c.annotation, CollectorNames.COLLECTOR_PARAMETER to c.collector),
                listOf(HintMessage.ANNOTATION to c.annotation.asString()),
            ),
        )
    }
    for (c in pairs) {
        add(
            HintSpec(
                CollectorNames.collectorKeyHint(c.collector), CollectorNames.collectorKeyHintFile(c.collector),
                listOf(CollectorNames.ANNOTATION_PARAMETER to c.annotation),
                listOf(HintMessage.ANNOTATION to c.annotation.asString()),
            ),
        )
    }
    for (c in collectors.distinctBy { it.tag to it.collector }) {
        val tag = c.tag ?: continue
        add(
            HintSpec(
                CollectorNames.collectorTagHint(tag), CollectorNames.collectorForHintFile(c.collector),
                listOf(CollectorNames.COLLECTOR_PARAMETER to c.collector),
            ),
        )
    }
    for (item in items.items) {
        for (x in item.annotations) {
            add(
                HintSpec(
                    CollectorNames.itemHint(x), CollectorNames.itemHintFile(item.holder),
                    listOf(CollectorNames.HOLDER_PARAMETER to item.holder),
                ),
            )
        }
    }
    for (x in items.missed.keys) {
        val marker = items.missedMarker(x)
        add(
            HintSpec(
                CollectorNames.missedHint(x), CollectorNames.missedHintFile(marker),
                listOf(CollectorNames.ANNOTATION_PARAMETER to x, CollectorNames.MARKER_PARAMETER to marker),
            ),
        )
    }
}.groupBy { it.callableId }
