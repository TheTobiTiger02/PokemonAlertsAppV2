package com.example.pokemonalertsv2.data

import android.content.Context
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Area weather from `/api/current-weather`, cached in memory.
 *
 * This is a *fallback* source: an alert that carries its own `currentWeather` always wins. It
 * fills the gap for cached and pushed alerts that arrive without weather, and it feeds the map
 * badge. Failures return null rather than propagating — weather is a hint here, never something
 * worth failing a screen over.
 */
class CurrentWeatherRepository @VisibleForTesting internal constructor(
    private val fetch: suspend (String) -> CurrentWeatherResponse,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) {

    private data class Cached(val response: CurrentWeatherResponse, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Cached>()

    /**
     * Last known weather for [area], or null when the area is unusable, the lookup fails, or the
     * server has nothing.
     */
    suspend fun weatherFor(area: String?): CurrentWeatherResponse? {
        val normalized = area?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // "All" is the app's every-area pseudo-value; the endpoint is per-area only.
        if (normalized.equals(ALL_AREAS, ignoreCase = true)) return null

        // The lock also coalesces concurrent callers, so a screen opening several cards at once
        // makes one request rather than one per card.
        return mutex.withLock {
            val cached = cache[normalized]
            if (cached != null && elapsedRealtime() - cached.fetchedAt < CACHE_TTL_MILLIS) {
                return@withLock cached.response
            }
            val fresh = runCatching { fetch(normalized) }.getOrNull()
                ?.takeIf { !it.currentWeather.isNullOrBlank() }
            if (fresh != null) {
                cache[normalized] = Cached(fresh, elapsedRealtime())
            }
            // A failed refresh keeps serving the stale value; nothing is better than nothing.
            fresh ?: cached?.response
        }
    }

    companion object {
        const val ALL_AREAS = "All"

        /** Game weather changes on the hour, so a few minutes of staleness is invisible. */
        private const val CACHE_TTL_MILLIS = 5 * 60 * 1000L

        @Volatile
        private var INSTANCE: CurrentWeatherRepository? = null

        fun getInstance(context: Context): CurrentWeatherRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val repository = PokemonAlertsRepository.create(context.applicationContext)
                    CurrentWeatherRepository(fetch = repository::getCurrentWeather)
                }.also { INSTANCE = it }
            }
        }
    }
}
