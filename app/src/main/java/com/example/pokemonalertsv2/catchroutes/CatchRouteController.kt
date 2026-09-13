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
    private val evaluator = CatchRouteProgress()
    private val mutex = Mutex()
    private var deadlineJob: Job? = null
    private var refreshJob: Job? = null
    private var lastRefresh = 0L
    private var retryAfter = 0L
    private var lastPersist = 0L
    private var generation = 0L
    private val restored = scope.launch { mutable.value = store.current()?.copy(needsRefresh = true); armDeadline() }

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
        lastRefresh = System.currentTimeMillis()
        retryAfter = 0
        val stale = System.currentTimeMillis() - itinerary.settings.startAtMillis > 30_000
        mutable.value = CatchSession(itinerary, needsRefresh = stale)
        persist()
        armDeadline()
        ContextCompat.startForegroundService(context, Intent(context, CatchRouteService::class.java))
    } }
    suspend fun stop() = command {
        cancelRefresh(); deadlineJob?.cancel(); deadlineJob = null
        mutable.value = null; evaluator.reset(); store.clear()
        // The service observes null and stops itself after fulfilling Android's foreground start.
        // Stopping a queued startForegroundService here can crash on rapid start/stop.
    }
    fun pause() = scope.launch { command {
        val s = mutable.value ?: return@command
        if (s.finished) return@command
        cancelRefresh()
        mutable.value = s.copy(paused = !s.paused, needsRefresh = !s.paused || s.needsRefresh)
        evaluator.reset(); persist()
        if (s.paused) recalculateLocked()
    } }
    fun caught(delta: Int) = scope.launch { command {
        mutable.value?.let { s -> mutable.value = s.copy(caught = (s.caught + delta).coerceAtLeast(0), undoCaught = s.caught, undoVisits = null); persist() }
    } }
    fun skip() = scope.launch { command {
        val s = mutable.value ?: return@command
        val next = s.remaining.firstOrNull() ?: return@command
        val group = s.remaining.takeWhile { it.meters - next.meters < 25 }
        mutable.value = s.copy(visits = s.visits + group.map { CatchVisit(it.opportunity, it.arrivalMillis, skipped = true) }, undoCaught = s.caught, undoVisits = s.visits)
        persist()
    } }
    fun undo() = scope.launch { command {
        mutable.value?.let { s ->
            val visits = s.undoVisits?.let { previous ->
                previous + s.visits.filter { !it.skipped && it !in previous }
            } ?: s.visits
            mutable.value = s.copy(visits = visits, caught = s.undoCaught ?: s.caught, undoVisits = null, undoCaught = null)
            persist()
        }
    } }
    fun setRadius(enabled: Boolean) = scope.launch { command {
        val s = mutable.value ?: return@command
        cancelRefresh()
        mutable.value = s.copy(itinerary = s.itinerary.copy(settings = s.itinerary.settings.copy(spacialRend = enabled)), needsRefresh = true)
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
        val updated = if (now >= s.itinerary.settings.startAtMillis && now < s.itinerary.settings.endAtMillis) evaluator.accept(s, p, fix.accuracy.toDouble(), now - age, now) else s
        mutable.value = updated
        if (updated.visits.size != s.visits.size || now - lastPersist > 10_000) persist()
        if (now >= s.itinerary.settings.endAtMillis) {
            finishIfExpired(); return@command
        }
        if (s.paused || s.finished || now < s.itinerary.settings.startAtMillis) return@command
        val missed = updated.remaining.any { it.opportunity.despawnAt <= now }
        val offPath = s.itinerary.path.zipWithNext().none { (a, b) -> catchCircleInterval(a.point, b.point, p, 60.0) != null }
        if (s.needsRefresh || now - lastRefresh > 120_000 || ((missed || offPath) && now - lastRefresh > 30_000)) recalculateLocked()
    }
    fun recalculate() = scope.launch { command { recalculateLocked() } }
    private fun recalculateLocked() {
        val s = mutable.value ?: return
        val p = location.value ?: run { message.value = "Waiting for a precise location before recalculating."; return }
        val now = System.currentTimeMillis()
        if (refreshJob?.isActive == true || now < retryAfter || s.paused || s.finished) return
        val remainingMinutes = kotlin.math.ceil((s.itinerary.settings.endAtMillis - now) / 60_000.0).toInt()
        if (remainingMinutes <= 0) return
        lastRefresh = now
        val epoch = generation
        refreshJob = scope.launch {
            recalculating.value = true
            try {
                val original = s.itinerary.settings
                val settings = original.copy(start = p, startAtMillis = now, durationMinutes = remainingMinutes,
                    finish = if (original.finish == CatchFinish.ROUND_TRIP) CatchFinish.PIN else original.finish,
                    end = original.destination, deadlineMillis = original.endAtMillis)
                val plan = CatchRoutePlanner(PokemonAlertsApi.catchRoutesService).generate(settings, s.visits)
                command {
                    val current = mutable.value
                    if (epoch == generation && current != null) {
                        mutable.value = current.copy(itinerary = plan, needsRefresh = false, progressMeters = 0.0)
                        evaluator.reset(); message.value = null; persist()
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
            message.value = "Session time finished. ${s.visits.count { !it.skipped }} visited · ${s.caught} caught."
            persist()
        }
    }
    private suspend fun persist() { mutable.value?.let { store.write(it) }; lastPersist = System.currentTimeMillis() }
    companion object {
        @Volatile private var instance: CatchRouteController? = null
        fun get(context: Context): CatchRouteController = instance ?: synchronized(this) { instance ?: CatchRouteController(context.applicationContext).also { instance = it } }
    }
}
