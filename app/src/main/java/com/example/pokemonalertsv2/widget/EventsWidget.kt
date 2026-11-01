package com.example.pokemonalertsv2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.events.EventsRepository
import com.example.pokemonalertsv2.events.GameEvent
import com.example.pokemonalertsv2.events.displayZone
import com.example.pokemonalertsv2.events.durationLabel
import com.example.pokemonalertsv2.events.eventTypeName
import com.example.pokemonalertsv2.events.groupEvents
import com.example.pokemonalertsv2.navigation.DEEP_LINK_SCHEME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One line of the events widget's list. */
internal sealed interface EventsWidgetRow {
    data class Header(val title: String) : EventsWidgetRow
    data class Event(val event: GameEvent, val running: Boolean, val detail: String) : EventsWidgetRow
}

/**
 * What the widget lists: running events under "Now" (ending soonest first), then every upcoming
 * one under "Upcoming" (starting soonest first), minus the types the trainer hid. The same
 * grouping as the Events tab, so both agree on what is running. The list scrolls, so upcoming
 * events are not capped.
 */
internal fun eventsWidgetRows(
    events: List<GameEvent>,
    nowMillis: Long,
    hiddenTypes: Set<String>,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): List<EventsWidgetRow> {
    val sections = groupEvents(events, nowMillis, hiddenTypes, zone)
    val upcoming = sections.upcoming.flatMap { it.second }
    return buildList {
        if (sections.now.isNotEmpty()) {
            add(EventsWidgetRow.Header("Now"))
            sections.now.forEach { event ->
                add(EventsWidgetRow.Event(event, running = true, detail = runningWhen(event, nowMillis, zone, locale)))
            }
        }
        if (upcoming.isNotEmpty()) {
            add(EventsWidgetRow.Header("Upcoming"))
            upcoming.forEach { event ->
                add(EventsWidgetRow.Event(event, running = false, detail = upcomingWhen(event, nowMillis, zone, locale)))
            }
        }
    }
}

/** "Ends Sun 3 Nov 20:00 · 2 d 4 h left", in the event's display zone. */
internal fun runningWhen(event: GameEvent, nowMillis: Long, deviceZone: ZoneId, locale: Locale): String {
    val zone = event.displayZone(deviceZone)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
    val date = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", locale)
    return "Ends ${end.format(date)} · ${durationLabel(event.endMillis - nowMillis)} left"
}

/**
 * "Sat 9 Nov 14:00 – 17:00 · in 8 days", or "Sat 9 Nov 10:00 – Mon 11 Nov 20:00 · tomorrow"
 * across days. Days are counted by calendar date in the event's display zone, so an event
 * tomorrow morning is "tomorrow" even when it is less than 24 hours away.
 */
internal fun upcomingWhen(event: GameEvent, nowMillis: Long, deviceZone: ZoneId, locale: Locale): String {
    val zone = event.displayZone(deviceZone)
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
    val date = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    val endText = if (end.toLocalDate() == start.toLocalDate()) end.format(time) else "${end.format(date)} ${end.format(time)}"
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, start.toLocalDate())
    val relative = when {
        days <= 0L -> "today, in ${durationLabel(event.startMillis - nowMillis)}"
        days == 1L -> "tomorrow"
        else -> "in $days days"
    }
    return "${start.format(date)} ${start.format(time)} – $endText · $relative"
}

/**
 * Per-widget event-type choice, stored with the app's other preferences as the types the
 * trainer hid, so event types that appear after the widget was set up show up by default.
 * Widgets set up before 1.26 stored the shown types instead; those are converted on first read.
 */
internal object EventsWidgetSettings {
    private fun legacyShownKey(appWidgetId: Int) = stringPreferencesKey("events_widget_types_$appWidgetId")
    private fun hiddenKey(appWidgetId: Int) = stringPreferencesKey("events_widget_hidden_$appWidgetId")

    private fun decode(stored: String): Set<String> = stored.split(',').filter(String::isNotBlank).toSet()

