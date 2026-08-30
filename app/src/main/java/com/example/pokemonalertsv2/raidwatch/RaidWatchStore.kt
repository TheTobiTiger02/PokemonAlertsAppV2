package com.example.pokemonalertsv2.raidwatch

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The single raid currently being watched.
 *
 * One at a time, deliberately: the Live Update owns a lock-screen slot, and a second one
 * competing for it would make both harder to read. Persisted so the watch survives process
 * death, the same way [com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository]
 * persists its destination.
 */
class RaidWatchStore(private val context: Context) {

    private val dataStore get() = context.alertPreferencesDataStore

    val watched: Flow<WatchedRaid?> = dataStore.data.map { prefs ->
        val alertJson = prefs[KEY_ALERT_JSON] ?: return@map null
        val alert = decode(alertJson) ?: return@map null
        WatchedRaid(
            alert = alert,
            startedAtMillis = prefs[KEY_STARTED_AT] ?: System.currentTimeMillis(),
            endMillis = prefs[KEY_END_AT] ?: return@map null
        )
    }

    /**
     * The suggested six for the watched raid, once [RaidTeamPrefetcher] has produced it.
     *
     * Null means "not computed yet"; a snapshot with no members means "there is no team to
     * compute", which the notification renders differently.
     */
    val teamSnapshot: Flow<RaidTeamSnapshot?> = dataStore.data.map { prefs ->
        prefs[KEY_TEAM_JSON]?.let(::decodeTeam)
    }

    /** One-shot read. Deliberately not `edit`, which would write on every read. */
    suspend fun current(): WatchedRaid? = watched.first()

    suspend fun currentTeam(): RaidTeamSnapshot? = teamSnapshot.first()

    suspend fun save(alert: PokemonAlert, startedAtMillis: Long, endMillis: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_ALERT_JSON] = json.encodeToString(PokemonAlert.serializer(), alert)
            prefs[KEY_STARTED_AT] = startedAtMillis
            prefs[KEY_END_AT] = endMillis
            // A new raid's team has not been computed yet, and the outgoing one must not be
            // shown against it.
            prefs.remove(KEY_TEAM_JSON)
        }
    }

    suspend fun saveTeam(snapshot: RaidTeamSnapshot) {
        dataStore.edit { prefs ->
            prefs[KEY_TEAM_JSON] = json.encodeToString(RaidTeamSnapshot.serializer(), snapshot)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ALERT_JSON)
            prefs.remove(KEY_STARTED_AT)
            prefs.remove(KEY_END_AT)
            prefs.remove(KEY_TEAM_JSON)
        }
    }

    private fun decode(alertJson: String): PokemonAlert? =
        runCatching { json.decodeFromString(PokemonAlert.serializer(), alertJson) }.getOrNull()

    private fun decodeTeam(teamJson: String): RaidTeamSnapshot? =
        runCatching { json.decodeFromString(RaidTeamSnapshot.serializer(), teamJson) }.getOrNull()

    companion object {
        private val KEY_ALERT_JSON = stringPreferencesKey("raid_watch_alert_json")
        private val KEY_STARTED_AT = longPreferencesKey("raid_watch_started_at")
        private val KEY_END_AT = longPreferencesKey("raid_watch_end_at")
        private val KEY_TEAM_JSON = stringPreferencesKey("raid_watch_team_json")

        private val json = Json { ignoreUnknownKeys = true }
    }
}

data class WatchedRaid(
    val alert: PokemonAlert,
    val startedAtMillis: Long,
    val endMillis: Long
)
