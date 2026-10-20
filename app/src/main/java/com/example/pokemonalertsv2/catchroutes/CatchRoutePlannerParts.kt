package com.example.pokemonalertsv2.catchroutes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.hunt.BatterySaverSwitch
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelPages
import com.example.pokemonalertsv2.ui.components.PanelRowIcon
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The parts the planner's newer designs (Rows, Map + sheet, Steps) share: one page per setting,
// the summary rows that open them, the Generate bar, and the slimmer route and walking cards.

/** The planner's editor pages. Rows and Map + sheet reach them from rows; Steps from its steps. */
internal enum class PlanPage(val title: String) {
    ROOT("Plan a route"),
    START("Start"),
    FINISH("Finish"),
    AREA("Area"),
    WHEN("When"),
    SUGGEST("Let the app choose"),
    INCLUDES("Includes"),
    ADVANCED("Advanced"),
    SAVED("Saved routes")
}

/** What the planner shows: the route being planned or walked, and the clock that ages it. */
internal class PlannerState(
    val settings: CatchRouteSettings,
    val plan: CatchItinerary?,
    val session: CatchSession?,
    val stops: List<List<CatchEncounter>>,
    val encounters: List<CatchEncounter>,
    val saved: List<CatchSetupEntity>,
    val refreshing: Boolean,
    val outOfDate: Boolean,
    val notice: String?,
    val now: Long,
)

/** What the planner asks the activity for: permissions, dialogs, other apps and the map. */
internal class PlannerActions(
    val useLocation: () -> Unit,
    val pickTime: () -> Unit,
    /** Map taps set the finish pin when true, the start otherwise. */
    val pickOnMap: (finish: Boolean) -> Unit,
    val openArea: () -> Unit,
    val openGoRoutes: () -> Unit,
    val startGuidance: () -> Unit,
    val setEditing: (Boolean) -> Unit,
    val floatingWindow: () -> Unit,
    val openInMaps: (CatchItinerary) -> Unit,
    val stop: () -> Unit,
    val showStop: (List<CatchEncounter>) -> Unit,
    val deleteSetup: (CatchSetupEntity) -> Unit,
)

internal fun catchClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

internal fun startSummary(model: CatchRoutesViewModel, s: CatchRouteSettings): String = when {
    s.fixed -> "${s.sourceRouteName ?: "GO route"}, as drawn"
    !model.hasStart -> "Not set"
    s.sourceRouteId != null -> "${model.startLabel} · near ${s.sourceRouteName ?: "a GO route"}"
    else -> model.startLabel
}

internal fun finishSummary(s: CatchRouteSettings): String = when {
    s.fixed -> "End of the GO route"
    s.finish == CatchFinish.ROUND_TRIP -> "Back to start"
    s.finish == CatchFinish.ANYWHERE -> "End anywhere"
    s.end != null -> "Pinned on the map"
    else -> "Tap the map to pin it"
}

internal fun areaSummary(s: CatchRouteSettings): String = if (s.area.isArea()) "Inside ${s.area.size} corners" else "None"

internal fun whenSummary(model: CatchRoutesViewModel, s: CatchRouteSettings): String =
    "${if (model.useNow) "Now" else catchClock(s.startAtMillis)}, ${s.durationMinutes} min"

internal fun includesSummary(s: CatchRouteSettings): String =
    listOfNotNull(if (s.includeEventSpawns) "Events on" else "Events off", "Spacial Rend".takeIf { s.spacialRend }).joinToString(" · ")

internal fun advancedSummary(s: CatchRouteSettings): String = listOfNotNull(
    String.format(Locale.getDefault(), "%.1f km/h", s.speedMps * 3.6),
    "waits up to ${s.maxWaitMinutes} min".takeIf { s.maxWaitMinutes > 0 },
    predictionLabel(s.prediction).takeIf { s.prediction != CatchPrediction.AUTOMATIC }
).joinToString(" · ")

