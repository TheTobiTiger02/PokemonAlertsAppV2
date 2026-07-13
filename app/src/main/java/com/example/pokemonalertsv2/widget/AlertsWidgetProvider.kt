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
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.AlertsMapActivity
import com.example.pokemonalertsv2.ui.alerts.displayCp
import com.example.pokemonalertsv2.ui.alerts.formatAlertTitle
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                updateAllSerialized(context, scheduleNext = true)
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
                updateMutex.withLock {
                    val nowMillis = System.currentTimeMillis()
                    runCatching {
                        PokemonAlertsRepository.create(context).clearExpiredAlerts(nowMillis)
                    }.onFailure { exception ->
                        Log.w(TAG, "Expired alert cleanup failed during resize", exception)
                    }
                    val builtWidget = buildViews(context, appWidgetId, appWidgetManager, nowMillis)
                    appWidgetManager.updateAppWidget(appWidgetId, builtWidget.views)
                    if (builtWidget.mode.usesCollection) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_alerts)
                    }
                }
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
            ACTION_REFRESH,
            ACTION_TIMER_TICK,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateAllSerialized(context, scheduleNext = true)
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
                        updateAllSerialized(context, scheduleNext = true)
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

    private suspend fun updateAllSerialized(context: Context, scheduleNext: Boolean = false) {
        updateMutex.withLock {
            updateAll(context, scheduleNext)
        }
    }

    private suspend fun updateAll(context: Context, scheduleNext: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelScheduledUpdate(context)
            return
        }

        val nowMillis = System.currentTimeMillis()
        try {
            runCatching {
                PokemonAlertsRepository.create(context).clearExpiredAlerts(nowMillis)
            }.onFailure { exception ->
                Log.w(TAG, "Expired alert cleanup failed", exception)
            }

            val collectionWidgetIds = mutableListOf<Int>()
            ids.forEach { id ->
                val builtWidget = buildViews(context, id, appWidgetManager, nowMillis)
                appWidgetManager.updateAppWidget(id, builtWidget.views)
                if (builtWidget.mode.usesCollection) collectionWidgetIds += id
            }
            if (collectionWidgetIds.isNotEmpty()) {
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    collectionWidgetIds.toIntArray(),
                    R.id.list_alerts
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Widget update failed", t)
        } finally {
            if (scheduleNext) scheduleNextUpdate(context)
        }
    }

    private suspend fun buildViews(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): BuiltWidget {
        val alertCounts = getAlertCounts(context, appWidgetId, nowMillis)
        val palette = resolveWidgetThemePalette(context)
        val alertCount = alertCounts.visibleCount
        lastCadenceAlertCounts[appWidgetId] = alertCounts.cadenceCount
        lastCadenceAlertCount = lastCadenceAlertCounts.values.maxOrNull()

        val mode = appWidgetManager?.let {
            layoutMode(it, appWidgetId, alertCount)
        } ?: WidgetLayoutMode.MEDIUM
        val views = when (mode) {
            WidgetLayoutMode.COMPACT -> buildCompactViews(
                context,
                appWidgetId,
                alertCount,
                alertCounts.firstAlert,
                palette,
                nowMillis
            )
            WidgetLayoutMode.MEDIUM,
            WidgetLayoutMode.LARGE_LIST -> buildFullViews(context, appWidgetId, alertCount, palette, nowMillis)
        }
        return BuiltWidget(views = views, mode = mode)
    }

    private fun buildCompactViews(
        context: Context,
        appWidgetId: Int,
        alertCount: Int,
        alert: PokemonAlert?,
        palette: WidgetThemePalette,
        nowMillis: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alerts_compact).apply {
            applyCompactWidgetPalette(palette)
        }

        // Logo/title click opens app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.header_open_app_compact, openPending)
        views.setOnClickPendingIntent(R.id.tv_title_compact, openPending)
        views.setOnClickPendingIntent(R.id.img_logo_compact, openPending)

        // Refresh button
        val refreshPending = PendingIntent.getBroadcast(
            context, 2, Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.btn_refresh_compact, refreshPending)

        views.setTextViewText(R.id.tv_compact_count, "$alertCount active")
        if (alert == null) {
            views.setTextViewText(R.id.tv_compact_alert_title, context.getString(R.string.widget_empty_title))
            views.setTextViewText(R.id.tv_compact_meta, context.getString(R.string.widget_empty_subtitle))
            views.setTextViewText(R.id.tv_compact_countdown, "")
        } else {
            val visualStyle = resolveAlertVisualStyle(alert)
            views.setTextViewText(R.id.tv_compact_alert_title, formatAlertTitle(alert))
            views.setTextViewText(
                R.id.tv_compact_meta,
                alert.displayCp?.let { "CP $it • ${visualStyle.label}" } ?: visualStyle.label
            )
            views.setTextColor(R.id.tv_compact_meta, visualStyle.category.accentArgb.toInt())
            val remaining = TimeUtils.parseEndTimeToMillis(alert.endTime)?.minus(nowMillis)
            views.setTextViewText(
                R.id.tv_compact_countdown,
                when {
                    remaining == null -> ""
                    remaining <= 0L -> context.getString(R.string.alert_expired)
                    else -> TimeUtils.formatDurationShort(remaining)
                }
            )
            val detailPending = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 8,
                AlertDetailActivity.createIntent(context, alert),
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
            )
            views.setOnClickPendingIntent(R.id.compact_alert_content, detailPending)
        }

        return views
    }

    private fun buildFullViews(
        context: Context,
        appWidgetId: Int,
        alertCount: Int,
        palette: WidgetThemePalette,
        nowMillis: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alerts).apply {
            applyFullWidgetPalette(palette)
        }

        // Title/logo click opens app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.header_open_app, openPending)
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
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMillis))
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

    private fun layoutMode(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        alertCount: Int
    ): WidgetLayoutMode {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return widgetLayoutModeForSize(minWidth, minHeight, alertCount)
    }

    private data class WidgetAlertCounts(
        val visibleCount: Int,
        val cadenceCount: Int,
        val alerts: List<PokemonAlert>
    ) {
        val firstAlert: PokemonAlert? get() = alerts.firstOrNull()
    }

    private data class BuiltWidget(
        val views: RemoteViews,
        val mode: WidgetLayoutMode
    )

    private suspend fun getAlertCounts(
        context: Context,
        appWidgetId: Int,
        nowMillis: Long
    ): WidgetAlertCounts {
        return try {
            val loadedAlerts = WidgetAlertLoader.load(
                context = context,
                appWidgetId = appWidgetId,
                nowMillis = nowMillis
            )
            WidgetAlertCounts(
                visibleCount = loadedAlerts.alerts.size,
                cadenceCount = loadedAlerts.cadenceAlerts.size,
                alerts = loadedAlerts.alerts
            )
        } catch (_: Throwable) {
            WidgetAlertCounts(visibleCount = 0, cadenceCount = 0, alerts = emptyList())
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
            if (shouldScheduleExactWidgetAlarm(hasActiveAlerts, canScheduleExact(am))) {
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
        private const val MIN_UPDATE_DELAY_MS = 1L
        private val UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)
        private val IDLE_UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)
        private val lastCadenceAlertCounts = ConcurrentHashMap<Int, Int>()
        private val updateMutex = Mutex()
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
                ?.let { expirationMillis -> expirationMillis - nowMillis }
                ?.coerceAtLeast(MIN_UPDATE_DELAY_MS)

            return minOf(UPDATE_INTERVAL_MS, expiryDelay ?: UPDATE_INTERVAL_MS)
        }

        internal fun sendUpdateBroadcast(context: Context) {
            context.sendBroadcast(Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH })
        }

        fun requestUpdate(context: Context) {
            WidgetUpdateCoordinator.request(context)
        }

        fun updateFromWorker(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            WidgetUpdateCoordinator.request(context)
        }
    }
}

