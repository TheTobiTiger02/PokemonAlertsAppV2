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
import retrofit2.create
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val BASE_URL = "http://match-profiles.gl.at.ply.gg:1855/"

@Serializable
data class HistoryResponse(
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int? = null,
    @SerialName("offset") val offset: Int? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("unlimited") val unlimited: Boolean? = null,
    @SerialName("data") val data: List<PokemonAlert>
)

interface PokemonAlertsService {
    @GET("api/pokemon")
    suspend fun getPokemonAlerts(): List<PokemonAlert>

    @GET("api/history/all")
    suspend fun getHistory(): HistoryResponse
}

object PokemonAlertsApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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
