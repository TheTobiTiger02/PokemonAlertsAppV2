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
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.CaughtAlert
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.ui.alerts.HUNT_ORDINAL_MAX
import com.example.pokemonalertsv2.hunt.HuntMapFocus
import com.example.pokemonalertsv2.hunt.HuntPlan
import com.example.pokemonalertsv2.hunt.huntOriginMoved
import com.example.pokemonalertsv2.hunt.huntRouteFocusCoordinates
import com.example.pokemonalertsv2.hunt.HuntBatterySaver
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.hunt.isHuntTarget
import com.example.pokemonalertsv2.hunt.insideHuntArea
import com.example.pokemonalertsv2.hunt.huntTargets
import com.example.pokemonalertsv2.hunt.huntTargetTitle
import com.example.pokemonalertsv2.hunt.shouldRetargetHuntTo
import com.example.pokemonalertsv2.ui.alerts.mapCoordinatesOrNull
import com.example.pokemonalertsv2.ui.alerts.mapCountdownLabel
import com.example.pokemonalertsv2.hunt.HuntRouteMatrixCache
import com.example.pokemonalertsv2.hunt.huntRoutingCandidates
import com.example.pokemonalertsv2.hunt.CATCH_UNDO_WINDOW_MILLIS
import com.example.pokemonalertsv2.hunt.isUndoOfferLive
import com.example.pokemonalertsv2.hunt.undoLastCatch
import com.example.pokemonalertsv2.ui.alerts.MapLocationTracker
import com.example.pokemonalertsv2.ui.alerts.MapPoseCadence
import com.example.pokemonalertsv2.ui.alerts.MapPoseTracker
import com.example.pokemonalertsv2.ui.alerts.MAP_PIP_REFIT_METERS
import com.example.pokemonalertsv2.ui.alerts.mapPipDistanceMeters
import com.example.pokemonalertsv2.ui.alerts.stepMapPipSelection
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    /** The cadence the running request was made with, so a change can be detected. */
    private var activeLocationCadence: ArrivalCadence? = null
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
    /** Observation time, not callback time: batches and replays are not new GPS fixes. */
    private var lastArrivalFixMillis: Long? = null
    private var locationAvailable = true
    private var lastWaitingForPreciseLocation = false
    private var lastInRange = false

    /**
     * Whether a hunt owns this journey. Mirrored into a field because the
     * notification is rebuilt from synchronous callbacks (location fixes, the
     * chip refresh loop) that cannot suspend to read the store.
     */
    private var renderedHuntTargets: List<PokemonAlert> = emptyList()
    private var huntStartedAt: Long? = null
    private var huntFocus = HuntMapFocus.READY
    private var huntActive = false

    /**
     * Whether the trainer has parked the route. Mirrored for the same reason
     * [huntActive] is: the acquire path runs on location fixes and cannot suspend.
     */
    private var huntPaused = false
    private var batterySaverJob: Job? = null
    private val batterySaver by lazy {
        HuntBatterySaver(this) {
            serviceScope.launch { huntRepository.setBatterySaverEnabled(false) }
        }
    }
    private var huntJob: Job? = null

    /**
     * Set when the target changes, consumed by the next [focusFloatingMap].
     *
     * Framing is normally withheld until the trainer has walked [MAP_PIP_REFIT_METERS],
     * and withheld entirely once they have panned the map by hand -- both right while
     * walking one leg, both wrong the instant the leg changes. Tapping a pin used to
     * retarget the hunt and leave the camera exactly where it was.
     */
    private var pendingCameraRefocus = false

    /** The floating pill, and whether the user has it switched on. */
    private val journeyOverlay by lazy { JourneyOverlay(applicationContext) }

    /** The floating map. Replaces the picture-in-picture window; see the class note. */
    private val floatingMap by lazy { FloatingMapOverlay(applicationContext) }

    /**
     * The blue dot and its heading beam.
     *
     * Separate from [locationSource] on purpose: that one prices the journey and
     * carries no compass, so on its own the map could only ever draw the plain
     * dot. This is the rotation-vector tracker the in-app map uses, and it is
     * only alive while the window is, because it holds a 1 Hz fix request.
     */
    private var poseTracker: MapPoseTracker? = null
    private var poseCadence: MapPoseCadence? = null

    /** What the floating map is currently drawing, so identical work is skipped. */
    private var renderedTargetKey: String? = null

    /** Set once the stored window geometry has been read back from disk. */
    private var geometryRestored = false
    private var geometrySaveJob: Job? = null
    private var artworkJob: Job? = null

    /** Where the camera was last framed from, for the move-before-refit rule. */
    private var lastFocusLatitude: Double? = null
    private var lastFocusLongitude: Double? = null

    /** Latest inputs for the floating map's marker set, kept so any of the three can move it. */
    private var huntDefinition: FilterDefinition? = null
    /** The hunt's drawn area; alerts outside it are invisible to every hunt decision. */
    private var huntArea: List<com.example.pokemonalertsv2.catchroutes.CatchPoint> = emptyList()
    private var huntName: String? = null

    /** Whether the standby camera has been put on the trainer yet. */
    private var standbyCentred = false
    private var acquireJob: Job? = null
    private var liveAlerts: List<PokemonAlert> = emptyList()
    private var dismissedAlertIds: Set<String> = emptySet()
    private var mapAlertsJob: Job? = null
    private var journeyOverlayEnabled = true
    private var overlayPreferenceJob: Job? = null
    private var undoOfferJob: Job? = null

    /**
     * Walked legs for the hunt ordering. Read on the fix path, never fetched there --
     * [huntMatrixJob] does that on its own cadence.
     */
    private val huntMatrix by lazy { HuntRouteMatrixCache.getInstance() }
    private var huntMatrixJob: Job? = null

    /**
     * The hunt's current plan. Made off the main thread by [huntPlanJob] and only read
     * everywhere else: planning used to run inline, on the main thread, on every location
     * fix, feed update and notification tick, and that is what made a long hunt lag.
     */
    private var huntPlan: HuntPlan = HuntPlan.Empty
    private var huntPlanJob: Job? = null
    private val huntPlanRequests = Channel<Unit>(Channel.CONFLATED)

    /** Where the trainer stood for [huntPlan]; walking on past a threshold asks for a new one. */
    private var huntPlanLatitude: Double? = null
    private var huntPlanLongitude: Double? = null

    /** The target the hunt itself last chose, as opposed to one the trainer picked elsewhere. */
    private var huntTargetId: String? = null

    /** A target the trainer picked by hand: the plan leads with it rather than switching away. */
    private var pinnedHuntTargetId: String? = null

    /** What the walked legs were last fetched for, so an unchanged hunt does not fetch again. */
    private var matrixOriginLatitude: Double? = null
    private var matrixOriginLongitude: Double? = null
    private var matrixTargetIds: Set<String> = emptySet()

    /** The countdown text the floating map's numbered pins were last drawn with. */
    private var renderedLabelKey: String? = null

    /** The live undo offer, or null. Drives the notification's Undo action. */
    private var undoOffer: CaughtAlert? = null

    override fun onCreate() {
        super.onCreate()
        ArrivalTrackingNotifications.ensureChannels(this)
        batterySaverJob = serviceScope.launch {
            kotlinx.coroutines.flow.combine(huntRepository.sessionFlow, huntRepository.batterySaverEnabled) { session, enabled ->
                session != null && enabled
            }.collect { batterySaver.setEnabled(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // One stop, not two. While a hunt is running the journey is only the
            // current leg of it, so stopping the leg alone left the hunt live and
            // it immediately picked another target -- the button looked broken.
            stopEverything(applicationContext)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SHOW_MAP) {
            // The map panel's button. It has nothing to draw without a journey, so
            // this is a no-op then rather than an empty window.
            showFloatingMap()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_GOT_IT) {
            serviceScope.launch {
                markCurrentTargetCaught()
                stopTrackingService()
            }
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_UNDO_CATCH) {
            // Everything the undo does to the journey is a write to the destination
            // store, which destinationJob is already collecting -- so there is
            // nothing to re-activate here by hand.
            serviceScope.launch { undoLastCatch(applicationContext) }
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
        if (undoOfferJob == null) {
            undoOfferJob = serviceScope.launch {
                AlertPreferences(applicationContext.alertPreferencesDataStore)
                    .lastCaughtAlert
                    .collectLatest { caught ->
                        undoOffer = caught?.takeIf { isUndoOfferLive(it, System.currentTimeMillis()) }
                        refreshCurrentNotification()
                        floatingMap.setUndoOffer(undoOffer != null)
                        // While walking, the 30s chip loop would drop the action on
                        // its own. In standby nothing re-posts, so the offer is aged
                        // out here instead of sitting there indefinitely.
                        val offer = undoOffer ?: return@collectLatest
                        val remaining = CATCH_UNDO_WINDOW_MILLIS -
                            (System.currentTimeMillis() - offer.caughtAtMillis)
                        if (remaining > 0) delay(remaining)
                        undoOffer = null
                        refreshCurrentNotification()
                        floatingMap.setUndoOffer(false)
                    }
            }
        }
        if (huntJob == null) {
            huntJob = serviceScope.launch {
                huntRepository.sessionFlow.collect { session ->
                    if (huntStartedAt != session?.startedAtMillis) huntFocus = HuntMapFocus.READY
                    huntStartedAt = session?.startedAtMillis
                    // Keep the definition, not just the flag: the floating map draws
                    // the hunt's targets, which only the definition can produce.
                    val definitionChanged = huntDefinition != session?.definition || huntArea != session?.area.orEmpty()
                    huntDefinition = session?.definition
                    huntArea = session?.area.orEmpty()
                    huntName = session?.name
                    huntTargetId = session?.targetUniqueId
                    if (definitionChanged) {
                        // A different hunt: nothing of the old route carries over.
                        huntPlan = HuntPlan.Empty
                        pinnedHuntTargetId = null
                    }
                    val active = session != null
                    val changed = active != huntActive
                    huntActive = active
                    val pausedChanged = huntPaused != (session?.paused == true)
                    huntPaused = session?.paused == true
                    floatingMap.setPaused(huntPaused)
                    // Standby says which of the two reasons it has no target, so the
                    // wording has to follow the flag rather than wait for the next one.
                    if (pausedChanged && !changed) refreshCurrentNotification()
                    if (active) {
                        startHuntPlanLoop()
                        if (definitionChanged || pausedChanged) requestHuntPlan()
                    }
                    refreshFloatingMapAlerts()
                    maybeAcquireHuntTarget()
                    if (!changed) return@collect
                    if (active) startHuntMatrixLoop() else stopHuntMatrixLoop()
                    if (!active) stopHuntPlanLoop()
                    showFloatingMap()
                    // A hunt started with no matches yet still has to hold the
                    // service open, or there is nothing alive to notice the first one.
                    if (active && currentDestination == null) enterHuntStandby()
                    if (!active) stopTrackingService(force = true)
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
        if (mapAlertsJob == null) {
            mapAlertsJob = serviceScope.launch {
                val alertsRepository = PokemonAlertsRepository.create(applicationContext)
                val preferences = AlertPreferences(applicationContext.alertPreferencesDataStore)
                combine(
                    alertsRepository.alerts,
                    preferences.dismissedAlertIds
                ) { alerts, dismissed -> alerts to dismissed }
                    .collect { (alerts, dismissed) ->
                        val destination = currentDestination
                        val targetWasInFeed = destination != null &&
                            liveAlerts.any { it.uniqueId == destination.uniqueId }
                        liveAlerts = alerts
                        dismissedAlertIds = dismissed
                        // An observed removal/invalidation ends this leg, not the hunt.
                        // Do not discard a restored or explicitly selected destination
                        // merely because the initial Room snapshot does not contain it.
                        if (huntActive && targetWasInFeed &&
                            alerts.none { it.uniqueId == destination.uniqueId } &&
                            repository.currentDestination()?.uniqueId == destination.uniqueId
                        ) {
                            repository.stopTracking()
                        }
                        requestHuntPlan()
                    }
            }
        }
        if (destinationJob == null) {
            destinationJob = serviceScope.launch {
                repository.destinationFlow.collectLatest { destination ->
                    if (destination == null) {
                        stopTrackingService()
                    } else if (belongsToCurrentHunt(destination)) {
                        activate(destination)
                    } else {
                        // Not this hunt's: drop it and let standby wait for a real
                        // match. Clearing the store re-enters here with null.
                        runCatching { repository.stopTracking() }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        batterySaverJob?.cancel()
        batterySaver.close()
        stopLocationUpdates()
        expiryJob?.cancel()
        chipRefreshJob?.cancel()
        walkingRouteJob?.cancel()
        huntJob?.cancel()
        acquireJob?.cancel()
        mapAlertsJob?.cancel()
        overlayPreferenceJob?.cancel()
        undoOfferJob?.cancel()
        huntMatrixJob?.cancel()
        huntPlanJob?.cancel()
        journeyOverlay.reset()
        stopPoseTracking()
        artworkJob?.cancel()
        renderedTargetKey = null
        floatingMap.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Whether a stored destination is something the running hunt actually wants.
     *
     * The journey store outlives a hunt -- it survives process death, and a new
     * hunt is started without touching it -- so booting a raid hunt would restore
     * whatever you happened to be walking to last time and present it as a raid
     * target. Reads the session from the store rather than the cached field: this
     * runs against the destination collector, which can beat the hunt collector.
     */
    private suspend fun belongsToCurrentHunt(destination: TrackedDestination): Boolean {
        val session = huntRepository.currentSession() ?: return true
        // Started after the hunt did, so somebody chose it on purpose -- an "I'm
        // going" during a hunt still wins. Only a journey that predates the hunt
        // is a leftover, and that is the one to check against what is being hunted.
        if (destination.startedAtMillis >= session.startedAtMillis) return true
        return isHuntTarget(destination.alert, session.definition, area = session.area)
    }

    private fun activate(destination: TrackedDestination) {
        val previousDestination = currentDestination
        if (previousDestination?.uniqueId != destination.uniqueId) {
            evaluator = ArrivalFixEvaluator()
            lastArrivalFixMillis = null
            arrivalInProgress = false
            // A new target is a new journey, so a pill the trainer swiped away on
            // the last one comes back rather than staying hidden for the trip.
            journeyOverlay.reset()
            renderedTargetKey = null
            lastFocusLatitude = null
            lastFocusLongitude = null
            // A new leg is a new picture: you and the thing you are now walking to.
            pendingCameraRefocus = true
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
        standbyCentred = false
        showFloatingMap()
        scheduleExpiry(destination)
        updateOngoing(
            destination = destination,
            distanceMeters = lastDirectDistanceMeters,
            waiting = lastWaitingForPreciseLocation,
            inRange = lastInRange
        )
        startChipRefreshLoop()
        // The distance was seeded from the last accepted fix just above, so the first
        // request already goes out at the rate this leg deserves.
        applyLocationCadence()
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
                expireUnavailableLocation()
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

    /**
     * Points the location request at the cadence the current distance calls for.
     *
     * The request cannot be re-rated in place, so a change means tearing the old one
     * down first -- leave it running and the fast request simply carries on
     * underneath the slow one, which is the trap `startPoseTracking` documents.
     */
    private fun applyLocationCadence() {
        val destination = currentDestination ?: return
        val wanted = arrivalCadenceFor(
            distanceMeters = lastDirectDistanceMeters,
            radiusMeters = destination.radiusMeters,
            current = activeLocationCadence
        )
        if (locationUpdatesStarted && wanted == activeLocationCadence) return
        if (locationUpdatesStarted) stopLocationUpdates()
        startLocationUpdates(wanted)
    }

    private fun startLocationUpdates(cadence: ArrivalCadence = ArrivalCadence.Arriving) {
        if (locationUpdatesStarted || !hasFineLocationPermission()) return
        activeLocationCadence = cadence
        val started = locationSource.start(
            cadence = cadence,
            onLocation = ::onLocation,
            onAvailabilityChanged = { available ->
                locationAvailable = available
                if (!available) evaluator.reset()
                expireUnavailableLocation()
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
        activeLocationCadence = null
        if (!locationUpdatesStarted) return
        locationSource.stop()
        locationUpdatesStarted = false
    }

    private fun onLocation(location: Location) {
        val destination = currentDestination
        if (!isFreshValidLocation(location)) {
            evaluator.reset()
            if (destination == null) return
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
                expireUnavailableLocation()
                updateOngoing(
                    destination = destination,
                    distanceMeters = lastDirectDistanceMeters,
                    waiting = lastWaitingForPreciseLocation,
                    inRange = lastInRange
                )
            }
            return
        }
        val fixMillis = locationObservationMillis(location)
        if (lastArrivalFixMillis?.let { fixMillis <= it } == true) return
        lastArrivalFixMillis = fixMillis
        lastAcceptedLocation = Location(location)
        locationAvailable = true
        if (huntActive) requestHuntPlanIfMoved(location)
        if (destination == null) {
            if (huntActive) maybeAcquireHuntTarget()
            return
        }
        val distance = floatArrayOf(directDistanceMeters(location, destination))
        val result = evaluator.evaluate(
                distanceMeters = distance[0],
                accuracyMeters = location.accuracy,
                radiusMeters = destination.radiusMeters,
                elapsedRealtimeMillis = fixMillis,
                gpsToleranceMeters = if (destination.alert.usesPokemonGoInteractionRadius()) {
                    MAX_GPS_TOLERANCE_METERS
                } else {
                    0f
                }
            )
        lastDirectDistanceMeters = distance[0]
        // Closing on the target buys a faster rate; walking away gives it back.
        applyLocationCadence()
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
            if (lastWaitingForPreciseLocation) return@launch
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
            val active = repository.currentDestination()
            if (active?.uniqueId != destination.uniqueId ||
                active.startedAtMillis != destination.startedAtMillis
            ) return@launch
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

            if (!repository.stopTrackingIfCurrent(destination)) return@launch
            if (!raidLiveUpdateStarted && hasNotificationPermission()) {
                ArrivalTrackingNotifications.postArrival(
                    this@ArrivalTrackingService,
                    destination
                )
            }
            // The destination collector owns standby/shutdown. A newer journey may
            // already be queued by the time the DataStore edit above returns.
        }
    }

    /** What to call a caught target in the undo button. */
    private fun caughtDisplayName(destination: TrackedDestination): String =
        huntTargetTitle(destination.alert)

    /**
     * "Got it": retire this target the same way a swipe on the feed would, so the
     * thing you just caught disappears from the list, the map and the widgets
     * rather than being offered again as the nearest match.
     */
    private suspend fun markCurrentTargetCaught() {
        val destination = currentDestination ?: repository.currentDestination()
        destination?.let { target ->
            runCatching {
                val preferences = AlertPreferences(applicationContext.alertPreferencesDataStore)
                preferences.addDismissedAlert(target.uniqueId)
                preferences.rememberCaughtAlert(target.uniqueId, caughtDisplayName(target))
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
            huntActive = huntActive,
            offerOverlay = readoutSurface() == JourneyReadoutSurface.MAP_LABEL,
            undoOffer = undoOffer
        )
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(ArrivalTrackingNotifications.ONGOING_NOTIFICATION_ID, notification)
        }
        // Same call site as the notification so the two readouts cannot drift, and so
        // the 30 s refresh loop keeps the pill alive while the trainer stands still.
        showJourneyOverlay(destination, distanceMeters, inRange)
        focusFloatingMap()
        refreshFloatingMapAlerts()
    }

    private fun showJourneyOverlay(
        destination: TrackedDestination,
        distanceMeters: Float?,
        inRange: Boolean
    ) {
        // One surface only. Where the status bar chip exists it is strictly better,
        // and where it does not the map draws the label instead -- either way the
        // pill stays out of the way rather than repeating the same number.
        if (readoutSurface() != JourneyReadoutSurface.OVERLAY_PILL) {
            journeyOverlay.hide()
            return
        }
        journeyOverlay.show(
            alert = destination.alert,
            distanceMeters = distanceMeters,
            inRange = inRange,
            huntActive = huntActive
        )
    }

    /**
     * The floating map belongs to a hunt: it is what you walk between targets with.
     * A one-off "I'm going" already has the notification and does not need a window.
     */
    private fun showFloatingMap() {
        if (!huntActive || !FloatingMapOverlay.canDraw(applicationContext)) {
            stopPoseTracking()
            floatingMap.hide()
            return
        }
        // Put the window back where it was left before it is ever shown; moving it
        // afterwards would make it jump across the screen in front of the trainer.
        if (!geometryRestored) {
            geometryRestored = true
            serviceScope.launch {
                runCatching {
                    AlertPreferences(applicationContext.alertPreferencesDataStore)
                        .getFloatingMapGeometry()
                }.getOrNull()?.let { floatingMap.restoreGeometry(it.x, it.y, it.width, it.height) }
                showFloatingMap()
            }
            return
        }
        floatingMap.onGeometryChanged = { x, y, width, height ->
            // Debounced: a drag reports on every release, and two drags in a row
            // should not queue two writes.
            geometrySaveJob?.cancel()
            geometrySaveJob = serviceScope.launch {
                delay(GEOMETRY_SAVE_DELAY_MS)
                runCatching {
                    AlertPreferences(applicationContext.alertPreferencesDataStore)
                        .updateFloatingMapGeometry(x, y, width, height)
                }.onFailure { Log.w(TAG, "Could not persist the map window geometry", it) }
            }
        }
        floatingMap.onRecenter = { focusFloatingMapOnUser() }
        floatingMap.onFit = {
            huntFocus = huntFocus.pressed()
            val destination = currentDestination
            if (huntFocus == HuntMapFocus.ROUTE) {
                focusHuntRoute(force = true)
            } else if (destination == null) {
                lastAcceptedLocation?.let { floatingMap.recenter(it.latitude, it.longitude) }
            } else {
                floatingMap.focus(
                    userLatitude = lastAcceptedLocation?.latitude,
                    userLongitude = lastAcceptedLocation?.longitude,
                    targetLatitude = destination.latitude,
                    targetLongitude = destination.longitude,
                    force = true
                )
            }
        }
        floatingMap.onOpenApp = { openTheApp() }
        floatingMap.onPrevious = { stepHuntTarget(forward = false) }
        floatingMap.onNext = { stepHuntTarget(forward = true) }
        floatingMap.onGotIt = { serviceScope.launch { catchTargetAndAdvance() } }
        floatingMap.onUndo = { serviceScope.launch { undoLastCatch(applicationContext) } }
        floatingMap.onAlertTap = { retargetHuntTo(it) }
        floatingMap.onRecalculate = { recalculateHuntRoute() }
        // One button, two meanings, because the state it acts on is visible in its own
        // glyph: park the route, or -- resuming -- plan a fresh one from here.
        floatingMap.onPauseToggle = {
            if (huntPaused) recalculateHuntRoute() else pauseHuntRoute()
        }
        floatingMap.onClusterTap = { cluster ->
            floatingMap.focusCluster(cluster.alerts.mapNotNull { it.mapCoordinatesOrNull() })
        }
        // Closing the window ends the hunt. There is one control for "I am done",
        // and it is the one every window has in its corner.
        floatingMap.onClose = { stopEverything(applicationContext) }
        val mapWasShowing = floatingMap.isShowing
        floatingMap.show {
            focusFloatingMap()
            refreshFloatingMapAlerts()
        }
        floatingMap.setUndoOffer(undoOffer != null)
        if (!mapWasShowing) batterySaver.restoreOverlayOrder()
        floatingMap.setPaused(huntPaused)
        // One place decides the cadence: full rate while there is a target being
        // walked to, backed off while the hunt is only waiting for one.
        startPoseTracking(if (currentDestination == null) MapPoseCadence.Standby else MapPoseCadence.Live)
        focusFloatingMap()
        refreshFloatingMapAlerts()
    }

    /**
     * "Got it" from the floating map: retire this target and walk to the next one.
     *
     * Deliberately not [markCurrentTargetCaught] followed by a step. That stops
     * tracking, which emits null to the destination collector and tears this
     * service down — the next target would then be started against a dying
     * scope. Replacing the destination keeps the journey, and the chip, alive.
     */
    private suspend fun catchTargetAndAdvance() {
        val caught = currentDestination ?: repository.currentDestination() ?: return
        runCatching {
            val preferences = AlertPreferences(applicationContext.alertPreferencesDataStore)
            preferences.addDismissedAlert(caught.uniqueId)
            // Remembered so a mis-tap on the tick can be taken back; see
            // AlertPreferences.lastCaughtAlert.
            preferences.rememberCaughtAlert(caught.uniqueId, caughtDisplayName(caught))
            AlertsWidgetProvider.requestUpdate(applicationContext)
        }.onFailure { Log.w(TAG, "Could not record the caught target", it) }

        pinnedHuntTargetId = null
        // Ahead of the dismissed-ids flow, which the next plan would otherwise race.
        dismissedAlertIds = dismissedAlertIds + caught.uniqueId
        // The next stop on the route already planned, skipping the one just caught rather
        // than waiting for the dismissed-ids flow to come back round.
        val next = huntPlan.route.firstOrNull { it.uniqueId != caught.uniqueId }
        if (next == null) {
            huntRepository.setTarget(null)
            repository.stopTracking()
            requestHuntPlan()
            return
        }
        runCatching {
            repository.startTracking(next)
            huntRepository.setTarget(next.uniqueId)
        }.onFailure { Log.w(TAG, "Could not advance to the next hunt target", it) }
        requestHuntPlan()
    }

    /**
     * Walks the hunt's target list from the window's buttons.
     *
     * The picture-in-picture window had to send every press through a
     * PendingIntent, a broadcast, the Activity and back down into composition.
     * An overlay gets the touch directly, so this is a method call.
     */
    private fun stepHuntTarget(forward: Boolean) {
        huntFocus = HuntMapFocus.READY
        val targets = huntPlan.ordered
        if (targets.isEmpty()) return
        val nextId = stepMapPipSelection(
            orderedIds = targets.map { it.uniqueId },
            currentId = currentDestination?.uniqueId,
            forward = forward
        ) ?: return
        val next = targets.firstOrNull { it.uniqueId == nextId } ?: return
        // Stepping is choosing by hand, so the plan must not step straight back.
        pinnedHuntTargetId = next.uniqueId
        serviceScope.launch {
            runCatching {
                repository.startTracking(next)
                huntRepository.setTarget(next.uniqueId)
            }.onFailure { Log.w(TAG, "Could not switch to the next hunt target", it) }
            requestHuntPlan()
        }
    }

    /**
     * Makes a tapped pin the hunt's target.
     *
     * Deliberately does **not** stop the current journey first. Clearing the slot
     * emits null on destinationFlow, which drives stopTrackingService() and tears the
     * service down -- the replacement would then be started on a dying scope. The
     * single slot is overwritten instead, and destinationJob picks it up, the same way
     * stepHuntTarget does it.
     */
    private fun retargetHuntTo(alert: PokemonAlert) {
        huntFocus = HuntMapFocus.READY
        pendingCameraRefocus = true
        focusFloatingMap()
        if (!shouldRetargetHuntTo(alert, currentDestination?.uniqueId, huntActive)) return
        pinnedHuntTargetId = alert.uniqueId
        serviceScope.launch {
            runCatching {
                // Tapping a pin is an unambiguous "go here", so it is also the way out
                // of a paused hunt -- otherwise the tap would start a leg that the
                // acquire guard immediately refuses to follow up.
                huntRepository.setPaused(false)
                repository.startTracking(alert)
                huntRepository.setTarget(alert.uniqueId)
            }.onFailure { Log.w(TAG, "Could not switch to the tapped hunt target", it) }
            requestHuntPlan()
        }
    }

    /**
     * Parks the route without ending the hunt.
     *
     * Clearing the destination drops the service into [enterHuntStandby], which keeps
     * the window, the foreground notification and a standby GPS rate alive -- exactly
     * the state a hunt waiting for its first match already runs in. The only
     * difference is [huntPaused], which stops the acquire path choosing for you.
     */
    private fun pauseHuntRoute() {
        if (!huntActive) return
        serviceScope.launch {
            runCatching {
                huntRepository.setPaused(true)
                huntRepository.setTarget(null)
                repository.stopTracking()
            }.onFailure { Log.w(TAG, "Could not pause the hunt route", it) }
        }
    }

    /**
     * Re-plans the route from where the trainer is standing now.
     *
     * Two things make this different from simply waiting for the next matrix refresh.
     * The legs from the trainer are dropped and re-asked rather than read out of a
     * cache that says where they were ten minutes ago, and the chain is built with no
     * anchor -- so the plan starts from the trainer instead of continuing onward from a
     * target they have walked away from. Also the way back out of a pause.
     */
    private fun recalculateHuntRoute() {
        if (!huntActive) return
        serviceScope.launch {
            runCatching { huntRepository.setPaused(false) }
            val definition = huntDefinition
            val origin = lastAcceptedLocation?.takeIf(::isFreshValidLocation)
            if (definition != null && origin != null) {
                val candidates = withContext(Dispatchers.Default) {
                    huntRoutingCandidates(
                        alerts = liveAlerts.insideHuntArea(huntArea),
                        definition = definition,
                        dismissedAlertIds = dismissedAlertIds,
                        originLatitude = origin.latitude,
                        originLongitude = origin.longitude
                    )
                }
                if (candidates.isNotEmpty()) {
                    huntMatrix.prefetch(
                        originLatitude = origin.latitude,
                        originLongitude = origin.longitude,
                        targets = candidates,
                        force = true
                    )
                }
            }
            // Planned after the matrix lands, and deliberately from scratch: no route to keep
            // and no hand-picked target to lead with.
            pinnedHuntTargetId = null
            applyHuntPlan(computeHuntPlan(fromScratch = true), follow = false)
            val next = huntPlan.route.firstOrNull()
            renderedTargetKey = null
            if (next == null) {
                refreshFloatingMapAlerts()
                return@launch
            }
            if (next.uniqueId == currentDestination?.uniqueId) {
                // Same answer as before: nothing to restart, but the trainer pressed a
                // button and deserves to see the map agree with the plan.
                pendingCameraRefocus = true
                refreshFloatingMapAlerts()
                focusFloatingMap()
                return@launch
            }
            runCatching {
                repository.startTracking(next)
                huntRepository.setTarget(next.uniqueId)
            }.onFailure { Log.w(TAG, "Could not recalculate the hunt route", it) }
        }
    }

    private fun requestHuntPlan() {
        huntPlanRequests.trySend(Unit)
    }

    /** Re-plans once the trainer has walked far enough for the old plan's first leg to be off. */
    private fun requestHuntPlanIfMoved(location: Location) {
        val latitude = huntPlanLatitude
        val longitude = huntPlanLongitude
        if (latitude == null || longitude == null ||
            huntOriginMoved(latitude, longitude, location.latitude, location.longitude, HUNT_PLAN_MOVE_METERS)
        ) {
            requestHuntPlan()
        }
    }

    /**
     * Plans on request, one at a time. Requests that land while a plan is being made
     * collapse into one more, and plans are spaced by [HUNT_PLAN_MIN_INTERVAL_MILLIS], so a
     * burst of fixes and feed updates costs one plan rather than one each.
     */
    private fun startHuntPlanLoop() {
        if (huntPlanJob?.isActive == true) return
        huntPlanJob = serviceScope.launch {
            for (request in huntPlanRequests) {
                applyHuntPlan(computeHuntPlan())
                delay(HUNT_PLAN_MIN_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopHuntPlanLoop() {
        huntPlanJob?.cancel()
        huntPlanJob = null
        huntPlan = HuntPlan.Empty
        huntPlanLatitude = null
        huntPlanLongitude = null
        pinnedHuntTargetId = null
        huntRepository.publishPlan(HuntPlan.Empty)
    }

    /**
     * The plan for the hunt as it stands, made on [Dispatchers.Default].
     *
     * Carries the current route forward unless [fromScratch] -- the Recalculate button --
     * with the target being walked to first, so a new alert is fitted into the walk rather
     * than the walk being rebuilt around it.
     */
    private suspend fun computeHuntPlan(fromScratch: Boolean = false): HuntPlan {
        val definition = huntDefinition ?: return HuntPlan.Empty
        // Every part of the plan is measured from where the trainer is. Without a fix the
        // destination stands in; without either there is nothing to measure from.
        val origin = lastAcceptedLocation
        val latitude = origin?.latitude ?: currentDestination?.latitude ?: return HuntPlan.Empty
        val longitude = origin?.longitude ?: currentDestination?.longitude ?: return HuntPlan.Empty
        val alerts = liveAlerts.insideHuntArea(huntArea)
        val dismissed = dismissedAlertIds
        val destinationId = currentDestination?.uniqueId
        val previous = if (fromScratch) {
            emptyList()
        } else {
            (listOfNotNull(destinationId) + huntPlan.route.map { it.uniqueId }).distinct()
        }
        val pinned = if (fromScratch) null else pinnedHuntTargetId?.takeIf { it == destinationId }
        val costs = huntMatrix.snapshot()
        huntPlanLatitude = latitude
        huntPlanLongitude = longitude
        return withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            huntTargets(
                alerts = alerts,
                definition = definition,
                dismissedAlertIds = dismissed,
                originLatitude = latitude,
                originLongitude = longitude,
                nowMillis = now,
                costs = costs.forOrigin(latitude, longitude, now),
                previousRouteIds = previous,
                pinnedId = pinned
            )
        }
    }

    private fun applyHuntPlan(plan: HuntPlan, follow: Boolean = true) {
        if (!huntActive) return
        huntPlan = plan
        huntRepository.publishPlan(plan)
        refreshFloatingMapAlerts()
        maybeAcquireHuntTarget()
        if (follow) followHuntPlan()
    }

    /**
     * Moves the walk onto the plan's first stop when the plan has moved on from the target.
     *
     * The planner only does that when it is clearly better, or when the target can no longer
     * be reached in time. A target the trainer picked by hand, or started outside the hunt,
     * is theirs and is left alone.
     */
    private fun followHuntPlan() {
        if (!huntActive || huntPaused) return
        val destination = currentDestination ?: return
        if (destination.uniqueId == pinnedHuntTargetId || destination.uniqueId != huntTargetId) return
        val head = huntPlan.route.firstOrNull() ?: return
        if (head.uniqueId == destination.uniqueId || acquireJob?.isActive == true) return
        acquireJob = serviceScope.launch {
            runCatching {
                if (repository.currentDestination()?.uniqueId != destination.uniqueId) return@launch
                repository.startTracking(head)
                huntRepository.setTarget(head.uniqueId)
            }.onFailure { Log.w(TAG, "Could not follow the hunt plan to ${head.uniqueId}", it) }
        }
    }

    /**
     * The hunt's plan drawn on the floating map: the route numbered and counting down, the
     * rest nearest first, and the target being walked to emphasised.
     */
    private fun refreshFloatingMapAlerts() {
        if (!floatingMap.isShowing) return
        // A broad hunt matches hundreds of alerts -- 837 on a live Quest hunt -- and
        // every one of them is a Canvas-drawn pin. Beyond the nearest few dozen they
        // are neither reachable on foot nor distinguishable in a window this small,
        // so the window draws the nearest slice and the emphasised target always.
        val plan = huntPlan
        val all = plan.ordered
        val emphasized = currentDestination?.uniqueId
        val nearest = all.take(FLOATING_MAP_MAX_MARKERS)
        val tracked = all.firstOrNull { it.uniqueId == emphasized }
        val targets = if (tracked != null && nearest.none { it.uniqueId == emphasized }) {
            nearest + tracked
        } else {
            nearest
        }
        val numbered = minOf(plan.route.size, targets.size)
        // This runs from updateOngoing, i.e. on every location fix. Rebuilding every
        // pin that often -- Canvas work, on the main thread -- was most of the lag.
        renderedHuntTargets = targets.take(numbered)
        val now = System.currentTimeMillis()
        // The numbered pins in order, everything else as a set: re-planning reshuffles the far
        // end of the route and which unnumbered match is nearest, and none of that changes
        // what a pin looks like.
        val wearsNumber = minOf(numbered, HUNT_ORDINAL_MAX)
        val key = targets.take(wearsNumber).joinToString(",") { it.uniqueId } + "|" +
            targets.drop(wearsNumber).map { it.uniqueId }.sorted().joinToString(",") + "|" + emphasized.orEmpty()
        val labelKey = targets.take(minOf(numbered, HUNT_ORDINAL_MAX))
            .joinToString(",") { mapCountdownLabel(it.endTime, now, minutePrecision = true) }
        if (key == renderedTargetKey && labelKey == renderedLabelKey) return
        val pinsChanged = key != renderedTargetKey
        renderedTargetKey = key
        renderedLabelKey = labelKey

        floatingMap.setAlerts(targets, emphasized, numbered)
        // A countdown ticking over only swaps label images; the artwork is already there.
        if (!pinsChanged) return
        if (huntFocus == HuntMapFocus.ROUTE) focusHuntRoute()
        // Then the real artwork, off the main thread, replacing the placeholders.
        artworkJob?.cancel()
        artworkJob = serviceScope.launch {
            runCatching { floatingMap.loadArtwork(targets, emphasized, numbered) }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) Log.w(TAG, "Artwork pass failed", it) }
        }
    }

    /** Fits the displayed numbered route without reranking it from a new GPS fix. */
    private fun focusHuntRoute(force: Boolean = false) {
        floatingMap.focusRoute(
            huntRouteFocusCoordinates(
                renderedHuntTargets, lastAcceptedLocation?.latitude, lastAcceptedLocation?.longitude
            ), force
        )
    }

    private fun focusFloatingMap() {
        if (huntFocus == HuntMapFocus.ROUTE) {
            pendingCameraRefocus = false
            return
        }
        val destination = currentDestination ?: return
        if (!floatingMap.isShowing) return
        // A changed target overrides both of the rules below: the move gate is about a
        // camera that is already on the right pair of points, and a hand-panned camera
        // was panned to look at the leg you are no longer walking.
        val refocus = pendingCameraRefocus
        pendingCameraRefocus = false
        // Re-frame only once the trainer has actually moved. Animating on every fix
        // reads as the map drifting under your thumb.
        val origin = lastAcceptedLocation
        if (origin != null) {
            val previousLat = lastFocusLatitude
            val previousLon = lastFocusLongitude
            if (!refocus && previousLat != null && previousLon != null) {
                val moved = mapPipDistanceMeters(
                    previousLat,
                    previousLon,
                    origin.latitude,
                    origin.longitude
                )
                if (moved < MAP_PIP_REFIT_METERS) return
            }
            lastFocusLatitude = origin.latitude
            lastFocusLongitude = origin.longitude
        }
        floatingMap.focus(
            userLatitude = lastAcceptedLocation?.latitude,
            userLongitude = lastAcceptedLocation?.longitude,
            targetLatitude = destination.latitude,
            targetLongitude = destination.longitude,
            force = refocus
        )
    }

    private fun startPoseTracking(cadence: MapPoseCadence = MapPoseCadence.Live) {
        if (poseTracker != null && poseCadence == cadence) return
        // A cadence change means a new request, and the old one has to go first or
        // the fast one keeps running underneath the slow one.
        stopPoseTracking()
        poseCadence = cadence
        poseTracker = MapLocationTracker(
            applicationContext,
            { pose ->
                floatingMap.setUserPose(pose)
                // The pose stream paints the window; the independent arrival source
                // supplies the origin for target acquisition even without a window.
                if (currentDestination == null) {
                    // Standby opens the window before there is any fix to frame, so
                    // the camera sits at null island until this puts it on the
                    // trainer the moment the first pose lands. Deliberately not gated
                    // on freshness: showing the map where the trainer last was beats
                    // showing them the Gulf of Guinea, and it is the same pose the
                    // in-app map already draws its dot from.
                    if (!standbyCentred && huntFocus != HuntMapFocus.ROUTE) {
                        standbyCentred = true
                        floatingMap.recenter(pose.location.latitude, pose.location.longitude)
                    }
                    // Target acquisition uses the service's location source, including
                    // when no overlay permission exists. A map pose only draws the map.
                }
            },
            { /* status drives the in-app map's chrome; the window has none */ },
            cadence
        ).also { it.start() }
    }

    /** Releases the 1 Hz fix request and the compass listener; not optional. */
    private fun stopPoseTracking() {
        poseTracker?.stop()
        poseTracker = null
        poseCadence = null
    }

    private fun readoutSurface(): JourneyReadoutSurface = resolveJourneyReadoutSurface(
        sdkInt = Build.VERSION.SDK_INT,
        canDrawOverlays = JourneyOverlay.canDraw(applicationContext),
        overlayAllowed = journeyOverlayEnabled
    )

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
            huntActive = huntActive,
            offerOverlay = readoutSurface() == JourneyReadoutSurface.MAP_LABEL,
            undoOffer = undoOffer
        )
    }

    /**
     * Re-posts whichever notification is standing, so an action that appeared or
     * expired reaches the shade without waiting for the next location fix.
     */
    // Guarded by hasNotificationPermission(), which lint cannot follow -- the same
    // reason updateOngoing carries this.
    @SuppressLint("MissingPermission")
    private fun refreshCurrentNotification() {
        if (!hasNotificationPermission()) return
        val notification = currentJourneyNotification()
            ?: if (huntActive) {
                ArrivalTrackingNotifications.huntStandby(
                    context = this,
                    huntName = huntName ?: "your target",
                    undoOffer = undoOffer,
                    paused = huntPaused
                )
            } else {
                return
            }
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

    /**
     * A hunt with nothing to walk to, right now.
     *
     * The journey is torn down but the service, the window and the pose tracker
     * stay up, so the moment a matching alert arrives [maybeAcquireHuntTarget]
     * can start walking you to it -- with no app to open and nothing to restart.
     */
    private fun enterHuntStandby() {
        stopLocationUpdates()
        expiryJob?.cancel()
        chipRefreshJob?.cancel()
        chipRefreshJob = null
        walkingRouteJob?.cancel()
        walkingRouteJob = null
        walkingRoute = null
        walkingRouteUpdatedAtMillis = 0L
        currentDestination = null
        lastArrivalFixMillis = null
        lastDirectDistanceMeters = null
        lastInRange = false
        lastWaitingForPreciseLocation = false
        journeyOverlay.reset()
        renderedTargetKey = null
        standbyCentred = false
        promoteToForeground(
            ArrivalTrackingNotifications.huntStandby(
                context = this,
                huntName = huntName ?: "your target",
                undoOffer = undoOffer,
                paused = huntPaused
            )
        )
        // Reached with currentDestination already null, so showFloatingMap picks
        // the backed-off cadence for us.
        showFloatingMap()
        refreshFloatingMapAlerts()
        focusFloatingMapOnUser()
        startLocationUpdates(ArrivalCadence.Standby)
        serviceScope.launch {
            if (repository.currentDestination() == null) {
                huntRepository.setTarget(null)
                maybeAcquireHuntTarget()
            }
        }
    }

    /**
     * Keeps the walked legs fresh while a hunt runs.
     *
     * Deliberately a slow loop of its own rather than anything hung off the location
     * callback: it reads [lastAcceptedLocation] and [liveAlerts], which the fix path
     * and the feed collector already maintain, so walking refreshes the matrix
     * without the 3 s path knowing this feature exists. A stationary trainer with a
     * warm cache costs one mutex acquisition per pass.
     */
    private fun startHuntMatrixLoop() {
        if (huntMatrixJob?.isActive == true) return
        huntMatrixJob = serviceScope.launch {
            while (isActive) {
                val definition = huntDefinition
                val origin = lastAcceptedLocation
                if (definition != null && origin != null && isFreshValidLocation(origin)) {
                    val candidates = withContext(Dispatchers.Default) {
                        huntRoutingCandidates(
                            alerts = liveAlerts.insideHuntArea(huntArea),
                            definition = definition,
                            dismissedAlertIds = dismissedAlertIds,
                            originLatitude = origin.latitude,
                            originLongitude = origin.longitude
                        )
                    }
                    // Only when it can change the answer: the trainer's row is kept until they
                    // have walked past the drift the costs tolerate anyway, and the legs between
                    // targets only need asking for when the targets themselves change. Asking
                    // every pass re-sent a full matrix every ~11 m walked.
                    val ids = candidates.mapTo(HashSet()) { it.id }
                    val fromLatitude = matrixOriginLatitude
                    val fromLongitude = matrixOriginLongitude
                    val moved = fromLatitude == null || fromLongitude == null ||
                        huntOriginMoved(fromLatitude, fromLongitude, origin.latitude, origin.longitude)
                    if (candidates.isNotEmpty() && (moved || ids != matrixTargetIds) &&
                        huntMatrix.prefetch(origin.latitude, origin.longitude, candidates)
                    ) {
                        matrixOriginLatitude = origin.latitude
                        matrixOriginLongitude = origin.longitude
                        matrixTargetIds = ids
                    }
                }
                // Also the plan's clock: stops run out of time while a trainer stands still,
                // and new legs may have just landed.
                requestHuntPlan()
                delay(HUNT_MATRIX_REFRESH_MILLIS)
            }
        }
    }

    private fun stopHuntMatrixLoop() {
        huntMatrixJob?.cancel()
        matrixOriginLatitude = null
        matrixOriginLongitude = null
        matrixTargetIds = emptySet()
        huntMatrixJob = null
    }

    /**
     * Starts walking to the nearest hunt target whenever the hunt has none.
     *
     * This is what makes a hunt self-driving: it runs from the alert feed, so a
     * hunt started before its quarry exists picks the first match up the instant
     * it arrives, whether or not the app is open. Target selection used to live
     * in the map screen, which only runs while that screen is composed.
     */
    private fun maybeAcquireHuntTarget() {
        if (!huntActive || currentDestination != null || acquireJob?.isActive == true) return
        // Paused is the whole feature: without this the hunt would pick the target
        // straight back up and the button would look broken, exactly the way Stop did
        // before it learned to end the hunt rather than the leg.
        if (huntPaused) return
        // Every part of choosing a target is measured from where the trainer is:
        // which one is nearest, and whether the walk fits in the time left. With no
        // fix yet the origin falls back to 0,0 and the "nearest" target is whichever
        // happens to lie closest to the Gulf of Guinea. Standby is already holding
        // the service open, and its GPS source supplies the missing origin.
        if (lastAcceptedLocation?.let(::isFreshValidLocation) != true) return
        val next = huntPlan.route.firstOrNull() ?: return
        acquireJob = serviceScope.launch {
            runCatching {
                // Re-check under the coroutine: the hunt can end between the feed
                // update that scheduled this and the write that would resurrect it.
                if (!huntRepository.isHunting()) return@launch
                // The destination collector may still be restoring a persisted leg.
                if (repository.currentDestination() != null) return@launch
                repository.startTracking(next)
                huntRepository.setTarget(next.uniqueId)
            }.onFailure { Log.w(TAG, "Could not start walking to ${next.uniqueId}", it) }
        }
    }

    /** Brings the app forward from the window, on whatever it was last showing. */
    private fun openTheApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }.onFailure { Log.w(TAG, "Could not open the app from the floating map", it) }
    }

    /** Standby has no destination, so the camera frames the trainer instead. */
    private fun focusFloatingMapOnUser() {
        huntFocus = HuntMapFocus.READY
        val origin = lastAcceptedLocation ?: return
        floatingMap.recenter(origin.latitude, origin.longitude)
    }

    /**
     * Ends the journey, and the service with it -- unless a hunt is running, in
     * which case the hunt is the thing that owns the service and it waits for its
     * next target instead of shutting down. See [enterHuntStandby].
     */
    private fun stopTrackingService(force: Boolean = false) {
        if (!force && huntActive) {
            enterHuntStandby()
            return
        }
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
        stopPoseTracking()
        artworkJob?.cancel()
        renderedTargetKey = null
        floatingMap.hide()
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
        } else {
            System.currentTimeMillis() - location.time
        }
        return ageMillis in 0L..MAX_LOCATION_AGE_MILLIS && location.hasAccuracy()
    }

    private fun locationObservationMillis(location: Location): Long =
        if (location.elapsedRealtimeNanos > 0L) {
            location.elapsedRealtimeNanos / 1_000_000L
        } else {
            // Older providers may supply only wall time. Put it on the evaluator's
            // monotonic clock while retaining the observation's age.
            SystemClock.elapsedRealtime() - (System.currentTimeMillis() - location.time)
        }

    /** Retain a transient outage's readout, but do not label an old fix as current. */
    private fun expireUnavailableLocation() {
        if (!locationAvailable && lastAcceptedLocation?.let(::isFreshValidLocation) != true) {
            evaluator.reset()
            lastWaitingForPreciseLocation = true
            lastInRange = false
            walkingRoute = null
            walkingRouteUpdatedAtMillis = 0L
        }
    }

    companion object {
        private const val TAG = "ArrivalTracking"

        /** Long enough that a drag settles, short enough to survive a quick stop. */
        private const val GEOMETRY_SAVE_DELAY_MS = 400L

        /** How many hunt targets the floating window draws, nearest first. */
        private const val FLOATING_MAP_MAX_MARKERS = 40
        const val ACTION_STOP = "com.example.pokemonalertsv2.tracking.STOP"
        const val ACTION_GOT_IT = "com.example.pokemonalertsv2.tracking.GOT_IT"
            const val ACTION_SHOW_MAP = "com.example.pokemonalertsv2.tracking.SHOW_MAP"
        const val ACTION_UNDO_CATCH = "com.example.pokemonalertsv2.tracking.UNDO_CATCH"

        /**
         * How often the walked legs are refreshed while hunting.
         *
         * Slow on purpose. The legs between targets do not change as the trainer
         * walks, only the row from the trainer does, and /api/routes/matrix shares one
         * per-client allowance with the walking routes the map is already fetching.
         */
        private const val HUNT_MATRIX_REFRESH_MILLIS = 30_000L

        /** Walked this far from where the plan was made, the first leg is worth re-pricing. */
        private const val HUNT_PLAN_MOVE_METERS = 25.0

        /** At most one plan per this long, however fast requests arrive. */
        private const val HUNT_PLAN_MIN_INTERVAL_MILLIS = 1_000L
        private const val MAX_LOCATION_AGE_MILLIS = 30_000L
        private const val MAX_GPS_TOLERANCE_METERS = 20f
        private const val ROUTE_DISPLAY_MAX_AGE_MILLIS = 10 * 60 * 1000L

        /**
         * Well inside the observed few-minute status bar chip timeout, cheap enough to run
         * for the whole journey: one notification rebuild and one silent re-post.
         *
         * Ten seconds rather than thirty because the chip is a distance the trainer is
         * watching change, and at 30 s a walk updates about once a block. This costs no
         * GPS and no network -- it re-renders the last accepted fix, so a standing
         * trainer sees exactly the same number three times -- which is why the real
         * fix rate is left to [arrivalCadenceFor] instead.
         */
        private const val CHIP_REFRESH_INTERVAL_MILLIS = 10_000L

        @Volatile
        internal var locationSourceFactory: ArrivalLocationSourceFactory =
            DefaultArrivalLocationSourceFactory

        /**
         * Ends the hunt and the journey from anywhere in the app.
         *
         * Clears both stores rather than messaging the service: the collectors
         * already tear the service down when either goes empty, and this way a
         * stop still works when no service is running to receive an intent.
         */
        fun stopEverything(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                runCatching { HuntRepository.getInstance(appContext).stop() }
                runCatching { ArrivalTrackingRepository.getInstance(appContext).stopTracking() }
            }
        }

        /**
         * Boots the service for a hunt that has just started.
         *
         * A hunt with no matching alert yet has no destination, and the service
         * used to exist only for a destination -- so such a hunt started and then
         * nothing happened, however many matches arrived afterwards.
         */
        fun startHunt(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ArrivalTrackingService::class.java)
            )
        }

        /** Opens the floating map for the journey already running, if there is one. */
        fun showFloatingMap(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, ArrivalTrackingService::class.java)
                        .setAction(ACTION_SHOW_MAP)
                )
            }
        }

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
                // A hunt keeps the service alive with no destination at all -- it is
                // waiting for its first match -- so a hunt on its own is reason enough
                // to come back after process death. Without this a hunt in standby
                // simply died with the process and never noticed the alert it wanted.
                val hunting = HuntRepository.getInstance(appContext).isHunting()
                val journeyReady = destination?.alert?.isEligibleArrivalDestination() == true
                if ((journeyReady || hunting) && fineGranted && notificationsGranted) {
                    start(appContext)
                } else if (destination != null) {
                    trackingRepository.stopTracking()
                }
            }
        }
    }
}
