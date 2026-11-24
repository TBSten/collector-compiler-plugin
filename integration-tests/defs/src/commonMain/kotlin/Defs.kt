package defs

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor
import me.tbsten.collector.collectAsList

interface Greeter { val name: String }

annotation class Entry

@CollectorFor<Entry>()
object EntryCollector : Collector<Greeter>

@CollectorFor<ext.Ext>()
object ExtCollector : Collector<() -> String>

@Entry
object DefsGreeter : Greeter { override val name = "defs" }

/** Collected while compiling :defs, so callers only ever see what :defs could see. */
inline fun collectedInsideDefsInline(): List<String> = EntryCollector.collectAsList().map { it.name }

/** Exactly one item exists (in :items1). */
annotation class SingleEntry

@CollectorFor<SingleEntry>()
object SingleCollector : Collector<Greeter>

/** No item exists anywhere. */
annotation class NoEntry

@CollectorFor<NoEntry>()
object NoneCollector : Collector<Greeter>