@VisibleForTesting
internal fun shouldScheduleExactWidgetAlarm(
    hasActiveAlerts: Boolean,
    canScheduleExactAlarms: Boolean
): Boolean = hasActiveAlerts && canScheduleExactAlarms

@VisibleForTesting
internal fun shouldUseCompactWidgetLayout(minWidthDp: Int, minHeightDp: Int): Boolean {
    return widgetLayoutModeForSize(minWidthDp, minHeightDp, alertCount = 0) ==
        WidgetLayoutMode.COMPACT
}

internal enum class WidgetLayoutMode {
    COMPACT,
    MEDIUM,
    LARGE_LIST
}

internal val WidgetLayoutMode.usesCollection: Boolean
    get() = this == WidgetLayoutMode.MEDIUM || this == WidgetLayoutMode.LARGE_LIST

@VisibleForTesting
internal fun widgetLayoutModeForSize(
    minWidthDp: Int,
    minHeightDp: Int,
    alertCount: Int
): WidgetLayoutMode {
    if (minWidthDp <= 0 || minHeightDp <= 0) return WidgetLayoutMode.COMPACT
    if (minWidthDp < 300 || minHeightDp < 190) return WidgetLayoutMode.COMPACT
    if (minHeightDp < 280) return WidgetLayoutMode.MEDIUM
    return WidgetLayoutMode.LARGE_LIST
}
