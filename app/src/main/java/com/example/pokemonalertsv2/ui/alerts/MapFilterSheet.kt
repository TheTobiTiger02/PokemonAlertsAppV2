@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.settings.DistanceOverridesDialog
import com.example.pokemonalertsv2.ui.settings.QuestRulesDialog
import com.example.pokemonalertsv2.ui.settings.SelectionDialog
import com.example.pokemonalertsv2.ui.settings.SpeciesSortOrder
import com.example.pokemonalertsv2.ui.settings.distanceLabel
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TravelTime

private enum class MapSelectorTarget(val title: String, val shortLabel: String) {
    SPAWN("Spawn species", "Spawns"),
    HUNDO("Hundo species", "100%"),
    PVP("PvP species", "PvP"),
    RARE("Rare species", "Rares"),
    NUNDO("Nundo species", "0%"),
    RAID_SPECIES("Raid species", "Raid Bosses"),
    RAID_TIERS("Raid tiers", "Tiers"),
    ROCKET("Rocket types", "Rockets")
}

private val SPECIES_TARGETS = listOf(
    MapSelectorTarget.HUNDO,
    MapSelectorTarget.PVP,
    MapSelectorTarget.RAID_SPECIES,
    MapSelectorTarget.RARE,
    MapSelectorTarget.SPAWN,
    MapSelectorTarget.NUNDO
)

