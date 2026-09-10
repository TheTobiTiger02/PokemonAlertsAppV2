package com.example.pokemonalertsv2.hunt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pokemonalertsv2.data.FilterDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val HUNT_SESSION_STORE = "hunt_session"
private val Context.huntSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = HUNT_SESSION_STORE
)

/**
 * One hunting trip: what you are looking for, and which match you are currently
 * walking to.
 *
 * The "what" is a [FilterDefinition] rather than a bespoke target model because
 * the existing filter vocabulary already says every hunt worth naming — Dragon
 * grunts are `rocketTypes = ONLY[Dragon]`, Spinda quests are an exact quest
 * pair — which means the pickers in Filter Studio work here untouched.
 */
@Serializable
data class HuntSession(
    val name: String,
    val definition: FilterDefinition,
    /**
     * Which row of the saved-hunt list this run came from, so re-running or
     * editing it writes back to the same one. Keeps the old wire name: it was
     * always null in practice, and renaming the JSON field would gain nothing.
     */
    @SerialName("profileId") val savedHuntId: String? = null,
    val startedAtMillis: Long,
    /** The alert being walked to, or null between targets. */
    val targetUniqueId: String? = null
)

/**
 * Stores the active hunt. One at a time, deliberately: a hunt owns the arrival
 * journey and the status-bar chip, and neither can be pointed at two things.
 *
 * Modelled on [com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository]
 * down to the single-JSON-slot layout, so the two behave the same way under
 * process death.
 */
class HuntRepository private constructor(context: Context) {
    private val dataStore = context.applicationContext.huntSessionDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val sessionFlow: Flow<HuntSession?> = dataStore.data
        .map { preferences -> preferences[ACTIVE_HUNT_KEY]?.decodeSession() }
        .distinctUntilChanged()

    val activeHunt = sessionFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    suspend fun currentSession(): HuntSession? =
        dataStore.data.first()[ACTIVE_HUNT_KEY]?.decodeSession()

    /** True without waiting for the eager [activeHunt] to warm up. */
    suspend fun isHunting(): Boolean = currentSession() != null

    suspend fun start(
        name: String,
        definition: FilterDefinition,
        savedHuntId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): HuntSession {
        val session = HuntSession(
            name = name,
            definition = definition,
            savedHuntId = savedHuntId,
            startedAtMillis = nowMillis
        )
        write(session)
        return session
    }

    suspend fun stop() {
        dataStore.edit { preferences -> preferences.remove(ACTIVE_HUNT_KEY) }
    }

    /**
     * Points the hunt at [uniqueId], or clears the target with null. A no-op
     * when no hunt is running, so a stale "Got it" cannot resurrect one.
     */
    suspend fun setTarget(uniqueId: String?) {
        dataStore.edit { preferences ->
            val session = preferences[ACTIVE_HUNT_KEY]?.decodeSession() ?: return@edit
            preferences[ACTIVE_HUNT_KEY] =
                json.encodeToString(HuntSession.serializer(), session.copy(targetUniqueId = uniqueId))
        }
    }

    // -- Saved hunts ------------------------------------------------------
    //
    // Same store as the active hunt: they are the same subject, and one store
    // means one thing to restore after process death.

    val savedHuntsFlow: Flow<List<SavedHunt>> = dataStore.data
        .map { preferences -> savedHuntOrder(preferences[SAVED_HUNTS_KEY].decodeSavedHunts()) }
        .distinctUntilChanged()

    val savedHunts = savedHuntsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    suspend fun currentSavedHunts(): List<SavedHunt> =
        savedHuntOrder(dataStore.data.first()[SAVED_HUNTS_KEY].decodeSavedHunts())

    /**
     * Remembers a hunt that is being started, and answers with the row it belongs
     * to — new, or the existing one it matched.
     */
    suspend fun recordStart(
        name: String,
        definition: FilterDefinition,
        replacingId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString()
    ): SavedHunt {
        var recorded: SavedHunt? = null
        dataStore.edit { preferences ->
            val (updated, row) = recordStartedHunt(
                existing = preferences[SAVED_HUNTS_KEY].decodeSavedHunts(),
                name = name,
                definition = definition,
                nowMillis = nowMillis,
                id = id,
                replacingId = replacingId
            )
            preferences[SAVED_HUNTS_KEY] = updated.encode()
            recorded = row
        }
        return recorded ?: SavedHunt(id, name, definition, nowMillis)
    }

    suspend fun renameSavedHunt(id: String, name: String) = editSavedHunts { renameSavedHunt(it, id, name) }

    suspend fun deleteSavedHunt(id: String) = editSavedHunts { removeSavedHunt(it, id) }

    private suspend fun editSavedHunts(transform: (List<SavedHunt>) -> List<SavedHunt>) {
        dataStore.edit { preferences ->
            preferences[SAVED_HUNTS_KEY] = transform(preferences[SAVED_HUNTS_KEY].decodeSavedHunts()).encode()
        }
    }

    /** A blob written by a future build, or a corrupt one, reads as no saved hunts. */
    private fun String?.decodeSavedHunts(): List<SavedHunt> = this?.let { raw ->
        runCatching { json.decodeFromString(ListSerializer(SavedHunt.serializer()), raw) }.getOrNull()
    } ?: emptyList()

    private fun List<SavedHunt>.encode(): String =
        json.encodeToString(ListSerializer(SavedHunt.serializer()), this)

    private suspend fun write(session: HuntSession) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_HUNT_KEY] = json.encodeToString(HuntSession.serializer(), session)
        }
    }

    private fun String.decodeSession(): HuntSession? =
        runCatching { json.decodeFromString(HuntSession.serializer(), this) }.getOrNull()

    companion object {
        private val ACTIVE_HUNT_KEY = stringPreferencesKey("active_hunt")
        private val SAVED_HUNTS_KEY = stringPreferencesKey("saved_hunts")

        @Volatile
        private var instance: HuntRepository? = null

        fun getInstance(context: Context): HuntRepository =
            instance ?: synchronized(this) {
                instance ?: HuntRepository(context).also { instance = it }
            }
    }
}
