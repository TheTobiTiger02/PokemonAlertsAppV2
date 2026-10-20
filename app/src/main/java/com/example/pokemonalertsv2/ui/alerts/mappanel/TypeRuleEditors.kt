package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.ui.alerts.MapSelectorTarget
import com.example.pokemonalertsv2.ui.alerts.SpeciesPickerContent
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow

/**
 * The rules one alert type can narrow, and the editor for them. Kecleon, weather and "other"
 * have none: they are on or off.
 */
internal enum class TypeEditor(val type: FilterAlertType) {
    SPAWN(FilterAlertType.SPAWN),
    HUNDO(FilterAlertType.HUNDO),
    PVP(FilterAlertType.PVP),
    NUNDO(FilterAlertType.NUNDO),
    COMMON(FilterAlertType.COMMON),
    RAIDS(FilterAlertType.RAID),
    ROCKET(FilterAlertType.ROCKET),
    QUESTS(FilterAlertType.QUEST)
}

internal fun FilterAlertType.editor(): TypeEditor? = TypeEditor.entries.firstOrNull { it.type == this }

private val ALL_TYPE_TOKENS = FilterAlertType.entries.map { it.name }

internal fun FilterDefinition.showsType(type: FilterAlertType): Boolean = alertTypes.contains(type.name)

/** Switches one alert type on or off, leaving the others as they are. */
internal fun FilterDefinition.withType(type: FilterAlertType, shown: Boolean): FilterDefinition =
    if (showsType(type) == shown) this else copy(alertTypes = alertTypes.toggled(type.name, ALL_TYPE_TOKENS))

private fun FilterSelection.count(noun: String, plural: String = "${noun}s"): String = when (mode) {
    FilterSelectionMode.ALL -> "All $plural"
    FilterSelectionMode.NONE -> "No $plural"
    FilterSelectionMode.ONLY -> if (selectedCount == 1) values.first() else "$selectedCount $plural"
}

/**
 * One line saying what a type shows, for rows and tiles that summarise a filter without opening
 * it: "All species", "Tier 5 · 3 bosses", "Off".
 */
internal fun FilterAlertType.ruleSummary(definition: FilterDefinition): String {
    if (!definition.showsType(this)) return "Off"
    val rules = when (this) {
        FilterAlertType.SPAWN -> definition.spawnSpecies.count("species", "species")
        FilterAlertType.HUNDO -> definition.hundoSpecies.count("species", "species")
        FilterAlertType.PVP -> definition.pvpSpecies.count("species", "species")
        FilterAlertType.NUNDO -> definition.nundoSpecies.count("species", "species")
        FilterAlertType.COMMON -> definition.commonSpecies.count("species", "species")
        FilterAlertType.RAID -> "${definition.raidTiers.count("tier")} · ${definition.raidSpecies.count("boss", "bosses").replaceFirstChar { it.lowercase() }}"
        FilterAlertType.ROCKET -> definition.rocketTypes.count("grunt type")
        FilterAlertType.QUEST -> when {
            definition.quests.facetEnabled -> "Task and reward rules"
            definition.quests.exactMode == FilterSelectionMode.ALL -> "All quests"
            definition.quests.exactMode == FilterSelectionMode.NONE -> "No quests"
            else -> definition.quests.exactPairs.size.let { if (it == 1) "1 quest" else "$it quests" }
        }
        else -> "Everything of this type"
    }
    val limit = definition.distanceOverrides.perType[name]
    return if (limit != null) "$rules · ${distanceLabel(limit)}" else rules
}

/** Whether a type narrows anything beyond being on: drives the blue "active" summaries. */
internal fun FilterAlertType.isNarrowed(definition: FilterDefinition): Boolean =
    definition.showsType(this) && ruleSummary(definition).let { !it.startsWith("All") && it != "Everything of this type" }

private fun TypeEditor.speciesTarget(): MapSelectorTarget? = when (this) {
    TypeEditor.SPAWN -> MapSelectorTarget.SPAWN
    TypeEditor.HUNDO -> MapSelectorTarget.HUNDO
    TypeEditor.PVP -> MapSelectorTarget.PVP
    TypeEditor.NUNDO -> MapSelectorTarget.NUNDO
    TypeEditor.COMMON -> MapSelectorTarget.COMMON
    else -> null
}

/** The editor itself, filling the space it is given. */
@Composable
internal fun TypeEditorBody(editor: TypeEditor, state: FiltersState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().testTag("type_editor_${editor.name.lowercase()}")) {
        val target = editor.speciesTarget()
        when {
            target != null -> SpeciesPickerContent(
                target = target,
                onTargetChange = {},
                definition = state.definition,
                catalog = state.catalog,
                artwork = state.artwork,
                onDefinitionChange = state.onDefinitionChange,
                modifier = Modifier.fillMaxSize().padding(horizontal = PanelGutter),
                showTitle = false,
                targets = listOf(target)
            )
            editor == TypeEditor.RAIDS -> RaidsTab(state)
            editor == TypeEditor.ROCKET -> RocketTab(state)
            else -> QuestsTab(state)
        }
    }
}

/**
 * A page for one type: a back arrow, a switch for the type itself, then its rules. Used where a
 * filter is a list of types that each open, rather than tabs.
 */
@Composable
internal fun TypeEditorPage(editor: TypeEditor, state: FiltersState, onBack: () -> Unit) {
    val type = editor.type
    Column(modifier = Modifier.fillMaxSize()) {
        PanelHeader(title = type.label, subtitle = type.ruleSummary(state.definition), onBack = onBack)
        PanelSwitchRow(
            title = "Show these alerts",
            checked = state.definition.showsType(type),
            onCheckedChange = { state.onDefinitionChange(state.definition.withType(type, it)) },
            modifier = Modifier.testTag("type_switch_${editor.name.lowercase()}")
        )
        TypeEditorBody(editor, state, Modifier.fillMaxWidth().weight(1f))
    }
}
