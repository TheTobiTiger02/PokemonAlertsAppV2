package com.example.pokemonalertsv2.events

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.work.EventReminderWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Events tab draws; kept separate from loading so it can be tested with fixed data. */
data class EventsUiState(
    val events: List<GameEvent> = emptyList(),
    val settings: EventSettings = EventSettings(),
    val loading: Boolean = false,
    val error: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
)

/** The tab as the app shows it: cached events at once, a refresh on open, reminders re-planned on change. */
@Composable
fun EventsRoute() {
    val context = LocalContext.current
    val repository = remember { EventsRepository(context) }
    val preferences = remember { EventPreferences(context) }
    val scope = rememberCoroutineScope()
    val settings by preferences.settings.collectAsStateWithLifecycle(EventSettings())
    var events by remember { mutableStateOf<List<GameEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun refresh() = scope.launch {
        loading = true; error = null
        try { events = repository.refresh() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            error = e.message ?: "Events could not be loaded."
        } finally { loading = false }
    }
    LaunchedEffect(Unit) {
        events = repository.cached()
        refresh()
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    fun changed(block: suspend () -> Unit) = scope.launch { block(); EventReminderWorker.replan(context) }
    EventsContent(
        loadPage = repository::page,
        rememberedPage = repository::remembered,
        state = EventsUiState(events, settings, loading, error, now),
        onRefresh = { refresh() },
        onToggleHidden = { type -> scope.launch { preferences.toggleHidden(type) } },
        onToggleStar = { id -> changed { preferences.toggleStar(id) } },
        onToggleReminderType = { type -> changed { preferences.toggleReminderType(type) } },
        onLeadMinutes = { minutes -> changed { preferences.setLeadMinutes(minutes) } },
        onOpenLink = { link -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun EventsContent(
    state: EventsUiState,
    onRefresh: () -> Unit,
    onToggleHidden: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onToggleReminderType: (String) -> Unit,
    onLeadMinutes: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    loadPage: suspend (String) -> EventPage? = { null },
    rememberedPage: (String) -> EventPage? = { null },
) {
    val sections = remember(state.events, state.nowMillis, state.settings.hiddenTypes) {
        groupEvents(state.events, state.nowMillis, state.settings.hiddenTypes)
    }
    val types = remember(state.events) { state.events.map { it.eventType }.distinct().sortedBy { eventTypeName(it) } }
    var selected by remember { mutableStateOf<GameEvent?>(null) }
    var reminderSettingsOpen by remember { mutableStateOf(false) }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().testTag("events_screen")) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Events", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reminderSettingsOpen = true }, modifier = Modifier.testTag("events_reminder_settings")) { Text("Reminders") }
                }
                Text("From LeekDuck. Times are local.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("events_error")) } }
            if (types.isNotEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { type ->
                        FilterChip(selected = type !in state.settings.hiddenTypes, onClick = { onToggleHidden(type) },
                            label = { Text(eventTypeName(type)) }, modifier = Modifier.testTag("events_type_$type"))
                    }
                }
            }
            if (sections.now.isEmpty() && sections.upcoming.isEmpty() && !state.loading) item {
                Text("No events to show.", style = MaterialTheme.typography.bodyMedium)
            }
            if (sections.now.isNotEmpty()) {
                item { SectionTitle("Happening now") }
                items(sections.now, key = { "now-${it.id}" }) { event ->
                    EventCard(event, state, onClick = { selected = event }, onToggleStar = onToggleStar)
                }
            }
            sections.upcoming.forEach { (day, events) ->
                item(key = "day-$day") { SectionTitle(dayLabel(day, state.nowMillis)) }
                items(events, key = { it.id }) { event ->
                    EventCard(event, state, onClick = { selected = event }, onToggleStar = onToggleStar)
                }
            }
        }
    }
    selected?.let { event ->
        EventDetailScreen(event, state, onDismiss = { selected = null }, onToggleStar = onToggleStar, onOpenLink = onOpenLink,
            loadPage = loadPage, rememberedPage = rememberedPage)
    }
    if (reminderSettingsOpen) ModalBottomSheet(onDismissRequest = { reminderSettingsOpen = false }) {
        ReminderSettings(state.settings, (types + DEFAULT_REMINDER_EVENT_TYPES).distinct().sortedBy { eventTypeName(it) },
            onToggleReminderType, onLeadMinutes)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun EventCard(event: GameEvent, state: EventsUiState, onClick: () -> Unit, onToggleStar: (String) -> Unit) {
    val running = event.startMillis <= state.nowMillis
    ElevatedCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).testTag("event_${event.id}")) {
        Column {
            event.image?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(110.dp))
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(event.heading ?: eventTypeName(event.eventType), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(event.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    StarButton(event, state.settings, onToggleStar)
                }
                Text(timeRange(event), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (running) {
                    LinearProgressIndicator(progress = { event.progress(state.nowMillis) }, modifier = Modifier.fillMaxWidth())
                    Text("${durationLabel(event.endMillis - state.nowMillis)} left", style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("Starts in ${durationLabel(event.startMillis - state.nowMillis)}", style = MaterialTheme.typography.labelMedium)
                }
                val pokemon = event.featured.ifEmpty { event.raidBosses }
                if (pokemon.isNotEmpty()) PokemonRow(pokemon)
                val badges = cardSectionBadges(event.sectionKeys)
                if (badges.isNotEmpty()) Text(badges.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
internal fun StarButton(event: GameEvent, settings: EventSettings, onToggleStar: (String) -> Unit) {
    val starred = event.id in settings.starredIds
    IconButton(onClick = { onToggleStar(event.id) }, modifier = Modifier.testTag("event_star_${event.id}")) {
        Icon(Icons.Filled.Star, contentDescription = if (starred) "Don't remind me" else "Remind me",
            tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    }
}

@Composable
internal fun PokemonRow(pokemon: List<EventPokemon>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pokemon) { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                AsyncImage(model = p.image, contentDescription = p.name, modifier = Modifier.size(44.dp).clip(CircleShape))
                Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.shinyAvailable) Text("✨ shiny", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ReminderSettings(settings: EventSettings, types: List<String>, onToggleReminderType: (String) -> Unit, onLeadMinutes: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Event reminders", style = MaterialTheme.typography.headlineSmall)
        Text("Get a notification before every event of these types. Star single events to be reminded of them too.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { type ->
                FilterChip(selected = type in settings.reminderTypes, onClick = { onToggleReminderType(type) },
                    label = { Text(eventTypeName(type)) }, modifier = Modifier.testTag("events_remind_$type"))
            }
        }
        Text("How long before", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            REMINDER_LEAD_CHOICES.forEach { minutes ->
                FilterChip(selected = settings.leadMinutes == minutes, onClick = { onLeadMinutes(minutes) }, label = { Text("$minutes min") })
            }
        }
    }
}

internal fun timeRange(event: GameEvent): String {
    val zone = event.displayZone()
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
    val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    return if (start.toLocalDate() == end.toLocalDate()) "${day.format(start)} · ${clock.format(start)}–${clock.format(end)}"
    else "${day.format(start)} ${clock.format(start)} – ${day.format(end)} ${clock.format(end)}"
}

private fun dayLabel(day: LocalDate, nowMillis: Long): String {
    val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(day)
    }
}
