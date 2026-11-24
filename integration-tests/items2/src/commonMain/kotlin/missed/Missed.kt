package missed

// No Collector for @Unseen is visible here (it is declared downstream, in errors/warnings), so these are not items.
// The module records them, and a call collecting @Unseen warns (COLLECT_MISSED_ITEMS).
@ext.Unseen object MissedObject

@ext.Unseen fun missedFunction() {}
