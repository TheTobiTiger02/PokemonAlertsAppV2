package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.ui.alerts.MapSelectorTarget
import com.example.pokemonalertsv2.ui.alerts.SpeciesPickerContent
import com.example.pokemonalertsv2.ui.components.PanelGutter

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
    QUESTS(FilterAlertType.QUEST),
    TAPPABLES(FilterAlertType.TAPPABLE)
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
            editor == TypeEditor.TAPPABLES -> TappablesTab(state)
            else -> QuestsTab(state)
        }
    }
}

