package com.example.pokemonalertsv2.ui.settings

/** One thing a person might look for in Settings, and the page it lives on. */
internal data class SettingsSearchEntry(
    val title: String,
    val destination: SettingsDestination,
    val keywords: List<String> = emptyList()
)

/** Settings worth finding by name. Titles are what each page shows, keywords what people type. */
internal val SETTINGS_SEARCH_INDEX = listOf(
    SettingsSearchEntry("Notifications on or off", SettingsDestination.NOTIFICATIONS, listOf("alerts", "push")),
    SettingsSearchEntry("Quiet hours", SettingsDestination.NOTIFICATIONS, listOf("night", "sleep", "do not disturb")),
    SettingsSearchEntry("Silence notifications", SettingsDestination.NOTIFICATIONS, listOf("mute", "pause", "snooze")),
    SettingsSearchEntry("Sound & vibration", SettingsDestination.NOTIFICATIONS, listOf("ringtone", "vibrate", "channel")),
    SettingsSearchEntry("Battery optimisation", SettingsDestination.NOTIFICATIONS, listOf("battery", "doze", "late")),
    SettingsSearchEntry("Location permission", SettingsDestination.NOTIFICATIONS, listOf("gps", "background location")),
    SettingsSearchEntry("Floating journey pill", SettingsDestination.NOTIFICATIONS, listOf("overlay", "i'm going")),
    SettingsSearchEntry("Filters", SettingsDestination.ALERT_FILTERS, listOf("filter studio", "profiles", "presets")),
    SettingsSearchEntry("Distance and walking time", SettingsDestination.ALERT_FILTERS, listOf("range", "km", "minutes")),
    SettingsSearchEntry("Theme", SettingsDestination.APPEARANCE_BEHAVIOR, listOf("dark mode", "light", "night mode")),
    SettingsSearchEntry("Row style", SettingsDestination.APPEARANCE_BEHAVIOR, listOf("compact", "large", "cards")),
    SettingsSearchEntry("Default sort", SettingsDestination.APPEARANCE_BEHAVIOR, listOf("order", "newest", "nearest")),
    SettingsSearchEntry("Marker grouping", SettingsDestination.MAP_TRACKING, listOf("cluster", "map")),
    SettingsSearchEntry("Arrival radius", SettingsDestination.MAP_TRACKING, listOf("arrived", "journey")),
    SettingsSearchEntry("GoDex checklist", SettingsDestination.GODEX, listOf("dex", "needed", "sign in")),
    SettingsSearchEntry("GoDex collection", SettingsDestination.GODEX_COLLECTION, listOf("caught", "collection")),
    SettingsSearchEntry("Raid counters", SettingsDestination.RAID_COUNTERS, listOf("pokebattler", "counters", "trainer level")),
    SettingsSearchEntry("Poké Genie import", SettingsDestination.RAID_COUNTERS, listOf("poke genie", "roster", "csv")),
    SettingsSearchEntry("Backup and restore", SettingsDestination.ABOUT_UPDATES, listOf("export", "import settings")),
    SettingsSearchEntry("Check for updates", SettingsDestination.ABOUT_UPDATES, listOf("version", "update")),
    SettingsSearchEntry("Crash report", SettingsDestination.ABOUT_UPDATES, listOf("bug", "crash")),
)

/**
 * Entries whose title or keywords contain every word of [query], title matches first. Accents are
 * ignored, so "poke" finds "Poké Genie".
 */
internal fun searchSettings(query: String, index: List<SettingsSearchEntry> = SETTINGS_SEARCH_INDEX): List<SettingsSearchEntry> {
    val words = fold(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return index
        .filter { entry ->
            val haystack = fold((listOf(entry.title) + entry.keywords).joinToString(" "))
            words.all { it in haystack }
        }
        .sortedByDescending { entry -> words.all { it in fold(entry.title) } }
}

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
