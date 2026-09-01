package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.Immutable
import com.example.pokemonalertsv2.data.PokemonAlert

/**
 * The one vocabulary every surface filters by.
 *
 * Feed, map, widgets and notifications used to each encode their own idea of what an alert
 * "is" (tab enums, fuzzy widget tokens, notification toggles). Every surface now narrows the
 * same [AlertCategory] set produced by [alertCategories], so "hide quests" means the same
 * thing everywhere while each surface keeps its own selection.
 *
 * [GENERIC] is deliberately absent from [FILTERABLE_ALERT_CATEGORIES]: it only exists as a
 * fallback visual style, and an alert that cannot be classified is never hidden by a filter.
 */
val FILTERABLE_ALERT_CATEGORIES: List<AlertCategory> = listOf(
    AlertCategory.SPAWN,
    AlertCategory.RAID,
    AlertCategory.QUEST,
    AlertCategory.ROCKET,
    AlertCategory.KECLEON,
    AlertCategory.HUNDO,
    AlertCategory.NUNDO,
    AlertCategory.PVP,
    AlertCategory.RARE,
    AlertCategory.WEATHER
)

/** Plural label used by every filter UI that lists categories. */
val AlertCategory.filterLabel: String
    get() = when (this) {
        AlertCategory.SPAWN -> "Spawns"
        AlertCategory.RAID -> "Raids"
        AlertCategory.QUEST -> "Quests"
        AlertCategory.ROCKET -> "Rocket"
        AlertCategory.KECLEON -> "Kecleon"
        AlertCategory.HUNDO -> "Hundos"
        AlertCategory.NUNDO -> "Nundos"
        AlertCategory.PVP -> "PvP"
        AlertCategory.RARE -> "Rare"
        AlertCategory.WEATHER -> "Weather"
        AlertCategory.GENERIC -> "Other"
    }

/**
 * Every category the alert belongs to. Deliberately overlapping: a 100% spawn counts as both
 * [AlertCategory.SPAWN] and [AlertCategory.HUNDO], so hiding spawns keeps the hundo visible.
 *
 * Type tokens are matched with the same tolerance the old per-surface code used: sources
 * vary between "WeatherChange" and "Weather Change", and rockets hide behind spellings like
 * "Team Rocket", so those two match by substring.
 */
fun PokemonAlert.alertCategories(): Set<AlertCategory> = buildSet {
    if (hasType("Raid")) add(AlertCategory.RAID)
    if (hasType("Quest")) add(AlertCategory.QUEST)
    if (hasTypeContaining("Rocket") || gruntType != null) add(AlertCategory.ROCKET)
    if (hasType("Kecleon")) add(AlertCategory.KECLEON)
    if (hasTypeContaining("Weather")) add(AlertCategory.WEATHER)
    if (hasType("Hundo") || isPerfect) add(AlertCategory.HUNDO)
    if (hasType("Nundo") || isNundo) add(AlertCategory.NUNDO)
    if (hasType("PvP")) add(AlertCategory.PVP)
    if (hasType("Rare")) add(AlertCategory.RARE)
    if (isSpawnAlert) add(AlertCategory.SPAWN)
}

/**
 * The set of *muted* categories, as stored by every surface. An empty set means "no
 * narrowing" (show all), and an alert with no recognisable category is never hidden — it
 * could not be un-muted by toggling anything.
 */
fun matchesCategorySelection(alert: PokemonAlert, mutedCategories: Set<AlertCategory>): Boolean {
    if (mutedCategories.isEmpty()) return true
    val categories = alert.alertCategories()
    if (categories.isEmpty()) return true
    return categories.none(mutedCategories::contains)
}

/** Stored name sets tolerate unknown values so older backups never crash a read. */
fun Set<String>.toCategorySelection(): Set<AlertCategory> =
    mapNotNull { name -> runCatching { AlertCategory.valueOf(name) }.getOrNull() }
        .filter(FILTERABLE_ALERT_CATEGORIES::contains)
        .toSet()

fun Set<AlertCategory>.toStoredNames(): Set<String> = map(AlertCategory::name).toSet()

/** Live per-category counts for chip badges, computed in one pass. */
fun countAlertsByCategory(alerts: List<PokemonAlert>): Map<AlertCategory, Int> {
    val counts = mutableMapOf<AlertCategory, Int>()
    alerts.forEach { alert ->
        alert.alertCategories().forEach { category ->
            counts[category] = (counts[category] ?: 0) + 1
        }
    }
    return counts
}

/**
 * Migration: widget configs stored display-label tokens ("Hundo", "PvP", …). They map
 * one-to-one onto category names; anything unrecognised is dropped rather than guessed.
 */
fun legacyWidgetTokenToCategory(token: String): AlertCategory? {
    val normalized = token.trim().lowercase()
    return when (normalized) {
        "spawn", "spawns" -> AlertCategory.SPAWN
        "raid", "raids" -> AlertCategory.RAID
        "quest", "quests" -> AlertCategory.QUEST
        "rocket" -> AlertCategory.ROCKET
        "kecleon" -> AlertCategory.KECLEON
        "hundo", "100" -> AlertCategory.HUNDO
        "nundo", "000" -> AlertCategory.NUNDO
        "pvp" -> AlertCategory.PVP
        "rare" -> AlertCategory.RARE
        "weather" -> AlertCategory.WEATHER
        else -> null
    }
}

/** One immutable snapshot of a surface's category selection for UI rendering. */
@Immutable
data class CategoryFilterState(
    /** Muted categories; empty = everything shown. */
    val selection: Set<AlertCategory> = emptySet(),
    val counts: Map<AlertCategory, Int> = emptyMap()
) {
    val isActive: Boolean get() = selection.isNotEmpty()
    val totalVisible: Int
        get() = counts.entries
            .filterNot { (category, _) -> category in selection }
            .sumOf { it.value }
}
