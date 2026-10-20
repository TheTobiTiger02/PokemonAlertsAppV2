@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.ui.alerts.mapAccent
import com.example.pokemonalertsv2.ui.alerts.mappanel.DistanceEditor
import com.example.pokemonalertsv2.ui.alerts.mappanel.FiltersState
import com.example.pokemonalertsv2.ui.alerts.mappanel.FiltersTabs
import com.example.pokemonalertsv2.ui.alerts.mappanel.TypeEditor
import com.example.pokemonalertsv2.ui.alerts.mappanel.TypeEditorPage
import com.example.pokemonalertsv2.ui.alerts.mappanel.editor
import com.example.pokemonalertsv2.ui.alerts.mappanel.isNarrowed
import com.example.pokemonalertsv2.ui.alerts.mappanel.ruleSummary
import com.example.pokemonalertsv2.ui.alerts.mappanel.showsType
import com.example.pokemonalertsv2.ui.alerts.mappanel.withType
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelPages
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * What every Filter Studio body edits and every way it can act. Changes go to the draft; the
 * host's Apply bar writes them out together.
 */
internal class StudioBodyState(
    val draft: FilterDefinition,
    val onDraftChange: (FilterDefinition) -> Unit,
    val catalog: FilterCatalog,
    val artwork: Map<String, String>,
    val rewardThumbnails: Map<String, String>,
    val matchCount: Int,
    val totalCount: Int,
    val areas: List<String>,
    val profileSummary: String,
    val openProfiles: () -> Unit,
    val saveProfile: () -> Unit,
    val openQuestRules: () -> Unit,
    val openDistanceOverrides: () -> Unit
) {
    fun filters(nearbyExtras: (@Composable () -> Unit)? = null) = FiltersState(
        definition = draft,
        catalog = catalog,
        artwork = artwork,
        rewardThumbnails = rewardThumbnails,
        categoryCounts = emptyMap(),
        visibleCount = matchCount,
        totalCount = totalCount,
        onDefinitionChange = onDraftChange,
        openQuests = openQuestRules,
        openDistanceOverrides = openDistanceOverrides,
        nearbyExtras = nearbyExtras
    )
}

/** Profile to load or link, and saving the draft as one; the same two rows in every body. */
@Composable
private fun ProfileRows(state: StudioBodyState) {
    PanelNavRow(
        title = "Profile",
        summary = state.profileSummary,
        onClick = state.openProfiles,
        modifier = Modifier.testTag("studio_profiles"),
        icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp)) }
    )
    PanelNavRow(
        title = "Save as profile",
        summary = "Keep these rules to reuse or link",
        onClick = state.saveProfile,
        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) }
    )
}

// --- A · Tabs ------------------------------------------------------------------------------

/** The map's Filters tabs, editing the draft; areas and profiles join the Nearby tab. */
@Composable
internal fun StudioTabsBody(state: StudioBodyState) {
    val filters = state.filters(nearbyExtras = {
        PanelSectionLabel("Areas")
        AreaChipsEditor(state.draft, state.areas, state.onDraftChange, Modifier.panelGutter())
        PanelSectionLabel("Profile")
        ProfileRows(state)
    })
    Box(Modifier.fillMaxSize().padding(top = Spacing.sm).testTag("studio_tabs")) {
        FiltersTabs(filters, header = {})
    }
}

// --- Pages shared by B and C ---------------------------------------------------------------

private enum class StudioPage { ROOT, AREAS, DISTANCE, SPAWN, HUNDO, PVP, NUNDO, COMMON, RAIDS, ROCKET, QUESTS }

private fun TypeEditor.page(): StudioPage = StudioPage.valueOf(name)
private fun StudioPage.editor(): TypeEditor? = TypeEditor.entries.firstOrNull { it.name == name }

@Composable
private fun StudioPages(state: StudioBodyState, root: @Composable (open: (StudioPage) -> Unit) -> Unit) {
    var page by rememberSaveable { mutableStateOf(StudioPage.ROOT) }
    val back = { page = StudioPage.ROOT }
    PanelPages(page = page, root = StudioPage.ROOT, onBack = back) { shown ->
        when (shown) {
            StudioPage.ROOT -> root { page = it }
            StudioPage.AREAS -> Column(Modifier.fillMaxSize().testTag("studio_page_areas")) {
                PanelHeader(title = "Areas", subtitle = state.draft.areaSummary(), onBack = back)
                AreaChipsEditor(state.draft, state.areas, state.onDraftChange, Modifier.panelGutter().padding(top = Spacing.sm))
            }
            StudioPage.DISTANCE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("studio_page_distance")) {
                PanelHeader(title = "Distance", subtitle = state.draft.distanceSummary(), onBack = back)
                DistanceEditor(state.filters())
            }
            else -> TypeEditorPage(shown.editor()!!, state.filters(), onBack = back)
        }
    }
}

/** The type's colour as a dot, the one place DESIGN.md lets it show. */
@Composable
private fun TypeDot(type: FilterAlertType, on: Boolean) {
    Box(Modifier.size(12.dp).background(type.mapAccent().copy(alpha = if (on) 1f else 0.35f), CircleShape))
}

// --- B · Overview --------------------------------------------------------------------------