private val DISTANCE_PRESETS = listOf(0, 1, 3, 5, 10, 25)

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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showQuests by remember { mutableStateOf(false) }
    var showDistanceOverrides by remember { mutableStateOf(false) }
    var speciesTarget by rememberSaveable { mutableStateOf(MapSelectorTarget.HUNDO) }
    var speciesSearchQuery by rememberSaveable { mutableStateOf("") }
    var speciesSortOrder by rememberSaveable { mutableStateOf(SpeciesSortOrder.DEX_NUMBER) }

    fun extractDex(key: String): Int {
        val url = artwork[key] ?: return Int.MAX_VALUE
        val match = Regex("""/(\d+)\.png""").find(url) ?: return Int.MAX_VALUE
        return match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Map Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$visibleCount of $totalCount alerts visible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { onDefinitionChange(FilterDefinition()) }) {
                    Text("Reset")
                }
                TextButton(onClick = onOpenFilterStudio) {
                    Text("Filter Studio")
                }
            }
        }

        // Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("General", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Species", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Raids & More", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        // Tab Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> GeneralFilterTab(
                    definition = definition,
                    onDefinitionChange = onDefinitionChange,
                    onOpenDistanceOverrides = { showDistanceOverrides = true }
                )
                1 -> SpeciesFilterTab(
                    activeTarget = speciesTarget,
                    onTargetChange = { speciesTarget = it },
                    definition = definition,
                    catalog = catalog,
                    artwork = artwork,
                    searchQuery = speciesSearchQuery,
                    onSearchQueryChange = { speciesSearchQuery = it },
                    sortOrder = speciesSortOrder,
                    onToggleSortOrder = {
                        speciesSortOrder = if (speciesSortOrder == SpeciesSortOrder.DEX_NUMBER) {
                            SpeciesSortOrder.NAME_AZ
                        } else {
                            SpeciesSortOrder.DEX_NUMBER
                        }
                    },
                    extractDex = ::extractDex,
                    onDefinitionChange = onDefinitionChange
                )
                2 -> RaidsAndMoreTab(
                    definition = definition,
                    catalog = catalog,
                    onDefinitionChange = onDefinitionChange,
                    onOpenQuests = { showQuests = true }
                )
            }
        }

        // Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Done")
            }
        }
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
private fun GeneralFilterTab(
    definition: FilterDefinition,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenDistanceOverrides: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Alert Types Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Alert Types", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = definition.alertTypes.mode == FilterSelectionMode.ALL,
                    onClick = { onDefinitionChange(definition.copy(alertTypes = FilterSelection.All)) },
                    label = { Text("All") },
                    shape = RoundedCornerShape(16.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(type.mapAccent())
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.semantics { contentDescription = "${type.label} filter" }
                    )
                }
            }
        }

        // Distance Section
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Distance Range", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = distanceLabel(definition.maxDistanceKm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = definition.maxDistanceKm.toFloat(),
                onValueChange = { onDefinitionChange(definition.copy(maxDistanceKm = kotlin.math.round(it).toInt())) },
                valueRange = 0f..MAX_FILTER_DISTANCE_KM.toFloat(),
                modifier = Modifier.semantics { contentDescription = "Maximum distance" }
            )
            // Quick preset chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DISTANCE_PRESETS.forEach { km ->
                    FilterChip(
                        selected = definition.maxDistanceKm == km,
                        onClick = { onDefinitionChange(definition.copy(maxDistanceKm = km)) },
                        label = { Text(if (km == 0) "Unlimited" else "$km km") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Reachable on Foot (Walking Time) Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reachable on Foot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = TravelTime.label(definition.maxWalkingMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TravelTime.PRESET_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = definition.maxWalkingMinutes == minutes,
                        onClick = { onDefinitionChange(definition.copy(maxWalkingMinutes = minutes)) },
                        label = { Text(TravelTime.label(minutes)) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Distance Overrides Card
        val overrideCount = definition.distanceOverrides.ruleCount
        OutlinedCard(
            onClick = onOpenDistanceOverrides,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Per-Type & Species Limits", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (overrideCount == 0) "No custom overrides set" else "$overrideCount custom distance rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_filter),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeciesFilterTab(
    activeTarget: MapSelectorTarget,
    onTargetChange: (MapSelectorTarget) -> Unit,
    definition: FilterDefinition,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SpeciesSortOrder,
    onToggleSortOrder: () -> Unit,
    extractDex: (String) -> Int,
    onDefinitionChange: (FilterDefinition) -> Unit
) {
    val currentSelection = definition.selectionFor(activeTarget)
    val candidates = remember(activeTarget, catalog) { activeTarget.candidates(catalog) }

    val normalizedCandidates = remember(candidates, currentSelection.values) {
        candidates.associateBy(::normalizeFilterToken).toMutableMap().apply {
            currentSelection.values.forEach { key -> putIfAbsent(normalizeFilterToken(key), key) }
        }.map { (key, value) -> key to value }
    }
    val availableKeys = remember(normalizedCandidates) { normalizedCandidates.mapTo(mutableSetOf()) { it.first } }
    val queryKey = remember(searchQuery) { normalizeFilterToken(searchQuery) }

    val displayList = remember(normalizedCandidates, currentSelection.normalizedValues, queryKey, sortOrder, artwork) {
        normalizedCandidates
            .filter { (key, _) ->
                key.contains(queryKey) || extractDex(key).toString().contains(queryKey)
            }
            .sortedWith(
                compareByDescending<Pair<String, String>> { (key, _) -> key in currentSelection.normalizedValues }
                    .thenComparing { (key, _) ->
                        if (sortOrder == SpeciesSortOrder.DEX_NUMBER) {
                            extractDex(key)
                        } else {
                            Int.MAX_VALUE
                        }
                    }
                    .thenBy { (_, value) -> value.lowercase() }
            )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Target selector chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SPECIES_TARGETS.forEach { target ->
                val selected = target == activeTarget
                val count = definition.selectionFor(target).selectedCount
                FilterChip(
                    selected = selected,
                    onClick = { onTargetChange(target) },
                    label = {
                        Text(if (count > 0 && definition.selectionFor(target).mode == FilterSelectionMode.ONLY) "${target.shortLabel} ($count)" else target.shortLabel)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Mode chips & Search/Sort row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterSelectionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = currentSelection.mode == mode,
                        onClick = {
                            val newSelection = when (mode) {
                                FilterSelectionMode.ALL -> FilterSelection.All
                                FilterSelectionMode.NONE -> FilterSelection.None
                                FilterSelectionMode.ONLY -> FilterSelection.only(currentSelection.normalizedValues)
                            }
                            onDefinitionChange(definition.withSelection(activeTarget, newSelection))
                        },
                        label = {
                            Text(when (mode) {
                                FilterSelectionMode.ALL -> "All"
                                FilterSelectionMode.NONE -> "None"
                                FilterSelectionMode.ONLY -> "Selected (${currentSelection.selectedCount})"
                            })
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            FilterChip(
                selected = true,
                onClick = onToggleSortOrder,
                label = { Text("Sort: ${sortOrder.label}") },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search ${activeTarget.shortLabel} (name or Dex #)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors()
        )

        // Direct Species Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            gridItems(displayList, key = { it.first }) { (key, value) ->
                val checked = currentSelection.mode == FilterSelectionMode.ALL ||
                    (currentSelection.mode == FilterSelectionMode.ONLY && key in currentSelection.normalizedValues)
                val unavailable = key !in availableKeys
                val dexNum = extractDex(key)

                OutlinedCard(
                    onClick = {
                        val currentSet = currentSelection.normalizedValues.toMutableSet()
                        if (currentSelection.mode == FilterSelectionMode.ALL) {
                            currentSet.addAll(normalizedCandidates.map { it.first })
                            currentSet.remove(key)
                        } else if (currentSelection.mode == FilterSelectionMode.NONE) {
                            currentSet.add(key)
                        } else {
                            if (key in currentSet) currentSet.remove(key) else currentSet.add(key)
                        }
                        onDefinitionChange(definition.withSelection(activeTarget, FilterSelection.only(currentSet)))
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (checked) 2.dp else 1.dp,
                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.semantics { contentDescription = if (checked) "$value, selected" else value }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 78.dp)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            AsyncImage(
                                model = artwork[key],
                                contentDescription = null,
                                modifier = Modifier.size(38.dp)
                            )
                            if (checked) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        if (dexNum != Int.MAX_VALUE) {
                            Text(
                                text = "#${dexNum.toString().padStart(3, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (unavailable) {
                            Text(
                                text = "Unavailable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RaidsAndMoreTab(
    definition: FilterDefinition,
    catalog: FilterCatalog,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenQuests: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Raid Tiers
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Raid Tiers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = definition.raidTiers.mode == FilterSelectionMode.ALL,
                        onClick = { onDefinitionChange(definition.copy(raidTiers = FilterSelection.All)) },
                        label = { Text("All") },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = definition.raidTiers.mode == FilterSelectionMode.NONE,
                        onClick = { onDefinitionChange(definition.copy(raidTiers = FilterSelection.None)) },
                        label = { Text("None") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                catalog.raidTiers.forEach { tier ->
                    val isSelected = definition.raidTiers.contains(tier)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val values = definition.raidTiers.normalizedValues.toMutableSet()
                            if (definition.raidTiers.mode == FilterSelectionMode.ALL) {
                                values += catalog.raidTiers.map(::normalizeFilterToken)
                            }
                            val key = normalizeFilterToken(tier)
                            if (isSelected) values -= key else values += key
                            onDefinitionChange(definition.copy(raidTiers = FilterSelection.only(values)))
                        },
                        label = { Text(tier) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Team GO Rocket Types
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Team GO Rocket", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = definition.rocketTypes.mode == FilterSelectionMode.ALL,
                        onClick = { onDefinitionChange(definition.copy(rocketTypes = FilterSelection.All)) },
                        label = { Text("All") },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = definition.rocketTypes.mode == FilterSelectionMode.NONE,
                        onClick = { onDefinitionChange(definition.copy(rocketTypes = FilterSelection.None)) },
                        label = { Text("None") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                catalog.rocketTypes.forEach { grunt ->
                    val isSelected = definition.rocketTypes.contains(grunt)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val values = definition.rocketTypes.normalizedValues.toMutableSet()
                            if (definition.rocketTypes.mode == FilterSelectionMode.ALL) {
                                values += catalog.rocketTypes.map(::normalizeFilterToken)
                            }
                            val key = normalizeFilterToken(grunt)
                            if (isSelected) values -= key else values += key
                            onDefinitionChange(definition.copy(rocketTypes = FilterSelection.only(values)))
                        },
                        label = { Text(grunt) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Field Research Quests Card
        OutlinedCard(
            onClick = onOpenQuests,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Field Research Quests", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (definition.quests.exactMode == FilterSelectionMode.ALL) {
                            "All quests visible"
                        } else {
                            "${definition.quests.exactPairs.size} quest pairs selected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_filter),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
