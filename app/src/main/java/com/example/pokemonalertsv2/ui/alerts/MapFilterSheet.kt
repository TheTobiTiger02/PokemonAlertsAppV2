@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.settings.DistanceOverridesDialog
import com.example.pokemonalertsv2.ui.settings.QuestRulesDialog
import com.example.pokemonalertsv2.ui.settings.SelectionDialog
import com.example.pokemonalertsv2.ui.settings.distanceLabel
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TravelTime

private enum class MapSelectorTarget(val title: String) {
    SPAWN("Spawn species"), RARE("Rare species"), HUNDO("Hundo species"),
    NUNDO("Nundo species"), PVP("PvP species"), RAID_SPECIES("Raid species"),
    RAID_TIERS("Raid tiers"), ROCKET("Rocket types")
}

/**
 * Map-local filter editor. Edits the same MAP assignment the Filter Studio owns, so the two
 * never diverge; changes are applied immediately because the map behind the sheet is the preview.
 */
@Composable
internal fun MapFilterSheet(
    definition: FilterDefinition,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    rewardThumbnails: Map<String, String>,
    visibleCount: Int,
    totalCount: Int,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenFilterStudio: () -> Unit,
    onDismiss: () -> Unit,
    useSidePanel: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (useSidePanel) {
        Surface(
            modifier = modifier.width(360.dp).padding(top = 72.dp, end = 16.dp, bottom = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp
        ) {
            MapFilterSheetContent(definition, catalog, artwork, rewardThumbnails, visibleCount, totalCount, onDefinitionChange, onOpenFilterStudio, onDismiss, Modifier.padding(20.dp))
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("map_filter_sheet")
        ) {
            MapFilterSheetContent(definition, catalog, artwork, rewardThumbnails, visibleCount, totalCount, onDefinitionChange, onOpenFilterStudio, onDismiss, Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun MapFilterSheetContent(
    definition: FilterDefinition,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    rewardThumbnails: Map<String, String>,
    visibleCount: Int,
    totalCount: Int,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenFilterStudio: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selector by remember { mutableStateOf<MapSelectorTarget?>(null) }
    var showQuests by remember { mutableStateOf(false) }
    var showDistanceOverrides by remember { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Column(modifier.heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$visibleCount of $totalCount alerts visible", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenFilterStudio) { Text("Filter Studio") }
        }

        // Quick toggles: the collapsed view is one tap from the common case.
        Text("Alert types", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = definition.alertTypes.mode == FilterSelectionMode.ALL,
                onClick = { onDefinitionChange(definition.copy(alertTypes = FilterSelection.All)) },
                label = { Text("All") }
            )
            FilterAlertType.entries.forEach { type ->
                val enabled = definition.alertTypes.contains(type.name)
                FilterChip(
                    selected = enabled,
                    onClick = {
                        val values = definition.alertTypes.normalizedValues.toMutableSet()
                        if (definition.alertTypes.mode == FilterSelectionMode.ALL) {
                            values += FilterAlertType.entries.map { normalizeFilterToken(it.name) }
                        }
                        val key = normalizeFilterToken(type.name)
                        if (enabled) values -= key else values += key
                        onDefinitionChange(definition.copy(alertTypes = FilterSelection.only(values)))
                    },
                    label = { Text(type.label) },
                    leadingIcon = {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(type.mapAccent()))
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.semantics { contentDescription = "${type.label} filter" }
                )
            }
        }

        Text("Distance — ${distanceLabel(definition.maxDistanceKm)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = definition.maxDistanceKm.toFloat(),
            onValueChange = { onDefinitionChange(definition.copy(maxDistanceKm = kotlin.math.round(it).toInt())) },
            valueRange = 0f..MAX_FILTER_DISTANCE_KM.toFloat(),
            modifier = Modifier.semantics { contentDescription = "Maximum distance" }
        )

        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "Fewer options" else "More options")
        }

        if (showDetails) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val overrideCount = definition.distanceOverrides.ruleCount
                OutlinedButton(onClick = { showDistanceOverrides = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (overrideCount == 0) "Per-type and per-species limits" else "Per-type and per-species limits ($overrideCount)")
                }

                Text("Reachable on foot — ${TravelTime.label(definition.maxWalkingMinutes)}", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelTime.PRESET_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = definition.maxWalkingMinutes == minutes,
                            onClick = { onDefinitionChange(definition.copy(maxWalkingMinutes = minutes)) },
                            label = { Text(TravelTime.label(minutes)) }
                        )
                    }
                }

                Text("Species and quests", style = MaterialTheme.typography.labelLarge)
                MapSelectorTarget.entries.forEach { target ->
                    OutlinedButton(onClick = { selector = target }, modifier = Modifier.fillMaxWidth()) {
                        Text(target.title, Modifier.weight(1f))
                        Text(definition.selectionFor(target).mapSummary(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = { showQuests = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Quests", Modifier.weight(1f))
                    Text(
                        if (definition.quests.exactMode == FilterSelectionMode.ALL) "All quests" else "${definition.quests.exactPairs.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onDefinitionChange(FilterDefinition()) }) { Text("Reset") }
            Button(onClick = onDismiss) { Text("Done") }
        }
    }

    selector?.let { target ->
        SelectionDialog(
            title = target.title,
            candidates = target.candidates(catalog),
            current = definition.selectionFor(target),
            onDismiss = { selector = null },
            artwork = artwork
        ) { selection ->
            onDefinitionChange(definition.withSelection(target, selection))
            selector = null
        }
    }
    if (showQuests) {
        QuestRulesDialog(definition.quests, catalog.quests, artwork, rewardThumbnails, { showQuests = false }) {
            onDefinitionChange(definition.copy(quests = it)); showQuests = false
        }
    }
    if (showDistanceOverrides) {
        DistanceOverridesDialog(
            definition = definition,
            speciesCandidates = remember(catalog, artwork) { catalog.speciesLimitCandidates(artwork.keys) },
            artwork = artwork,
            onDismiss = { showDistanceOverrides = false }
        ) { onDefinitionChange(definition.copy(distanceOverrides = it)); showDistanceOverrides = false }
    }
}

private fun FilterSelection.mapSummary(): String = when (mode) {
    FilterSelectionMode.ALL -> "All"
    FilterSelectionMode.NONE -> "None"
    FilterSelectionMode.ONLY -> "$selectedCount"
}

private fun FilterDefinition.selectionFor(target: MapSelectorTarget): FilterSelection = when (target) {
    MapSelectorTarget.SPAWN -> spawnSpecies
    MapSelectorTarget.RARE -> rareSpecies
    MapSelectorTarget.HUNDO -> hundoSpecies
    MapSelectorTarget.NUNDO -> nundoSpecies
    MapSelectorTarget.PVP -> pvpSpecies
    MapSelectorTarget.RAID_SPECIES -> raidSpecies
    MapSelectorTarget.RAID_TIERS -> raidTiers
    MapSelectorTarget.ROCKET -> rocketTypes
}

private fun FilterDefinition.withSelection(target: MapSelectorTarget, selection: FilterSelection): FilterDefinition = when (target) {
    MapSelectorTarget.SPAWN -> copy(spawnSpecies = selection)
    MapSelectorTarget.RARE -> copy(rareSpecies = selection)
    MapSelectorTarget.HUNDO -> copy(hundoSpecies = selection)
    MapSelectorTarget.NUNDO -> copy(nundoSpecies = selection)
    MapSelectorTarget.PVP -> copy(pvpSpecies = selection)
    MapSelectorTarget.RAID_SPECIES -> copy(raidSpecies = selection)
    MapSelectorTarget.RAID_TIERS -> copy(raidTiers = selection)
    MapSelectorTarget.ROCKET -> copy(rocketTypes = selection)
}

private fun MapSelectorTarget.candidates(catalog: FilterCatalog): List<String> = when (this) {
    MapSelectorTarget.RAID_SPECIES -> catalog.raidSpecies
    MapSelectorTarget.RAID_TIERS -> catalog.raidTiers
    MapSelectorTarget.ROCKET -> catalog.rocketTypes
    else -> catalog.spawnSpecies
}

@Composable
private fun FilterAlertType.mapAccent(): Color = when (this) {
    FilterAlertType.SPAWN -> AlertCategory.SPAWN
    FilterAlertType.RAID -> AlertCategory.RAID
    FilterAlertType.QUEST -> AlertCategory.QUEST
    FilterAlertType.ROCKET -> AlertCategory.ROCKET
    FilterAlertType.KECLEON -> AlertCategory.KECLEON
    FilterAlertType.HUNDO -> AlertCategory.HUNDO
    FilterAlertType.NUNDO -> AlertCategory.NUNDO
    FilterAlertType.PVP -> AlertCategory.PVP
    FilterAlertType.RARE -> AlertCategory.RARE
    FilterAlertType.WEATHER -> AlertCategory.WEATHER
    FilterAlertType.OTHER -> AlertCategory.GENERIC
}.accentColor()
