@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.hunt.HuntControls
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.settings.DistanceOverridesDialog
import com.example.pokemonalertsv2.ui.settings.QuestRulesDialog
import com.example.pokemonalertsv2.ui.settings.SpeciesSortOrder
import com.example.pokemonalertsv2.ui.settings.SwitchSetting
import com.example.pokemonalertsv2.ui.settings.distanceLabel
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.s2CellAt
import com.example.pokemonalertsv2.util.TravelTime

internal enum class MapSelectorTarget(val title: String, val shortLabel: String) {
    SPAWN("Spawn species", "Spawns"),
    HUNDO("Hundo species", "100%"),
    PVP("PvP species", "PvP"),
    RARE("Rare species", "Rares"),
    NUNDO("Nundo species", "0%"),
    RAID_SPECIES("Raid species", "Raid Bosses"),
    RAID_TIERS("Raid tiers", "Tiers"),
    ROCKET("Rocket types", "Rockets")
}

internal val SPECIES_TARGETS = listOf(
    MapSelectorTarget.HUNDO,
    MapSelectorTarget.PVP,
    MapSelectorTarget.RAID_SPECIES,
    MapSelectorTarget.RARE,
    MapSelectorTarget.SPAWN,
    MapSelectorTarget.NUNDO
)

private val DISTANCE_PRESETS = listOf(0, 1, 3, 5, 10, 25)

/**
 * Section ids, kept as bits of one Int so several sections can be open at once and the set
 * survives a rotation without a custom Saver.
 */
private const val SECTION_TYPES = 0
private const val SECTION_RAIDS = 1
private const val SECTION_ROCKET = 2
private const val SECTION_DISTANCE = 3
private const val SECTION_STYLE = 4
private const val SECTION_DENSITY = 5
private const val SECTION_OVERLAYS = 6
private const val SECTION_BEHAVIOUR = 7

