package com.example.pokemonalertsv2.tracking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ARRIVAL_TRACKING_STORE = "arrival_tracking"
private val Context.arrivalTrackingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = ARRIVAL_TRACKING_STORE
)

@Serializable
data class TrackedDestination(
    val alert: PokemonAlert,
    val radiusMeters: Int,
    val startedAtMillis: Long
) {
    val uniqueId: String get() = alert.uniqueId
    val latitude: Double get() = requireNotNull(alert.latitude)
    val longitude: Double get() = requireNotNull(alert.longitude)
}

internal fun PokemonAlert.isEligibleArrivalDestination(
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val lat = latitude ?: return false
    val lng = longitude ?: return false
    if (!lat.isFinite() || !lng.isFinite()) return false
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
    if (lat == 0.0 && lng == 0.0) return false
    if (isInvalidated) return false
    return TimeUtils.parseEndTimeToMillis(endTime)?.let { it > nowMillis } ?: true
}

class ArrivalTrackingRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.arrivalTrackingDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val destinationFlow: Flow<TrackedDestination?> = dataStore.data
        .map { preferences -> preferences[ACTIVE_DESTINATION_KEY]?.decodeDestination() }
        .distinctUntilChanged()

    val activeDestination = destinationFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val arrivalRadiusMeters = dataStore.data
        .map { preferences ->
            normalizeRadius(preferences[ARRIVAL_RADIUS_KEY] ?: DEFAULT_RADIUS_METERS)
        }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_RADIUS_METERS
        )

    suspend fun currentDestination(): TrackedDestination? =
        dataStore.data.first()[ACTIVE_DESTINATION_KEY]?.decodeDestination()

    suspend fun startTracking(
        alert: PokemonAlert,
        nowMillis: Long = System.currentTimeMillis()
    ): TrackedDestination {
        require(alert.isEligibleArrivalDestination(nowMillis)) {
            "Alert is not an active destination with valid coordinates"
        }
        val radius = normalizeRadius(
            dataStore.data.first()[ARRIVAL_RADIUS_KEY] ?: DEFAULT_RADIUS_METERS
        )
        val destination = TrackedDestination(
            alert = alert,
            radiusMeters = radius,
            startedAtMillis = nowMillis
        )
        dataStore.edit { preferences ->
            preferences[ACTIVE_DESTINATION_KEY] = json.encodeToString(destination)
        }
        return destination
    }

    suspend fun stopTracking() {
        dataStore.edit { preferences -> preferences.remove(ACTIVE_DESTINATION_KEY) }
    }

    suspend fun updateArrivalRadius(radiusMeters: Int) {
        val normalized = normalizeRadius(radiusMeters)
        dataStore.edit { preferences ->
            preferences[ARRIVAL_RADIUS_KEY] = normalized
            preferences[ACTIVE_DESTINATION_KEY]
                ?.decodeDestination()
                ?.copy(radiusMeters = normalized)
                ?.let { preferences[ACTIVE_DESTINATION_KEY] = json.encodeToString(it) }
        }
    }

    private fun String.decodeDestination(): TrackedDestination? =
        runCatching { json.decodeFromString<TrackedDestination>(this) }.getOrNull()

    companion object {
        const val DEFAULT_RADIUS_METERS = 40
        const val MIN_RADIUS_METERS = 20
        const val MAX_RADIUS_METERS = 200
        const val RADIUS_STEP_METERS = 5

        private val ACTIVE_DESTINATION_KEY = stringPreferencesKey("active_destination")
        private val ARRIVAL_RADIUS_KEY = intPreferencesKey("arrival_radius_meters")

        @Volatile
        private var instance: ArrivalTrackingRepository? = null

        fun getInstance(context: Context): ArrivalTrackingRepository =
            instance ?: synchronized(this) {
                instance ?: ArrivalTrackingRepository(context).also { instance = it }
            }

        fun normalizeRadius(radiusMeters: Int): Int {
            val clamped = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
            val steps = ((clamped - MIN_RADIUS_METERS) / RADIUS_STEP_METERS.toFloat())
                .toInt()
            val lower = MIN_RADIUS_METERS + steps * RADIUS_STEP_METERS
            val upper = (lower + RADIUS_STEP_METERS).coerceAtMost(MAX_RADIUS_METERS)
            return if (clamped - lower < upper - clamped) lower else upper
        }
    }
}

internal enum class ArrivalFixResult {
    WAITING,
    FIRST_IN_RANGE,
    ARRIVED
}

internal class ArrivalFixEvaluator(
    private val minimumConfirmationMillis: Long = 2_000L
) {
    private var firstQualifyingFixAtMillis: Long? = null

    fun evaluate(
        distanceMeters: Float,
        accuracyMeters: Float,
        radiusMeters: Int,
        elapsedRealtimeMillis: Long
    ): ArrivalFixResult {
        val maximumAccuracy = minOf(radiusMeters.toFloat(), 50f)
        val qualifying = distanceMeters.isFinite() &&
            accuracyMeters.isFinite() &&
            distanceMeters <= radiusMeters &&
            accuracyMeters in 0f..maximumAccuracy

        if (!qualifying) {
            firstQualifyingFixAtMillis = null
            return ArrivalFixResult.WAITING
        }

        val first = firstQualifyingFixAtMillis
        if (first == null) {
            firstQualifyingFixAtMillis = elapsedRealtimeMillis
            return ArrivalFixResult.FIRST_IN_RANGE
        }
        return if (elapsedRealtimeMillis - first >= minimumConfirmationMillis) {
            ArrivalFixResult.ARRIVED
        } else {
            ArrivalFixResult.FIRST_IN_RANGE
        }
    }

    fun reset() {
        firstQualifyingFixAtMillis = null
    }
}