/**
 * A flat list that reads as a statement of the filter: where, then one row per alert type with
 * what it shows. Types with rules open their editor as a page; the rest are switches.
 */
@Composable
internal fun StudioOverviewBody(state: StudioBodyState) {
    StudioPages(state) { open ->
        LazyColumn(Modifier.fillMaxSize().testTag("studio_overview"), contentPadding = PaddingValues(bottom = Spacing.lg)) {
            item { ProfileRows(state) }
            item { PanelSectionLabel("Where") }
            item {
                PanelNavRow(
                    title = "Areas",
                    summary = state.draft.areaSummary(),
                    active = state.draft.areas.mode != FilterSelectionMode.ALL,
                    onClick = { open(StudioPage.AREAS) },
                    modifier = Modifier.testTag("studio_row_areas"),
                    icon = { PanelIcon(R.drawable.ic_map) }
                )
            }
            item {
                PanelNavRow(
                    title = "Distance",
                    summary = state.draft.distanceSummary(),
                    active = state.draft.maxDistanceMeters > 0 || state.draft.maxWalkingMinutes > 0 || state.draft.distanceOverrides.ruleCount > 0,
                    onClick = { open(StudioPage.DISTANCE) },
                    modifier = Modifier.testTag("studio_row_distance"),
                    icon = { PanelIcon(R.drawable.ic_radius) }
                )
            }
            item { PanelSectionLabel("What") }
            items(FilterAlertType.entries, key = { it.name }) { type ->
                val on = state.draft.showsType(type)
                val editor = type.editor()
                if (editor != null) {
                    PanelNavRow(
                        title = type.label,
                        summary = type.ruleSummary(state.draft),
                        active = type.isNarrowed(state.draft),
                        onClick = { open(editor.page()) },
                        modifier = Modifier.alpha(if (on) 1f else 0.7f).testTag("studio_row_${type.name.lowercase()}"),
                        icon = { TypeDot(type, on) }
                    )
                } else {
                    PanelSwitchRow(
                        title = type.label,
                        subtitle = if (on) "Shown" else "Off",
                        checked = on,
                        onCheckedChange = { state.onDraftChange(state.draft.withType(type, it)) },
                        modifier = Modifier.testTag("studio_switch_${type.name.lowercase()}"),
                        icon = { TypeDot(type, on) }
                    )
                }
            }
        }
    }
}

// --- C · Tiles -----------------------------------------------------------------------------

/**
 * Every alert type as a tile to switch on and off, under three chips for profile, area and
 * distance. Holding a tile, or its tune button, opens that type's rules.
 */
@Composable
internal fun StudioTilesBody(state: StudioBodyState) {
    StudioPages(state) { open ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().testTag("studio_tiles"),
            contentPadding = PaddingValues(start = PanelGutter, end = PanelGutter, top = Spacing.sm, bottom = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SummaryChip("Profile", state.profileSummary, false, state.openProfiles, "studio_chip_profile")
                    SummaryChip("Areas", state.draft.areaSummary(), state.draft.areas.mode != FilterSelectionMode.ALL, { open(StudioPage.AREAS) }, "studio_chip_areas")
                    SummaryChip(
                        "Distance", state.draft.distanceSummary(),
                        state.draft.maxDistanceMeters > 0 || state.draft.maxWalkingMinutes > 0,
                        { open(StudioPage.DISTANCE) }, "studio_chip_distance"
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(
                        "Alert types · hold one for its rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { state.onDraftChange(state.draft.copy(alertTypes = FilterSelection.All)) }) { Text("All") }
                    TextButton(onClick = { state.onDraftChange(state.draft.copy(alertTypes = FilterSelection.None)) }) { Text("None") }
                }
            }
            items(FilterAlertType.entries, key = { it.name }) { type ->
                TypeTile(
                    type = type,
                    definition = state.draft,
                    onToggle = { state.onDraftChange(state.draft.withType(type, !state.draft.showsType(type))) },
                    onOpen = type.editor()?.let { editor -> { open(editor.page()) } }
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, active: Boolean, onClick: () -> Unit, tag: String) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text("$label · $value", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun TypeTile(type: FilterAlertType, definition: FilterDefinition, onToggle: () -> Unit, onOpen: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val on = definition.showsType(type)
    Surface(
        modifier = Modifier.heightIn(min = 88.dp).testTag("studio_tile_${type.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        color = if (on) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surface,
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant)
    ) {
        Box(
            Modifier
                .combinedClickable(
                    role = Role.Switch,
                    onClickLabel = if (on) "Hide ${type.label}" else "Show ${type.label}",
                    onLongClickLabel = onOpen?.let { "Edit rules" },
                    onLongClick = onOpen,
                    onClick = onToggle
                )
                .semantics { contentDescription = "${type.label}, ${if (on) "shown" else "off"}" }
                .padding(Spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs), modifier = Modifier.padding(end = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeDot(type, on)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(type.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = type.ruleSummary(definition),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (type.isNarrowed(definition)) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (on) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                )
            }
            if (onOpen != null) {
                IconButton(
                    onClick = onOpen,
                    modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).testTag("studio_tune_${type.name.lowercase()}")
                ) {
                    Icon(
                        painterResource(R.drawable.ic_tune),
                        contentDescription = "Edit ${type.label} rules",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
