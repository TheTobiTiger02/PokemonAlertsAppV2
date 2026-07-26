package com.example.pokemonalertsv2.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArrivalTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { ArrivalTrackingRepository.getInstance(applicationContext) }
    private val locationSource by lazy {
        locationSourceFactory.create(applicationContext)
    }
    private var currentDestination: TrackedDestination? = null
    private var destinationJob: Job? = null
    private var expiryJob: Job? = null
    private var evaluator = ArrivalFixEvaluator()
    private var locationUpdatesStarted = false
    private var arrivalInProgress = false

    override fun onCreate() {
        super.onCreate()
        ArrivalTrackingNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                repository.stopTracking()
                stopTrackingService()
            }
            return START_NOT_STICKY
        }

        promoteToForeground(ArrivalTrackingNotifications.restoring(this))
        if (destinationJob == null) {
            destinationJob = serviceScope.launch {
                repository.destinationFlow.collectLatest { destination ->
                    if (destination == null) {
                        stopTrackingService()
                    } else {
                        activate(destination)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        expiryJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun activate(destination: TrackedDestination) {
        if (currentDestination?.uniqueId != destination.uniqueId) {
            evaluator = ArrivalFixEvaluator()
            arrivalInProgress = false
        }
        currentDestination = destination
        scheduleExpiry(destination)
        updateOngoing(destination)
        startLocationUpdates()
    }

    private fun scheduleExpiry(destination: TrackedDestination) {
        expiryJob?.cancel()
        val endMillis = TimeUtils.parseEndTimeToMillis(destination.alert.endTime) ?: return
        val remaining = endMillis - System.currentTimeMillis()
        if (remaining <= 0L) {
            serviceScope.launch {
                repository.stopTracking()
                stopTrackingService()
            }
            return
        }
        expiryJob = serviceScope.launch {
            delay(remaining)
            repository.stopTracking()
            stopTrackingService()
        }
    }

    private fun startLocationUpdates() {
        if (locationUpdatesStarted || !hasFineLocationPermission()) return
        val started = locationSource.start(
            onLocation = ::onLocation,
            onAvailabilityChanged = { available ->
                if (!available) {
                    currentDestination?.let { updateOngoing(it, waiting = true) }
                }
            }
        )
        if (started) {
            locationUpdatesStarted = true
        } else {
            currentDestination?.let { destination -> updateOngoing(destination, waiting = true) }
        }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesStarted) return
        locationSource.stop()
        locationUpdatesStarted = false
    }

    private fun onLocation(location: Location) {
        val destination = currentDestination ?: return
        if (!isFreshValidLocation(location)) {
            updateOngoing(destination, waiting = true)
            return
        }
        val distance = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            destination.latitude,
            destination.longitude,
            distance
        )
        when (
            evaluator.evaluate(
                distanceMeters = distance[0],
                accuracyMeters = location.accuracy,
                radiusMeters = destination.radiusMeters,
                elapsedRealtimeMillis = SystemClock.elapsedRealtime()
            )
        ) {
            ArrivalFixResult.ARRIVED -> handleArrival(destination)
            ArrivalFixResult.FIRST_IN_RANGE,
            ArrivalFixResult.WAITING -> updateOngoing(
                destination = destination,
                distanceMeters = distance[0],
                waiting = location.accuracy > minOf(destination.radiusMeters.toFloat(), 50f)
            )
        }
    }

    private fun handleArrival(destination: TrackedDestination) {
        if (arrivalInProgress) return
        arrivalInProgress = true
        serviceScope.launch {
            repository.stopTracking()
            if (hasNotificationPermission()) {
                ArrivalTrackingNotifications.postArrival(this@ArrivalTrackingService, destination)
            }
            stopTrackingService()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateOngoing(
        destination: TrackedDestination,
        distanceMeters: Float? = null,
        waiting: Boolean = false
    ) {
        if (!hasNotificationPermission()) return
        val notification = ArrivalTrackingNotifications.ongoing(
            context = this,
            destination = destination,
            distanceMeters = distanceMeters,
            waitingForPreciseLocation = waiting
        )
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(ArrivalTrackingNotifications.ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun promoteToForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            ArrivalTrackingNotifications.ONGOING_NOTIFICATION_ID,
            notification,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
        )
    }

    private fun stopTrackingService() {
        stopLocationUpdates()
        expiryJob?.cancel()
        currentDestination = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isFreshValidLocation(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        val ageMillis = if (location.elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L)
                .coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
        }
        return ageMillis <= MAX_LOCATION_AGE_MILLIS && location.hasAccuracy()
    }

    companion object {
        const val ACTION_STOP = "com.example.pokemonalertsv2.tracking.STOP"
        private const val MAX_LOCATION_AGE_MILLIS = 30_000L

        @Volatile
        internal var locationSourceFactory: ArrivalLocationSourceFactory =
            DefaultArrivalLocationSourceFactory

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ArrivalTrackingService::class.java)
            )
        }

        fun resumeIfActive(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                val trackingRepository = ArrivalTrackingRepository.getInstance(appContext)
                val destination = trackingRepository.currentDestination()
                val fineGranted = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val notificationsGranted =
                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            appContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                if (
                    destination?.alert?.isEligibleArrivalDestination() == true &&
                    fineGranted &&
                    notificationsGranted
                ) {
                    start(appContext)
                } else if (destination != null) {
                    trackingRepository.stopTracking()
                }
            }
        }
    }
}
