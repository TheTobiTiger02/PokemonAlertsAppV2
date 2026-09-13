package com.example.pokemonalertsv2.catchroutes

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.tracking.ArrivalLocationSourceFactory
import com.example.pokemonalertsv2.tracking.DefaultArrivalLocationSourceFactory
import com.example.pokemonalertsv2.tracking.ArrivalCadence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class CatchRouteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { CatchRouteController.get(this) }
    private val locationSource by lazy { locationSourceFactory.create(applicationContext) }
    private var collectionJob: Job? = null
    private var latestStartId = 0
    private var overlay: LinearLayout? = null
    private var overlayMap: CatchRouteMapView? = null
    private var overlayText: TextView? = null
    private var overlayPause: Button? = null
    private val manager by lazy { getSystemService(WindowManager::class.java) }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Catch routes", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, notification(controller.session.value))
        when (intent?.action) {
            "stop" -> scope.launch { controller.stop() }
            "pause" -> controller.pause()
            "catch" -> controller.caught(1)
            "map" -> showOverlay()
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
                combine(controller.session, controller.location, controller.message) { session, point, message -> Triple(session, point, message) }.collectLatest { (s, point, _) ->
                    if (s == null || s.finished) {
                        if (controller.session.value == s) { locationSource.stop(); stopSelfResult(latestStartId) }
                        return@collectLatest
                    }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(s))
                    overlayMap?.update(s.itinerary.settings, s.itinerary, point)
                    overlayText?.text = readout(s)
                    overlayPause?.text = if (s.paused) "Resume" else "Pause"
                }
            }
        }
        return START_STICKY
    }
    private fun readout(s: CatchSession?): String = if (s == null) "Restoring route…" else
        "${if (s.paused) "Paused · " else ""}${s.remaining.size} remaining · ${s.caught} caught"
    private fun notification(s: CatchSession?): Notification {
        val open = PendingIntent.getActivity(this, 70, Intent(this, CatchRoutesActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(s?.itinerary?.settings?.name ?: "Catch route").setContentText(readout(s))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${readout(s)}\nPotential encounters; walking time only. ${controller.message.value.orEmpty()}"))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        listOf("pause" to if (s?.paused == true) "Resume" else "Pause", "catch" to "+ Catch", "map" to "Map").forEachIndexed { i, (action, label) ->
            builder.addAction(0, label, PendingIntent.getService(this, 71 + i, Intent(this, CatchRouteService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            controller.message.value = "Allow display over other apps to use the floating map."
            return
        }
        if (overlay != null) return
        val s = controller.session.value ?: return
        val density = resources.displayMetrics.density
        val width = (320 * density).toInt().coerceAtMost(resources.displayMetrics.widthPixels)
        val params = WindowManager.LayoutParams(width, (360 * density).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = (80 * density).toInt() }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xfff6f8fc.toInt()) }
        val title = TextView(this).apply { text = readout(s); setTextColor(0xff14213d.toInt()); textSize = 14f; setPadding(12, 12, 12, 12) }
        overlayText = title
        var downX = 0f; var downY = 0f; var x = 0; var y = 0
        title.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; x = params.x; y = params.y }
                MotionEvent.ACTION_MOVE -> { params.x = (x + e.rawX - downX).toInt().coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - width)); params.y = (y + e.rawY - downY).toInt().coerceIn(0, maxOf(0, resources.displayMetrics.heightPixels - params.height)); manager.updateViewLayout(root, params) }
            }; true
        }
        root.addView(title)
        val map = CatchRouteMapView(this).also { it.update(s.itinerary.settings, s.itinerary, controller.location.value) }
        overlayMap = map
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this)
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label; textSize = 10f; setPadding(0, 0, 0, 0); setOnClickListener { action() }
            buttons.addView(this, LinearLayout.LayoutParams(0, (48 * density).toInt(), 1f))
        }
        overlayPause = button(if (s.paused) "Resume" else "Pause") { controller.pause() }
        button("+ Catch") { controller.caught(1) }
        button("Replan") { controller.recalculate() }
        button("Close") { hideOverlay() }
        root.addView(buttons)
        runCatching { manager.addView(root, params); overlay = root }.onFailure { map.destroy(); overlayMap = null; overlayText = null }
    }
    private fun hideOverlay() { overlay?.let { runCatching { manager.removeView(it) } }; overlayMap?.destroy(); overlay = null; overlayMap = null; overlayText = null; overlayPause = null }
    override fun onDestroy() {
        scope.cancel()
        locationSource.stop()
        hideOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
    companion object {
        internal const val NOTIFICATION_ID = 7350
        private const val CHANNEL = "catch_routes"

        @Volatile
        internal var locationSourceFactory: ArrivalLocationSourceFactory =
            DefaultArrivalLocationSourceFactory
    }
}
