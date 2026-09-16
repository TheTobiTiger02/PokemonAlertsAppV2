package com.example.pokemonalertsv2.catchroutes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository
import com.example.pokemonalertsv2.data.counters.PokemonSpriteUrls
import com.example.pokemonalertsv2.ui.alerts.rememberCountdownClock
import com.example.pokemonalertsv2.ui.theme.SuccessGreen
import com.example.pokemonalertsv2.ui.theme.WarningAmber
import com.example.pokemonalertsv2.util.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One spawnpoint as the planner knows it: its windows, and where the route meets them. */
internal data class SpawnpointSelection(
    val pointId: String,
    val point: CatchPoint,
    val opportunities: List<SpawnOpportunity>,
    val encounters: List<CatchEncounter>,
)

/** Every spawnpoint touched by [encounters], in route order, one entry per point. */
internal fun spawnpointSelections(encounters: List<CatchEncounter>): List<SpawnpointSelection> =
    encounters.groupBy { it.opportunity.pointId }.map { (id, list) ->
        SpawnpointSelection(id, list.first().opportunity.point, list.map { it.opportunity }.distinctBy { it.id }.sortedBy { it.availableFrom },
            list.sortedBy { it.arrivalMillis })
    }

internal enum class SpawnState { ACTIVE, UPCOMING, ENDED }

/** What a spawnpoint is doing at [now], for the headline pill. */
internal data class SpawnStatus(val state: SpawnState, val text: String)

internal fun spawnStatus(windows: List<SpawnOpportunity>, now: Long): SpawnStatus {
    val active = windows.filter { now >= it.availableFrom && now < it.despawnAt }.maxByOrNull { it.despawnAt }
    if (active != null) return SpawnStatus(SpawnState.ACTIVE, "Active · ${TimeUtils.formatDurationShort(active.despawnAt - now)} left")
    val next = windows.filter { it.availableFrom > now }.minByOrNull { it.availableFrom }
    if (next != null) {
        val wait = next.availableFrom - now
        return SpawnStatus(SpawnState.UPCOMING,
            if (wait <= 60 * 60_000L) "Spawns in ${TimeUtils.formatDurationShort(wait)}" else "Next at ${clock(next.availableFrom)}")
    }
    return SpawnStatus(SpawnState.ENDED, "No window in range")
}

/** How much to trust the timing, in words, from the backend's probability and basis. */
internal fun confidenceLabel(o: SpawnOpportunity): String = when {
    o.observed -> "Seen live"
    o.expectedCatch >= 0.8 -> "Verified schedule"
    o.expectedCatch >= 0.5 -> "Likely"
    else -> "Uncertain"
}

internal fun basisLabel(basis: String): String = when (basis) {
    "observed_encounter" -> "Seen live by a scanner"
    "recurring_schedule" -> "Learned hourly schedule"
    "inferred_lifetime" -> "Estimated 60-minute lifetime"
    "assumed_duration" -> "Known despawn, assumed duration"
    "unverified_encounter" -> "Seen live, despawn unverified"
    else -> basis.replace('_', ' ')
}

internal fun durationLabel(schedule: SpawnSchedule?): String = when (schedule?.durationBasis) {
    "observed_60" -> "60 min"
    "observed_30" -> "30 min"
    else -> "Unknown"
}

/** Minute:second of the hour, the way a spawnpoint's schedule reads: ":23:40". */
internal fun secondOfHourLabel(second: Int): String = String.format(Locale.US, ":%02d:%02d", second / 60, second % 60)

private val uncertaintyText = mapOf(
    "requires_live_confirmation" to "Requires live confirmation: only live sightings are planned here.",
    "conflicting_timing_evidence" to "Scanners disagree about this spawnpoint's timing.",
    "no_recent_live_observation" to "No live sighting in the last 15 minutes.",
    "stale_or_unknown_catalogue_refresh" to "Catalogue not refreshed in the last two days.",
    "assumed_spawn_duration" to "The duration is assumed, so the start time is an estimate.",
    "assumed_60_minute_lifetime" to "Uses a learned 60-minute lifetime.",
    "learned_30_minute_lifetime" to "Uses a learned 30-minute lifetime.",
    "first_seen_is_not_spawn_time" to "The first sighting is not necessarily when it spawned.",
    "unverified_expiry" to "The despawn time was not verified.",
    "recurrence_not_live_confirmation" to "Predicted from past hours, not seen live this hour.",
    "legacy_despawn_provenance" to "Timing comes from older data without provenance.",
    "legacy_start_provenance" to "Start time comes from older data without provenance.",
    "ambiguous_spawnpoint_association" to "Sightings could belong to a neighbouring spawnpoint.",
    "event_only_spawnpoint" to "Only spawns during certain events.",
    "dormant_spawnpoint" to "Nothing has spawned here recently, although nearby spawns were scanned.",
    "catalogue_only" to "Only known from a spawnpoint catalogue, not seen by the live scanner.",
)

