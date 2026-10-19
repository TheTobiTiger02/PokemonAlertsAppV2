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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.data.speciesLimitCandidates
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.MapSelectorTarget
import com.example.pokemonalertsv2.ui.alerts.SPECIES_TARGETS
import com.example.pokemonalertsv2.ui.alerts.SpeciesPickerSheet
import com.example.pokemonalertsv2.ui.alerts.mapAccent
import com.example.pokemonalertsv2.ui.alerts.mapCategory
import com.example.pokemonalertsv2.ui.alerts.selectionFor
import com.example.pokemonalertsv2.ui.alerts.speciesSummary
import com.example.pokemonalertsv2.ui.alerts.tokenSummary
import com.example.pokemonalertsv2.ui.components.DistanceLimitPicker
import com.example.pokemonalertsv2.ui.settings.DistanceOverridesDialog
import com.example.pokemonalertsv2.ui.settings.QuestRulesDialog
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TravelTime

/** The Filters sheet's pages: the list, and the three editors that open over it. */
internal enum class FiltersPage(val title: String) {
    ROOT("Filters"),
    ALERT_TYPES("Alert types"),
    RAID_TIERS("Raid tiers"),
    ROCKET("Team GO Rocket")
}

/**
 * What is on the map. One short list: the rules in force as removable chips, how near an alert
 * has to be, and a row per kind of alert that opens its own editor. Changes apply immediately;
 * the map behind the sheet is the preview.
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
    var page by rememberSaveable { mutableStateOf(FiltersPage.ROOT) }
    var showQuests by rememberSaveable { mutableStateOf(false) }
    var showDistanceOverrides by rememberSaveable { mutableStateOf(false) }
    var speciesPickerTarget by rememberSaveable { mutableStateOf<MapSelectorTarget?>(null) }

    MapPanelContainer(
        useSidePanel = useSidePanel,
        testTag = "map_filter_sheet",
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            MapPanelPages(page = page, root = FiltersPage.ROOT, onBack = { page = FiltersPage.ROOT }) { shown ->
                when (shown) {
                    FiltersPage.ROOT -> FiltersRoot(
                        definition = definition,
                        visibleCount = visibleCount,
                        totalCount = totalCount,
                        catalog = catalog,
                        onDefinitionChange = onDefinitionChange,
                        onOpenPage = { page = it },
                        onOpenSpecies = { speciesPickerTarget = MapSelectorTarget.HUNDO },
                        onOpenQuests = { showQuests = true },
                        onOpenDistanceOverrides = { showDistanceOverrides = true },
                        onOpenFilterStudio = onOpenFilterStudio
                    )
                    FiltersPage.ALERT_TYPES -> AlertTypesPage(
                        definition = definition,
                        categoryCounts = categoryCounts,
                        onDefinitionChange = onDefinitionChange,
                        onBack = { page = FiltersPage.ROOT }
                    )
                    FiltersPage.RAID_TIERS -> TokenPage(
                        title = shown.title,
                        tokens = catalog.raidTiers,
                        selection = definition.raidTiers,
                        onSelectionChange = { onDefinitionChange(definition.copy(raidTiers = it)) },
                        onBack = { page = FiltersPage.ROOT }
                    )
                    FiltersPage.ROCKET -> TokenPage(
                        title = shown.title,
                        tokens = catalog.rocketTypes,
                        selection = definition.rocketTypes,
                        onSelectionChange = { onDefinitionChange(definition.copy(rocketTypes = it)) },
                        onBack = { page = FiltersPage.ROOT }
                    )
                }
            }
        }
        // The count is read before the sheet closes rather than after.
        PanelFooterButton(
            text = if (visibleCount > 0) "Show $visibleCount alerts" else "Done",
            onClick = onDismiss
        )
    }

    speciesPickerTarget?.let { target ->
        SpeciesPickerSheet(
            initialTarget = target,
            definition = definition,
            catalog = catalog,
            artwork = artwork,
            onDefinitionChange = onDefinitionChange,
            onDismiss = { speciesPickerTarget = null }
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

@Composable
private fun FiltersRoot(
    definition: FilterDefinition,
    visibleCount: Int,
    totalCount: Int,
    catalog: FilterCatalog,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenPage: (FiltersPage) -> Unit,
    onOpenSpecies: () -> Unit,
    onOpenQuests: () -> Unit,
    onOpenDistanceOverrides: () -> Unit,
    onOpenFilterStudio: () -> Unit
) {
    val chips = remember(definition, catalog) { activeFilterChips(definition, catalog) }
    Column {
        PanelHeader(
            title = FiltersPage.ROOT.title,
            subtitle = "$visibleCount of $totalCount alerts shown",
            actions = {
                // Nothing to reset when nothing is set, and an always-on Reset invites accidents.
                if (definition != FilterDefinition()) {
                    TextButton(onClick = { onDefinitionChange(FilterDefinition()) }) { Text("Reset") }
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag("map_panel_list"),
            contentPadding = PaddingValues(bottom = Spacing.md)
        ) {
            if (chips.isNotEmpty()) {
                item("active") {
                    LazyRow(
                        modifier = Modifier.padding(top = Spacing.md).testTag("map_active_filters"),
                        contentPadding = PaddingValues(horizontal = PanelGutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(chips, key = { it.id }) { chip ->
                            InputChip(
                                selected = true,
                                onClick = { onDefinitionChange(chip.clear(definition)) },
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
            }

            item("distance_label") { PanelSectionLabel("Distance") }
            item("distance") {
                Column(modifier = Modifier.panelGutter(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ValueLine("Max distance", if (definition.maxDistanceMeters > 0) distanceLabel(definition.maxDistanceMeters) else "Any")
                    DistanceLimitPicker(
                        meters = definition.maxDistanceMeters,
                        onChange = { onDefinitionChange(definition.copy(maxDistanceMeters = it)) }
                    )
                }
            }
            item("walking") {
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
                                onClick = { onDefinitionChange(definition.copy(maxWalkingMinutes = minutes)) },
                                label = { Text(TravelTime.label(minutes)) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null
                            )
                        }
                    }
                }
            }
            item("overrides") {
                val count = definition.distanceOverrides.ruleCount
                PanelNavRow(
                    title = "Limits per type & species",
                    summary = when (count) {
                        0 -> "Same limit for everything"
                        1 -> "1 custom limit"
                        else -> "$count custom limits"
                    },
                    active = count > 0,
                    onClick = onOpenDistanceOverrides,
                    icon = { PanelIcon(R.drawable.ic_navigate) }
                )
            }

            item("kind_label") { PanelSectionLabel("Refine by kind") }
            item("types") {
                PanelNavRow(
                    title = "Alert types",
                    summary = alertTypesSummary(definition.alertTypes),
                    active = definition.alertTypes != FilterDefinition().alertTypes,
                    onClick = { onOpenPage(FiltersPage.ALERT_TYPES) },
                    modifier = Modifier.testTag("map_filter_types"),
                    icon = { PanelIcon(R.drawable.ic_category) }
                )
            }
            item("species") {
                PanelNavRow(
                    title = "Species",
                    summary = definition.speciesSummary(),
                    active = SPECIES_TARGETS.any { definition.selectionFor(it).mode != FilterSelectionMode.ALL },
                    onClick = onOpenSpecies,
                    icon = { PanelIcon(R.drawable.ic_species) }
                )
            }
            item("raids") {
                PanelNavRow(
                    title = FiltersPage.RAID_TIERS.title,
                    summary = definition.raidTiers.tokenSummary(catalog.raidTiers, "tiers"),
                    active = definition.raidTiers.mode != FilterSelectionMode.ALL,
                    onClick = { onOpenPage(FiltersPage.RAID_TIERS) },
                    modifier = Modifier.testTag("map_filter_raids"),
                    icon = { PanelIcon(R.drawable.ic_star_border) }
                )
            }
            item("rocket") {
                PanelNavRow(
                    title = FiltersPage.ROCKET.title,
                    summary = definition.rocketTypes.tokenSummary(catalog.rocketTypes, "grunt types"),
                    active = definition.rocketTypes.mode != FilterSelectionMode.ALL,
                    onClick = { onOpenPage(FiltersPage.ROCKET) },
                    icon = { Text("R", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                )
            }
            item("quests") {
                val active = definition.quests != FilterDefinition().quests
                PanelNavRow(
                    title = "Field research",
                    summary = if (active) "Custom quest rules" else "All quests",
                    active = active,
                    onClick = onOpenQuests,
                    icon = { PanelIcon(R.drawable.ic_reward_item) }
                )
            }

            item("more_label") { PanelSectionLabel("More") }
            item("studio") {
                PanelNavRow(
                    title = "Advanced rules",
                    summary = "Areas, presets and more in Filter Studio",
                    onClick = onOpenFilterStudio,
                    icon = { PanelIcon(R.drawable.ic_tools) }
                )
            }
        }
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

private fun alertTypesSummary(selection: FilterSelection): String = when (selection.mode) {
    FilterSelectionMode.ALL -> "All ${FilterAlertType.entries.size} types"
    FilterSelectionMode.NONE -> "None"
    FilterSelectionMode.ONLY -> "${selection.normalizedValues.size} of ${FilterAlertType.entries.size} types"
}

/** The page header for an editor: back, its title, and All / None. */
@Composable
private fun EditorHeader(title: String, onBack: () -> Unit, onAll: () -> Unit, onNone: () -> Unit) {
    PanelHeader(title = title, onBack = onBack) {
        TextButton(onClick = onAll) { Text("All") }
        TextButton(onClick = onNone) { Text("None") }
    }
}