private fun predictionLabel(p: CatchPrediction) = when (p) {
    CatchPrediction.AUTOMATIC -> "Automatic (learned timing)"
    CatchPrediction.THIRTY_MINUTES -> "Assume 30-minute spawns"
    CatchPrediction.SIXTY_MINUTES -> "Assume 60-minute spawns"
    CatchPrediction.SUPPORTED_ONLY -> "Supported windows only"
}

/** The settings as rows, each saying what is set and opening its page. */
@Composable
internal fun PlanRows(model: CatchRoutesViewModel, s: CatchRouteSettings, savedCount: Int, onOpen: (PlanPage) -> Unit) {
    PanelSectionLabel("Where")
    PanelNavRow("Start", startSummary(model, s), { onOpen(PlanPage.START) }, Modifier.testTag("plan_row_start"),
        active = s.sourceRouteId != null) { PanelIcon(R.drawable.ic_my_location) }
    if (!s.fixed) {
        PanelNavRow("Finish", finishSummary(s), { onOpen(PlanPage.FINISH) }, Modifier.testTag("plan_row_finish")) { PanelIcon(R.drawable.ic_navigate) }
        PanelNavRow("Area", areaSummary(s), { onOpen(PlanPage.AREA) }, Modifier.testTag("plan_row_area"),
            active = s.area.isArea()) { PanelIcon(R.drawable.ic_map) }
    }
    PanelSectionLabel("When")
    PanelNavRow("When", whenSummary(model, s), { onOpen(PlanPage.WHEN) }, Modifier.testTag("plan_row_when")) { PanelIcon(R.drawable.ic_timer) }
    PanelNavRow(PlanPage.SUGGEST.title, if (s.fixed) "Best start time" else "Best start spot or time", { onOpen(PlanPage.SUGGEST) },
        Modifier.testTag("plan_row_suggest")) { PanelIcon(R.drawable.ic_insights) }
    PanelSectionLabel("What")
    PanelNavRow("Includes", includesSummary(s), { onOpen(PlanPage.INCLUDES) }, Modifier.testTag("plan_row_includes"),
        active = s.includeEventSpawns || s.spacialRend) { PanelIcon(R.drawable.ic_category) }
    PanelNavRow("Advanced", advancedSummary(s), { onOpen(PlanPage.ADVANCED) }, Modifier.testTag("plan_row_advanced")) { PanelIcon(R.drawable.ic_tune) }
    PanelNavRow(PlanPage.SAVED.title, if (savedCount == 0) "Save this setup to reuse it" else "$savedCount saved", { onOpen(PlanPage.SAVED) },
        Modifier.testTag("plan_row_saved")) { PanelIcon(R.drawable.ic_star_border) }
}

/** One page's controls, without its header; the caller scrolls it. [onDone] returns to the list. */
@Composable
internal fun PlanEditor(page: PlanPage, model: CatchRoutesViewModel, state: PlannerState, actions: PlannerActions, onDone: () -> Unit) {
    val s = state.settings
    when (page) {
        PlanPage.ROOT -> Unit
        PlanPage.START -> StartEditor(model, s, actions)
        PlanPage.FINISH -> FinishEditor(model, s, actions)
        PlanPage.AREA -> AreaEditor(model, s, actions)
        PlanPage.WHEN -> WhenEditor(model, s, actions)
        PlanPage.SUGGEST -> SuggestEditor(model, s, onDone)
        PlanPage.INCLUDES -> IncludesEditor(model, s)
        PlanPage.ADVANCED -> AdvancedEditor(model, s)
        PlanPage.SAVED -> SavedEditor(model, state.saved, actions, onDone)
    }
}

/** A choice among several, as a whole-row radio button. */
@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, tag: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = PanelGutter, vertical = Spacing.xs)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A command row: like a nav row, but it acts instead of opening a page. */
@Composable
private fun ActionRow(title: String, subtitle: String, iconId: Int, onClick: () -> Unit, tag: String, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = PanelGutter, vertical = Spacing.sm)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelRowIcon { PanelIcon(iconId) }
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PageNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PanelGutter, vertical = Spacing.xs))
}