/** Event types, the way players say them. */
internal fun eventTypeLabel(type: String): String = when (type) {
    "pokemon-spotlight-hour" -> "Spotlight Hours"
    "community-day" -> "Community Days"
    "wild-area" -> "Wild Area events"
    "event" -> "events"
    else -> type.replace('-', ' ')
}

/**
 * The one-line story of a spawnpoint that is not simply regular: event-only, dormant or known only from
 * the catalogue. Null for an ordinary point.
 */
internal fun activityNotice(pattern: String?, eventTypes: List<String>, nextEvent: SpawnpointEvent?, reason: String?, now: Long): String? = when {
    pattern == "event_only" -> buildString {
        append("Event spawnpoint: only spawns during ")
        append(eventTypes.ifEmpty { listOf("event") }.joinToString(" and ") { eventTypeLabel(it) })
        append(".")
        nextEvent?.let { e ->
            if (now >= e.startAt && now < e.endAt) append(" Active now: ${e.name}, until ${clock(e.endAt)}.")
            else append(" Next: ${e.name}, ${dayAndClock(e.startAt)}.")
            if (e.featured.isNotEmpty()) append(" Featured: ${e.featured.joinToString(", ")}.")
        }
    }
    pattern == "likely_event" -> "Likely event spawnpoint: it appears only during some periods."
    pattern == "dormant" || reason == "dormant_spawnpoint" -> "Inactive: nothing has spawned here recently, although nearby spawns were scanned."
    reason == "catalogue_only" -> "Only known from the WingullMap catalogue. Routes wait until the live scanner sees it."
    else -> null
}

private fun dayAndClock(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))

internal fun uncertaintyLabel(code: String): String = uncertaintyText[code] ?: code.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * Where the spawn sits within an hour, as fractions 0–1 of the hour: the known window
 * (possibly wrapping past :00) and the despawn tick. Uses the learned schedule when it has a
 * duration, otherwise the next window the backend planned.
 */
internal data class HourBar(val segments: List<ClosedFloatingPointRange<Float>>, val despawn: Float?, val certain: Boolean)

internal fun hourBar(schedule: SpawnSchedule?, window: SpawnOpportunity?, zone: ZoneId = ZoneId.systemDefault()): HourBar {
    fun secondOfHour(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).let { it.minute * 60 + it.second }
    val despawn = schedule?.despawnSecondOfHour ?: window?.let { secondOfHour(it.despawnAt) }
    val duration = schedule?.durationSeconds ?: window?.let { ((it.despawnAt - it.availableFrom) / 1000).toInt().coerceIn(1, 3600) }
    if (despawn == null || duration == null) return HourBar(emptyList(), despawn?.div(3600f), false)
    val start = despawn - duration
    val segments = if (duration >= 3600) listOf(0f..1f) else if (start >= 0) listOf(start / 3600f..despawn / 3600f)
        else listOf(0f..despawn / 3600f, (start + 3600) / 3600f..1f)
    return HourBar(segments, despawn / 3600f, schedule?.durationSeconds != null || window?.observed == true)
}

