package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
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

interface PokemonAlertsService {
    @GET("api/pokemon")
    suspend fun getPokemonAlerts(): List<PokemonAlert>

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
}
