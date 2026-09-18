package com.example.pokemonalertsv2.catchroutes

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.tracking.ArrivalLocationSourceFactory
import com.example.pokemonalertsv2.tracking.DefaultArrivalLocationSourceFactory
import com.example.pokemonalertsv2.tracking.ArrivalCadence
import com.example.pokemonalertsv2.tracking.FloatingWindow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class CatchRouteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { CatchRouteController.get(this) }
    private val locationSource by lazy { locationSourceFactory.create(applicationContext) }
    private val window by lazy { CatchRouteWindow(this) }
    private var collectionJob: Job? = null
    private var latestStartId = 0
    private var geometryRestored = false
    private var geometrySaveJob: Job? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Catch routes", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, notification(controller.session.value))
        when (intent?.action) {
            "stop" -> scope.launch { controller.stop() }
            "pause" -> controller.pause()
            "map" -> showWindow()
        }
        if (collectionJob?.isActive != true) {
            collectionJob = scope.launch {
                controller.ready()
                if (controller.session.value == null || controller.session.value?.finished == true) { stopSelfResult(latestStartId); return@launch }
                if (ContextCompat.checkSelfPermission(this@CatchRouteService, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    controller.message.value = "Precise location permission is required for guidance."
                    stopSelfResult(latestStartId); return@launch
                }
                locationSource.start(ArrivalCadence.Approach, { fix -> scope.launch { controller.onFix(fix) } }, { available ->
                    if (!available) controller.message.value = "Waiting for location. Visits are paused until fresh fixes arrive."
                })
                combine(controller.session, controller.location, controller.message, controller.outOfDate, controller.recalculating) { session, point, _, outOfDate, replanning ->
                    GuidanceState(session, point, outOfDate, replanning)
                }.collectLatest { (s, point, outOfDate, replanning) ->
                    if (s == null || s.finished) {
                        if (controller.session.value == s) { locationSource.stop(); stopSelfResult(latestStartId) }
                        return@collectLatest
                    }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(s))
                    window.update(s, point, outOfDate, replanning)
                }
            }
        }
        return START_STICKY
    }
    private fun readout(s: CatchSession?): String = if (s == null) "Restoring route…" else
        "${if (s.paused) "Paused · " else ""}${s.availabilityReadout}"
    private fun notification(s: CatchSession?): Notification {
        val open = PendingIntent.getActivity(this, 70, Intent(this, CatchRoutesActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(s?.itinerary?.settings?.name ?: "Catch route").setContentText(readout(s))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${readout(s)}\nPotential encounters; walking time only. ${controller.message.value.orEmpty()}"))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        listOf("pause" to if (s?.paused == true) "Resume" else "Pause", "map" to "Map").forEachIndexed { i, (action, label) ->
            builder.addAction(0, label, PendingIntent.getService(this, 71 + i, Intent(this, CatchRouteService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }

    /**
     * Opens the floating window, once its saved geometry is back: putting it where the trainer
     * left it after it is already on screen would make it jump in front of them.
     */
    private fun showWindow() {
        if (window.isShowing) return
        if (!FloatingWindow.canDraw(this)) {
            controller.message.value = "Allow display over other apps to use the floating map."
            return
        }
        if (!geometryRestored) {
            geometryRestored = true
            scope.launch {
                runCatching { preferences().getCatchRouteWindowGeometry() }
                    .getOrNull()?.let { window.restoreGeometry(it.x, it.y, it.width, it.height) }
                showWindow()
            }
            return
        }
        val session = controller.session.value ?: return
        window.onPause = { controller.pause() }
        window.onReplan = { controller.recalculate() }
        window.onClose = { window.hide() }
        window.onGeometryChanged = { x, y, width, height ->
            // Debounced: a drag reports on every release, and two drags in a row
            // should not queue two writes.
            geometrySaveJob?.cancel()
            geometrySaveJob = scope.launch {
                delay(GEOMETRY_SAVE_DELAY_MS)
                runCatching { preferences().updateCatchRouteWindowGeometry(x, y, width, height) }
            }
        }
        if (!window.show(session, controller.location.value)) {
            controller.message.value = "The floating map could not be opened."
        }
    }

    private fun preferences() = AlertPreferences(applicationContext.alertPreferencesDataStore)

    override fun onDestroy() {
        scope.cancel()
        locationSource.stop()
        window.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
    /** Everything the notification and the floating window draw from. */
    private data class GuidanceState(val session: CatchSession?, val point: CatchPoint?, val outOfDate: Boolean, val replanning: Boolean)

    companion object {
        internal const val NOTIFICATION_ID = 7350
        private const val CHANNEL = "catch_routes"
        private const val GEOMETRY_SAVE_DELAY_MS = 400L

        @Volatile
        internal var locationSourceFactory: ArrivalLocationSourceFactory =
            DefaultArrivalLocationSourceFactory
    }
}