private fun clock(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun parseInstant(value: String?) = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/**
 * Details for one or more spawnpoints: what it is doing now, its hourly schedule, how sure the
 * backend is, where this route meets it, and what spawned there recently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpawnpointDetailSheet(
    selections: List<SpawnpointSelection>,
    itinerary: CatchItinerary?,
    loadDetail: suspend (String) -> SpawnpointDetail,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = selections.size > 1)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        val now by rememberCountdownClock()
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("spawnpoint_detail_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selections.size == 1) "Spawnpoint" else "${selections.size} spawnpoints",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            selections.forEachIndexed { index, selection ->
                if (index > 0) HorizontalDivider()
                if (selections.size > 1) Text("Spawnpoint ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SpawnpointSection(selection, itinerary, now, loadDetail)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpawnpointSection(selection: SpawnpointSelection, itinerary: CatchItinerary?, now: Long, loadDetail: suspend (String) -> SpawnpointDetail) {
    var detail by remember(selection.pointId) { mutableStateOf<SpawnpointDetail?>(null) }
    var failed by remember(selection.pointId) { mutableStateOf(false) }
    LaunchedEffect(selection.pointId) {
        runCatching { loadDetail(selection.pointId) }.onSuccess { detail = it }.onFailure { failed = true }
    }
    val windows = (selection.opportunities + detail?.upcoming.orEmpty()).distinctBy { it.id }.sortedBy { it.availableFrom }
    val primary = windows.firstOrNull { now < it.despawnAt } ?: windows.lastOrNull()
    val schedule = detail?.schedule ?: selection.opportunities.firstNotNullOfOrNull { it.schedule }
    val status = spawnStatus(windows, now)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (container, content) = when (status.state) {
                SpawnState.ACTIVE -> SuccessGreen to Color.White
                SpawnState.UPCOMING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                SpawnState.ENDED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Pill(status.text, container, content)
            primary?.let {
                val sure = it.observed || it.expectedCatch >= 0.8
                Pill("${confidenceLabel(it)} · ${(it.expectedCatch * 100).toInt()}%",
                    if (sure) SuccessGreen.copy(alpha = 0.16f) else WarningAmber.copy(alpha = 0.16f),
                    if (sure) SuccessGreen else WarningAmber)
            }
        }

        SectionLabel("Every hour")
        HourBarView(hourBar(schedule, primary), now)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Fact("Despawns", schedule?.despawnSecondOfHour?.let(::secondOfHourLabel) ?: primary?.let { clock(it.despawnAt) } ?: "Unknown", Modifier.weight(1f))
            Fact("Lasts", durationLabel(schedule), Modifier.weight(1f))
            Fact("Agreeing cycles", schedule?.supportCycles?.let { s -> schedule.totalCycles?.let { "$s of $it" } ?: "$s" } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // "Never" only once the backend has answered; before that, or without it, it is unknown.
            val unknown = if (detail == null) "—" else "Never"
            Fact("Last verified", parseInstant(schedule?.lastVerifiedAt)?.let { TimeUtils.formatTimeAgo(it) } ?: unknown, Modifier.weight(1f))
            Fact("Seen live", parseInstant(detail?.liveLastSeenAt ?: primary?.liveLastSeenAt)?.let { TimeUtils.formatTimeAgo(it) } ?: unknown, Modifier.weight(1f))
            Fact("Last active", parseInstant(detail?.lastActiveAt)?.let { TimeUtils.formatTimeAgo(it) } ?: unknown, Modifier.weight(1f))
        }

        if (selection.encounters.isNotEmpty()) {
            SectionLabel("On this route")
            selection.encounters.forEach { e ->
                val wait = itinerary?.waits?.firstOrNull { kotlin.math.abs(it.meters - e.meters) <= (itinerary.settings.radius * 2) }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reach it at ${clock(e.arrivalMillis)}", style = MaterialTheme.typography.titleSmall)
                            Text("${e.meters.toInt()} m along the route · ${TimeUtils.formatDurationShort(e.opportunity.despawnAt - e.arrivalMillis)} to spare",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (wait != null) Pill("Wait ${TimeUtils.formatDurationShort(wait.millis)}", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        if (windows.isNotEmpty()) {
            SectionLabel("Windows")
            windows.filter { it.despawnAt > now }.take(4).ifEmpty { windows.takeLast(1) }.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${clock(w.availableFrom)}–${clock(w.despawnAt)}", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
                    Text(basisLabel(w.basis), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(w.expectedCatch * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val sightings = detail?.sightings.orEmpty().filter { it.pokemonId != null }
        if (sightings.isNotEmpty()) {
            SectionLabel("Recent spawns")
            RecentSpawns(sightings)
        }

        val warnings = buildList {
            activityNotice(detail?.activityPattern ?: primary?.activityPattern, detail?.eventTypes.orEmpty(), detail?.nextEvent, detail?.reason, now)?.let(::add)
            if ((primary?.requiresLiveConfirmation == true || detail?.requiresLiveConfirmation == true) &&
                (detail?.activityPattern ?: primary?.activityPattern) !in setOf("event_only", "dormant") && detail?.reason != "catalogue_only")
                add("Requires live confirmation: only live sightings are planned here.")
            if (primary?.timingConflict == true || detail?.timingConflict == true) add("Scanners disagree about this spawnpoint's timing.")
        }
        warnings.forEach { WarningRow(it) }
        if (failed) Text("More details are unavailable right now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        EvidenceSection(selection, primary, detail)
    }
}

@Composable
private fun EvidenceSection(selection: SpawnpointSelection, primary: SpawnOpportunity?, detail: SpawnpointDetail?) {
    var open by remember(selection.pointId) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Evidence", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Hide evidence" else "Show evidence")
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val codes = (primary?.uncertainty.orEmpty() + detail?.uncertainty.orEmpty()).distinct()
                codes.forEach { Text("• ${uncertaintyLabel(it)}", style = MaterialTheme.typography.bodySmall) }
                val evidence = listOfNotNull(
                    primary?.let { "Planning basis: ${basisLabel(it.basis)}" },
                    (primary?.source ?: detail?.source)?.let { "Source: $it" },
                    primary?.despawnBasis?.let { "Despawn evidence: ${it.replace('_', ' ')}" },
                    primary?.lowerBoundSeconds?.let { "Seen at least ${it / 60} min before despawn" },
                    detail?.verifiedEncounterCount?.let { "$it verified sightings in the last 30 days" },
                    parseInstant(primary?.catalogueSeenAt ?: detail?.catalogueSeenAt)?.let { "Catalogue refreshed ${TimeUtils.formatTimeAgo(it)}" },
                    "Spawnpoint ${selection.pointId.takeLast(12)} · ${String.format(Locale.US, "%.5f, %.5f", selection.point.latitude, selection.point.longitude)}",
                )
                evidence.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun RecentSpawns(sightings: List<SpawnpointSighting>) {
    val context = LocalContext.current
    var names by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        names = runCatching { PokemonSpeciesRepository.getInstance(context).getAllSpeciesList().associate { it.id to it.name } }.getOrDefault(emptyMap())
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sightings.take(10)) { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    AsyncImage(model = PokemonSpriteUrls.candidates(s.pokemonId, null).lastOrNull(), contentDescription = names[s.pokemonId],
                        modifier = Modifier.size(40.dp))
                }
                Text(names[s.pokemonId] ?: "#${s.pokemonId}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // When it was first seen, then when it despawned.
                s.firstSeenAt?.let { Text("seen ${clock(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                s.despawnAt?.let { Text("gone ${clock(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun HourBarView(bar: HourBar, now: Long) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = if (bar.certain) SuccessGreen else WarningAmber
    val tick = MaterialTheme.colorScheme.onSurface
    val nowFraction = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).let { (it.minute * 60 + it.second) / 3600f }
    Column {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val barTop = size.height * 0.25f
            val barHeight = size.height * 0.5f
            drawRoundRect(track, Offset(0f, barTop), Size(size.width, barHeight), CornerRadius(barHeight / 2))
            bar.segments.forEach { s ->
                drawRoundRect(fill.copy(alpha = if (bar.certain) 0.85f else 0.55f), Offset(size.width * s.start, barTop),
                    Size(size.width * (s.endInclusive - s.start), barHeight), CornerRadius(barHeight / 2))
            }
            bar.despawn?.let { d -> drawRect(tick, Offset(size.width * d - 1.5f, barTop - 4f), Size(3f, barHeight + 8f)) }
            drawCircle(Color.White, radius = size.height * 0.22f, center = Offset(size.width * nowFraction, size.height / 2))
            drawCircle(tick, radius = size.height * 0.15f, center = Offset(size.width * nowFraction, size.height / 2))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(":00", ":15", ":30", ":45", ":00").forEachIndexed { i, label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = when (i) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 4 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center })
            }
        }
        Text(if (bar.segments.isEmpty()) "Schedule not learned yet" else if (bar.certain) "Shaded: when a Pokémon is there · dot: now" else "Shaded: estimated window · dot: now",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WarningRow(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WarningAmber.copy(alpha = 0.12f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
