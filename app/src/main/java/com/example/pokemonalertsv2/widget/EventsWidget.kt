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
import java.util.Locale

/** One line of the events widget's list. */
internal sealed interface EventsWidgetRow {
    data class Header(val title: String) : EventsWidgetRow
    data class Event(val event: GameEvent, val running: Boolean, val detail: String) : EventsWidgetRow
}

/**
 * What the widget lists: running events under "Now" (ending soonest first), then upcoming ones
 * under "Upcoming" (starting soonest first), limited to [shownTypes] when the trainer picked
 * any. The same grouping as the Events tab, so both agree on what is running.
 */
internal fun eventsWidgetRows(
    events: List<GameEvent>,
    nowMillis: Long,
    shownTypes: Set<String>?,
    zone: ZoneId = ZoneId.systemDefault(),
    maxUpcoming: Int = EVENTS_WIDGET_MAX_UPCOMING
): List<EventsWidgetRow> {
    val hidden = if (shownTypes == null) emptySet() else events.map { it.eventType }.toSet() - shownTypes
    val sections = groupEvents(events, nowMillis, hidden, zone)
    val upcoming = sections.upcoming.flatMap { it.second }.take(maxUpcoming)
    return buildList {
        if (sections.now.isNotEmpty()) {
            add(EventsWidgetRow.Header("Now"))
            sections.now.forEach { event ->
                add(EventsWidgetRow.Event(event, running = true, detail = "Ends in ${durationLabel(event.endMillis - nowMillis)}"))
            }
        }
        if (upcoming.isNotEmpty()) {
            add(EventsWidgetRow.Header("Upcoming"))
            upcoming.forEach { event ->
                add(EventsWidgetRow.Event(event, running = false, detail = upcomingWhen(event, zone)))
            }
        }
    }
}

/** "Sat 14:00 – 17:00", or "Sat 10:00 – Mon 20:00" across days, in the event's display zone. */
private fun upcomingWhen(event: GameEvent, deviceZone: ZoneId): String {
    val zone = event.displayZone(deviceZone)
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
    val day = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val endText = if (end.toLocalDate() == start.toLocalDate()) end.format(time) else "${end.format(day)} ${end.format(time)}"
    return "${start.format(day)} ${start.format(time)} – $endText"
}

internal const val EVENTS_WIDGET_MAX_UPCOMING = 12

/** Per-widget event-type choice, stored with the app's other preferences. Null means every type. */
internal object EventsWidgetSettings {
    private fun key(appWidgetId: Int) = stringPreferencesKey("events_widget_types_$appWidgetId")

    suspend fun shownTypes(context: Context, appWidgetId: Int): Set<String>? =
        context.alertPreferencesDataStore.data.first()[key(appWidgetId)]
            ?.let { stored -> stored.split(',').filter(String::isNotBlank).toSet() }

    suspend fun setShownTypes(context: Context, appWidgetId: Int, types: Set<String>?) {
        context.alertPreferencesDataStore.edit { prefs ->
            if (types == null) prefs.remove(key(appWidgetId)) else prefs[key(appWidgetId)] = types.sorted().joinToString(",")
        }
    }

    suspend fun remove(context: Context, appWidgetId: Int) = setShownTypes(context, appWidgetId, null)
}

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