/**
 * Everything the map can do, behind the rail's settings button.
 *
 * The panel is one list, in three plain-language groups: what is on the map, how near it has to
 * be, and how it all looks. That ordering is the frequency ordering - narrowing is a daily
 * action, restyling is not - and the group headings exist so a setting can be found by asking
 * the question it answers rather than by remembering its name.
 *
 * Changes apply immediately: the map behind the panel is the preview.
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
    refreshing: Boolean,
    onRefresh: () -> Unit,
    userLocation: android.location.Location?,
    weatherCells: List<MapWeatherCell>,
    mapCentre: MapCameraSnapshot,
    mapStyle: MapStylePreference,
    onMapStyleChanged: (MapStylePreference) -> Unit,
    showTimeLabels: Boolean,
    onToggleTimeLabels: () -> Unit,
    showSpawnRadius: Boolean,
    onToggleSpawnRadius: () -> Unit,
    spacialRendEnabled: Boolean,
    onToggleSpacialRend: () -> Unit,
    showWeatherCells: Boolean,
    onToggleWeatherCells: () -> Unit,
    showDismissed: Boolean,
    onToggleDismissed: () -> Unit,
    categoryCounts: Map<AlertCategory, Int> = emptyMap(),
    onEnterPictureInPicture: (() -> Unit)? = null,
    autoEnterPictureInPicture: Boolean = false,
    onToggleAutoEnterPictureInPicture: (() -> Unit)? = null,
    useSidePanel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val content = @Composable { contentModifier: Modifier ->
        MapFilterSheetContent(
            definition = definition,
            catalog = catalog,
            artwork = artwork,
            rewardThumbnails = rewardThumbnails,
            visibleCount = visibleCount,
            totalCount = totalCount,
            categoryCounts = categoryCounts,
            onDefinitionChange = onDefinitionChange,
            onOpenFilterStudio = onOpenFilterStudio,
            onDismiss = onDismiss,
            refreshing = refreshing,
            onRefresh = onRefresh,
            userLocation = userLocation,
            weatherCells = weatherCells,
            mapCentre = mapCentre,
            mapStyle = mapStyle,
            onMapStyleChanged = onMapStyleChanged,
            showTimeLabels = showTimeLabels,
            onToggleTimeLabels = onToggleTimeLabels,
            showSpawnRadius = showSpawnRadius,
            onToggleSpawnRadius = onToggleSpawnRadius,
            spacialRendEnabled = spacialRendEnabled,
            onToggleSpacialRend = onToggleSpacialRend,
            showWeatherCells = showWeatherCells,
            onToggleWeatherCells = onToggleWeatherCells,
            showDismissed = showDismissed,
            onToggleDismissed = onToggleDismissed,
            onEnterPictureInPicture = onEnterPictureInPicture,
            autoEnterPictureInPicture = autoEnterPictureInPicture,
            onToggleAutoEnterPictureInPicture = onToggleAutoEnterPictureInPicture,
            modifier = contentModifier
        )
    }

    if (useSidePanel) {
        Surface(
            modifier = modifier
                .width(380.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = MAP_TOP_CHROME_HEIGHT, end = 16.dp, bottom = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp
        ) {
            content(Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        }
    } else {
        // Fully expanded from the start. At partial expansion the sheet's own drag handling
        // takes the first vertical gesture to grow itself, so the first swipe over the content
        // scrolled nothing - the panel simply looked like it had ignored the gesture.
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("map_filter_sheet")
        ) {
            content(Modifier.padding(horizontal = 20.dp))
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
    categoryCounts: Map<AlertCategory, Int>,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenFilterStudio: () -> Unit,
    onDismiss: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    userLocation: android.location.Location?,
    weatherCells: List<MapWeatherCell>,
    mapCentre: MapCameraSnapshot,
    mapStyle: MapStylePreference,
    onMapStyleChanged: (MapStylePreference) -> Unit,
    showTimeLabels: Boolean,
    onToggleTimeLabels: () -> Unit,
    showSpawnRadius: Boolean,
    onToggleSpawnRadius: () -> Unit,
    spacialRendEnabled: Boolean,
    onToggleSpacialRend: () -> Unit,
    showWeatherCells: Boolean,
    onToggleWeatherCells: () -> Unit,
    showDismissed: Boolean,
    onToggleDismissed: () -> Unit,
    onEnterPictureInPicture: (() -> Unit)? = null,
    autoEnterPictureInPicture: Boolean = false,
    onToggleAutoEnterPictureInPicture: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // A bitmask rather than a single id: closing one section to read another, and losing the
    // first, made comparing two settings a game of memory.
    var expandedMask by rememberSaveable { mutableIntStateOf(1 shl SECTION_TYPES) }
    var showQuests by rememberSaveable { mutableStateOf(false) }
    var showDistanceOverrides by rememberSaveable { mutableStateOf(false) }
    var speciesPickerTarget by rememberSaveable { mutableStateOf<MapSelectorTarget?>(null) }

    fun isOpen(section: Int) = expandedMask and (1 shl section) != 0
    fun toggle(section: Int) {
        expandedMask = expandedMask xor (1 shl section)
    }

    val isDefault = definition == FilterDefinition()

    Column(modifier = modifier.fillMaxWidth()) {
        MapPanelHeader(
            visibleCount = visibleCount,
            totalCount = totalCount,
            isDefault = isDefault,
            onReset = { onDefinitionChange(FilterDefinition()) },
            userLocation = userLocation,
            weatherCells = weatherCells,
            refreshing = refreshing,
            onRefresh = onRefresh,
            onEnterPictureInPicture = onEnterPictureInPicture,
            catalog = catalog,
            artwork = artwork,
            questRewardThumbnails = rewardThumbnails,
            categoryCounts = categoryCounts
        )

        // One scroller, and nothing scrollable inside it. The species grid used to live here as
        // a height-capped LazyVerticalGrid, which meant a drag starting over the grid could
        // never reach the panel; it has its own full-height sheet now.
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .testTag("map_panel_list"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(bottom = Spacing.md)
        ) {
            item("group_what") { MapGroupHeading("What's on the map") }

            item("types") {
                MapPanelSection(
                    title = "Alert types",
                    summary = definition.alertTypes.typeSummary(),
                    active = definition.alertTypes.mode != FilterSelectionMode.ALL,
                    expanded = isOpen(SECTION_TYPES),
                    onToggle = { toggle(SECTION_TYPES) }
                ) {
                    AlertTypesSection(definition, categoryCounts, onDefinitionChange)
                }
            }

            item("species") {
                MapPanelLauncherRow(
                    title = "Species",
                    summary = definition.speciesSummary(),
                    active = SPECIES_TARGETS.any {
                        definition.selectionFor(it).mode != FilterSelectionMode.ALL
                    },
                    onClick = { speciesPickerTarget = MapSelectorTarget.HUNDO }
                )
            }

            item("raids") {
                MapPanelSection(
                    title = "Raid tiers",
                    summary = definition.raidTiers.tokenSummary(catalog.raidTiers, "tiers"),
                    active = definition.raidTiers.mode != FilterSelectionMode.ALL,
                    expanded = isOpen(SECTION_RAIDS),
                    onToggle = { toggle(SECTION_RAIDS) }
                ) {
                    TokenSelectionSection(
                        tokens = catalog.raidTiers,
                        selection = definition.raidTiers,
                        onSelectionChange = { onDefinitionChange(definition.copy(raidTiers = it)) }
                    )
                }
            }

            item("rocket") {
                MapPanelSection(
                    title = "Team GO Rocket",
                    summary = definition.rocketTypes.tokenSummary(catalog.rocketTypes, "grunt types"),
                    active = definition.rocketTypes.mode != FilterSelectionMode.ALL,
                    expanded = isOpen(SECTION_ROCKET),
                    onToggle = { toggle(SECTION_ROCKET) }
                ) {
                    TokenSelectionSection(
                        tokens = catalog.rocketTypes,
                        selection = definition.rocketTypes,
                        onSelectionChange = { onDefinitionChange(definition.copy(rocketTypes = it)) }
                    )
                }
            }

            item("quests") {
                MapPanelLauncherRow(
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
            }

            item("group_near") { MapGroupHeading("How near") }

            item("distance") {
                MapPanelSection(
                    title = "Distance & walking time",
                    summary = definition.distanceSummary(),
                    active = definition.maxDistanceKm > 0 || definition.maxWalkingMinutes > 0,
                    expanded = isOpen(SECTION_DISTANCE),
                    onToggle = { toggle(SECTION_DISTANCE) }
                ) {
                    DistanceSection(definition, onDefinitionChange)
                }
            }

            item("overrides") {
                MapPanelLauncherRow(
                    title = "Per-type & species limits",
                    summary = definition.distanceOverrides.ruleCount.let { count ->
                        if (count == 0) "No custom overrides set" else "$count custom distance rules"
                    },
                    active = definition.distanceOverrides.ruleCount > 0,
                    onClick = { showDistanceOverrides = true }
                )
            }

            item("group_looks") { MapGroupHeading("How it looks") }

            item("style") {
                MapPanelSection(
                    title = "Map style",
                    summary = mapStyle.panelLabel(),
                    active = mapStyle != MapStylePreference.GOOGLE_STANDARD,
                    expanded = isOpen(SECTION_STYLE),
                    onToggle = { toggle(SECTION_STYLE) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            MapStylePreference.entries.forEach { style ->
                                MapStyleTile(
                                    style = style,
                                    selected = mapStyle == style,
                                    onClick = { onMapStyleChanged(style) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (mapStyle == MapStylePreference.OPENSTREETMAP) {
                            OfflineTilesSection(centre = mapCentre)
                        }
                    }
                }
            }

            item("density") {
                MapClusteringSection(
                    expanded = isOpen(SECTION_DENSITY),
                    onToggle = { toggle(SECTION_DENSITY) }
                )
            }

            item("overlays") {
                MapPanelSection(
                    title = "Overlays",
                    summary = overlaySummary(
                        showTimeLabels = showTimeLabels,
                        showSpawnRadius = showSpawnRadius,
                        spacialRendEnabled = spacialRendEnabled,
                        showWeatherCells = showWeatherCells
                    ),
                    active = showTimeLabels || showSpawnRadius || spacialRendEnabled,
                    expanded = isOpen(SECTION_OVERLAYS),
                    onToggle = { toggle(SECTION_OVERLAYS) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        SwitchSetting(
                            title = "Countdown labels",
                            subtitle = "Print the time left under every marker",
                            checked = showTimeLabels,
                            onCheckedChange = { onToggleTimeLabels() }
                        )
                        SwitchSetting(
                            title = "Weather cells",
                            subtitle = "Outline the game's weather cell over each scanned area",
                            checked = showWeatherCells,
                            onCheckedChange = { onToggleWeatherCells() }
                        )
                        SwitchSetting(
                            title = "Spawn radius",
                            subtitle = "Draw the 40m circle a spawn can sit anywhere inside",
                            checked = showSpawnRadius,
                            onCheckedChange = { onToggleSpawnRadius() }
                        )
                        SwitchSetting(
                            title = "Spacial Rend",
                            subtitle = "Widen that circle to 80m",
                            checked = spacialRendEnabled,
                            onCheckedChange = { onToggleSpacialRend() },
                            enabled = showSpawnRadius
                        )
                    }
                }
            }

            item("behaviour") {
                MapPanelSection(
                    title = "Behaviour",
                    summary = behaviourSummary(showDismissed, autoEnterPictureInPicture),
                    active = showDismissed || autoEnterPictureInPicture,
                    expanded = isOpen(SECTION_BEHAVIOUR),
                    onToggle = { toggle(SECTION_BEHAVIOUR) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Box(modifier = Modifier.testTag("map_show_dismissed")) {
                            SwitchSetting(
                                title = "Show dismissed alerts",
                                subtitle = "Keep alerts on the map after you dismiss them",
                                checked = showDismissed,
                                onCheckedChange = { onToggleDismissed() }
                            )
                        }
                        if (onToggleAutoEnterPictureInPicture != null) {
                            Box(modifier = Modifier.testTag("map_auto_pip")) {
                                SwitchSetting(
                                    title = stringResource(R.string.map_pip_auto_enter),
                                    subtitle = "Shrink the map into a floating window when you leave the app",
                                    checked = autoEnterPictureInPicture,
                                    onCheckedChange = { onToggleAutoEnterPictureInPicture() }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenFilterStudio) {
                Text(stringResource(R.string.map_filter_studio))
            }
            // The button says what closing the panel will leave on the map, so the count is
            // read before the panel is dismissed rather than after.
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

/** Title, live count, local weather and the two actions that are not settings. */
@Composable
private fun MapPanelHeader(
    visibleCount: Int,
    totalCount: Int,
    isDefault: Boolean,
    onReset: () -> Unit,
    userLocation: android.location.Location?,
    weatherCells: List<MapWeatherCell>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onEnterPictureInPicture: (() -> Unit)?,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    questRewardThumbnails: Map<String, String>,
    categoryCounts: Map<AlertCategory, Int>
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.map_panel_title),
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
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.map_filter_reset))
                }
            }
        }

        MapPanelWeatherLine(userLocation = userLocation, weatherCells = weatherCells)

        MapQuickActions(
            refreshing = refreshing,
            onRefresh = onRefresh,
            onEnterPictureInPicture = onEnterPictureInPicture,
            catalog = catalog,
            artwork = artwork,
            questRewardThumbnails = questRewardThumbnails,
            categoryCounts = categoryCounts
        )
    }
}

