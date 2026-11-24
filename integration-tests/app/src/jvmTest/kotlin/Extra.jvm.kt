actual fun extraEntries(): List<String> = System.getProperty("extraEntries")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
