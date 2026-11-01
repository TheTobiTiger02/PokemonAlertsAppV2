package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.pokemonalertsv2.tracking.NavigationSessionGate

/** One session owner, shared by foreground service and every route surface. */
class CatchRouteController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = CatchRouteStore.get(context)
    private val mutable = MutableStateFlow<CatchSession?>(null)
    val session: StateFlow<CatchSession?> = mutable.asStateFlow()
    val message = MutableStateFlow<String?>(null)
    val location = MutableStateFlow<CatchPoint?>(null)
    val recalculating = MutableStateFlow(false)
    /** The route that just ended, until a screen has shown it. */
    val lastSummary = MutableStateFlow<CatchRouteSummary?>(null)
    /**
     * The route no longer matches the walk: a stop still ahead has already despawned, or the
     * trainer has been well away from the path for a while. Advice only -- the route changes
     * when the trainer taps Replan, never on its own, because a guided walk that rebuilt itself
     * every couple of minutes could not be followed.
     */
    val outOfDate = MutableStateFlow(false)
    private var offPathSince: Long? = null
    /**
     * Confirms leaving the route once it has lasted [OFF_PATH_MILLIS]. A timer, not the next fix:
     * fixes only arrive after 5 m of movement, so a trainer standing still off the route would
     * otherwise never be told.
     */
    private var offPathCheck: Job? = null
    private val evaluator = CatchRouteProgress()
    private val mutex = Mutex()
    private var deadlineJob: Job? = null
    private var refreshJob: Job? = null
    private var retryAfter = 0L
    private var lastPersist = 0L
    private var generation = 0L
    // Stops are timed in absolute instants, so a restored route is as valid as it was: any stop
    // that despawned meanwhile shows up as out of date on the next fix.
    private val restored = scope.launch { mutable.value = store.current(); armDeadline() }

    suspend fun ready() = restored.join()
    private suspend fun <T> command(block: suspend () -> T): T {
        ready()
        return withContext(Dispatchers.Main.immediate) { mutex.withLock { block() } }
    }
    suspend fun begin(itinerary: CatchItinerary) = NavigationSessionGate.change { command {
        cancelRefresh()
        HuntRepository.getInstance(context).stop()
        ArrivalTrackingRepository.getInstance(context).stopTracking()
        context.stopService(Intent(context, ArrivalTrackingService::class.java))
        evaluator.reset()
        retryAfter = 0
        resetOutOfDate()
        mutable.value = CatchSession(itinerary, startedAtMillis = System.currentTimeMillis(), originalSettings = itinerary.settings)
        persist()
        armDeadline()
        ContextCompat.startForegroundService(context, Intent(context, CatchRouteService::class.java))
    } }
    suspend fun stop() = command {
        cancelRefresh(); deadlineJob?.cancel(); deadlineJob = null
        mutable.value?.let { s -> if (!s.finished) record(s) }
        mutable.value = null; evaluator.reset(); resetOutOfDate(); store.clear()
        // The service observes null and stops itself after fulfilling Android's foreground start.
        // Stopping a queued startForegroundService here can crash on rapid start/stop.
    }
    fun pause() = scope.launch { command {
        val s = mutable.value ?: return@command
        if (s.finished) return@command
        cancelRefresh()
        val now = System.currentTimeMillis()
        mutable.value = if (s.paused) s.copy(paused = false).resumedAt(now) else s.copy(paused = true).pausedAt(now)
        evaluator.reset(); clearOffPath(); persist()
    } }
    fun skip() = scope.launch { command {
        val s = mutable.value ?: return@command
        val ahead = s.ahead(System.currentTimeMillis())
        val next = ahead.firstOrNull() ?: return@command
        val group = ahead.takeWhile { it.meters - next.meters < 25 }
        mutable.value = s.copy(visits = s.visits + group.map { CatchVisit(it.opportunity, it.arrivalMillis, skipped = true) }, undoVisits = s.visits)
        persist()
    } }
    fun undo() = scope.launch { command {
        mutable.value?.let { s ->
            val visits = s.undoVisits?.let { previous ->
                previous + s.visits.filter { !it.skipped && it !in previous }
            } ?: s.visits
            mutable.value = s.copy(visits = visits, undoVisits = null)
            persist()
        }
    } }
    fun setRadius(enabled: Boolean) = scope.launch { command {
        val s = mutable.value ?: return@command
        cancelRefresh()
        mutable.value = s.copy(itinerary = s.itinerary.copy(settings = s.itinerary.settings.copy(spacialRend = enabled)))
        // Switching the range is the trainer asking for a route planned with it.
        persist(); evaluator.reset(); recalculateLocked()
    } }
    suspend fun onFix(fix: Location) = command {
        val now = System.currentTimeMillis()
        val age = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
        if (!fix.hasAccuracy() || age !in 0..10_000 || fix.accuracy > 40) return@command
        val p = CatchPoint(fix.latitude, fix.longitude)
        if (!p.valid) return@command
        location.value = p
        if (message.value?.startsWith("Waiting for location.") == true) message.value = null
        val s = mutable.value ?: return@command
        val running = now >= s.itinerary.settings.startAtMillis && now < s.itinerary.settings.endAtMillis
        val updated = if (running) evaluator.accept(s, p, fix.accuracy.toDouble(), now - age, now)
            .let { if (it.paused || it.finished) it else it.withWalk(p, fix.accuracy.toDouble()) } else s
        mutable.value = updated
        if (updated.visits.size != s.visits.size || now - lastPersist > 10_000) persist()
        if (now >= s.itinerary.settings.endAtMillis) {
            finishIfExpired(); return@command
        }
        if (s.paused || s.finished || now < s.itinerary.settings.startAtMillis) return@command
        // Stops already walked past are missed, not a reason to replan. Only what lies ahead counts, and only
        // when enough of it would be gone by the time the plan's pace reaches it.
        val ahead = updated.ahead(now)
        val speed = s.itinerary.settings.speedMps
        val unreachable = ahead.count { now + ((it.meters - updated.progressMeters).coerceAtLeast(0.0) / speed * 1000).toLong() >= it.opportunity.despawnAt }
        val missed = unreachable >= maxOf(3, ahead.size / 10)
        val offPath = s.itinerary.path.zipWithNext().none { (a, b) -> catchCircleInterval(a.point, b.point, p, OFF_PATH_METERS) != null }
        // One stray fix is not leaving the route; only a sustained stretch away from it is.
        if (!offPath) clearOffPath()
        else if (offPathSince == null) {
            offPathSince = now
            offPathCheck = scope.launch { delay(OFF_PATH_MILLIS); command { if (offPathSince != null) outOfDate.value = true } }
        }
        outOfDate.value = missed || offPathSince?.let { now - it >= OFF_PATH_MILLIS } == true
    }
    fun recalculate() = scope.launch { command { recalculateLocked() } }
    private fun recalculateLocked() {
        val s = mutable.value ?: return
        val p = location.value ?: run { message.value = "Waiting for a precise location before recalculating."; return }
        val now = System.currentTimeMillis()
        if (refreshJob?.isActive == true || now < retryAfter || s.paused || s.finished) return
        val remainingMinutes = kotlin.math.ceil((s.itinerary.settings.endAtMillis - now) / 60_000.0).toInt()
        if (remainingMinutes <= 0) return
        // The current route stays on screen, and keeps counting visits, until the new one exists.
        val epoch = generation
        refreshJob = scope.launch {
            recalculating.value = true
            try {
                command { persist() }
                val original = s.itinerary.settings
                val settings = original.copy(start = p, startAtMillis = now, durationMinutes = remainingMinutes,
                    finish = if (original.finish == CatchFinish.ROUND_TRIP) CatchFinish.PIN else original.finish,
                    end = original.destination, deadlineMillis = original.endAtMillis,
                    // A route walked as drawn carries on from where the trainer is on it, never re-optimised.
                    fixedPath = if (original.fixed) remainingFixedPath(original.fixedPath, p, s.progressMeters) else original.fixedPath)
                // What was already walked costs the same as any backtrack: a replan prefers fresh streets.
                val plan = CatchRoutePlanner(PokemonAlertsApi.catchRoutesService).generate(settings, s.visits, priorTrack = s.track)
                command {
                    val current = mutable.value
                    if (epoch == generation && current != null) {
                        mutable.value = current.copy(itinerary = plan, progressMeters = 0.0, replans = current.replans + 1)
                        evaluator.reset(); resetOutOfDate(); message.value = null; persist()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                command {
                    if (epoch == generation) {
                        val failedAt = System.currentTimeMillis()
                        retryAfter = maxOf(failedAt + 30_000, (e as? CatchApiException)?.retryAtMillis ?: 0L)
                        message.value = e.message ?: "Could not refresh route. Existing path remains visible."
                    }
                }
            } finally { withContext(NonCancellable) { command { if (epoch == generation) recalculating.value = false } } }
        }
    }
    private fun clearOffPath() { offPathSince = null; offPathCheck?.cancel(); offPathCheck = null }
    private fun resetOutOfDate() { outOfDate.value = false; clearOffPath() }
    private fun cancelRefresh() {
        generation++; refreshJob?.cancel(); refreshJob = null; recalculating.value = false
    }
    private fun armDeadline() {
        deadlineJob?.cancel()
        val s = mutable.value ?: return
        if (s.finished) return
        deadlineJob = scope.launch {
            yield()
            delay((s.itinerary.settings.endAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
            command { finishIfExpired() }
        }
    }
    private suspend fun finishIfExpired() {
        val s = mutable.value ?: return
        if (!s.finished && System.currentTimeMillis() >= s.itinerary.settings.endAtMillis) {
            cancelRefresh(); evaluator.reset()
            mutable.value = s.copy(paused = true, finished = true)
            message.value = "Session time finished. ${s.caught} visited."
            persist()
            record(s)
        }
    }
    /** Adds the route that is ending to the history and offers its summary. */
    private suspend fun record(s: CatchSession) {
        val summary = catchRouteSummary(s, s.originalSettings ?: s.itinerary.settings, System.currentTimeMillis(), s.replans)
        if (!summary.isWorthKeeping()) return
        runCatching { store.addHistory(summary) }
        lastSummary.value = summary
        CatchRouteNotifications.postFinished(context, summary)
    }
    private suspend fun persist() { mutable.value?.let { store.write(it) }; lastPersist = System.currentTimeMillis() }
    companion object {
        /** Further than this from every leg counts as away from the route. */
        private const val OFF_PATH_METERS = 60.0
        /** And for this long; GPS jitter near a leg must not call a route out of date. */
        private const val OFF_PATH_MILLIS = 20_000L
        @Volatile private var instance: CatchRouteController? = null
        fun get(context: Context): CatchRouteController = instance ?: synchronized(this) { instance ?: CatchRouteController(context.applicationContext).also { instance = it } }
    }
}
