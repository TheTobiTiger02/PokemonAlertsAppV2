package com.example.pokemonalertsv2.events

import com.example.pokemonalertsv2.util.AppText
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import com.example.pokemonalertsv2.ui.theme.Dimens
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.pokemonalertsv2.R
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
import androidx.compose.foundation.layout.FlowRow

private const val EVENTS_REFRESH_INTERVAL_MS = 10 * 60 * 1000L

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
fun EventsRoute(onOpenSettings: (() -> Unit)? = null) {
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
            error = if (events.isEmpty()) context.getString(R.string.events_events_could_not_be_loaded) else context.getString(R.string.events_could_not_refresh_showing_saved_events)
        } finally { loading = false }
    }
    LaunchedEffect(Unit) { events = repository.cached() }
    // The tab stays composed between visits, so "refresh on open" follows the lifecycle,
    // which is RESUMED exactly while the tab is showing. Throttled: a quick look back at the
    // tab should not refetch the whole calendar.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var lastRefresh by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            if (System.currentTimeMillis() - lastRefresh > EVENTS_REFRESH_INTERVAL_MS) {
                lastRefresh = System.currentTimeMillis()
                refresh()
            }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    fun changed(block: suspend () -> Unit) = scope.launch { block(); EventReminderWorker.replan(context) }
    val requestedEventId by EventDeepLink.pending.collectAsStateWithLifecycle()
    EventsContent(
        openEventId = requestedEventId,
        onOpenEventHandled = EventDeepLink::consume,
        loadPage = repository::page,
        rememberedPage = repository::remembered,
        state = EventsUiState(events, settings, loading, error, now),
        onRefresh = { refresh() },
        onOpenSettings = onOpenSettings,
        onToggleHidden = { type -> scope.launch { preferences.toggleHidden(type) } },
        onShowAllTypes = { scope.launch { preferences.showAllTypes() } },
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
    onShowAllTypes: () -> Unit,
    onToggleStar: (String) -> Unit,
    onToggleReminderType: (String) -> Unit,
    onLeadMinutes: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    loadPage: suspend (String) -> EventPage? = { null },
    rememberedPage: (String) -> EventPage? = { null },
    onOpenSettings: (() -> Unit)? = null,
    /** An event to open in its detail view once the list has it, e.g. from the widget. */
    openEventId: String? = null,
    onOpenEventHandled: (String) -> Unit = {},
) {
    val sections = remember(state.events, state.nowMillis, state.settings.hiddenTypes) {
        groupEvents(state.events, state.nowMillis, state.settings.hiddenTypes)
    }
    val types = remember(state.events) { state.events.map { it.eventType }.distinct().sortedBy { eventTypeName(it) } }
    var selected by remember { mutableStateOf<GameEvent?>(null) }
    // The cached list can still be empty on a cold start; wait for events rather than dropping
    // the request, and drop it once the loaded list does not have the event.
    LaunchedEffect(openEventId, state.events) {
        val id = openEventId ?: return@LaunchedEffect
        if (state.events.isEmpty()) return@LaunchedEffect
        state.events.firstOrNull { it.id == id }?.let { selected = it }
        onOpenEventHandled(id)
    }
    var reminderSettingsOpen by remember { mutableStateOf(false) }
    var typesOpen by remember { mutableStateOf(false) }
    val hiddenTypeCount = types.count { it in state.settings.hiddenTypes }
    var view by rememberSaveable { mutableStateOf(EventView.ALL) }
    val starred = state.settings.starredIds
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    fun List<GameEvent>.inView() = filter { event ->
        (view != EventView.STARRED || event.id in starred) &&
            (needle.isEmpty() || event.name.contains(needle, ignoreCase = true) ||
                event.heading?.contains(needle, ignoreCase = true) == true ||
                event.featured.any { it.name.contains(needle, ignoreCase = true) } ||
                event.raidBosses.any { it.name.contains(needle, ignoreCase = true) })
    }
    val nowEvents = remember(sections, view, starred, needle) {
        if (view == EventView.UPCOMING) emptyList() else sections.now.inView()
    }
    val upcomingDays = remember(sections, view, starred, needle) {
        if (view == EventView.NOW) emptyList()
        else sections.upcoming.map { (day, events) -> day to events.inView() }.filter { it.second.isNotEmpty() }
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().testTag("events_screen")) {
        // Staggered: a card with a Pokémon row is taller than one without, and a plain grid
        // would leave a gap beside every short card.
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(Dimens.GridCardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.events_events), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reminderSettingsOpen = true }, modifier = Modifier.testTag("events_reminder_settings")) { Text(stringResource(R.string.events_reminders)) }
                    if (types.isNotEmpty()) {
                        IconButton(onClick = { typesOpen = true }, modifier = Modifier.testTag("events_filter")) {
                            BadgedBox(badge = { if (hiddenTypeCount > 0) Badge() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_filter),
                                    contentDescription = if (hiddenTypeCount == 0) {
                                        "Filter event types"
                                    } else {
                                        "Filter event types, $hiddenTypeCount hidden"
                                    }
                                )
                            }
                        }
                    }
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.events_settings))
                        }
                    }
                }
            }
            // One row of quick views; which event types show is the filter button above.
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EventView.entries.forEach { option ->
                        FilterChip(selected = view == option, onClick = { view = option }, label = { Text(option.label) },
                            modifier = Modifier.testTag("events_view_${option.name.lowercase()}"))
                    }
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.events_search_events_or_pok_mon)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.events_clear_search)) } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("events_search")
                )
            }
            state.error?.let { message ->
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("events_error")) {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.events_retry)) }
                    }
                }
            }
            if (nowEvents.isEmpty() && upcomingDays.isEmpty() && !state.loading) item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    when {
                        needle.isNotEmpty() -> stringResource(R.string.events_no_events_match_x, needle)
                        view == EventView.STARRED -> stringResource(R.string.events_no_starred_events_tap_the_star)
                        else -> stringResource(R.string.events_no_events_to_show)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (nowEvents.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) { SectionTitle(stringResource(R.string.events_happening_now)) }
                items(nowEvents, key = { "now-${it.id}" }) { event ->
                    EventCard(event, state, onClick = { selected = event }, onToggleStar = onToggleStar)
                }
            }
            upcomingDays.forEach { (day, events) ->
                item(key = "day-$day", span = StaggeredGridItemSpan.FullLine) { SectionTitle(dayLabel(day, state.nowMillis)) }
                items(events, key = { it.id }) { event ->
                    EventCard(event, state, onClick = { selected = event }, onToggleStar = onToggleStar)
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    stringResource(R.string.events_event_data_from_leekduck_times_are),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
    if (typesOpen) ModalBottomSheet(onDismissRequest = { typesOpen = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.events_event_types), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onShowAllTypes, enabled = hiddenTypeCount > 0, modifier = Modifier.testTag("events_types_all")) {
                    Text(stringResource(R.string.events_show_all))
                }
            }
            Text(stringResource(R.string.events_untick_a_type_to_hide_it), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { type ->
                    FilterChip(selected = type !in state.settings.hiddenTypes, onClick = { onToggleHidden(type) },
                        label = { Text(eventTypeName(type)) }, modifier = Modifier.testTag("events_type_$type"))
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

/** Which slice of the event list is showing. Not persisted: it is a way of looking, not a setting. */
private enum class EventView(val label: String) {
    ALL(AppText.get(R.string.alerts_all)),
    NOW(AppText.get(R.string.catchroutes_now)),
    UPCOMING(AppText.get(R.string.events_upcoming)),
    STARRED(AppText.get(R.string.events_starred))
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
                    Text(stringResource(R.string.events_starts_in_x, durationLabel(event.startMillis - state.nowMillis)), style = MaterialTheme.typography.labelMedium)
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
    // A toggle, so TalkBack reads "Remind me, on/off"; filled and outlined, so the state is not colour alone.
    androidx.compose.material3.IconToggleButton(
        checked = starred,
        onCheckedChange = { onToggleStar(event.id) },
        modifier = Modifier.testTag("event_star_${event.id}")
    ) {
        val tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        if (starred) {
            Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.events_remind_me), tint = tint)
        } else {
            Icon(painterResource(R.drawable.ic_star_outline), contentDescription = stringResource(R.string.events_remind_me), tint = tint)
        }
    }
}

@Composable
internal fun PokemonRow(pokemon: List<EventPokemon>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pokemon) { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                AsyncImage(model = p.image, contentDescription = p.name, modifier = Modifier.size(44.dp).clip(CircleShape))
                Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.shinyAvailable) Text(stringResource(R.string.events_shiny), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ReminderSettings(settings: EventSettings, types: List<String>, onToggleReminderType: (String) -> Unit, onLeadMinutes: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.events_event_reminders), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.events_get_a_notification_before_every_event),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { type ->
                FilterChip(selected = type in settings.reminderTypes, onClick = { onToggleReminderType(type) },
                    label = { Text(eventTypeName(type)) }, modifier = Modifier.testTag("events_remind_$type"))
            }
        }
        Text(stringResource(R.string.events_how_long_before), style = MaterialTheme.typography.titleSmall)
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
        today -> AppText.get(R.string.alerts_today)
        today.plusDays(1) -> AppText.get(R.string.events_tomorrow)
        else -> DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(day)
    }
}