/**
 * Alert types as compact chips: dot, name and live count. The chip rail over the map edits the
 * same setting; this page also reaches "Other", which the rail has no chip for.
 */
@Composable
private fun AlertTypesPage(
    definition: FilterDefinition,
    categoryCounts: Map<AlertCategory, Int>,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onBack: () -> Unit
) {
    val allTokens = remember { FilterAlertType.entries.map { it.name } }
    Column(modifier = Modifier.testTag("map_filter_types_page")) {
        EditorHeader(
            title = FiltersPage.ALERT_TYPES.title,
            onBack = onBack,
            onAll = { onDefinitionChange(definition.copy(alertTypes = FilterSelection.All)) },
            onNone = { onDefinitionChange(definition.copy(alertTypes = FilterSelection.None)) }
        )
        EditorNote("The chips above the map change these too.")
        FlowRow(
            modifier = Modifier.panelGutter().padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            FilterAlertType.entries.forEach { type ->
                val selected = definition.alertTypes.contains(type.name)
                val count = categoryCounts[type.mapCategory()] ?: 0
                FilterChip(
                    selected = selected,
                    onClick = {
                        onDefinitionChange(definition.copy(alertTypes = definition.alertTypes.toggled(type.name, allTokens)))
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

/** Raid tiers and Rocket grunt types: the same chip list over different catalogs. */
@Composable
private fun TokenPage(
    title: String,
    tokens: List<String>,
    selection: FilterSelection,
    onSelectionChange: (FilterSelection) -> Unit,
    onBack: () -> Unit
) {
    Column {
        EditorHeader(
            title = title,
            onBack = onBack,
            onAll = { onSelectionChange(FilterSelection.All) },
            onNone = { onSelectionChange(FilterSelection.None) }
        )
        if (tokens.isEmpty()) {
            EditorNote("These load with the next refresh.")
        } else {
            EditorNote("Only the ones you pick stay on the map.")
            FlowRow(
                modifier = Modifier.panelGutter().padding(bottom = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                tokens.forEach { token ->
                    val selected = selection.contains(token)
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectionChange(selection.toggled(token, tokens)) },
                        label = { Text(token) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.panelGutter().padding(top = Spacing.xs, bottom = Spacing.md)
    )
}
