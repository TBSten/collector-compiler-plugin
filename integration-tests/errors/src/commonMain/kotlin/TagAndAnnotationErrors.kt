// Step 4 cases: tags, collectWithAnnotation / collectAsMap and callable references.
// Each line marked `// E: <DIAGNOSTIC>` must be reported on that line; nothing else may be reported.
import defs.EntryCollector
import defs.PLUGINS
import defs.Page
import defs.Plugin
import defs.SampleCollector
import defs.Screen
import defs.ScreenCollector
import me.tbsten.collector.*

annotation class Local

@CollectorFor<Local>(tag = PLUGINS) object NonLiteralTag : Collector<Plugin> // E: COLLECTOR_FOR_TAG_NOT_LITERAL

// equal annotations, so collectAsMap would have two values for one key
@Screen("/dup") object DuplicateA : Page { override val title = "a" }
@Screen("/dup") object DuplicateB : Page { override val title = "b" }

fun tags(tag: String) {
    collectAsList<String>("plugins") // E: COLLECT_TAG_TYPE_MISMATCH
    collectAsList<Plugin>(tag) // E: COLLECT_TAG_NOT_CONSTANT
    collectAsList<Plugin>("plug" + tag) // E: COLLECT_TAG_NOT_CONSTANT
    collectSingle<Plugin>(PLUGINS) // E: COLLECT_SINGLE_COUNT
    collectSingleOrNull<Plugin>("plugins") // E: COLLECT_SINGLE_OR_NULL_COUNT
}

// the same key as an item of :defs, which wrote the array as a literal and is read here from its compiled form
@defs.Keyed(names = arrayOf("a", "b")) val keyedHere = "here"

fun annotations() {
    ScreenCollector.collectWithAnnotation<Local, Page>() // E: COLLECT_ANNOTATION_TYPE_MISMATCH
    ScreenCollector.collectAsMap<Screen, Page>() // E: COLLECT_AS_MAP_DUPLICATE_KEY
    defs.KeyedCollector.collectAsMap<defs.Keyed, String>() // E: COLLECT_AS_MAP_DUPLICATE_KEY
    ScreenCollector.lazyCollectAsMap<Screen, Page>() // E: COLLECT_AS_MAP_DUPLICATE_KEY
}

fun receivers() {
    ScreenCollector.let { it.collectAsList() } // E: RECEIVER_NOT_COLLECTOR_OBJECT
    EntryCollector::collectAsList // E: COLLECT_CALLABLE_REFERENCE
}

fun fineTagsAndAnnotations() {
    collectAsList<Any>(PLUGINS)
    collectSingle<Plugin>("solo")
    collectSingleOrNull<Plugin>("no collector has this tag")
    collectAsList<Plugin>("no collector has this tag")
    SampleCollector.collectAsMap<defs.Sample, () -> String>()
    ScreenCollector.collectWithAnnotation<Annotation, Page>()
    with(ScreenCollector) { collectAsList() }
}
