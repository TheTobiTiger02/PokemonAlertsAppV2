@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.ui.alerts.MapSelectorTarget
import com.example.pokemonalertsv2.ui.alerts.SPECIES_TARGETS
import com.example.pokemonalertsv2.ui.alerts.SpeciesPickerContent
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.claimSheetDrags
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.panelGutter
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp

/** The Filters tabs, in the order PogoMapper-style mappers use: where, then what. */
private enum class FiltersTab(val label: String) {
    NEARBY("Nearby"),
    TYPES("Types"),
    POKEMON("Pokémon"),
    RAIDS("Raids"),
    ROCKET("Rocket"),
    QUESTS("Quests")
}

/**
 * Every filter edits in place under a tab strip, no pages to open: the Pokémon tab is the species
 * artwork grid itself, with search and set-all, as PogoMapper lays out its lists.
 */
@Composable
internal fun FiltersTabs(state: FiltersState) {
    var tab by rememberSaveable { mutableStateOf(FiltersTab.NEARBY) }
    var speciesTarget by rememberSaveable { mutableStateOf(MapSelectorTarget.HUNDO) }
    // The sheet has a fixed height, so the tab body takes what is left and switching tabs never
    // resizes the sheet under the thumb.
    Column(modifier = Modifier.fillMaxSize()) {
        FiltersHeader(state)
        ActiveFiltersRow(state)
        PrimaryScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            edgePadding = PanelGutter,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(top = Spacing.sm).testTag("map_filter_tabs")
        ) {
            FiltersTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = { Text(entry.label) },
                    modifier = Modifier.testTag("map_filter_tab_${entry.name.lowercase()}")
                )
            }
        }
        // Only the header, chips and tabs drag the sheet; the body claims the rest.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .claimSheetDrags()
                .padding(top = Spacing.sm)
        ) {
            when (tab) {
                FiltersTab.NEARBY -> ScrollingTab {
                    DistanceEditor(state)
                    PanelSectionLabel("More")
                    PanelNavRow(
                        title = "Advanced rules",
                        summary = "Areas, presets and more in Filter Studio",
                        onClick = state.openFilterStudio,
                        modifier = Modifier.testTag("map_filter_studio"),
                        icon = { PanelIcon(R.drawable.ic_tools) }
                    )
                }
                FiltersTab.TYPES -> ScrollingTab {
                    AllNoneRow(
                        onAll = { state.onDefinitionChange(state.definition.copy(alertTypes = FilterSelection.All)) },
                        onNone = { state.onDefinitionChange(state.definition.copy(alertTypes = FilterSelection.None)) }
                    )
                    AlertTypesEditor(state)
                }
                FiltersTab.POKEMON -> SpeciesPickerContent(
                    target = speciesTarget,
                    onTargetChange = { speciesTarget = it },
                    definition = state.definition,
                    catalog = state.catalog,
                    artwork = state.artwork,
                    onDefinitionChange = state.onDefinitionChange,
                    modifier = Modifier.fillMaxSize().padding(horizontal = PanelGutter),
                    showTitle = false,
                    // Raid bosses live with the raid tiers, in the Raids tab.
                    targets = SPECIES_TARGETS - MapSelectorTarget.RAID_SPECIES
                )
                FiltersTab.RAIDS -> RaidsTab(state)
                FiltersTab.ROCKET -> ScrollingTab {
                    AllNoneRow(
                        onAll = { state.onDefinitionChange(state.definition.copy(rocketTypes = FilterSelection.All)) },
                        onNone = { state.onDefinitionChange(state.definition.copy(rocketTypes = FilterSelection.None)) }
                    )
                    TokenGridOrNote(
                        tokens = state.catalog.rocketTypes,
                        selection = state.definition.rocketTypes,
                        onSelectionChange = { state.onDefinitionChange(state.definition.copy(rocketTypes = it)) },
                        perRow = 3,
                        look = ::rocketTypeLook
                    )
                }
                FiltersTab.QUESTS -> QuestRewardGrid(
                    rules = state.definition.quests,
                    quests = state.catalog.quests,
                    rewardThumbnails = state.rewardThumbnails,
                    artwork = state.artwork,
                    onRulesChange = { state.onDefinitionChange(state.definition.copy(quests = it)) },
                    onOpenRules = state.openQuests
                )
            }
        }
    }
}

@Composable
private fun ScrollingTab(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("map_panel_list")) {
        content()
    }
}

/** The two answers wanted far more often than any single item. */
@Composable
private fun AllNoneRow(onAll: () -> Unit, onNone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = Spacing.sm),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onAll) { Text("All") }
        TextButton(onClick = onNone) { Text("None") }
    }
}

/** Raids: the tiers as eggs, or the bosses as the species grid, one at a time. */
@Composable
private fun RaidsTab(state: FiltersState) {
    var bosses by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().panelGutter().padding(bottom = Spacing.sm)) {
            listOf(false to "Tiers", true to "Bosses").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = bosses == value,
                    onClick = { bosses = value },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    modifier = Modifier.testTag("map_raids_${label.lowercase()}")
                ) { Text(label) }
            }
        }
        if (bosses) {
            SpeciesPickerContent(
                target = MapSelectorTarget.RAID_SPECIES,
                onTargetChange = {},
                definition = state.definition,
                catalog = state.catalog,
                artwork = state.artwork,
                onDefinitionChange = state.onDefinitionChange,
                modifier = Modifier.fillMaxSize().padding(horizontal = PanelGutter),
                showTitle = false,
                targets = listOf(MapSelectorTarget.RAID_SPECIES)
            )
        } else {
            // Four eggs a row leave "Mega Legendary" two cramped lines on a narrow phone.
            BoxWithConstraints {
                val perRow = if (maxWidth < 330.dp) 3 else 4
                ScrollingTab {
                    AllNoneRow(
                        onAll = { state.onDefinitionChange(state.definition.copy(raidTiers = FilterSelection.All)) },
                        onNone = { state.onDefinitionChange(state.definition.copy(raidTiers = FilterSelection.None)) }
                    )
                    TokenGridOrNote(
                        tokens = state.catalog.raidTiers,
                        selection = state.definition.raidTiers,
                        onSelectionChange = { state.onDefinitionChange(state.definition.copy(raidTiers = it)) },
                        perRow = perRow,
                        look = ::raidTierLook
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenGridOrNote(
    tokens: List<String>,
    selection: FilterSelection,
    onSelectionChange: (FilterSelection) -> Unit,
    perRow: Int,
    look: (String) -> TokenLook
) {
    if (tokens.isEmpty()) {
        EditorNote("These load with the next refresh.")
    } else {
        TokenTileGrid(
            tokens = tokens,
            selection = selection,
            onSelectionChange = onSelectionChange,
            perRow = perRow,
            look = look,
            modifier = Modifier.panelGutter().padding(bottom = Spacing.lg)
        )
    }
}
