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
import retrofit2.http.Query
import retrofit2.create
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val BASE_URL = "http://api.alsbach-scanner.uk"

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
    suspend fun getTotalStats(): TotalStatsResponse
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
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
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
