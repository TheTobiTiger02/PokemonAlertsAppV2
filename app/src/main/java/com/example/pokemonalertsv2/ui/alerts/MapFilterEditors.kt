@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.settings.SpeciesSortOrder
import com.example.pokemonalertsv2.ui.theme.Spacing

internal enum class MapSelectorTarget(val title: String, val shortLabel: String) {
    SPAWN("IV spawn species", "IV spawns"),
    HUNDO("Hundo species", "100%"),
    PVP("PvP species", "PvP"),
    COMMON("Common species", "Common"),
    NUNDO("Nundo species", "0%"),
    RAID_SPECIES("Raid species", "Raid Bosses"),
    RAID_TIERS("Raid tiers", "Tiers"),
    ROCKET("Rocket types", "Rockets"),
    TAPPABLE("Tappable species and loot", "Tappables")
}

internal val SPECIES_TARGETS = listOf(
    MapSelectorTarget.HUNDO,
    MapSelectorTarget.PVP,
    MapSelectorTarget.RAID_SPECIES,
    MapSelectorTarget.COMMON,
    MapSelectorTarget.SPAWN,
    MapSelectorTarget.NUNDO,
    MapSelectorTarget.TAPPABLE
)

/**
 * Alert types as tiles, each carrying its marker colour and how many of that kind are live. The
 * hunt picker uses it; the map's filters use the compact chip page instead.
 */
@Composable
internal fun AlertTypesSection(
    definition: FilterDefinition,
    categoryCounts: Map<AlertCategory, Int>,
    onDefinitionChange: (FilterDefinition) -> Unit,
    setAllLabel: String
) {
    val allTokens = remember { FilterAlertType.entries.map { normalizeFilterToken(it.name) } }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MapSetAllRow(
            label = setAllLabel,
            onAll = { onDefinitionChange(definition.copy(alertTypes = FilterSelection.All)) },
            onNone = { onDefinitionChange(definition.copy(alertTypes = FilterSelection.None)) }
        )
        // Three to a row, sharing the width: a fixed tile width left a ragged column of dead
        // space on the right, and the eleven types then needed six rows instead of four.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = 3
        ) {
            FilterAlertType.entries.forEach { type ->
                val selected = definition.alertTypes.contains(type.name)
                val accent = type.mapAccent()
                MapToggleTile(
                    label = type.label,
                    count = categoryCounts[type.mapCategory()] ?: 0,
                    accent = accent,
                    selected = selected,
                    onClick = {
                        onDefinitionChange(
                            definition.copy(alertTypes = definition.alertTypes.toggled(type.name, allTokens))
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${type.label} filter" }
                )
            }
            // Keeps the last, partly filled row aligned with the ones above it.
            repeat((3 - FilterAlertType.entries.size % 3) % 3) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Wingull's "Set All ✓ ✗" - the two answers that are wanted far more often than any one item. */
@Composable
internal fun MapSetAllRow(
    label: String,
    onAll: () -> Unit,
    onNone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAll, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xxs))
            Text("All")
        }
        TextButton(onClick = onNone, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xxs))
            Text("None")
        }
    }
}

/** A selectable tile: colour, name, and how many are live right now. */
@Composable
private fun MapToggleTile(
    label: String,
    count: Int,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        // Selection is the app's one blue; the type colour stays in the dot, where it helps
        // match the tile to its markers without turning the grid into a paint chart.
        color = if (selected) scheme.primary.copy(alpha = 0.12f) else scheme.surface,
        contentColor = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) scheme.primary else scheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else accent.copy(alpha = 0.35f))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (count > 0) "$count" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * The species picker's body: which list, search, sort, set-all and the artwork grid, all in one
 * lazy grid that scrolls as a whole, so a host must give it bounded space.
 */
