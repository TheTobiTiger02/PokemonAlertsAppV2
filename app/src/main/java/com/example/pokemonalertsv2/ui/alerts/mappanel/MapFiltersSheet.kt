@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.data.speciesLimitCandidates
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.mapAccent
import com.example.pokemonalertsv2.ui.alerts.mapCategory
import com.example.pokemonalertsv2.ui.components.DistanceLimitPicker
import com.example.pokemonalertsv2.ui.settings.DistanceOverridesDialog
import com.example.pokemonalertsv2.ui.settings.QuestRulesDialog
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TravelTime
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelFooterButton
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.panelGutter

/** Everything the Filters tabs read and every way they can act. */
internal class FiltersState(
    val definition: FilterDefinition,
    val catalog: FilterCatalog,
    val artwork: Map<String, String>,
    val rewardThumbnails: Map<String, String>,
    val categoryCounts: Map<AlertCategory, Int>,
    val visibleCount: Int,
    val totalCount: Int,
    val onDefinitionChange: (FilterDefinition) -> Unit,
    val openQuests: () -> Unit,
    val openDistanceOverrides: () -> Unit,
    val openFilterStudio: () -> Unit
)

/**
 * What is on the map, as tabs that edit in place: where (Nearby), then what (types, Pokémon,
 * raids, Rocket, quests). Changes apply immediately; the map behind the sheet is the preview.
 */
@Composable
internal fun MapFiltersSheet(
    definition: FilterDefinition,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    rewardThumbnails: Map<String, String>,
    visibleCount: Int,
    totalCount: Int,
    categoryCounts: Map<AlertCategory, Int>,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenFilterStudio: () -> Unit,
    onDismiss: () -> Unit,
    useSidePanel: Boolean,
    modifier: Modifier = Modifier
) {
    var showQuests by rememberSaveable { mutableStateOf(false) }
    var showDistanceOverrides by rememberSaveable { mutableStateOf(false) }

    MapPanelContainer(
        useSidePanel = useSidePanel,
        testTag = "map_filter_sheet",
        onDismiss = onDismiss,
        modifier = modifier,
        tall = true
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FiltersTabs(
                FiltersState(
                    definition = definition,
                    catalog = catalog,
                    artwork = artwork,
                    rewardThumbnails = rewardThumbnails,
                    categoryCounts = categoryCounts,
                    visibleCount = visibleCount,
                    totalCount = totalCount,
                    onDefinitionChange = onDefinitionChange,
                    openQuests = { showQuests = true },
                    openDistanceOverrides = { showDistanceOverrides = true },
                    openFilterStudio = onOpenFilterStudio
                )
            )
        }
        // The count is read before the sheet closes rather than after.
        PanelFooterButton(
            text = if (visibleCount > 0) "Show $visibleCount alerts" else "Done",
            onClick = onDismiss
        )
    }

    if (showQuests) {
        QuestRulesDialog(
            current = definition.quests,
            catalog = catalog.quests,
            artwork = artwork,
            rewardThumbnails = rewardThumbnails,
            onDismiss = { showQuests = false }
        ) {
            onDefinitionChange(definition.copy(quests = it))
            showQuests = false
        }
    }
    if (showDistanceOverrides) {
        DistanceOverridesDialog(
            definition = definition,
            speciesCandidates = remember(catalog, artwork) { catalog.speciesLimitCandidates(artwork.keys) },
            artwork = artwork,
            onDismiss = { showDistanceOverrides = false }
        ) {
            onDefinitionChange(definition.copy(distanceOverrides = it))
            showDistanceOverrides = false
        }
    }
}

/** Title, how many alerts the filters leave, and Reset when anything is set. */
@Composable
internal fun FiltersHeader(state: FiltersState) {
    PanelHeader(
        title = "Filters",
        subtitle = "${state.visibleCount} of ${state.totalCount} alerts shown",
        actions = {
            // Nothing to reset when nothing is set, and an always-on Reset invites accidents.
            if (state.definition != FilterDefinition()) {
                TextButton(onClick = { state.onDefinitionChange(FilterDefinition()) }) { Text("Reset") }
            }
        }
    )
}

/** One removable chip per rule in force; nothing at all when the filters are the defaults. */
@Composable
internal fun ActiveFiltersRow(state: FiltersState, modifier: Modifier = Modifier) {
    val chips = remember(state.definition, state.catalog) { activeFilterChips(state.definition, state.catalog) }
    if (chips.isEmpty()) return
    LazyRow(
        modifier = modifier.padding(top = Spacing.sm).testTag("map_active_filters"),
        contentPadding = PaddingValues(horizontal = PanelGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(chips, key = { it.id }) { chip ->
            InputChip(
                selected = true,
                onClick = { state.onDefinitionChange(chip.clear(state.definition)) },
                label = { Text(chip.label) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove ${chip.label}",
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/** Max distance, walking time, and the per-type limits behind them. */
@Composable
internal fun DistanceEditor(state: FiltersState) {
    val definition = state.definition
    Column {
        Column(modifier = Modifier.panelGutter(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ValueLine("Max distance", if (definition.maxDistanceMeters > 0) distanceLabel(definition.maxDistanceMeters) else "Any")
            DistanceLimitPicker(
                meters = definition.maxDistanceMeters,
                onChange = { state.onDefinitionChange(definition.copy(maxDistanceMeters = it)) }
            )
        }
        Column(
            modifier = Modifier.panelGutter().padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            ValueLine("Walking time", TravelTime.label(definition.maxWalkingMinutes))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TravelTime.PRESET_MINUTES.forEach { minutes ->
                    val selected = definition.maxWalkingMinutes == minutes
                    FilterChip(
                        selected = selected,
                        onClick = { state.onDefinitionChange(definition.copy(maxWalkingMinutes = minutes)) },
                        label = { Text(TravelTime.label(minutes)) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }
            }
        }
        val count = definition.distanceOverrides.ruleCount
        PanelNavRow(
            title = "Limits per type & species",
            summary = when (count) {
                0 -> "Same limit for everything"
                1 -> "1 custom limit"
                else -> "$count custom limits"
            },
            active = count > 0,
            onClick = state.openDistanceOverrides,
            icon = { PanelIcon(R.drawable.ic_navigate) }
        )
    }
}

/** A setting's name with its current value on the right, above the control that sets it. */
@Composable
private fun ValueLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Alert types as compact chips: dot, name and live count. The chip rail over the map edits the
 * same setting; this also reaches "Other", which the rail has no chip for.
 */
@Composable
internal fun AlertTypesEditor(state: FiltersState) {
    val definition = state.definition
    val allTokens = remember { FilterAlertType.entries.map { it.name } }
    Column(modifier = Modifier.testTag("map_filter_types_page")) {
        EditorNote("The chips above the map change these too.")
        FlowRow(
            modifier = Modifier.panelGutter().padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            FilterAlertType.entries.forEach { type ->
                val selected = definition.alertTypes.contains(type.name)
                val count = state.categoryCounts[type.mapCategory()] ?: 0
                FilterChip(
                    selected = selected,
                    onClick = {
                        state.onDefinitionChange(definition.copy(alertTypes = definition.alertTypes.toggled(type.name, allTokens)))
                    },
                    label = { Text(if (count > 0) "${type.label} · $count" else type.label) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(type.mapAccent().copy(alpha = if (selected) 1f else 0.4f), CircleShape)
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = "${type.label} filter" }
                )
            }
        }
    }
}

@Composable
internal fun EditorNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.panelGutter().padding(top = Spacing.xs, bottom = Spacing.md)
    )
}