@Composable
internal fun StartEditor(model: CatchRoutesViewModel, s: CatchRouteSettings, actions: PlannerActions) {
    if (!s.fixed) {
        PanelSectionLabel("Start from")
        ChoiceRow("Your location", model.hasStart && model.startLabel == "Your location", actions.useLocation, "start_location")
        ChoiceRow("Pick on the map", model.hasStart && model.startLabel == "Pinned on the map", { actions.pickOnMap(false) }, "start_pin",
            subtitle = "Tap the map to place it")
        // A start from a suggestion, a saved setup or the history shows as what it is.
        if (model.hasStart && model.startLabel != "Your location" && model.startLabel != "Pinned on the map")
            ChoiceRow(model.startLabel, true, {}, "start_other")
    }
    PanelSectionLabel("Pokémon GO route")
    PanelNavRow("GO route", when {
        s.fixed -> "Walking ${s.sourceRouteName ?: "an imported route"} as drawn"
        s.sourceRouteId != null -> "Planning near ${s.sourceRouteName ?: "an imported route"}"
        else -> "Walk one as drawn, or plan near it"
    }, actions.openGoRoutes, Modifier.testTag("open_go_routes"), active = s.sourceRouteId != null) { PanelIcon(R.drawable.ic_route) }
    if (s.sourceRouteId != null) TextButton(onClick = model::removeImport, modifier = Modifier.padding(start = Spacing.sm).testTag("remove_go_route")) {
        Text("Remove GO route")
    }
}

@Composable
internal fun FinishEditor(model: CatchRoutesViewModel, s: CatchRouteSettings, actions: PlannerActions) {
    if (s.fixed) { PageNote("A GO route walked as drawn ends where it ends."); return }
    CatchFinish.entries.forEach { finish ->
        ChoiceRow(
            title = when (finish) { CatchFinish.ROUND_TRIP -> "Back to start"; CatchFinish.ANYWHERE -> "End anywhere"; CatchFinish.PIN -> "End at a pin" },
            selected = s.finish == finish,
            onClick = { model.edit(s.copy(finish = finish)); actions.pickOnMap(finish == CatchFinish.PIN) },
            tag = "finish_${finish.name}",
            subtitle = if (finish != CatchFinish.PIN) null else if (s.end != null) "Pinned. Tap the map to move it" else "Tap the map to pin it"
        )
    }
}