@Composable
internal fun SpeciesPickerContent(
    target: MapSelectorTarget,
    onTargetChange: (MapSelectorTarget) -> Unit,
    definition: FilterDefinition,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    onDefinitionChange: (FilterDefinition) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    targets: List<MapSelectorTarget> = SPECIES_TARGETS
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(SpeciesSortOrder.DEX_NUMBER) }
    val gridState = rememberLazyGridState()

    val currentSelection = definition.selectionFor(target)
    val candidates = remember(target, catalog) { target.candidates(catalog) }
    val normalizedCandidates = remember(candidates, currentSelection.values) {
        candidates.associateBy(::normalizeFilterToken).toMutableMap().apply {
            currentSelection.values.forEach { key -> putIfAbsent(normalizeFilterToken(key), key) }
        }.map { (key, value) -> key to value }
    }
    val availableKeys = remember(normalizedCandidates) {
        normalizedCandidates.mapTo(mutableSetOf()) { it.first }
    }
    // Forms ("Arcanine Hisuian", "Mega Dragonite") resolve to their own sprite and the base
    // species' Dex number, so they sort and search alongside it.
    val arts = remember(normalizedCandidates, artwork) {
        normalizedCandidates.associate { (key, value) -> key to speciesArt(value, artwork) }
    }
    fun dexOf(key: String): Int = arts[key]?.dex ?: Int.MAX_VALUE
    val leadingKeys = remember(target, catalog) { target.leadingCandidates(catalog).mapTo(mutableSetOf(), ::normalizeFilterToken) }
    val queryKey = remember(searchQuery) { normalizeFilterToken(searchQuery) }
    val displayList = remember(normalizedCandidates, currentSelection.normalizedValues, queryKey, sortOrder, arts, leadingKeys) {
        normalizedCandidates
            .filter { (key, _) -> key.contains(queryKey) || dexOf(key).let { it != Int.MAX_VALUE && it.toString().contains(queryKey) } }
            .sortedWith(
                compareByDescending<Pair<String, String>> { (key, _) -> key in currentSelection.normalizedValues }
                    .thenByDescending { (key, _) -> key in leadingKeys }
                    .thenComparing { (key, _) ->
                        if (sortOrder == SpeciesSortOrder.DEX_NUMBER) dexOf(key) else Int.MAX_VALUE
                    }
                    .thenBy { (_, value) -> value.lowercase() }
            )
    }
    // A keyed grid keeps its first visible item in view when the order changes, which after a
    // re-sort left the list scrolled to wherever that item had moved. A new order starts at the top.
    LaunchedEffect(sortOrder, target, queryKey) { gridState.scrollToItem(0) }

    // One scroller for the whole picker: the list chips, search and sort scroll away with the
    // grid, so on a narrow or large-text phone the artwork still gets most of the height.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(76.dp),
        state = gridState,
        modifier = modifier.fillMaxWidth().testTag("species_picker_grid"),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.padding(bottom = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (showTitle) {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Which list is being edited. Each target keeps its own selection.
                if (targets.size > 1) FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    targets.forEach { candidate ->
                        val selection = definition.selectionFor(candidate)
                        val count = selection.selectedCount
                        FilterChip(
                            selected = candidate == target,
                            onClick = { onTargetChange(candidate) },
                            label = {
                                Text(
                                    if (count > 0 && selection.mode == FilterSelectionMode.ONLY) {
                                        "${candidate.shortLabel} ($count)"
                                    } else {
                                        candidate.shortLabel
                                    }
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search name or Dex #", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Count, sort and set-all share one row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${displayList.size} shown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentSelection.summaryLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilterChip(
                        selected = true,
                        onClick = {
                            sortOrder = if (sortOrder == SpeciesSortOrder.DEX_NUMBER) {
                                SpeciesSortOrder.NAME_AZ
                            } else {
                                SpeciesSortOrder.DEX_NUMBER
                            }
                        },
                        label = { Text("Sort: ${sortOrder.label}") },
                        shape = RoundedCornerShape(16.dp)
                    )
                    TextButton(
                        onClick = { onDefinitionChange(definition.withSelection(target, FilterSelection.All)) },
                        contentPadding = PaddingValues(horizontal = Spacing.sm)
                    ) { Text("All") }
                    TextButton(
                        onClick = { onDefinitionChange(definition.withSelection(target, FilterSelection.None)) },
                        contentPadding = PaddingValues(horizontal = Spacing.sm)
                    ) { Text("None") }
                }
            }
        }
        gridItems(displayList, key = { it.first }) { (key, value) ->
            val checked = currentSelection.mode == FilterSelectionMode.ALL ||
                (currentSelection.mode == FilterSelectionMode.ONLY &&
                    key in currentSelection.normalizedValues)
            SpeciesTile(
                name = value,
                art = arts[key] ?: SpeciesArt(null, null, null, null),
                checked = checked,
                unavailable = key !in availableKeys,
                onClick = {
                    val currentSet = currentSelection.normalizedValues.toMutableSet()
                    when (currentSelection.mode) {
                        FilterSelectionMode.ALL -> {
                            currentSet.addAll(normalizedCandidates.map { it.first })
                            currentSet.remove(key)
                        }
                        FilterSelectionMode.NONE -> currentSet.add(key)
                        FilterSelectionMode.ONLY ->
                            if (key in currentSet) currentSet.remove(key) else currentSet.add(key)
                    }
                    onDefinitionChange(
                        definition.withSelection(target, FilterSelection.only(currentSet))
                    )
                }
            )
        }
    }
}

