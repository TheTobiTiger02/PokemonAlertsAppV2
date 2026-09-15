package com.example.pokemonalertsv2.catchroutes

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import kotlinx.coroutines.*

class CatchRoutesViewModel(app: Application) : AndroidViewModel(app) {
    val store = CatchRouteStore.get(app)
    val controller = CatchRouteController.get(app)
    var settings by mutableStateOf(CatchRouteSettings())
        private set
    var itinerary by mutableStateOf<CatchItinerary?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selectedId by mutableStateOf<String?>(null)
        private set
    var hasStart by mutableStateOf(false)
        private set
    var retryAt by mutableLongStateOf(0)
        private set
    var useNow by mutableStateOf(true)
        private set
    private var job: Job? = null
    private var generation = 0L
    fun edit(value: CatchRouteSettings) { generation++; job?.cancel(); busy = false; settings = value; itinerary = null; error = null; recommendation = null }
    fun startPoint(value: CatchPoint) { hasStart = true; edit(settings.copy(start = value)) }
    fun schedule(at: Long?) { useNow = at == null; edit(settings.copy(startAtMillis = at ?: 0)) }
    fun load(entity: CatchSetupEntity) {
        runCatching { store.decodeSetup(entity) }.onSuccess {
            edit(it.copy(startAtMillis = 0)); selectedId = entity.id; hasStart = true; useNow = true
        }.onFailure { error = "This saved setup could not be read." }
    }
    fun save(duplicate: Boolean = false) = viewModelScope.launch {
        selectedId = store.save(settings.copy(startAtMillis = 0), if (duplicate) null else selectedId)
        progress = if (duplicate) "Copy saved" else "Setup saved"
    }
    fun delete(entity: CatchSetupEntity) = viewModelScope.launch {
        store.delete(entity.id)
        if (selectedId == entity.id) selectedId = null
    }
    fun cancel() { generation++; job?.cancel(); busy = false; progress = "Generation canceled" }
    fun generate() {
        if (busy) return
        error = null
        if (!hasStart) { error = "Use your location or pick a starting point on the map."; return }
        if (settings.durationMinutes !in 10..360) { error = "Choose 10–360 minutes."; return }
        if (System.currentTimeMillis() < retryAt) { error = "Routing is busy. Retry after the countdown."; return }
        val resolved = settings.copy(startAtMillis = if (useNow) System.currentTimeMillis() else settings.startAtMillis)
        if (resolved.startAtMillis < System.currentTimeMillis() - 5_000) { error = "Choose a future departure or Start now."; return }
        val epoch = ++generation
        job = viewModelScope.launch {
            busy = true; itinerary = null
            try {
                itinerary = CatchRoutePlanner(PokemonAlertsApi.catchRoutesService).generate(resolved) { if (epoch == generation) progress = it }
                progress = "Route ready"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Route generation failed."; retryAt = (e as? CatchApiException)?.retryAtMillis ?: 0 }
            finally { if (epoch == generation) busy = false }
        }
    }
    /** What the last recommendation found, e.g. "Best start: 14 expected · next best 11". */
    var recommendation by mutableStateOf<String?>(null)
        private set

    /** Picks the start position with the most expected catches for the chosen departure. */
    fun recommendStart() = recommend("start spots") { service, resolved, progress ->
        CatchRouteRecommender(service).recommendStart(resolved, progress = progress).also { hasStart = true }
    }

    /** Picks the departure in the next six hours with the most expected catches from the chosen start. */
    fun recommendTime() = recommend("departure times") { service, resolved, progress ->
        CatchRouteRecommender(service).recommendTime(resolved, progress = progress).also { useNow = false }
    }

    private fun recommend(what: String, search: suspend (CatchRoutesService, CatchRouteSettings, (String) -> Unit) -> CatchRecommendation) {
        if (busy) return
        error = null; recommendation = null
        if (settings.durationMinutes !in 10..360) { error = "Choose 10–360 minutes."; return }
        if (System.currentTimeMillis() < retryAt) { error = "Routing is busy. Retry after the countdown."; return }
        val resolved = settings.copy(startAtMillis = if (useNow) System.currentTimeMillis() else settings.startAtMillis)
        val epoch = ++generation
        job = viewModelScope.launch {
            busy = true; itinerary = null
            try {
                val found = search(PokemonAlertsApi.catchRoutesService, resolved) { if (epoch == generation) progress = it }
                settings = found.settings
                itinerary = found.itinerary
                val zone = java.time.ZoneId.systemDefault()
                fun describe(s: CatchRouteSettings) = java.time.Instant.ofEpochMilli(s.startAtMillis).atZone(zone)
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                // Candidates were ranked on straight-line estimates; only the chosen route's numbers are real.
                recommendation = "Best of ${found.compared} $what · leaving ${describe(found.settings)}"
                progress = "Route ready"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "No recommendation found."; retryAt = (e as? CatchApiException)?.retryAtMillis ?: 0 }
            finally { if (epoch == generation) busy = false }
        }
    }

    fun follow() = viewModelScope.launch {
        itinerary?.let { plan ->
            try { controller.begin(plan) } catch (e: Exception) { error = e.message ?: "Unable to start guidance." }
        }
    }
    fun report(message: String) { error = message }
}