@Composable
internal fun AreaEditor(model: CatchRoutesViewModel, s: CatchRouteSettings, actions: PlannerActions) {
    if (s.fixed) { PageNote("A GO route walked as drawn has no area."); return }
    ChoiceRow("No limit", !s.area.isArea(), { model.edit(s.copy(area = emptyList())) }, "area_none")
    PanelNavRow(if (s.area.isArea()) "Edit area" else "Draw an area",
        if (s.area.isArea()) "Inside ${s.area.size} corners" else "Keep the route inside a shape",
        actions.openArea, Modifier.testTag("area_draw"), active = s.area.isArea()) { PanelIcon(R.drawable.ic_map) }
    Box(Modifier.panelGutter()) { SavedAreaChips(s.area) { model.edit(s.copy(area = it)) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WhenEditor(model: CatchRoutesViewModel, s: CatchRouteSettings, actions: PlannerActions) {
    PanelSectionLabel("Leave")
    ChoiceRow("Now", model.useNow, { model.schedule(null) }, "when_now")
    ChoiceRow("Later", !model.useNow, actions.pickTime, "when_later",
        subtitle = if (model.useNow) "Pick a day and time" else "At ${catchClock(s.startAtMillis)}. Tap to change")
    PanelSectionLabel("Walk for")
    FlowRow(Modifier.panelGutter(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.Center) {
        listOf(30, 60, 90, 120).forEach { minutes ->
            FilterChip(selected = s.durationMinutes == minutes, onClick = { model.edit(s.copy(durationMinutes = minutes)) },
                label = { Text("$minutes min") }, modifier = Modifier.testTag("duration_$minutes"))
        }
        OutlinedTextField(value = s.durationMinutes.toString(), onValueChange = { model.edit(s.copy(durationMinutes = it.toIntOrNull() ?: 0)) },
            label = { Text("Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.width(110.dp).testTag("duration_field"))
    }
}

@Composable
internal fun SuggestEditor(model: CatchRoutesViewModel, s: CatchRouteSettings, onDone: () -> Unit) {
    PageNote("Compares nearby options with your other settings and plans the best one.")
    if (!s.fixed) ActionRow("Best start spot", "Most expected catches for this departure", R.drawable.ic_my_location,
        { model.recommendStart(); onDone() }, "recommend_start", enabled = !model.busy)
    ActionRow("Best start time", if (model.hasStart) "Best departure in the next six hours" else "Set a start first",
        R.drawable.ic_timer, { model.recommendTime(); onDone() }, "recommend_time", enabled = !model.busy && model.hasStart)
}

@Composable
internal fun IncludesEditor(model: CatchRoutesViewModel, s: CatchRouteSettings) {
    PanelSwitchRow("Event spawns", s.includeEventSpawns, { model.edit(s.copy(includeEventSpawns = it)) },
        Modifier.testTag("include_events"), subtitle = "Spawnpoints that only spawn during Spotlight Hours, Community Days and similar events") {
        PanelIcon(R.drawable.ic_category)
    }
    if (s.includeEventSpawns) {
        PanelSectionLabel("Which events")
        EVENT_SPAWN_TYPES.chunked(2).forEach { pair ->
            Row(Modifier.panelGutter().padding(vertical = Spacing.xxs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { type ->
                    val selected = type in s.eventSpawnTypes
                    // The last type stays on: with none chosen the switch above would do nothing.
                    EventTile(EVENT_SPAWN_TYPE_LABELS[type] ?: type, selected, Modifier.weight(1f).testTag("event_type_$type")) {
                        val next = if (selected) s.eventSpawnTypes - type else s.eventSpawnTypes + type
                        if (next.isNotEmpty()) model.edit(s.copy(eventSpawnTypes = next))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        PageNote("Planned like any other spawnpoint, even when no event is running.")
    }
    PanelSwitchRow("Spacial Rend", s.spacialRend, { model.edit(s.copy(spacialRend = it)) }, Modifier.testTag("include_spacial_rend"),
        subtitle = "Catch range ${s.radius.toInt()} m") { PanelIcon(R.drawable.ic_radius) }
}

@Composable
private fun EventTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 56.dp).toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdvancedEditor(model: CatchRoutesViewModel, s: CatchRouteSettings) {
    PanelSectionLabel("Walking pace · ${String.format(Locale.getDefault(), "%.1f", s.speedMps * 3.6)} km/h")
    Slider(value = s.speedMps.toFloat(), onValueChange = { model.edit(s.copy(speedMps = it.toDouble())) }, valueRange = 0.5f..2.5f,
        modifier = Modifier.panelGutter().testTag("advanced_pace"))
    PanelSectionLabel("Spawn timing")
    CatchPrediction.entries.forEach { prediction ->
        ChoiceRow(predictionLabel(prediction), s.prediction == prediction, { model.edit(s.copy(prediction = prediction)) }, "prediction_${prediction.name}")
    }
    PanelSectionLabel("Wait at a spawnpoint for it to appear")
    FlowRow(Modifier.panelGutter(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(0, 2, 5, 10).forEach { minutes ->
            FilterChip(selected = s.maxWaitMinutes == minutes, onClick = { model.edit(s.copy(maxWaitMinutes = minutes)) },
                label = { Text(if (minutes == 0) "Never" else "Up to $minutes min") }, modifier = Modifier.testTag("wait_$minutes"))
        }
    }
    PanelSectionLabel("Name")
    OutlinedTextField(value = s.name, onValueChange = { model.edit(s.copy(name = it.take(80))) }, label = { Text("Route name") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().panelGutter().testTag("route_name"))
}

@Composable
internal fun SavedEditor(model: CatchRoutesViewModel, saved: List<CatchSetupEntity>, actions: PlannerActions, onDone: () -> Unit) {
    ActionRow("Save this setup", if (model.hasStart) "Overwrites the setup you loaded, if any" else "Set a start first",
        R.drawable.ic_star_border, { model.save() }, "save_setup", enabled = model.hasStart)
    ActionRow("Save as a copy", "Keeps the loaded setup as it was", R.drawable.ic_content_copy, { model.save(true) }, "save_setup_copy",
        enabled = model.hasStart)
    PanelSectionLabel(if (saved.isEmpty()) "No saved setups yet" else "Saved setups")
    saved.forEach { setup ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { model.load(setup); onDone() }.padding(start = PanelGutter, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Text(setup.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { actions.deleteSetup(setup) }) { Icon(Icons.Filled.Close, contentDescription = "Delete ${setup.name}") }
        }
    }
}

/** What went wrong, the retry countdown and generation progress, above the Generate button. */
@Composable
internal fun GenerateStatus(model: CatchRoutesViewModel, now: Long) {
    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.panelGutter().padding(top = Spacing.xs).testTag("catch_route_error")) }
    if (model.retryAt > now) Text("Retry in ${(model.retryAt - now) / 1000 + 1}s", modifier = Modifier.panelGutter())
    if (model.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth().panelGutter().padding(top = Spacing.sm))
        Text(model.progress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.panelGutter().padding(top = Spacing.xxs))
    } else if (model.progress.isNotBlank()) {
        Text(model.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.panelGutter().padding(top = Spacing.xxs))
    }
}

/** Generate, or Cancel while a route is being worked out. */
@Composable
internal fun GenerateButton(model: CatchRoutesViewModel, now: Long, modifier: Modifier = Modifier) {
    if (model.busy) OutlinedButton(onClick = model::cancel, modifier = modifier.height(52.dp).testTag("cancel_catch_route")) { Text("Cancel", maxLines = 1) }
    else Button(onClick = model::generate, enabled = model.retryAt <= now, shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(52.dp).testTag("generate_catch_route")) { Text("Generate route", maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** The fixed bottom bar of the Rows design: status and one full-width Generate. */
@Composable
internal fun GenerateBar(model: CatchRoutesViewModel, now: Long, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.padding(bottom = Spacing.sm)) {
            GenerateStatus(model, now)
            GenerateButton(model, now, Modifier.fillMaxWidth().panelGutter().padding(top = Spacing.sm))
        }
    }
}

/**
 * The planner's list and its pages for the Rows and Map + sheet designs. [rootTop] sits above the
 * rows (Map + sheet puts its peek there); [scrollModifier] goes on every page's scroller.
 */
@Composable
internal fun PlanPanel(
    model: CatchRoutesViewModel,
    state: PlannerState,
    actions: PlannerActions,
    page: PlanPage,
    onPage: (PlanPage) -> Unit,
    modifier: Modifier = Modifier,
    scrollModifier: Modifier = Modifier,
    rootTop: @Composable ColumnScope.() -> Unit = {},
) {
    PanelPages(page = page, root = PlanPage.ROOT, onBack = { onPage(PlanPage.ROOT) }) { shown ->
        Column(modifier.fillMaxWidth()) {
            if (shown != PlanPage.ROOT) PanelHeader(shown.title, onBack = { onPage(PlanPage.ROOT) })
            Column(scrollModifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg).testTag("plan_page_${shown.name.lowercase()}")) {
                if (shown == PlanPage.ROOT) {
                    rootTop()
                    if (state.plan != null) TextButton(onClick = { actions.setEditing(false) }, modifier = Modifier.padding(start = Spacing.sm)) {
                        Text("‹ Back to route")
                    }
                    PlanRows(model, state.settings, state.saved.size, onPage)
                } else PlanEditor(shown, model, state, actions) { onPage(PlanPage.ROOT) }
            }
        }
    }
}

/** The route pages after generating: the route or the walk, its stops, and the walk's options. */
private enum class ResultPage { ROOT, OPTIONS }

/**
 * A planned route or a walk in progress, for every newer design. [firstItem] goes on the card at
 * the top, so Map + sheet can peek exactly that much.
 */
@Composable
internal fun ResultPanel(
    model: CatchRoutesViewModel,
    state: PlannerState,
    actions: PlannerActions,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    firstItem: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(ResultPage.ROOT) }
    val session = state.session
    if (session == null && page != ResultPage.ROOT) page = ResultPage.ROOT
    PanelPages(page = page, root = ResultPage.ROOT, onBack = { page = ResultPage.ROOT }) { shown ->
        if (shown == ResultPage.OPTIONS && session != null) {
            Column(modifier.fillMaxWidth()) {
                PanelHeader("Route options", onBack = { page = ResultPage.ROOT })
                Column(listModifier.verticalScroll(rememberScrollState())) {
                    PanelSwitchRow("Spacial Rend", state.settings.spacialRend, { model.controller.setRadius(it) },
                        Modifier.testTag("route_option_spacial_rend"), subtitle = "Catch range ${state.settings.radius.toInt()} m") {
                        PanelIcon(R.drawable.ic_radius)
                    }
                    BatterySaverSwitch(description = "Route Battery Saver",
                        explanation = "Face down during a route blacks out the screen. Long press it to turn off. Shared with hunts.",
                        modifier = Modifier.panelGutter().padding(vertical = Spacing.md))
                }
            }
        } else {
            val plan = state.plan ?: return@PanelPages
            LazyColumn(listModifier.then(modifier).fillMaxWidth().testTag("catch_route_result_list"),
                contentPadding = PaddingValues(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item {
                    Box(firstItem.panelGutter().padding(top = Spacing.sm)) {
                        if (session != null) WalkingCard(model, session, state, actions) { page = ResultPage.OPTIONS }
                        else PlannedCard(model, plan, state.encounters, actions)
                    }
                }
                item { PanelSectionLabel("Stops") }
                val nextIndex = session?.let { catchNextStopIndex(it, state.now) }
                itemsIndexed(state.stops) { index, group ->
                    val visited = session != null && group.all { e -> session.wasVisited(e.opportunity) }
                    val previousMeters = state.stops.getOrNull(index - 1)?.first()?.meters ?: 0.0
                    StopItem(index + 1, group, previousMeters, plan, state.settings, visited, next = index == nextIndex,
                        modifier = Modifier.panelGutter().testTag("catch_stop_$index")) { actions.showStop(group) }
                }
            }
        }
    }
}

/** The planned route: what it catches, how far, when you are back, and the two things to do next. */
@Composable
private fun PlannedCard(model: CatchRoutesViewModel, plan: CatchItinerary, encounters: List<CatchEncounter>, actions: PlannerActions) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text("${encounters.size}", style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("catch_route_result"))
                    Text(" Pokémon", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("planned_route_menu")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Route actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Open in Google Maps") }, onClick = { menu = false; actions.openInMaps(plan) },
                            modifier = Modifier.testTag("export_catch_route"))
                    }
                }
            }
            val minutes = (plan.finishAtMillis - plan.settings.startAtMillis) / 60_000
            Text("${String.format(Locale.getDefault(), "%.1f", plan.distanceMeters / 1000)} km · $minutes min · back ${catchClock(plan.finishAtMillis)}" +
                if (plan.waits.isNotEmpty()) " · ${plan.waits.size} short waits" else "", style = MaterialTheme.typography.titleSmall)
            Text("about ${String.format(Locale.getDefault(), "%.1f", encounters.sumOf { it.opportunity.expectedCatch })} expected · " +
                "${encounters.count { it.opportunity.observed }} seen live" + (if (plan.settings.area.isArea()) " · inside your area" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            plan.settings.sourceRouteName?.let { name ->
                Text(if (plan.settings.fixed) "Walking $name as drawn" else "Planned near $name", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            model.recommendation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (plan.warnings.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (open) "Hide notes" else "${plan.warnings.size} notes about the data")
                }
                if (open) plan.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = actions.startGuidance, modifier = Modifier.weight(1f).testTag("follow_catch_route")) {
                    Text("Start", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { actions.setEditing(true) }, modifier = Modifier.weight(1f).testTag("edit_catch_route")) {
                    Text("Edit", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The walk in progress: the tally, two buttons for what is needed while walking (the floating map
 * and pause), and everything else in the ⋮ menu. The switches live on the Route options page.
 */
@Composable
private fun WalkingCard(model: CatchRoutesViewModel, session: CatchSession, state: PlannerState, actions: PlannerActions, onOptions: () -> Unit) {
    val now = state.now
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.itinerary.settings.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("catch_session_menu")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Route actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Skip stop") }, onClick = { menu = false; model.controller.skip() }, enabled = !session.finished)
                        DropdownMenuItem(text = { Text("Undo") }, onClick = { menu = false; model.controller.undo() })
                        DropdownMenuItem(text = { Text("Recalculate") }, onClick = { menu = false; model.controller.recalculate() },
                            enabled = !state.refreshing && !session.finished)
                        DropdownMenuItem(text = { Text("Open in Google Maps") }, onClick = { menu = false; actions.openInMaps(session.itinerary) })
                        DropdownMenuItem(text = { Text("Route options") }, onClick = { menu = false; onOptions() },
                            modifier = Modifier.testTag("catch_route_options"))
                        DropdownMenuItem(text = { Text("Stop route", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; actions.stop() })
                    }
                }
            }
            Column(Modifier.padding(end = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (session.finished) Text("Session finished", style = MaterialTheme.typography.titleMedium)
                Text("${session.caught} caught · ${session.ahead(now).size} ahead · ${session.missed(now).size} missed",
                    modifier = Modifier.testTag("catch_session_tally"))
                val active = session.activeMillis(now)
                val speed = if (active >= 60_000 && session.walkedMeters >= 50) session.walkedMeters / (active / 1000.0) * 3.6 else null
                Text("${formatCatchKm(session.walkedMeters)} walked · ${active / 60_000} min · ${formatCatchSpeed(speed)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                catchPaceHint(session, now)?.let {
                    Text(it.replaceFirstChar { c -> c.uppercase() }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                }
                // Advice only: the route changes when the trainer asks for it, never on its own.
                if (state.outOfDate && !session.finished && !state.refreshing)
                    Text("Route out of date: a stop has despawned or you left the route. Recalculate from the ⋮ menu.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
                    Button(onClick = actions.floatingWindow, modifier = Modifier.weight(1f).testTag("catch_floating_map")) {
                        Text("Floating map", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { model.controller.pause() }, enabled = !session.finished, modifier = Modifier.weight(1f)) {
                        Text(if (session.paused) "Resume" else "Pause", maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One numbered stop, its badge in theme colours: filled blue for the next stop, muted once visited. */
@Composable
private fun StopItem(number: Int, group: List<CatchEncounter>, previousMeters: Double, plan: CatchItinerary,
    settings: CatchRouteSettings, visited: Boolean, next: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val first = group.first()
    val wait = plan.waits.firstOrNull { kotlin.math.abs(it.meters - first.meters) <= settings.radius * 2 }
    val colors = MaterialTheme.colorScheme
    val (badge, onBadge) = when {
        next -> colors.primary to colors.onPrimary
        visited -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium,
        color = if (next) colors.surfaceContainerHigh else colors.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                Text("$number", color = onBadge, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                val events = group.count { it.opportunity.eventOnly }
                Text("${catchClock(first.arrivalMillis)} · ${group.size} Pokémon" + if (events > 0) " · $events event" else "",
                    style = MaterialTheme.typography.titleSmall, color = if (visited) colors.onSurfaceVariant else Color.Unspecified)
                val walk = (first.meters - previousMeters).coerceAtLeast(0.0)
                val margin = group.minOf { it.opportunity.despawnAt - it.arrivalMillis } / 60_000
                Text("${walk.toInt()} m walk · earliest despawn in $margin min" +
                    (wait?.let { " · wait ${it.millis / 60_000}:${String.format(Locale.US, "%02d", it.millis / 1000 % 60)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (visited) Icon(Icons.Filled.Check, contentDescription = "Done", tint = colors.onSurfaceVariant)
        }
    }
}

/** Reports a composable's height to the Map + sheet design, which peeks exactly that much. */
internal fun Modifier.reportHeight(onHeight: (Int) -> Unit): Modifier = onSizeChanged { onHeight(it.height) }
