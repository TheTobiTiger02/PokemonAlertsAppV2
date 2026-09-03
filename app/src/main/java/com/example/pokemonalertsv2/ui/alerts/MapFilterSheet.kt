@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.settings.DistanceOverridesDialog
import com.example.pokemonalertsv2.ui.settings.QuestRulesDialog
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

/** Section ids, stored as Ints so the open section survives a rotation without a custom Saver. */
private const val SECTION_NONE = -1
private const val SECTION_TYPES = 0
private const val SECTION_DISTANCE = 1
private const val SECTION_SPECIES = 2
private const val SECTION_RAIDS = 3
private const val SECTION_ROCKET = 4

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
            MapFilterSheetContent(definition, catalog, artwork, rewardThumbnails, visibleCount, totalCount, onDefinitionChange, onOpenFilterStudio, onDismiss, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
    }
}

/**
 * One scroll, not three tabs.
 *
 * Tabs hid the sheet's own state: a distance set on "General" was invisible from "Species", so
 * the only way to know why the map was empty was to check every tab. Each section now carries a
 * one-line summary of what it is set to while collapsed, which makes the whole filter readable
 * without opening anything.
 */
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
    var expandedSection by rememberSaveable { mutableIntStateOf(SECTION_TYPES) }
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

    fun toggle(section: Int) {
        expandedSection = if (expandedSection == section) SECTION_NONE else section
    }

    val isDefault = definition == FilterDefinition()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.map_filters_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$visibleCount of $totalCount alerts visible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Nothing to reset when nothing is set, and an always-on Reset invites accidents.
            AnimatedVisibility(visible = !isDefault, enter = appExpandIn(), exit = appCollapseOut()) {
                TextButton(onClick = { onDefinitionChange(FilterDefinition()) }) {
                    Text(stringResource(R.string.map_filter_reset))
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            MapFilterSection(
                title = "Alert types",
                summary = definition.alertTypes.typeSummary(),
                active = definition.alertTypes.mode != FilterSelectionMode.ALL,
                expanded = expandedSection == SECTION_TYPES,
                onToggle = { toggle(SECTION_TYPES) }
            ) {
                AlertTypesSection(definition, onDefinitionChange)
            }

            MapFilterSection(
                title = "Distance & walking time",
                summary = definition.distanceSummary(),
                active = definition.maxDistanceKm > 0 || definition.maxWalkingMinutes > 0,
                expanded = expandedSection == SECTION_DISTANCE,
                onToggle = { toggle(SECTION_DISTANCE) }
            ) {
                DistanceSection(definition, onDefinitionChange)
            }

            MapFilterSection(
                title = "Species",
                summary = definition.speciesSummary(),
                active = SPECIES_TARGETS.any { definition.selectionFor(it).mode != FilterSelectionMode.ALL },
                expanded = expandedSection == SECTION_SPECIES,
                onToggle = { toggle(SECTION_SPECIES) }
            ) {
                SpeciesSection(
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
            }

            MapFilterSection(
                title = "Raid tiers",
                summary = definition.raidTiers.tokenSummary(catalog.raidTiers, "tiers"),
                active = definition.raidTiers.mode != FilterSelectionMode.ALL,
                expanded = expandedSection == SECTION_RAIDS,
                onToggle = { toggle(SECTION_RAIDS) }
            ) {
                TokenSelectionSection(
                    tokens = catalog.raidTiers,
                    selection = definition.raidTiers,
                    onSelectionChange = { onDefinitionChange(definition.copy(raidTiers = it)) }
                )
            }

            MapFilterSection(
                title = "Team GO Rocket",
                summary = definition.rocketTypes.tokenSummary(catalog.rocketTypes, "grunt types"),
                active = definition.rocketTypes.mode != FilterSelectionMode.ALL,
                expanded = expandedSection == SECTION_ROCKET,
                onToggle = { toggle(SECTION_ROCKET) }
            ) {
                TokenSelectionSection(
                    tokens = catalog.rocketTypes,
                    selection = definition.rocketTypes,
                    onSelectionChange = { onDefinitionChange(definition.copy(rocketTypes = it)) }
                )
            }

            // These two own a full-screen editor of their own, so they are launchers rather
            // than sections that expand in place.
            MapFilterLauncherRow(
                title = "Field research quests",
                summary = if (definition.quests.exactMode == FilterSelectionMode.ALL) {
                    "All quests visible"
                } else {
                    "${definition.quests.exactPairs.size} quest pairs selected"
                },
                active = definition.quests.exactMode != FilterSelectionMode.ALL ||
                    definition.quests.facetEnabled,
                onClick = { showQuests = true }
            )

            MapFilterLauncherRow(
                title = "Per-type & species limits",
                summary = definition.distanceOverrides.ruleCount.let { count ->
                    if (count == 0) "No custom overrides set" else "$count custom distance rules"
                },
                active = definition.distanceOverrides.ruleCount > 0,
                onClick = { showDistanceOverrides = true }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenFilterStudio) {
                Text(stringResource(R.string.map_filter_studio))
            }
            // The button says what closing the sheet will leave on the map, so the count is
            // read before the sheet is dismissed rather than after.
            Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) {
                Text(
                    if (visibleCount > 0) {
                        stringResource(R.string.map_filter_show_count, visibleCount)
                    } else {
                        "Done"
                    }
                )
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

/**
 * One collapsible filter section.
 *
 * The summary is the point: collapsed, the sheet has to read as a complete statement of what
 * the map is currently narrowed to.
 */
@Composable
private fun MapFilterSection(
    title: String,
    summary: String,
    active: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "map_filter_section_chevron"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (active) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(visible = expanded, enter = appExpandIn(), exit = appCollapseOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)
                ) {
                    content()
                }
            }
        }
    }
}

