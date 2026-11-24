package me.tbsten.collector.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor

class CollectorCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = CollectorNames.PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}
