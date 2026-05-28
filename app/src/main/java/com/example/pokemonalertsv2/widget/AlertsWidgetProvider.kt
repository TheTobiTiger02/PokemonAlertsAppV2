package com.example.pokemonalertsv2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import androidx.core.app.AlarmManagerCompat
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.AlertsMapActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AlertsWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledUpdate(context)
        lastCadenceAlertCounts.clear()
        lastCadenceAlertCount = null
        WidgetAlertSnapshotStore.clear()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAll(context, scheduleNext = true)
            } finally {
                try { pending.finish() } catch (_: Throwable) {}
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Clean up per-widget filter prefs
        appWidgetIds.forEach { id -> WidgetFilterPrefs.removeFilters(context, id) }
        appWidgetIds.forEach { id -> lastCadenceAlertCounts.remove(id) }
        appWidgetIds.forEach { id -> WidgetAlertSnapshotStore.remove(id) }
        lastCadenceAlertCount = lastCadenceAlertCounts.values.maxOrNull()
        val remainingIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        if (remainingIds.isEmpty()) cancelScheduledUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Rebuild views for this widget (may switch between compact/full)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    PokemonAlertsRepository.create(context).clearExpiredAlerts()
                }.onFailure { exception ->
                    Log.w(TAG, "Expired alert cleanup failed during resize", exception)
                }
                val views = buildViews(context, appWidgetId, appWidgetManager)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_alerts)
            } catch (t: Throwable) {
                Log.w(TAG, "Widget resize update failed", t)
            } finally {
                try { pending.finish() } catch (_: Throwable) {}
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH, ACTION_TIMER_TICK -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateAll(context, scheduleNext = true)
                    } finally {
                        try { pending.finish() } catch (_: Throwable) {}
                    }
                }
            }
            ACTION_DISMISS_WIDGET -> {
                val alertId = intent.getStringExtra(EXTRA_DISMISS_ALERT_ID) ?: return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefs = AlertPreferences(context.alertPreferencesDataStore)
                        prefs.addDismissedAlert(alertId)
                        updateAll(context, scheduleNext = true)
                    } finally {
                        try { pending.finish() } catch (_: Throwable) {}
                    }
                }
            }
            ACTION_NAVIGATE -> {
                val lat = intent.getDoubleExtra(EXTRA_NAV_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_NAV_LNG, Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w")).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(mapIntent)
                    } catch (_: Throwable) {
                        val browserIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        try { context.startActivity(browserIntent) } catch (_: Throwable) {}
                    }
                }
            }
            ACTION_ITEM_CLICK -> {
                // Forward to AlertDetailActivity
                val detailIntent = Intent(context, AlertDetailActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Copy all extras from the fill-in intent (contains alert data)
                    putExtras(intent)
                }
                try { context.startActivity(detailIntent) } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun updateAll(context: Context, scheduleNext: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelScheduledUpdate(context)
            return
        }

        try {
            runCatching {
                PokemonAlertsRepository.create(context).clearExpiredAlerts()
            }.onFailure { exception ->
                Log.w(TAG, "Expired alert cleanup failed", exception)
            }

            ids.forEach { id ->
                val views = buildViews(context, id, appWidgetManager)
                appWidgetManager.updateAppWidget(id, views)
            }
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.list_alerts)
        } catch (t: Throwable) {
            Log.w(TAG, "Widget update failed", t)
        } finally {
            if (scheduleNext) scheduleNextUpdate(context)
        }
    }

    private suspend fun buildViews(context: Context, appWidgetId: Int, appWidgetManager: AppWidgetManager? = null): RemoteViews {
        // Detect compact mode based on widget dimensions
        val isCompact = appWidgetManager?.let { isCompactMode(it, appWidgetId) } ?: false

        val alertCounts = getAlertCounts(context, appWidgetId)
        val alertCount = alertCounts.visibleCount
        lastCadenceAlertCounts[appWidgetId] = alertCounts.cadenceCount
        lastCadenceAlertCount = lastCadenceAlertCounts.values.maxOrNull()

        if (isCompact) {
            return buildCompactViews(context, appWidgetId, alertCount)
        }
        return buildFullViews(context, appWidgetId, alertCount)
    }

    private fun buildCompactViews(context: Context, appWidgetId: Int, alertCount: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alerts_compact)

        // Logo/title click opens app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.tv_title_compact, openPending)
        views.setOnClickPendingIntent(R.id.img_logo_compact, openPending)

        // Refresh button
        val refreshPending = PendingIntent.getBroadcast(
            context, 2, Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.btn_refresh_compact, refreshPending)

        // Count
        views.setTextViewText(R.id.tv_compact_count, alertCount.toString())

        return views
    }

    private fun buildFullViews(context: Context, appWidgetId: Int, alertCount: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alerts)

        // Title/logo click opens app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.tv_title, openPending)
        views.setOnClickPendingIntent(R.id.img_logo, openPending)

        // Map Button click
        val mapIntent = Intent(context, AlertsMapActivity::class.java)
        val mapPending = PendingIntent.getActivity(
            context, 1, mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.btn_map, mapPending)

        // Refresh button
        val refreshPending = PendingIntent.getBroadcast(
            context, 2, Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)

        // Active count badge
        if (alertCount > 0) {
            views.setTextViewText(R.id.tv_count, context.getString(R.string.widget_active_count, alertCount))
            views.setViewVisibility(R.id.tv_count, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tv_count, View.GONE)
        }

        // Last updated
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        views.setTextViewText(R.id.tv_last_updated, context.getString(R.string.widget_updated_at, time))

        // Empty view management: show/hide custom empty container based on alert count
        if (alertCount == 0) {
            views.setViewVisibility(R.id.empty_container, View.VISIBLE)
            views.setViewVisibility(R.id.list_alerts, View.GONE)
        } else {
            views.setViewVisibility(R.id.empty_container, View.GONE)
            views.setViewVisibility(R.id.list_alerts, View.VISIBLE)
        }

        // Remote adapter with unique data per widget id
        val svcIntent = Intent(context, AlertsWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.list_alerts, svcIntent)

        // PendingIntent template → all item interactions route through the provider as broadcasts
        val templateIntent = Intent(context, AlertsWidgetProvider::class.java)
        val broadcastTemplate = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            templateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setPendingIntentTemplate(R.id.list_alerts, broadcastTemplate)

        return views
    }

    private fun isCompactMode(appWidgetManager: AppWidgetManager, appWidgetId: Int): Boolean {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        // Consider compact if height is less than ~120dp (roughly 2 list items)
        return minHeight in 1..119
    }

    private data class WidgetAlertCounts(
        val visibleCount: Int,
        val cadenceCount: Int
    )

    private suspend fun getAlertCounts(context: Context, appWidgetId: Int): WidgetAlertCounts {
        return try {
            val loadedAlerts = WidgetAlertLoader.load(context, appWidgetId)
            WidgetAlertCounts(
                visibleCount = loadedAlerts.alerts.size,
                cadenceCount = loadedAlerts.cadenceAlerts.size
            )
        } catch (_: Throwable) {
            WidgetAlertCounts(visibleCount = 0, cadenceCount = 0)
        }
    }

    private fun scheduleNextUpdate(context: Context) {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelScheduledUpdate(context)
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK }
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_CANCEL_CURRENT or mutableFlag())
        val nowMillis = System.currentTimeMillis()
        val hasActiveAlerts = (lastCadenceAlertCount ?: 1) > 0
        val delayMillis = calculateNextUpdateDelay(
            nowMillis = nowMillis,
            hasActiveAlerts = hasActiveAlerts,
            nextExpirationMillis = WidgetAlertSnapshotStore.nextExpirationMillis(nowMillis)
        )
        val triggerAt = nowMillis + delayMillis

        try {
            if (hasActiveAlerts && canScheduleExact(am)) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                AlarmManagerCompat.setAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (exception: SecurityException) {
            Log.w(TAG, "Exact widget alarm denied; using inexact fallback.", exception)
            runCatching {
                AlarmManagerCompat.setAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }.onFailure {
                Log.w(TAG, "Failed to schedule fallback widget alarm.", it)
            }
        }
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun cancelScheduledUpdate(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK }
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or mutableFlag())
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    companion object {
        private const val TAG = "AlertsWidget"
        private const val ACTION_REFRESH = "com.example.pokemonalertsv2.widget.ACTION_REFRESH"
        private const val ACTION_TIMER_TICK = "com.example.pokemonalertsv2.widget.ACTION_TIMER_TICK"
        const val ACTION_DISMISS_WIDGET = "com.example.pokemonalertsv2.widget.ACTION_DISMISS_WIDGET"
        const val ACTION_NAVIGATE = "com.example.pokemonalertsv2.widget.ACTION_NAVIGATE"
        const val ACTION_ITEM_CLICK = "com.example.pokemonalertsv2.widget.ACTION_ITEM_CLICK"
        const val EXTRA_DISMISS_ALERT_ID = "extra_dismiss_alert_id"
        const val EXTRA_NAV_LAT = "extra_nav_lat"
        const val EXTRA_NAV_LNG = "extra_nav_lng"
        private const val REQUEST_CODE = 2025
        private val MIN_UPDATE_DELAY_MS = TimeUnit.SECONDS.toMillis(1)
        private val EXPIRY_UPDATE_BUFFER_MS = TimeUnit.SECONDS.toMillis(1)
        private val UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)
        private val IDLE_UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)
        private val lastCadenceAlertCounts = ConcurrentHashMap<Int, Int>()
        @Volatile
        private var lastCadenceAlertCount: Int? = null

        private fun mutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        @VisibleForTesting
        internal fun calculateNextUpdateDelay(
            nowMillis: Long,
            hasActiveAlerts: Boolean,
            nextExpirationMillis: Long?
        ): Long {
            if (!hasActiveAlerts) return IDLE_UPDATE_INTERVAL_MS

            val expiryDelay = nextExpirationMillis
                ?.let { expirationMillis -> expirationMillis - nowMillis + EXPIRY_UPDATE_BUFFER_MS }
                ?.coerceAtLeast(MIN_UPDATE_DELAY_MS)

            return minOf(UPDATE_INTERVAL_MS, expiryDelay ?: UPDATE_INTERVAL_MS)
        }

        fun requestUpdate(context: Context) {
            context.sendBroadcast(Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH })
        }

        fun updateFromWorker(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            requestUpdate(context)
        }
    }
}
