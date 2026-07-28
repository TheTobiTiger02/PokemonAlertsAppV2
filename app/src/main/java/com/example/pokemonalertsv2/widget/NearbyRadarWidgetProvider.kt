package com.example.pokemonalertsv2.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun radarWidgetActionIntent(
    context: Context,
    appWidgetId: Int,
    action: String
): Intent = Intent(context, NearbyRadarWidgetProvider::class.java).apply {
    this.action = action
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

class NearbyRadarWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextUpdate(context, hasAlerts = false)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledUpdate(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgetsAsync(
            context = context,
            manager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            action = RadarAction.RENDER
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = when (intent.action) {
            ACTION_REFRESH, ACTION_TIMER_TICK -> RadarAction.RENDER
            ACTION_NEXT_ALERT -> RadarAction.NEXT
            ACTION_TOGGLE_VIEW -> RadarAction.TOGGLE_VIEW
            else -> return
        }
        val manager = AppWidgetManager.getInstance(context)
        val requestedWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val appWidgetIds = if (requestedWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            intArrayOf(requestedWidgetId)
        } else {
            manager.getAppWidgetIds(ComponentName(context, NearbyRadarWidgetProvider::class.java))
        }
        if (appWidgetIds.isEmpty()) return

        if (intent.action == ACTION_REFRESH) {
            appWidgetIds.forEach { id ->
                manager.partiallyUpdateAppWidget(
                    id,
                    RemoteViews(context.packageName, R.layout.widget_nearby_radar).apply {
                        setTextViewText(R.id.radar_location_status, "Refreshing…")
                    }
                )
            }
        }
        updateWidgetsAsync(context, manager, appWidgetIds, action)
        if (intent.action == ACTION_REFRESH) {
            AlertsWidgetProvider.sendUpdateBroadcast(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetsAsync(
            context = context,
            manager = appWidgetManager,
            appWidgetIds = intArrayOf(appWidgetId),
            action = RadarAction.RENDER
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            WidgetConfigurationStore.remove(context, it)
            RadarWidgetStateStore.remove(context, it)
            WidgetAlertSnapshotStore.remove(it)
        }
        val remaining = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NearbyRadarWidgetProvider::class.java))
        if (remaining.isEmpty()) cancelScheduledUpdate(context)
    }

    private fun updateWidgetsAsync(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        action: RadarAction
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateMutex.withLock {
                    appWidgetIds.forEach { appWidgetId ->
                        update(context, manager, appWidgetId, action)
                    }
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "Radar widget update failed", throwable)
            } finally {
                val hasAlerts = appWidgetIds.any { id ->
                    WidgetAlertSnapshotStore.currentRenderSnapshot(id)?.alerts?.isNotEmpty() == true
                }
                scheduleNextUpdate(context, hasAlerts)
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        action: RadarAction
    ) {
        val loaded = WidgetAlertLoader.load(
            context = context,
            appWidgetId = appWidgetId,
            highAccuracyLocation = action == RadarAction.RENDER
        )
        val renderSnapshot = WidgetAlertSnapshotStore.currentRenderSnapshot(
            appWidgetId = appWidgetId,
            expectedGeneration = loaded.generation
        )
        val orderedAlerts = orderRadarAlerts(
            alerts = loaded.alerts,
            origin = loaded.location?.let(WidgetAlertFilter::originFrom),
            walkingRoutes = renderSnapshot?.walkingRoutes.orEmpty()
        )
        val storedState = RadarWidgetStateStore.get(context, appWidgetId)
        val requestedState = when (action) {
            RadarAction.RENDER -> storedState
            RadarAction.NEXT -> advanceRadarSelection(orderedAlerts, storedState)
            RadarAction.TOGGLE_VIEW -> storedState.copy(
                viewMode = when (storedState.viewMode) {
                    RadarViewMode.FOCUS -> RadarViewMode.OVERVIEW
                    RadarViewMode.OVERVIEW -> RadarViewMode.FOCUS
                }
            )
        }
        val selection = resolveRadarSelection(orderedAlerts, requestedState)
        if (selection.state != storedState) {
            RadarWidgetStateStore.save(context, appWidgetId, selection.state)
        }

        val dimensions = radarBitmapDimensions(manager, appWidgetId)
        val renderDensity = 2f
        val mapSnapshot = WidgetRadarImageRenderer.render(
            context = context,
            input = RadarRenderInput(
                alerts = orderedAlerts,
                location = loaded.location,
                widthPx = dimensions.first,
                heightPx = dimensions.second,
                density = renderDensity,
                insets = RadarRenderInsets(
                    leftPx = (18 * renderDensity).toInt(),
                    topPx = (64 * renderDensity).toInt(),
                    rightPx = (18 * renderDensity).toInt(),
                    bottomPx = (80 * renderDensity).toInt()
                ),
                selectedAlertId = selection.selectedAlert?.uniqueId,
                viewMode = selection.state.viewMode
            )
        )
        val views = RemoteViews(context.packageName, R.layout.widget_nearby_radar)
        views.setImageViewBitmap(R.id.radar_map, mapSnapshot.bitmap)
        views.setTextViewText(R.id.radar_count, loaded.alerts.size.toString())
        views.setTextViewText(
            R.id.radar_location_status,
            buildLocationStatus(context, loaded, mapSnapshot)
        )
        bindHeaderActions(context, views, appWidgetId, selection.state)
        bindMapActions(context, views, appWidgetId)
        bindSelectedAlert(
            context = context,
            views = views,
            appWidgetId = appWidgetId,
            loaded = loaded,
            selection = selection,
            walkingRoutes = renderSnapshot?.walkingRoutes.orEmpty(),
            mapAvailable = mapSnapshot.mapAvailable
        )
        manager.updateAppWidget(appWidgetId, views)
    }

    private fun bindMapActions(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int
    ) {
        val openMapIntent = MainActivity.createMapIntent(context)
        views.setOnClickPendingIntent(
            R.id.radar_map,
            PendingIntent.getActivity(
                context,
                appWidgetId * REQUEST_CODE_MULTIPLIER,
                openMapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        )
        views.setOnClickPendingIntent(
            R.id.radar_location_status,
            PendingIntent.getActivity(
                context,
                appWidgetId * REQUEST_CODE_MULTIPLIER + 5,
                openMapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        )
    }

    private fun bindHeaderActions(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        state: RadarWidgetState
    ) {
        views.setOnClickPendingIntent(
            R.id.radar_refresh,
            radarBroadcastPendingIntent(
                context = context,
                appWidgetId = appWidgetId,
                requestOffset = 1,
                action = ACTION_REFRESH
            )
        )
        val showOverview = state.viewMode == RadarViewMode.FOCUS
        views.setImageViewResource(
            R.id.radar_toggle_view,
            if (showOverview) R.drawable.ic_fit_map else R.drawable.ic_my_location
        )
        views.setContentDescription(
            R.id.radar_toggle_view,
            context.getString(
                if (showOverview) {
                    R.string.widget_radar_show_overview_cd
                } else {
                    R.string.widget_radar_show_focus_cd
                }
            )
        )
        views.setOnClickPendingIntent(
            R.id.radar_toggle_view,
            radarBroadcastPendingIntent(
                context = context,
                appWidgetId = appWidgetId,
                requestOffset = 6,
                action = ACTION_TOGGLE_VIEW
            )
        )
    }

    private suspend fun bindSelectedAlert(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        loaded: WidgetAlertLoader.LoadedAlerts,
        selection: RadarSelection,
        walkingRoutes: Map<String, com.example.pokemonalertsv2.util.WalkingRouteInfo>,
        mapAvailable: Boolean
    ) {
        val selectedAlert = selection.selectedAlert
        if (selectedAlert == null) {
            views.setViewVisibility(R.id.radar_alert_image, View.GONE)
            views.setTextViewText(
                R.id.radar_alert_title,
                when (loaded.state) {
                    WidgetLoadState.ERROR -> "Refresh failed"
                    WidgetLoadState.LOCATION_UNAVAILABLE -> "Location unavailable"
                    else -> context.getString(R.string.widget_empty_title)
                }
            )
            views.setTextViewText(
                R.id.radar_alert_meta,
                if (mapAvailable) {
                    context.getString(R.string.widget_empty_subtitle)
                } else {
                    "Map unavailable"
                }
            )
            views.setViewVisibility(R.id.radar_next, View.GONE)
            views.setViewVisibility(R.id.radar_navigate, View.GONE)
            views.setViewVisibility(R.id.radar_dismiss, View.GONE)
            return
        }

        views.setViewVisibility(R.id.radar_alert_image, View.VISIBLE)
        views.setImageViewBitmap(
            R.id.radar_alert_image,
            WidgetAlertImageRenderer.render(
                context = context,
                alert = selectedAlert,
                sizeDp = 48,
                palette = resolveWidgetThemePalette(context)
            )
        )
        val remaining = TimeUtils.parseEndTimeToMillis(selectedAlert.endTime)
            ?.minus(System.currentTimeMillis())
        val routeInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = loaded.location?.let { location ->
                val latitude = selectedAlert.latitude ?: return@let null
                val longitude = selectedAlert.longitude ?: return@let null
                WalkingRouteUtils.straightLineDistanceMeters(
                    originLatitude = location.latitude,
                    originLongitude = location.longitude,
                    destinationLatitude = latitude,
                    destinationLongitude = longitude
                )
            },
            routeInfo = walkingRoutes[selectedAlert.uniqueId]
        )
        val countdown = remaining?.takeIf { it > 0 }?.let(TimeUtils::formatDurationShort)
        val routeText = listOfNotNull(
            routeInfo.distanceText,
            routeInfo.walkingText
        ).joinToString(" · ").takeIf(String::isNotBlank)
        views.setTextViewText(R.id.radar_alert_title, selectedAlert.name)
        views.setTextViewText(
            R.id.radar_alert_meta,
            listOfNotNull(
                "${selection.selectedIndex + 1} of ${selection.alerts.size}",
                countdown,
                routeText
            ).joinToString(" · ")
        )

        val showNext = selection.alerts.size > 1
        views.setViewVisibility(R.id.radar_next, if (showNext) View.VISIBLE else View.GONE)
        if (showNext) {
            views.setContentDescription(
                R.id.radar_next,
                context.getString(
                    R.string.widget_radar_next_cd,
                    selection.selectedIndex + 1,
                    selection.alerts.size
                )
            )
            views.setOnClickPendingIntent(
                R.id.radar_next,
                radarBroadcastPendingIntent(
                    context = context,
                    appWidgetId = appWidgetId,
                    requestOffset = 7,
                    action = ACTION_NEXT_ALERT
                )
            )
        }
        views.setViewVisibility(R.id.radar_navigate, View.VISIBLE)
        views.setViewVisibility(R.id.radar_dismiss, View.VISIBLE)
        views.setOnClickPendingIntent(
            R.id.radar_summary,
            PendingIntent.getActivity(
                context,
                appWidgetId * REQUEST_CODE_MULTIPLIER + 2,
                AlertDetailActivity.createIntent(context, selectedAlert, returnToAlerts = true),
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        )
        views.setOnClickPendingIntent(
            R.id.radar_navigate,
            PendingIntent.getBroadcast(
                context,
                appWidgetId * REQUEST_CODE_MULTIPLIER + 3,
                Intent(context, AlertsWidgetProvider::class.java).apply {
                    action = AlertsWidgetProvider.ACTION_NAVIGATE
                    putExtra(AlertsWidgetProvider.EXTRA_NAV_LAT, selectedAlert.latitude)
                    putExtra(AlertsWidgetProvider.EXTRA_NAV_LNG, selectedAlert.longitude)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        )
        views.setOnClickPendingIntent(
            R.id.radar_dismiss,
            PendingIntent.getBroadcast(
                context,
                appWidgetId * REQUEST_CODE_MULTIPLIER + 4,
                Intent(context, AlertsWidgetProvider::class.java).apply {
                    action = AlertsWidgetProvider.ACTION_DISMISS_WIDGET
                    putExtra(AlertsWidgetProvider.EXTRA_DISMISS_ALERT_ID, selectedAlert.uniqueId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        )
    }

    private fun buildLocationStatus(
        context: Context,
        loaded: WidgetAlertLoader.LoadedAlerts,
        snapshot: RadarSnapshot
    ): String = buildString {
        append(
            when {
                loaded.location != null -> radarLocationAgeLabel(loaded.location)
                !hasLocationPermission(context) -> "Enable location · tap here"
                else -> "Location unavailable · tap to retry"
            }
        )
        if (!snapshot.mapAvailable) append(" · Map unavailable")
    }

    private fun radarBroadcastPendingIntent(
        context: Context,
        appWidgetId: Int,
        requestOffset: Int,
        action: String
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId * REQUEST_CODE_MULTIPLIER + requestOffset,
        radarWidgetActionIntent(context, appWidgetId, action),
        PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
    )

    private fun radarBitmapDimensions(
        manager: AppWidgetManager,
        appWidgetId: Int
    ): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: 280
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            .takeIf { it > 0 } ?: 150
        val widthPx = (widthDp * 2f).toInt().coerceIn(480, 800)
        val heightPx = (heightDp * 2f).toInt().coerceIn(260, 480)
        return widthPx to heightPx
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun scheduleNextUpdate(context: Context, hasAlerts: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val delay = if (hasAlerts) 60_000L else 15 * 60_000L
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TIMER_REQUEST_CODE,
            Intent(context, NearbyRadarWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val triggerAt = SystemClock.elapsedRealtime() + delay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pendingIntent)
        }
    }

    private fun cancelScheduledUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TIMER_REQUEST_CODE,
            Intent(context, NearbyRadarWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK },
            PendingIntent.FLAG_NO_CREATE or immutableFlag()
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private enum class RadarAction {
        RENDER,
        NEXT,
        TOGGLE_VIEW
    }

    companion object {
        internal const val ACTION_REFRESH =
            "com.example.pokemonalertsv2.widget.ACTION_REFRESH_RADAR"
        internal const val ACTION_TIMER_TICK =
            "com.example.pokemonalertsv2.widget.ACTION_TIMER_TICK_RADAR"
        internal const val ACTION_NEXT_ALERT =
            "com.example.pokemonalertsv2.widget.ACTION_NEXT_ALERT_RADAR"
        internal const val ACTION_TOGGLE_VIEW =
            "com.example.pokemonalertsv2.widget.ACTION_TOGGLE_VIEW_RADAR"
        private const val TIMER_REQUEST_CODE = 4026
        private const val REQUEST_CODE_MULTIPLIER = 10
        private const val TAG = "NearbyRadarWidget"
        private val updateMutex = Mutex()

        internal fun sendUpdateBroadcast(context: Context) {
            context.sendBroadcast(
                Intent(context, NearbyRadarWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
            )
        }

        private fun immutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
    }
}
