package com.example.pokemonalertsv2.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import java.util.LinkedHashSet

class PokemonAlertsRepository @VisibleForTesting internal constructor(
    private val service: PokemonAlertsService,
    private val preferences: AlertPreferencesStore
) {

    suspend fun fetchAlerts(): List<PokemonAlert> = service.getPokemonAlerts()

    suspend fun detectNewAlerts(alerts: List<PokemonAlert>): List<PokemonAlert> {
        if (alerts.isEmpty()) return emptyList()
        val seenIds = preferences.getSeenAlertIds()
        return alerts.filter { it.uniqueId !in seenIds }
    }

    suspend fun markAlertsAsSeen(alerts: Collection<PokemonAlert>) {
        if (alerts.isEmpty()) return
        val current = preferences.getSeenAlertIds()
        val updated = LinkedHashSet<String>(current.size + alerts.size).apply {
            addAll(current)
            alerts.forEach { add(it.uniqueId) }
        }
        preferences.updateSeenAlertIds(updated)
    }

    fun observeSeenAlerts(): Flow<Set<String>> = preferences.seenAlertIds

    companion object {
        fun create(context: Context): PokemonAlertsRepository {
            val appContext = context.applicationContext
            val preferences = AlertPreferences(appContext.alertPreferencesDataStore)
            return PokemonAlertsRepository(
                service = PokemonAlertsApi.service,
                preferences = preferences
            )
        }
    }
}
