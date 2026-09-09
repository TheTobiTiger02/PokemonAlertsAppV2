package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import java.util.Locale

/**
 * What to call a hunt, derived from what was actually picked.
 *
 * The name is the only description of the hunt the trainer sees afterwards — on
 * the map panel and, indirectly, in the journey — so it has to read like the
 * thing they asked for ("Dragon grunts") rather than a restatement of the filter
 * model ("Rocket, 1 grunt type").
 *
 * Deliberately narrow: it names the *narrowest* thing chosen and falls back to
 * the alert type. Nobody needs a name that enumerates every criterion.
 */
internal fun huntName(definition: FilterDefinition, catalog: FilterCatalog): String {
    val types = definition.selectedTypes()
    val type = types.singleOrNull()

    return when (type) {
        FilterAlertType.ROCKET ->
            definition.rocketTypes.singleLabel()?.let { "$it grunts" } ?: "Rocket grunts"

        FilterAlertType.QUEST -> questName(definition, catalog)

        FilterAlertType.RAID ->
            definition.raidSpecies.singleLabel()?.let { "$it raids" }
                ?: definition.raidTiers.singleLabel()?.let { "$it raids" }
                ?: "Raids"

        FilterAlertType.SPAWN -> definition.spawnSpecies.singleLabel() ?: "Spawns"
        FilterAlertType.HUNDO -> definition.hundoSpecies.singleLabel()?.let { "$it hundos" } ?: "Hundos"
        FilterAlertType.NUNDO -> definition.nundoSpecies.singleLabel()?.let { "$it nundos" } ?: "Nundos"
        FilterAlertType.PVP -> definition.pvpSpecies.singleLabel()?.let { "$it PvP" } ?: "PvP"
        FilterAlertType.RARE -> definition.rareSpecies.singleLabel() ?: "Rare spawns"

        // Several types at once, or none narrowed: name the breadth honestly
        // rather than picking one of them and implying the others are excluded.
        else -> when {
            types.isEmpty() -> "Everything"
            types.size <= MAX_LISTED_TYPES -> types.joinToString(" & ") { it.label }
            else -> "${types.size} alert types"
        }
    }
}

private fun questName(definition: FilterDefinition, catalog: FilterCatalog): String {
    val rules = definition.quests
    val pair = rules.exactPairs.singleOrNull()
    if (pair != null) {
        val quest = catalog.quests.firstOrNull {
            it.taskKey == pair.taskKey && it.rewardKey == pair.rewardKey
        }
        quest?.reward?.takeIf { it.isNotBlank() }?.let { return "$it quests" }
    }
    rules.rewards.singleLabel()?.let { return "$it quests" }
    return "Quests"
}

/** The alert types a definition actually narrows to, empty when it takes them all. */
private fun FilterDefinition.selectedTypes(): List<FilterAlertType> =
    if (alertTypes.mode != FilterSelectionMode.ONLY) {
        emptyList()
    } else {
        FilterAlertType.entries.filter { alertTypes.contains(it.name) }
    }

/**
 * The single chosen value, when exactly one was chosen. Anything broader has no
 * name worth borrowing, so the caller falls back to the category.
 */
private fun FilterSelection.singleLabel(): String? {
    if (mode != FilterSelectionMode.ONLY) return null
    val only = values.singleOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return only.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private const val MAX_LISTED_TYPES = 2