/**
 * One collapsible section.
 *
 * The summary is the point: collapsed, the panel has to read as a complete statement of what the
 * map is currently narrowed to.
 */
@Composable
internal fun MapPanelSection(
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
        label = "map_panel_section_chevron"
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

/** A section whose editor is a screen of its own rather than an expanding body. */
@Composable
private fun MapPanelLauncherRow(
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

/** Names the question the sections beneath it answer. */
@Composable
private fun MapGroupHeading(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xxs, top = Spacing.md, bottom = Spacing.xxs)
    )
}

/**
 * The two actions the header bar used to hold.
 *
 * Neither earns permanent space on the map: the feed already polls every 30s, so refresh is a
 * "now, please" rather than the only way to get data, and picture-in-picture is a once-a-session
 * gesture.
 */
@Composable
internal fun MapQuickActions(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onEnterPictureInPicture: (() -> Unit)?,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    questRewardThumbnails: Map<String, String>,
    categoryCounts: Map<AlertCategory, Int>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
    if (onEnterPictureInPicture != null) {
        HuntControls(
            catalog = catalog,
            artwork = artwork,
            questRewardThumbnails = questRewardThumbnails,
            categoryCounts = categoryCounts,
            onHuntStarted = onEnterPictureInPicture
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilledTonalButton(
            onClick = onRefresh,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            AnimatedRefreshIcon(
                refreshing = refreshing,
                contentDescription = stringResource(R.string.refresh_alerts)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text("Refresh", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onEnterPictureInPicture != null) {
            FilledTonalButton(
                onClick = onEnterPictureInPicture,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pip),
                    contentDescription = "Open map in picture-in-picture",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Floating map", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    }
}

/**
 * The weather where the user is standing.
 *
 * The map draws weather as a cell, which is located but can sit off-screen at street zoom.
 * This is the always-reachable copy, and the reason the floating corner badge could go.
 */
@Composable
private fun MapPanelWeatherLine(
    userLocation: android.location.Location?,
    weatherCells: List<MapWeatherCell>
) {
    // Matched by S2 cell rather than by the old centre-and-radius area lookup. That lookup
    // keys off a hardcoded name table which the backend has already outgrown - it still says
    // "Darmstadt" where the feed now reports "Darmstadt-North" and "Darmstadt-South" - so it
    // resolves to an area nothing has weather for. Containment in the cell that is drawn on
    // the map cannot drift, because it is derived from the same data.
    val here = remember(userLocation?.latitude, userLocation?.longitude, weatherCells) {
        val location = userLocation ?: return@remember null
        val cell = s2CellAt(location.latitude, location.longitude)
        weatherCells.firstOrNull { it.cell == cell }
    } ?: return

    Text(
        text = "${here.area} · ${here.display.compactLabel}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A provider choice, sized and shaped like the thing it turns on rather than a text chip. */
@Composable
private fun MapStyleTile(
    style: MapStylePreference,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) scheme.primary else scheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.md, horizontal = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_map),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = style.panelLabel(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (style == MapStylePreference.OPENSTREETMAP) "Offline capable" else "Google",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun MapStylePreference.panelLabel(): String = when (this) {
    MapStylePreference.GOOGLE_STANDARD -> "Standard"
    MapStylePreference.GOOGLE_SATELLITE -> "Satellite"
    MapStylePreference.OPENSTREETMAP -> "OpenStreetMap"
}

private fun overlaySummary(
    showTimeLabels: Boolean,
    showSpawnRadius: Boolean,
    spacialRendEnabled: Boolean,
    showWeatherCells: Boolean
): String {
    val on = buildList {
        if (showTimeLabels) add("Countdowns")
        if (showWeatherCells) add("Weather cells")
        if (showSpawnRadius) add(if (spacialRendEnabled) "Spawn radius 80m" else "Spawn radius")
    }
    return if (on.isEmpty()) "None" else on.joinToString(", ")
}

private fun behaviourSummary(showDismissed: Boolean, autoPip: Boolean): String {
    val on = buildList {
        if (showDismissed) add("Dismissed alerts shown")
        if (autoPip) add("Auto floating map")
    }
    return if (on.isEmpty()) "Defaults" else on.joinToString(", ")
}

/**
 * Alert types as tiles rather than chips, each carrying its marker colour and how many of that
 * kind are live. The colours make this the legend as well, which is why the separate one is gone.
 */
@Composable
internal fun AlertTypesSection(
    definition: FilterDefinition,
    categoryCounts: Map<AlertCategory, Int>,
    onDefinitionChange: (FilterDefinition) -> Unit,
    // The map panel's copy points at the rail above the map. Reused elsewhere -- the
    // hunt picker -- there is no rail, and saying so would be a lie.
    setAllLabel: String = "The rail above the map edits this too"
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
                        val values = definition.alertTypes.normalizedValues.toMutableSet()
                        if (definition.alertTypes.mode == FilterSelectionMode.ALL) {
                            values += allTokens
                        }
                        val key = normalizeFilterToken(type.name)
                        if (selected) values -= key else values += key
                        onDefinitionChange(definition.copy(alertTypes = FilterSelection.only(values)))
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
        color = if (selected) accent.copy(alpha = 0.16f) else scheme.surface,
        contentColor = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accent else scheme.outlineVariant
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
internal fun TokenSelectionSection(
    tokens: List<String>,
    selection: FilterSelection,
    onSelectionChange: (FilterSelection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MapSetAllRow(
            label = if (tokens.isEmpty()) "Loads with the next refresh" else "${tokens.size} available",
            onAll = { onSelectionChange(FilterSelection.All) },
            onNone = { onSelectionChange(FilterSelection.None) }
        )
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

/**
 * The species lists, in a sheet of their own.
 *
 * A thousand-entry grid does not belong inside another scroller: capped to 320dp inside the
 * panel it was both too small to browse and a trap for every drag that started over it. Given
 * the whole screen it can be searched, sorted and set in bulk, which is what Wingull does with
 * the same problem.
 */
@Composable
internal fun SpeciesPickerSheet(
    initialTarget: MapSelectorTarget,
    definition: FilterDefinition,
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    var target by rememberSaveable { mutableStateOf(initialTarget) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(SpeciesSortOrder.DEX_NUMBER) }

    fun extractDex(key: String): Int {
        val url = artwork[key] ?: return Int.MAX_VALUE
        val match = Regex("""/(\d+)\.png""").find(url) ?: return Int.MAX_VALUE
        return match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
    }

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
    val queryKey = remember(searchQuery) { normalizeFilterToken(searchQuery) }
    val displayList = remember(normalizedCandidates, currentSelection.normalizedValues, queryKey, sortOrder, artwork) {
        normalizedCandidates
            .filter { (key, _) -> key.contains(queryKey) || extractDex(key).toString().contains(queryKey) }
            .sortedWith(
                compareByDescending<Pair<String, String>> { (key, _) -> key in currentSelection.normalizedValues }
                    .thenComparing { (key, _) ->
                        if (sortOrder == SpeciesSortOrder.DEX_NUMBER) extractDex(key) else Int.MAX_VALUE
                    }
                    .thenBy { (_, value) -> value.lowercase() }
            )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("species_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = target.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Which list is being edited. Each target keeps its own selection.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                SPECIES_TARGETS.forEach { candidate ->
                    val selection = definition.selectionFor(candidate)
                    val count = selection.selectedCount
                    FilterChip(
                        selected = candidate == target,
                        onClick = { target = candidate },
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
                placeholder = { Text("Search ${target.shortLabel} (name or Dex #)…") },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "${displayList.size} shown · ${currentSelection.summaryLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
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
            }

            MapSetAllRow(
                label = "Set all",
                onAll = { onDefinitionChange(definition.withSelection(target, FilterSelection.All)) },
                onNone = { onDefinitionChange(definition.withSelection(target, FilterSelection.None)) }
            )

            // The grid is the sheet's only scroller, and it gets the rest of the height.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag("species_picker_grid"),
                contentPadding = PaddingValues(bottom = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                gridItems(displayList, key = { it.first }) { (key, value) ->
                    val checked = currentSelection.mode == FilterSelectionMode.ALL ||
                        (currentSelection.mode == FilterSelectionMode.ONLY &&
                            key in currentSelection.normalizedValues)
                    SpeciesTile(
                        name = value,
                        artworkUrl = artwork[key],
                        dexNumber = extractDex(key),
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
    }
}

@Composable
private fun SpeciesTile(
    name: String,
    artworkUrl: String?,
    dexNumber: Int,
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
                    model = artworkUrl,
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
            if (dexNumber != Int.MAX_VALUE) {
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

private fun FilterSelection.summaryLabel(): String = when (mode) {
    FilterSelectionMode.ALL -> "all selected"
    FilterSelectionMode.NONE -> "none selected"
    FilterSelectionMode.ONLY -> "$selectedCount selected"
}

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

internal fun FilterDefinition.selectionFor(target: MapSelectorTarget): FilterSelection = when (target) {
    MapSelectorTarget.SPAWN -> spawnSpecies
    MapSelectorTarget.RARE -> rareSpecies
    MapSelectorTarget.HUNDO -> hundoSpecies
    MapSelectorTarget.NUNDO -> nundoSpecies
    MapSelectorTarget.PVP -> pvpSpecies
    MapSelectorTarget.RAID_SPECIES -> raidSpecies
    MapSelectorTarget.RAID_TIERS -> raidTiers
    MapSelectorTarget.ROCKET -> rocketTypes
}

internal fun FilterDefinition.withSelection(target: MapSelectorTarget, selection: FilterSelection): FilterDefinition = when (target) {
    MapSelectorTarget.SPAWN -> copy(spawnSpecies = selection)
    MapSelectorTarget.RARE -> copy(rareSpecies = selection)
    MapSelectorTarget.HUNDO -> copy(hundoSpecies = selection)
    MapSelectorTarget.NUNDO -> copy(nundoSpecies = selection)
    MapSelectorTarget.PVP -> copy(pvpSpecies = selection)
    MapSelectorTarget.RAID_SPECIES -> copy(raidSpecies = selection)
    MapSelectorTarget.RAID_TIERS -> copy(raidTiers = selection)
    MapSelectorTarget.ROCKET -> copy(rocketTypes = selection)
}

internal fun MapSelectorTarget.candidates(catalog: FilterCatalog): List<String> = when (this) {
    MapSelectorTarget.RAID_SPECIES -> catalog.raidSpecies
    MapSelectorTarget.RAID_TIERS -> catalog.raidTiers
    MapSelectorTarget.ROCKET -> catalog.rocketTypes
    else -> catalog.spawnSpecies
}

private fun FilterAlertType.mapCategory(): AlertCategory = when (this) {
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
}

@Composable
private fun FilterAlertType.mapAccent(): Color = mapCategory().accentColor()
