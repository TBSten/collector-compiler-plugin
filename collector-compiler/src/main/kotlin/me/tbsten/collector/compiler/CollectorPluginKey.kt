package me.tbsten.collector.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey

/** The key of every declaration the plugin generates: FIR creates them with it, and IR recognises them by it. */
object CollectorPluginKey : GeneratedDeclarationKey()
