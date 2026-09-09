package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query
import retrofit2.create
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val BASE_URL = BuildConfig.ALERTS_API_BASE_URL

@Serializable
data class HistoryResponse(
    @SerialName("total") val total: Int? = null,
    @SerialName("limit") val limit: Int? = null,
    @SerialName("offset") val offset: Int? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("unlimited") val unlimited: Boolean? = null,
    @SerialName("data") val data: List<PokemonAlert> = emptyList()
)

/** Read-only weather state returned for one requested area. */
@Serializable
data class CurrentWeatherResponse(
    val area: String = "",
    val currentWeather: String? = null,
    val observedAt: String? = null,
    val isCurrentHour: Boolean = false,
    val currentWeatherConfirmed: Boolean? = null
) {
    /**
     * Whether the reported weather is confirmed rather than merely the last one seen.
     *
     * The server may name this either way; `isCurrentHour` is the older spelling and means the
     * same thing, so an explicit `currentWeatherConfirmed` wins and it falls back otherwise.
     */
    val confirmed: Boolean get() = currentWeatherConfirmed ?: isCurrentHour
}

/**
 * Server response for /api/stats/total.
 * Passing a date query parameter (YYYY-MM-DD) returns stats scoped to that day.
 * Every field is nullable / defaulted so unknown future keys won't crash parsing.
 *
 * The server nests per-type counts inside a "byType" map:
 * ```json
 * { "totalAlerts": 3054, "totalToday": 42, "byType": { "Hundo": 514, … } }
 * ```
 */
@Serializable
data class TotalStatsResponse(
    @SerialName("totalAlerts")    val totalAlerts: Int? = null,
    @SerialName("totalToday")     val totalToday: Int? = null,
    @SerialName("byType")         val byType: Map<String, Int> = emptyMap(),
    @SerialName("generatedAt")    val generatedAt: String? = null,
    @SerialName("uptime")         val uptime: Long? = null
)

@Serializable
data class WalkingRouteCoordinates(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class WalkingRouteDestination(
    val id: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class WalkingRouteRequest(
    val origin: WalkingRouteCoordinates,
    val destinations: List<WalkingRouteDestination>
)

@Serializable
data class WalkingRouteResult(
    val id: String,
    val status: String,
    val distanceMeters: Int? = null,
    val durationSeconds: Long? = null
)

@Serializable
data class WalkingRoutesResponse(
    val provider: String,
    val calculatedAt: String,
    val routes: List<WalkingRouteResult> = emptyList()
)

@Serializable
data class FilterCatalogObservationWindow(
    val days: Int = 0,
    val start: String = "",
    val end: String = "",
    val historyRowLimit: Int = 0
)

@Serializable
data class FilterCatalogQuest(
    val key: String,
    val taskKey: String,
    val rewardKey: String,
    val task: String,
    val reward: String,
    val active: Boolean = false,
    val lastSeenAt: String? = null
)

@Serializable
data class FilterCatalog(
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val observationWindow: FilterCatalogObservationWindow = FilterCatalogObservationWindow(),
    val areas: List<String> = emptyList(),
    val spawnSpecies: List<String> = emptyList(),
    val raidSpecies: List<String> = emptyList(),
    val raidTiers: List<String> = emptyList(),
    val rocketTypes: List<String> = emptyList(),
    val quests: List<FilterCatalogQuest> = emptyList()
)

interface FilterCatalogService {
    @GET("api/filter-catalog")
    suspend fun getFilterCatalog(): FilterCatalog
}

/**
 * The push topic scheme. Returned as a raw [Response] so a 304 can be told apart from a body:
 * the endpoint is ETagged and cached for an hour, and the catalog changes only when the
 * operator adds an area or renames the base topic.
 */
interface PushTopicsService {
    @GET("api/push-topics")
    suspend fun getPushTopics(
        @Header("If-None-Match") etag: String? = null
    ): Response<PushTopicCatalog>
}

/**
 * Identity of an alert the server dropped from the active set.
 *
 * Carried as the alert's own fields rather than a local key: [PokemonAlert.uniqueId]
 * is this app's invention, and the backend has no business knowing its shape.
 */
@Serializable
data class RemovedAlert(
    val id: Int? = null,
    val name: String? = null,
    val endTime: String? = null
) {
    /** Rebuilt with exactly the rule [PokemonAlert.uniqueId] uses. */
    val uniqueId: String
        get() = id?.let { "server-$it" } ?: "${name.orEmpty().trim()}|${endTime.orEmpty().trim()}"
}

/**
 * The answer to "what changed since revision N".
 *
 * [full] means the server could not answer from its removal history — a restart,
 * a cursor from the future, or an outage longer than the retained log — and
 * [alerts] is a complete snapshot to replace the local cache with. Otherwise
 * [alerts] are upserts and [removed] are deletions.
 */
@Serializable
data class AlertSyncResponse(
    val revision: Long = 0L,
    val full: Boolean = true,
    val alerts: List<PokemonAlert> = emptyList(),
    val removed: List<RemovedAlert> = emptyList()
)

interface PokemonAlertsService {
    /**
     * Returned as a raw [Response] so a 304 is distinguishable from a body — the
     * same reason [PushTopicsService] does. Omitting [since] asks for a full
     * snapshot; passing the last known revision asks for a delta.
     */
    @GET("api/pokemon")
    suspend fun getPokemonAlerts(
        @Query("since") since: Long? = null,
        @Header("If-None-Match") etag: String? = null
    ): Response<AlertSyncResponse>

    /** Looks up durable weather for one area without requiring an alert. */
    @GET("api/current-weather")
    suspend fun getCurrentWeather(@Query("area") area: String): CurrentWeatherResponse

    @GET("api/history/all")
    suspend fun getHistory(
        @Query("type") type: String? = null,
        @Query("date") date: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("q") q: String? = null
    ): HistoryResponse

    @GET("api/history")
    suspend fun getHistoryPaged(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("type") type: String? = null,
        @Query("date") date: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("q") q: String? = null
    ): HistoryResponse

    @GET("api/stats/total")
    suspend fun getTotalStats(
        @Query("date") date: String? = null
    ): TotalStatsResponse

    @POST("api/routes/walking")
    suspend fun getWalkingRoutes(
        @Body request: WalkingRouteRequest
    ): WalkingRoutesResponse
}

object PokemonAlertsApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    val service: PokemonAlertsService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create()
    }

    val pushTopicsService: PushTopicsService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create()
    }

    val catalogService: FilterCatalogService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create()
    }
}
