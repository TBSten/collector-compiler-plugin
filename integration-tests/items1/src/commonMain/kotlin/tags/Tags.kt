package tags

import defs.Page
import defs.Plugin
import defs.PluginA
import defs.PluginB
import defs.Sample
import defs.Screen
import defs.Solo

@PluginA object Alpha : Plugin { override val id = "alpha" }

@PluginB object Beta : Plugin { override val id = "beta" }

@PluginA @PluginB object Both : Plugin { override val id = "both" }

@Solo object Only : Plugin { override val id = "only" }

@Screen("/home") object Home : Page { override val title = "Home" }

@Screen("/settings", order = 1) class Settings : Page { override val title = "Settings" }

@Sample("light") @Sample("dark") fun themed(): String = "themed"

@Sample("plain") private fun plain(): String = "plain"
