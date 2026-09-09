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
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArrivalTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { ArrivalTrackingRepository.getInstance(applicationContext) }
    private val locationSource by lazy {
        locationSourceFactory.create(applicationContext)
    }
    private val walkingRouteRepository by lazy { WalkingRouteRepository.getInstance() }
    private val huntRepository by lazy { HuntRepository.getInstance(applicationContext) }
    private var currentDestination: TrackedDestination? = null
    private var destinationJob: Job? = null
    private var expiryJob: Job? = null
    private var chipRefreshJob: Job? = null
    private var walkingRouteJob: Job? = null
    private var evaluator = ArrivalFixEvaluator()
    private var locationUpdatesStarted = false
    private var arrivalInProgress = false
    private var walkingRoute: WalkingRouteInfo? = null
    private var walkingRouteUpdatedAtMillis = 0L
    private var lastDirectDistanceMeters: Float? = null

    /**
     * The last fix good enough to measure from. Kept so that switching destination can price the
     * new one straight away: the fused request only calls back once the user has moved, so a
     * standing trainer would otherwise leave the Live Update with no distance at all.
     */
    private var lastAcceptedLocation: Location? = null
    private var lastWaitingForPreciseLocation = false
    private var lastInRange = false

    /**
     * Whether a hunt owns this journey. Mirrored into a field because the
     * notification is rebuilt from synchronous callbacks (location fixes, the
     * chip refresh loop) that cannot suspend to read the store.
     */
    private var huntActive = false
    private var huntJob: Job? = null

    /** The floating pill, and whether the user has it switched on. */
    private val journeyOverlay by lazy { JourneyOverlay(applicationContext) }
    private var journeyOverlayEnabled = true
    private var overlayPreferenceJob: Job? = null

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

        if (intent?.action == ACTION_GOT_IT) {
            serviceScope.launch {
                markCurrentTargetCaught()
                stopTrackingService()
            }
            return START_NOT_STICKY
        }

        promoteToForeground(currentJourneyNotification() ?: ArrivalTrackingNotifications.restoring(this))
        if (overlayPreferenceJob == null) {
            overlayPreferenceJob = serviceScope.launch {
                AlertPreferences(applicationContext.alertPreferencesDataStore)
                    .journeyOverlayEnabled
                    .collect { enabled ->
                        journeyOverlayEnabled = enabled
                        if (!enabled) journeyOverlay.hide()
                        currentDestination?.let { refreshJourneyOverlay(it) }
                    }
            }
        }
        if (huntJob == null) {
            huntJob = serviceScope.launch {
                huntRepository.sessionFlow.collect { session ->
                    val active = session != null
                    if (active == huntActive) return@collect
                    huntActive = active
                    // The Got it action appears and disappears with the hunt, so
                    // the standing notification has to be rebuilt, not left stale.
                    currentDestination?.let { destination ->
                        updateOngoing(
                            destination = destination,
                            distanceMeters = lastDirectDistanceMeters,
                            waiting = lastWaitingForPreciseLocation,
                            inRange = lastInRange
                        )
                    }
                }
            }
        }
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
        chipRefreshJob?.cancel()
        walkingRouteJob?.cancel()
        huntJob?.cancel()
        overlayPreferenceJob?.cancel()
        journeyOverlay.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun activate(destination: TrackedDestination) {
        val previousDestination = currentDestination
        if (previousDestination?.uniqueId != destination.uniqueId) {
            evaluator = ArrivalFixEvaluator()
            arrivalInProgress = false
            // A new target is a new journey, so a pill the trainer swiped away on
            // the last one comes back rather than staying hidden for the trip.
            journeyOverlay.reset()
            walkingRouteJob?.cancel()
            walkingRouteJob = null
            walkingRoute = null
            walkingRouteUpdatedAtMillis = 0L
            // Measure the new destination from the last fix rather than starting blank. Without
            // a distance the notification is built with indeterminate progress and no short
            // critical text, so the system has nothing to promote and the Live Update drops
            // out of the status bar into the shade until the user moves far enough for a new
            // callback. Display only: arrival is still decided by fixes the evaluator sees.
            lastDirectDistanceMeters = lastAcceptedLocation?.let { location ->
                directDistanceMeters(location, destination)
            }
            lastWaitingForPreciseLocation = false
            lastInRange = false
        } else if (previousDestination.radiusMeters != destination.radiusMeters) {
            evaluator.reset()
            lastInRange = false
        }
        currentDestination = destination
        scheduleExpiry(destination)
        updateOngoing(
            destination = destination,
            distanceMeters = lastDirectDistanceMeters,
            waiting = lastWaitingForPreciseLocation,
            inRange = lastInRange
        )
        startChipRefreshLoop()
        startLocationUpdates()
    }

    /**
     * Re-post the journey notification on a timer. The status bar chip renders around
     * notification updates and times out after a few minutes of silence, so a trainer
     * standing still — no location callbacks means no re-posts — would watch the chip
     * fade even though the journey is alive. Navigation apps keep their chip the same
     * way. Silent by setOnlyAlertOnce, and it keeps the "x left" text fresh as a bonus.
     */
    private fun startChipRefreshLoop() {
        chipRefreshJob?.cancel()
        chipRefreshJob = serviceScope.launch {
            while (isActive) {
                delay(CHIP_REFRESH_INTERVAL_MILLIS)
                val destination = currentDestination ?: break
                updateOngoing(
                    destination = destination,
                    distanceMeters = lastDirectDistanceMeters,
                    waiting = lastWaitingForPreciseLocation,
                    inRange = lastInRange
                )
            }
        }
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
                if (!available && lastDirectDistanceMeters == null) {
                    lastWaitingForPreciseLocation = true
                    currentDestination?.let { destination ->
                        updateOngoing(
                            destination = destination,
                            distanceMeters = lastDirectDistanceMeters,
                            waiting = true
                        )
                    }
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
            evaluator.reset()
            if (lastDirectDistanceMeters == null) {
                lastWaitingForPreciseLocation = true
                lastInRange = false
                updateOngoing(
                    destination = destination,
                    distanceMeters = null,
                    waiting = true,
                    inRange = false
                )
            } else {
                // A stale/unavailable callback is transient on many phones. Keep the last
                // known distance and route visible instead of flickering back to a blocked state.
                lastWaitingForPreciseLocation = false
                updateOngoing(
                    destination = destination,
                    distanceMeters = lastDirectDistanceMeters,
                    waiting = false,
                    inRange = lastInRange
                )
            }
            return
        }
        lastAcceptedLocation = location
        val distance = floatArrayOf(directDistanceMeters(location, destination))
        val result = evaluator.evaluate(
                distanceMeters = distance[0],
                accuracyMeters = location.accuracy,
                radiusMeters = destination.radiusMeters,
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                gpsToleranceMeters = if (destination.alert.usesPokemonGoInteractionRadius()) {
                    MAX_GPS_TOLERANCE_METERS
                } else {
                    0f
                }
            )
        lastDirectDistanceMeters = distance[0]
        // A valid fix is useful for display even when its accuracy is too coarse to confirm
        // arrival. The evaluator still rejects fixes beyond its safety ceiling.
        lastWaitingForPreciseLocation = false
        lastInRange = result != ArrivalFixResult.WAITING
        when (result) {
            ArrivalFixResult.ARRIVED -> handleArrival(destination)
            ArrivalFixResult.FIRST_IN_RANGE -> {
                // A raid's first trustworthy in-range fix is enough to switch surfaces. The
                // raid Live Update is non-destructive and keeps running through the raid, so
                // waiting for a second fix only leaves an already-arrived trainer staring at
                // "In range" instead of the hundo CPs they need at the catch screen.
                if (RaidTierParser.isRaid(destination.alert)) {
                    handleArrival(destination)
                } else {
                    updateOngoing(
                        destination = destination,
                        distanceMeters = distance[0],
                        waiting = false,
                        inRange = true
                    )
                }
            }
            ArrivalFixResult.WAITING -> updateOngoing(
                destination = destination,
                distanceMeters = distance[0],
                waiting = lastWaitingForPreciseLocation,
                inRange = lastInRange
            )
        }
        if (result == ArrivalFixResult.WAITING ||
            (result == ArrivalFixResult.FIRST_IN_RANGE && !RaidTierParser.isRaid(destination.alert))
        ) {
            requestWalkingRoute(destination, location)
        }
    }

    private fun requestWalkingRoute(destination: TrackedDestination, location: Location) {
        if (walkingRouteJob?.isActive == true) return
        val origin = Location(location)
        walkingRouteJob = serviceScope.launch {
            val route = withContext(Dispatchers.IO) {
                walkingRouteRepository.getWalkingRoutes(
                    origin = origin,
                    alerts = listOf(destination.alert),
                    timeoutMillis = WalkingRouteRepository.BACKGROUND_TIMEOUT_MILLIS
                )[destination.uniqueId]
            }
            val active = currentDestination
            if (active?.uniqueId != destination.uniqueId) return@launch
            if (route != null) {
                walkingRoute = route
                walkingRouteUpdatedAtMillis = SystemClock.elapsedRealtime()
            } else if (
                walkingRoute != null &&
                SystemClock.elapsedRealtime() - walkingRouteUpdatedAtMillis >=
                ROUTE_DISPLAY_MAX_AGE_MILLIS
            ) {
                walkingRoute = null
                walkingRouteUpdatedAtMillis = 0L
            } else {
                return@launch
            }
            updateOngoing(
                destination = active,
                distanceMeters = lastDirectDistanceMeters,
                waiting = lastWaitingForPreciseLocation,
                inRange = lastInRange
            )
        }
    }

    private fun handleArrival(destination: TrackedDestination) {
        if (arrivalInProgress) return
        arrivalInProgress = true
        serviceScope.launch {
            val raidLiveUpdateStarted = if (RaidTierParser.isRaid(destination.alert)) {
                // Complete the durable handoff before clearing the destination. Clearing it
                // emits null to the collector, which tears this service down and cancels its
                // scope; doing that first can interrupt RaidWatchController's DataStore write.
                RaidWatchController.start(this@ArrivalTrackingService, destination.alert)
            } else {
                false
            }
            // Arriving is not the end of a hunt — it is the moment the chip starts
            // earning its place, showing the CP or the stop name you came for. Hold
            // the journey open until "Got it" retires this target. A raid is the
            // exception: its Live Update has taken over the chip already.
            if (huntActive && !raidLiveUpdateStarted) {
                lastInRange = true
                updateOngoing(
                    destination = destination,
                    distanceMeters = lastDirectDistanceMeters,
                    waiting = false,
                    inRange = true
                )
                return@launch
            }

            repository.stopTracking()
            if (!raidLiveUpdateStarted && hasNotificationPermission()) {
                ArrivalTrackingNotifications.postArrival(
                    this@ArrivalTrackingService,
                    destination
                )
            }
            stopTrackingService()
        }
    }

    /**
     * "Got it": retire this target the same way a swipe on the feed would, so the
     * thing you just caught disappears from the list, the map and the widgets
     * rather than being offered again as the nearest match.
     */
    private suspend fun markCurrentTargetCaught() {
        val destination = currentDestination ?: repository.currentDestination()
        destination?.let { target ->
            runCatching {
                AlertPreferences(applicationContext.alertPreferencesDataStore)
                    .addDismissedAlert(target.uniqueId)
                AlertsWidgetProvider.requestUpdate(applicationContext)
            }.onFailure { Log.w(TAG, "Could not record the caught target", it) }
        }
        huntRepository.setTarget(null)
        repository.stopTracking()
    }

    @SuppressLint("MissingPermission")
    private fun updateOngoing(
        destination: TrackedDestination,
        distanceMeters: Float? = null,
        waiting: Boolean = false,
        inRange: Boolean = false
    ) {
        if (!hasNotificationPermission()) return
        val notification = ArrivalTrackingNotifications.ongoing(
            context = this,
            destination = destination,
            distanceMeters = distanceMeters,
            walkingRoute = walkingRoute,
            inRange = inRange,
            waitingForPreciseLocation = waiting,
            huntActive = huntActive
        )
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(ArrivalTrackingNotifications.ONGOING_NOTIFICATION_ID, notification)
        }
        // Same call site as the notification so the two readouts cannot drift, and so
        // the 30 s refresh loop keeps the pill alive while the trainer stands still.
        showJourneyOverlay(destination, distanceMeters, inRange)
    }

    private fun showJourneyOverlay(
        destination: TrackedDestination,
        distanceMeters: Float?,
        inRange: Boolean
    ) {
        if (!journeyOverlayEnabled) return
        journeyOverlay.show(
            alert = destination.alert,
            distanceMeters = distanceMeters,
            inRange = inRange,
            huntActive = huntActive
        )
    }

    private fun refreshJourneyOverlay(destination: TrackedDestination) {
        showJourneyOverlay(destination, lastDirectDistanceMeters, lastInRange)
    }

    /**
     * The live journey notification as it stands right now, so a repeated start (a map PiP
     * browse switch or a resume on app open) can re-promote with it. The restoring placeholder
     * is category service and unpromoted, so promoting with it would demote the Live Update out
     * of the status bar chip; the destination flow never re-emits for the already-collected
     * destination, leaving the demoted notification in the shade until the next location
     * callback happens to re-post.
     */
    private fun currentJourneyNotification(): android.app.Notification? {
        val destination = currentDestination ?: return null
        return ArrivalTrackingNotifications.ongoing(
            context = this,
            destination = destination,
            distanceMeters = lastDirectDistanceMeters,
            walkingRoute = walkingRoute,
            inRange = lastInRange,
            waitingForPreciseLocation = lastWaitingForPreciseLocation,
            huntActive = huntActive
        )
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
        chipRefreshJob?.cancel()
        chipRefreshJob = null
        walkingRouteJob?.cancel()
        walkingRouteJob = null
        walkingRoute = null
        walkingRouteUpdatedAtMillis = 0L
        currentDestination = null
        journeyOverlay.reset()
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

    private fun directDistanceMeters(
        location: Location,
        destination: TrackedDestination
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            destination.latitude,
            destination.longitude,
            results
        )
        return results[0]
    }

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
        private const val TAG = "ArrivalTracking"
        const val ACTION_STOP = "com.example.pokemonalertsv2.tracking.STOP"
        const val ACTION_GOT_IT = "com.example.pokemonalertsv2.tracking.GOT_IT"
        private const val MAX_LOCATION_AGE_MILLIS = 30_000L
        private const val MAX_GPS_TOLERANCE_METERS = 20f
        private const val ROUTE_DISPLAY_MAX_AGE_MILLIS = 10 * 60 * 1000L

        /**
         * Well inside the observed few-minute status bar chip timeout, cheap enough to run
         * for the whole journey: one notification rebuild and one silent re-post.
         */
        private const val CHIP_REFRESH_INTERVAL_MILLIS = 30_000L

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
