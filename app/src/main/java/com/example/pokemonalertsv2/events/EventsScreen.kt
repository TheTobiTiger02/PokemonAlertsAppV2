package com.example.pokemonalertsv2.events

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

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

/** Activity-scoped calendar data survives tab switches without restarting a network request. */
class EventsViewModel(application: Application, private val savedState: androidx.lifecycle.SavedStateHandle) : AndroidViewModel(application) {
    val repository = EventsRepository(application)
    val preferences = EventPreferences(application)
    val listState = LazyListState(savedState["events.index"] ?: 0, savedState["events.offset"] ?: 0)
    private var selected by mutableStateOf<String?>(savedState["events.selected"])
    var selectedId: String?
        get() = selected
        set(value) { selected = value; savedState["events.selected"] = value }
    var events by mutableStateOf<List<GameEvent>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
                savedState["events.index"] = index
                savedState["events.offset"] = offset
            }
        }
        viewModelScope.launch {
            events = repository.cached()
            refresh()
        }
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try { events = repository.refresh() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't refresh events. Check your connection and try again." }
            finally { loading = false }
        }
    }

    fun change(replan: Boolean = true, block: suspend EventPreferences.() -> Unit) {
        viewModelScope.launch {
            try {
                preferences.block()
                if (replan) EventReminderWorker.replan(getApplication())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't save your event preferences. Please try again." }
        }
    }
}

@Composable
fun EventsRoute(model: EventsViewModel) {
    val context = LocalContext.current
    val settings by model.preferences.settings.collectAsStateWithLifecycle(EventSettings())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    EventsContent(
        loadPage = model.repository::page,
        rememberedPage = model.repository::remembered,
        state = EventsUiState(model.events, settings, model.loading, model.error, now),
        listState = model.listState,
        selectedEventId = model.selectedId,
        onSelectEvent = { model.selectedId = it },
        onRefresh = model::refresh,
        onToggleHidden = { type -> model.change(replan = false) { toggleHidden(type) } },
        onToggleStar = { id -> model.change { toggleStar(id) } },
        onToggleReminderType = { type -> model.change { toggleReminderType(type) } },
        onLeadMinutes = { minutes -> model.change { setLeadMinutes(minutes) } },
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
    listState: LazyListState = rememberLazyListState(),
    selectedEventId: String? = null,
    onSelectEvent: ((String?) -> Unit)? = null,
) {
    val sections = remember(state.events, state.nowMillis, state.settings.hiddenTypes) {
        groupEvents(state.events, state.nowMillis, state.settings.hiddenTypes)
    }
    val types = remember(state.events) { state.events.map { it.eventType }.distinct().sortedBy { eventTypeName(it) } }
    var selected by remember { mutableStateOf<GameEvent?>(null) }
    val currentSelection = if (onSelectEvent != null) state.events.firstOrNull { it.id == selectedEventId } else selected
    fun select(event: GameEvent?) { if (onSelectEvent != null) onSelectEvent(event?.id) else selected = event }
    var filtersOpen by remember { mutableStateOf(false) }
    var reminderSettingsOpen by remember { mutableStateOf(false) }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().testTag("events_screen")) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Events", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reminderSettingsOpen = true }, modifier = Modifier.testTag("events_reminder_settings")) { Text("Reminders") }
                }
                Text("From LeekDuck. Times are local.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error?.let { message -> item {
                Column {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("events_error"))
                    if (state.events.isNotEmpty()) Text("Showing saved events.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Try again") }
                }
            } }
            if (types.isNotEmpty()) item {
                val visibleTypes = types.count { it !in state.settings.hiddenTypes }
                OutlinedButton(onClick = { filtersOpen = true }, modifier = Modifier.testTag("events_filters")) {
                    Text(if (visibleTypes == types.size) "Categories · All" else "Categories · $visibleTypes of ${types.size}")
                }
            }
            if (sections.now.isEmpty() && sections.upcoming.isEmpty() && !state.loading) item {
                Text(if (state.settings.hiddenTypes.isNotEmpty()) "No matching events. Try showing more categories." else "No upcoming events. Pull down to refresh.", style = MaterialTheme.typography.bodyMedium)
            }
            if (sections.now.isNotEmpty()) {
                item { SectionTitle("Happening now") }
                items(sections.now, key = { "now-${it.id}" }) { event ->
                    EventCard(event, state, onClick = { select(event) }, onToggleStar = onToggleStar)
                }
            }
            if (sections.upcoming.isNotEmpty()) item { SectionTitle("Coming next") }
            sections.upcoming.forEach { (day, events) ->
                item(key = "day-$day") { SectionTitle(dayLabel(day, state.nowMillis)) }
                items(events, key = { it.id }) { event ->
                    EventCard(event, state, onClick = { select(event) }, onToggleStar = onToggleStar)
                }
            }
        }
    }
    currentSelection?.let { event ->
        EventDetailScreen(event, state, onDismiss = { select(null) }, onToggleStar = onToggleStar, onOpenLink = onOpenLink,
            loadPage = loadPage, rememberedPage = rememberedPage)
    }
    if (filtersOpen) ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
            item {
                Text("Event categories", style = MaterialTheme.typography.headlineSmall)
                Text("Choose which events appear in your calendar.", style = MaterialTheme.typography.bodyMedium)
            }
            items(types, key = { it }) { type ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggleHidden(type) }
                    .heightIn(min = 48.dp).testTag("events_type_$type"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = type !in state.settings.hiddenTypes, onCheckedChange = null)
                    Text(eventTypeName(type), modifier = Modifier.padding(start = 12.dp))
                }
            }
            item { TextButton(onClick = { filtersOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
        }
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
                        val category = event.heading ?: eventTypeName(event.eventType)
                        if (!event.name.contains(category, ignoreCase = true)) {
                            Text(category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
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
                if (event.id in state.settings.starredIds || event.eventType in state.settings.reminderTypes) {
                    Text("Reminder · ${state.settings.leadMinutes} min before start", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
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
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
