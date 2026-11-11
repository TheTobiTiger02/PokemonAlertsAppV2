package com.example.pokemonalertsv2.ui.settings
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.util.AppText



/** One thing a person might look for in Settings, and the page it lives on. */
internal data class SettingsSearchEntry(
    val title: String,
    val destination: SettingsDestination,
    val keywords: List<String> = emptyList()
)

/** Settings worth finding by name. Titles are what each page shows, keywords what people type. */
internal val SETTINGS_SEARCH_INDEX = listOf(
    SettingsSearchEntry(AppText.get(R.string.settings_notifications_on_or_off), SettingsDestination.NOTIFICATIONS, listOf("alerts", "push")),
    SettingsSearchEntry(AppText.get(R.string.settings_quiet_hours), SettingsDestination.NOTIFICATIONS, listOf("night", "sleep", "do not disturb")),
    SettingsSearchEntry(AppText.get(R.string.settings_silence_notifications), SettingsDestination.NOTIFICATIONS, listOf("mute", "pause", "snooze")),
    SettingsSearchEntry(AppText.get(R.string.settings_sound_vibration), SettingsDestination.NOTIFICATIONS, listOf("ringtone", "vibrate", "channel")),
    SettingsSearchEntry(AppText.get(R.string.settings_battery_optimisation), SettingsDestination.NOTIFICATIONS, listOf("battery", "doze", "late")),
    SettingsSearchEntry(AppText.get(R.string.settings_location_permission), SettingsDestination.NOTIFICATIONS, listOf("gps", "background location")),
    SettingsSearchEntry(AppText.get(R.string.settings_floating_journey_pill), SettingsDestination.NOTIFICATIONS, listOf("overlay", "i'm going")),
    SettingsSearchEntry(AppText.get(R.string.alerts_filters), SettingsDestination.ALERT_FILTERS, listOf("filter studio", "profiles", "presets")),
    SettingsSearchEntry(AppText.get(R.string.settings_distance_and_walking_time), SettingsDestination.ALERT_FILTERS, listOf("range", "km", "minutes")),
    SettingsSearchEntry(AppText.get(R.string.settings_theme), SettingsDestination.APPEARANCE_BEHAVIOR, listOf("dark mode", "light", "night mode")),
    SettingsSearchEntry(AppText.get(R.string.settings_row_style), SettingsDestination.APPEARANCE_BEHAVIOR, listOf("compact", "large", "cards")),
    SettingsSearchEntry(AppText.get(R.string.settings_default_sort), SettingsDestination.APPEARANCE_BEHAVIOR, listOf("order", "newest", "nearest")),
    SettingsSearchEntry(AppText.get(R.string.alerts_marker_grouping), SettingsDestination.MAP_TRACKING, listOf("cluster", "map")),
    SettingsSearchEntry(AppText.get(R.string.settings_arrival_radius), SettingsDestination.MAP_TRACKING, listOf("arrived", "journey")),
    SettingsSearchEntry(AppText.get(R.string.settings_godex_checklist), SettingsDestination.GODEX, listOf("dex", "needed", "sign in")),
    SettingsSearchEntry(AppText.get(R.string.settings_godex_collection), SettingsDestination.GODEX_COLLECTION, listOf("caught", "collection")),
    SettingsSearchEntry(AppText.get(R.string.settings_raid_counters), SettingsDestination.RAID_COUNTERS, listOf("pokebattler", "counters", "trainer level")),
    SettingsSearchEntry(AppText.get(R.string.settings_pok_genie_import), SettingsDestination.RAID_COUNTERS, listOf("poke genie", "roster", "csv")),
    SettingsSearchEntry(AppText.get(R.string.settings_backup_and_restore), SettingsDestination.ABOUT_UPDATES, listOf("export", "import settings")),
    SettingsSearchEntry(AppText.get(R.string.settings_check_for_updates_2), SettingsDestination.ABOUT_UPDATES, listOf("version", "update")),
    SettingsSearchEntry(AppText.get(R.string.settings_crash_report), SettingsDestination.ABOUT_UPDATES, listOf("bug", "crash")),
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