/** A section whose editor is a dialog rather than an expanding body. */
@Composable
private fun MapFilterLauncherRow(
    title: String,
    summary: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (active) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_filter),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AlertTypesSection(
    definition: FilterDefinition,
    onDefinitionChange: (FilterDefinition) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "The rail above the map edits the same setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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
}

@Composable
private fun DistanceSection(
    definition: FilterDefinition,
    onDefinitionChange: (FilterDefinition) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Straight-line distance", style = MaterialTheme.typography.labelLarge)
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reachable on foot", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = TravelTime.label(definition.maxWalkingMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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
    }
}

/** Raid tiers and Rocket grunt types are the same control over different catalogs. */
@Composable
private fun TokenSelectionSection(
    tokens: List<String>,
    selection: FilterSelection,
    onSelectionChange: (FilterSelection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(
                selected = selection.mode == FilterSelectionMode.ALL,
                onClick = { onSelectionChange(FilterSelection.All) },
                label = { Text("All") },
                shape = RoundedCornerShape(16.dp)
            )
            FilterChip(
                selected = selection.mode == FilterSelectionMode.NONE,
                onClick = { onSelectionChange(FilterSelection.None) },
                label = { Text("None") },
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (tokens.isEmpty()) {
            Text(
                text = "Nothing to choose from yet — the catalog loads with the next refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            tokens.forEach { token ->
                val isSelected = selection.contains(token)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val values = selection.normalizedValues.toMutableSet()
                        if (selection.mode == FilterSelectionMode.ALL) {
                            values += tokens.map(::normalizeFilterToken)
                        }
                        val key = normalizeFilterToken(token)
                        if (isSelected) values -= key else values += key
                        onSelectionChange(FilterSelection.only(values))
                    },
                    label = { Text(token) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeciesSection(
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

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Which list is being edited. Each target keeps its own selection.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            SPECIES_TARGETS.forEach { target ->
                val selected = target == activeTarget
                val selection = definition.selectionFor(target)
                val count = selection.selectedCount
                FilterChip(
                    selected = selected,
                    onClick = { onTargetChange(target) },
                    label = {
                        Text(
                            if (count > 0 && selection.mode == FilterSelectionMode.ONLY) {
                                "${target.shortLabel} ($count)"
                            } else {
                                target.shortLabel
                            }
                        )
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                            Text(
                                when (mode) {
                                    FilterSelectionMode.ALL -> "All"
                                    FilterSelectionMode.NONE -> "None"
                                    FilterSelectionMode.ONLY -> "Selected (${currentSelection.selectedCount})"
                                }
                            )
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

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search ${activeTarget.shortLabel} (name or Dex #)…") },
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

        // Bounded, not weighted: this grid now lives inside the sheet's own vertical scroll,
        // which would otherwise measure it with an infinite height.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            contentPadding = PaddingValues(bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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

// ---------------------------------------------------------------------------- summaries

private fun FilterSelection.typeSummary(): String = when (mode) {
    FilterSelectionMode.ALL -> "All types"
    FilterSelectionMode.NONE -> "No types — the map will be empty"
    FilterSelectionMode.ONLY -> "$selectedCount of ${FilterAlertType.entries.size} types"
}

private fun FilterSelection.tokenSummary(catalog: List<String>, noun: String): String = when (mode) {
    FilterSelectionMode.ALL -> "All $noun"
    FilterSelectionMode.NONE -> "No $noun"
    FilterSelectionMode.ONLY -> {
        val chosen = catalog.filter(::contains)
        when {
            chosen.isEmpty() -> "$selectedCount selected"
            chosen.size <= 3 -> chosen.joinToString(", ")
            else -> chosen.take(2).joinToString(", ") + " +${chosen.size - 2}"
        }
    }
}

private fun FilterDefinition.distanceSummary(): String {
    val parts = buildList {
        if (maxDistanceKm > 0) add(distanceLabel(maxDistanceKm))
        if (maxWalkingMinutes > 0) add(TravelTime.label(maxWalkingMinutes) + " walk")
    }
    return if (parts.isEmpty()) "No limit" else parts.joinToString(" · ")
}

private fun FilterDefinition.speciesSummary(): String {
    val narrowed = SPECIES_TARGETS.filter { selectionFor(it).mode != FilterSelectionMode.ALL }
    return when {
        narrowed.isEmpty() -> "All species"
        narrowed.size <= 3 -> narrowed.joinToString(", ") { it.shortLabel } + " narrowed"
        else -> "${narrowed.size} lists narrowed"
    }
}

// ----------------------------------------------------------------------------- helpers

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