@Composable
private fun SpeciesTile(
    name: String,
    art: SpeciesArt,
    checked: Boolean,
    unavailable: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (checked) 2.dp else 1.dp,
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.semantics { contentDescription = if (checked) "$name, selected" else name }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                AsyncImage(
                    model = art.url,
                    contentDescription = null,
                    // Some new forms are listed before their sprite exists.
                    error = art.fallbackUrl?.let { coil.compose.rememberAsyncImagePainter(it) },
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
            art.form?.let { form ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = form,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            art.dex?.let { dexNumber ->
                Text(
                    text = "#${dexNumber.toString().padStart(3, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = name,
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

// ---------------------------------------------------------------------------- summaries

internal fun FilterSelection.summaryLabel(): String = when (mode) {
    FilterSelectionMode.ALL -> "all selected"
    FilterSelectionMode.NONE -> "none selected"
    FilterSelectionMode.ONLY -> "$selectedCount selected"
}

// ----------------------------------------------------------------------------- helpers

internal fun FilterDefinition.selectionFor(target: MapSelectorTarget): FilterSelection = when (target) {
    MapSelectorTarget.SPAWN -> spawnSpecies
    MapSelectorTarget.COMMON -> commonSpecies
    MapSelectorTarget.HUNDO -> hundoSpecies
    MapSelectorTarget.NUNDO -> nundoSpecies
    MapSelectorTarget.PVP -> pvpSpecies
    MapSelectorTarget.RAID_SPECIES -> raidSpecies
    MapSelectorTarget.RAID_TIERS -> raidTiers
    MapSelectorTarget.ROCKET -> rocketTypes
    MapSelectorTarget.TAPPABLE -> tappableSpecies
}

internal fun FilterDefinition.withSelection(target: MapSelectorTarget, selection: FilterSelection): FilterDefinition = when (target) {
    MapSelectorTarget.SPAWN -> copy(spawnSpecies = selection)
    MapSelectorTarget.COMMON -> copy(commonSpecies = selection)
    MapSelectorTarget.HUNDO -> copy(hundoSpecies = selection)
    MapSelectorTarget.NUNDO -> copy(nundoSpecies = selection)
    MapSelectorTarget.PVP -> copy(pvpSpecies = selection)
    MapSelectorTarget.RAID_SPECIES -> copy(raidSpecies = selection)
    MapSelectorTarget.RAID_TIERS -> copy(raidTiers = selection)
    MapSelectorTarget.ROCKET -> copy(rocketTypes = selection)
    MapSelectorTarget.TAPPABLE -> copy(tappableSpecies = selection)
}

internal fun MapSelectorTarget.candidates(catalog: FilterCatalog): List<String> = when (this) {
    MapSelectorTarget.RAID_SPECIES -> catalog.raidSpecies
    MapSelectorTarget.RAID_TIERS -> catalog.raidTiers
    MapSelectorTarget.ROCKET -> catalog.rocketTypes
    MapSelectorTarget.TAPPABLE -> catalog.tappableRewards + catalog.spawnSpecies
    else -> catalog.spawnSpecies
}

/** Candidates listed ahead of the species, which have no Dex number to sort by: Tappable loot. */
internal fun MapSelectorTarget.leadingCandidates(catalog: FilterCatalog): List<String> =
    if (this == MapSelectorTarget.TAPPABLE) catalog.tappableRewards else emptyList()

internal fun FilterAlertType.mapCategory(): AlertCategory = when (this) {
    FilterAlertType.SPAWN -> AlertCategory.SPAWN
    FilterAlertType.RAID -> AlertCategory.RAID
    FilterAlertType.QUEST -> AlertCategory.QUEST
    FilterAlertType.ROCKET -> AlertCategory.ROCKET
    FilterAlertType.KECLEON -> AlertCategory.KECLEON
    FilterAlertType.HUNDO -> AlertCategory.HUNDO
    FilterAlertType.NUNDO -> AlertCategory.NUNDO
    FilterAlertType.PVP -> AlertCategory.PVP
    FilterAlertType.COMMON -> AlertCategory.COMMON
    FilterAlertType.TAPPABLE -> AlertCategory.TAPPABLE
    FilterAlertType.WEATHER -> AlertCategory.WEATHER
    FilterAlertType.OTHER -> AlertCategory.GENERIC
}

@Composable
internal fun FilterAlertType.mapAccent(): Color = mapCategory().accentColor()
