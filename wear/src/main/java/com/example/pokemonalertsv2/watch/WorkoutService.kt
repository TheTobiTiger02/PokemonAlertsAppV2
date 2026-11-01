package com.example.pokemonalertsv2.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.SampleDataPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Measures the session on the watch: a Health Services walking exercise without GPS (the phone
 * already tracks the route), for heart rate, steps and calories. When another app owns the
 * exercise, it falls back to heart rate alone. Readings go to the phone every [SEND_EVERY_MILLIS]
 * as a few dozen bytes over the watch's existing phone link.
 */
class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sender: Job? = null
    private var usingExercise = false
    private var measuring = false

    @Volatile private var lastHeartRate: Int? = null
    @Volatile private var stepsTotal = 0L
    @Volatile private var caloriesTotal = 0.0

    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!running) {
            running = true
            scope.launch { startMeasuring() }
            sender = scope.launch { sendLoop() }
            scope.launch {
                // The phone ending its session ends the workout too.
                SessionStore.state.collect { state -> if (!state.active && running) stopEverything() }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Workout tracking", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setContentTitle("Tracking your session")
            .setContentText("Heart rate and steps go to your phone")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private suspend fun startMeasuring() {
        runCatching {
            val capabilities = exerciseClient.getCapabilitiesAsync().await()
            val supported = capabilities.getExerciseTypeCapabilities(ExerciseType.WALKING).supportedDataTypes
            val wanted = setOf<DataType<*, *>>(DataType.HEART_RATE_BPM, DataType.STEPS_TOTAL, DataType.CALORIES_TOTAL)
                .filter { it in supported }.toSet()
            check(wanted.isNotEmpty()) { "Walking exercise supports none of the wanted data" }
            // Another app's workout (e.g. Samsung Health) would be ended by ours; leave it be.
            val info = exerciseClient.getCurrentExerciseInfoAsync().await()
            check(info.exerciseTrackedStatus != OTHER_APP_IN_PROGRESS) {
                "Another app is tracking a workout"
            }
            exerciseClient.setUpdateCallback(exerciseCallback)
            val config = ExerciseConfig.builder(ExerciseType.WALKING)
                .setDataTypes(wanted)
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(false)
                .build()
            exerciseClient.startExerciseAsync(config).await()
            usingExercise = true
        }.onFailure { error ->
            Log.w(TAG, "Exercise unavailable, measuring heart rate only", error)
            runCatching {
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, measureCallback)
                measuring = true
            }.onFailure { Log.w(TAG, "Heart rate unavailable", it) }
        }
    }

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) = read(update.latestMetrics)
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) { Log.w(TAG, "Exercise callback failed", throwable) }
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
    }

    private val measureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) = Unit
        override fun onDataReceived(data: DataPointContainer) = read(data)
    }

    private fun read(data: DataPointContainer) {
        data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point: SampleDataPoint<Double> ->
            val bpm = point.value.roundToInt()
            if (bpm > 0) { lastHeartRate = bpm; SessionStore.heartRate.value = bpm }
        }
        data.getData(DataType.STEPS_TOTAL)?.let { stepsTotal = maxOf(stepsTotal, it.total) }
        data.getData(DataType.CALORIES_TOTAL)?.let { caloriesTotal = maxOf(caloriesTotal, it.total) }
    }

    private suspend fun sendLoop() {
        while (scope.isActive) {
            delay(SEND_EVERY_MILLIS)
            val hr = lastHeartRate
            if (hr == null && stepsTotal == 0L && caloriesTotal == 0.0) continue
            PhoneLink.send(this, WatchSample(System.currentTimeMillis(), hr, stepsTotal, caloriesTotal))
            // A stale reading is not sent twice as if it were new.
            lastHeartRate = null
        }
    }

    private fun stopEverything() {
        running = false
        sender?.cancel()
        scope.launch {
            if (usingExercise) runCatching { exerciseClient.endExerciseAsync().await() }
            if (measuring) runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, measureCallback).await() }
            usingExercise = false
            measuring = false
            SessionStore.heartRate.value = null
            ServiceCompat.stopForeground(this@WorkoutService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WorkoutService"
        private const val CHANNEL = "workout"
        private const val NOTIFICATION_ID = 11
        private const val SEND_EVERY_MILLIS = 15_000L
        /** ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS; the library marks its constant restricted. */
        private const val OTHER_APP_IN_PROGRESS = 1
        const val ACTION_STOP = "com.example.pokemonalertsv2.watch.STOP_WORKOUT"

        @Volatile var running = false
            private set
    }
}