    /** The hidden types; [knownTypes] are the types a legacy allow-list is measured against. */
    suspend fun hiddenTypes(context: Context, appWidgetId: Int, knownTypes: () -> Collection<String>): Set<String> {
        val prefs = context.alertPreferencesDataStore.data.first()
        prefs[hiddenKey(appWidgetId)]?.let { return decode(it) }
        val legacy = prefs[legacyShownKey(appWidgetId)] ?: return emptySet()
        val hidden = knownTypes().toSet() - decode(legacy)
        setHiddenTypes(context, appWidgetId, hidden)
        return hidden
    }

    suspend fun setHiddenTypes(context: Context, appWidgetId: Int, types: Set<String>) {
        context.alertPreferencesDataStore.edit { prefs ->
            prefs.remove(legacyShownKey(appWidgetId))
            prefs[hiddenKey(appWidgetId)] = types.sorted().joinToString(",")
        }
    }

    suspend fun remove(context: Context, appWidgetId: Int) {
        context.alertPreferencesDataStore.edit { prefs ->
            prefs.remove(legacyShownKey(appWidgetId))
            prefs.remove(hiddenKey(appWidgetId))
        }
    }
}

/** Types seen in the calendar plus the common ones, so a quiet week still offers them. */
internal fun knownEventTypes(events: List<GameEvent>): List<String> =
    (events.map { it.eventType } + COMMON_EVENT_TYPES).distinct().sortedBy(::eventTypeName)

internal val COMMON_EVENT_TYPES = listOf(
    "community-day", "pokemon-spotlight-hour", "raid-hour", "raid-day", "raid-battles",
    "max-battles", "max-mondays", "go-battle-league", "event", "season"
)

/**
 * Home-screen list of current and upcoming game events. Tapping an event opens it in the app's
 * Events tab; the header opens the tab. The list comes from the same on-disk calendar the tab
 * keeps, refreshed by the reminder worker and whenever the tab loads.
 */
class EventsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        widgetScope.launch {
            try {
                update(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        widgetScope.launch {
            try {
                appWidgetIds.forEach { EventsWidgetSettings.remove(context.applicationContext, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_OPEN_EVENTS = 41_001
        private const val REQUEST_OPEN_EVENT = 41_002
        private const val STALE_AFTER_MILLIS = 6 * 60 * 60 * 1000L
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Re-reads the calendar into every events widget, e.g. after the app fetched it. */
        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, EventsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            widgetScope.launch { update(appContext, manager, ids) }
        }

        /** Redraws one widget, e.g. after its configuration changed. */
        internal suspend fun update(context: Context, appWidgetId: Int) =
            update(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            // The list itself only reads the stored calendar, so a missing or old one is
            // fetched here, before the list is told to reload.
            val repository = EventsRepository(context)
            if (repository.isStale(STALE_AFTER_MILLIS)) runCatching { repository.refresh(notifyWidgets = false) }
            val palette = resolveWidgetThemePalette(context)
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id, palette)) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.events_list)
        }

        internal fun eventDeepLink(eventId: String): Uri =
            Uri.parse("$DEEP_LINK_SCHEME://event/${Uri.encode(eventId)}")

        private fun buildViews(context: Context, appWidgetId: Int, palette: WidgetThemePalette): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_events)
            views.setInt(R.id.widget_events_root, "setBackgroundResource", palette.backgroundDrawable)
            views.setTextColor(R.id.events_title, palette.onSurface)
            views.setTextColor(R.id.events_empty, palette.onSurfaceVariant)
            val serviceIntent = Intent(context, EventsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // A distinct data Uri per widget, or the system hands every widget the same factory.
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.events_list, serviceIntent)
            views.setEmptyView(R.id.events_list, R.id.events_empty)

            val openEvents = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_EVENTS,
                Intent(Intent.ACTION_VIEW, Uri.parse("$DEEP_LINK_SCHEME://events"), context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.events_header, openEvents)
            views.setOnClickPendingIntent(R.id.events_empty, openEvents)

            // Rows fill in the event's link; the template must be mutable for that.
            val template = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_EVENT,
                Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.events_list, template)
            return views
        }
    }
}
