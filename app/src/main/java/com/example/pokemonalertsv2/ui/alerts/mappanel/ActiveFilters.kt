package com.example.pokemonalertsv2.ui.alerts.mappanel

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.ui.alerts.SPECIES_TARGETS
import com.example.pokemonalertsv2.ui.alerts.selectionFor
import com.example.pokemonalertsv2.ui.alerts.withSelection
import com.example.pokemonalertsv2.util.TravelTime

/** One rule narrowing the map, and how to undo just that rule. */
internal data class ActiveFilterChip(
    val id: String,
    val label: String,
    val clear: (FilterDefinition) -> FilterDefinition
)

private val Defaults = FilterDefinition()

/**
 * Every rule in [definition] that differs from the defaults, in the order the Filters sheet
 * lists them. Each chip clears only its own field, back to its [FilterDefinition] default.
 * [catalog] supplies the totals for "2 of 14"; an empty catalog just leaves them out.
 */
internal fun activeFilterChips(
    definition: FilterDefinition,
    catalog: FilterCatalog = FilterCatalog()
): List<ActiveFilterChip> = buildList {
    if (definition.alertTypes != Defaults.alertTypes) {
        add(
            ActiveFilterChip(
                id = "types",
                label = "Types: " + definition.alertTypes.countLabel(FilterAlertType.entries.size),
                clear = { it.copy(alertTypes = Defaults.alertTypes) }
            )
        )
    }
    if (definition.maxDistanceMeters > 0) {
        add(
            ActiveFilterChip(
                id = "distance",
                label = "≤ " + distanceLabel(definition.maxDistanceMeters),
                clear = { it.copy(maxDistanceMeters = Defaults.maxDistanceMeters) }
            )
        )
    }
    if (definition.maxWalkingMinutes > 0) {
        add(
            ActiveFilterChip(
                id = "walking",
                label = "≤ " + TravelTime.label(definition.maxWalkingMinutes) + " walk",
                clear = { it.copy(maxWalkingMinutes = Defaults.maxWalkingMinutes) }
            )
        )
    }
    definition.distanceOverrides.ruleCount.takeIf { it > 0 }?.let { count ->
        add(
            ActiveFilterChip(
                id = "overrides",
                label = if (count == 1) "1 distance rule" else "$count distance rules",
                clear = { it.copy(distanceOverrides = Defaults.distanceOverrides) }
            )
        )
    }
    SPECIES_TARGETS.forEach { target ->
        val selection = definition.selectionFor(target)
        if (selection.mode != FilterSelectionMode.ALL) {
            add(
                ActiveFilterChip(
                    id = "species_${target.name}",
                    label = target.title + ": " + selection.countLabel(total = null),
                    clear = { it.withSelection(target, FilterSelection.All) }
                )
            )
        }
    }
    if (definition.raidTiers.mode != FilterSelectionMode.ALL) {
        add(
            ActiveFilterChip(
                id = "raid_tiers",
                label = "Raid tiers: " + definition.raidTiers.countLabel(catalog.raidTiers.size.takeIf { it > 0 }),
                clear = { it.copy(raidTiers = Defaults.raidTiers) }
            )
        )
    }
    if (definition.rocketTypes.mode != FilterSelectionMode.ALL) {
        add(
            ActiveFilterChip(
                id = "rocket",
                label = "Rocket: " + definition.rocketTypes.countLabel(catalog.rocketTypes.size.takeIf { it > 0 }),
                clear = { it.copy(rocketTypes = Defaults.rocketTypes) }
            )
        )
    }
    if (definition.quests != Defaults.quests) {
        add(
            ActiveFilterChip(
                id = "quests",
                label = "Quests: custom",
                clear = { it.copy(quests = Defaults.quests) }
            )
        )
    }
    // Areas are only set in Filter Studio, but they narrow this map all the same.
    if (definition.areas.mode != FilterSelectionMode.ALL) {
        add(
            ActiveFilterChip(
                id = "areas",
                label = "Areas: " + definition.areas.countLabel(catalog.areas.size.takeIf { it > 0 }),
                clear = { it.copy(areas = Defaults.areas) }
            )
        )
    }
}

/** "all", "none", "3" or, with a [total], "3 of 11". */
private fun FilterSelection.countLabel(total: Int?): String = when (mode) {
    FilterSelectionMode.ALL -> "all"
    FilterSelectionMode.NONE -> "none"
    FilterSelectionMode.ONLY -> if (total != null) "${normalizedValues.size} of $total" else "${normalizedValues.size}"
}
