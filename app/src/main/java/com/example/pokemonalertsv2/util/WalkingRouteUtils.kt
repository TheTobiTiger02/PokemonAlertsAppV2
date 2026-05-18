package com.example.pokemonalertsv2.util

import android.location.Location
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

data class WalkingRouteInfo(
    val distanceMeters: Int,
    val durationSeconds: Long
)

data class RouteDisplayInfo(
    val straightLineDistanceMeters: Float?,
    val distanceText: String?,
    val walkingText: String?
)

object WalkingRouteUtils {
    private const val BASE_URL = "https://api.openrouteservice.org/"
    private const val METRIC_DISTANCE = "distance"
    private const val METRIC_DURATION = "duration"
    private const val UNIT_METERS = "m"
    private const val MAX_DESTINATIONS_PER_REQUEST = 25
    private const val CACHE_TTL_MS = 2 * 60 * 1000L
    private const val DEFAULT_TIMEOUT_MS = 4_000L

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val service: OpenRouteServiceMatrixService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenRouteServiceMatrixService::class.java)
    }

    suspend fun getWalkingRoutes(
        origin: Location,
        alerts: List<PokemonAlert>,
        apiKey: String = BuildConfig.OPENROUTESERVICE_API_KEY,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Map<String, WalkingRouteInfo> = withContext(Dispatchers.IO) {
        if (!isRoutesApiKeyConfigured(apiKey) || alerts.isEmpty()) return@withContext emptyMap()

        val now = System.currentTimeMillis()
        val destinations = alerts.mapNotNull { alert ->
            val latitude = alert.latitude ?: return@mapNotNull null
            val longitude = alert.longitude ?: return@mapNotNull null
            RouteDestination(
                alertId = alert.uniqueId,
                latitude = latitude,
                longitude = longitude,
                cacheKey = cacheKey(origin.latitude, origin.longitude, latitude, longitude)
            )
        }
        if (destinations.isEmpty()) return@withContext emptyMap()

        val routeResults = mutableMapOf<String, WalkingRouteInfo>()
        val missingDestinations = mutableListOf<RouteDestination>()
        destinations.forEach { destination ->
            val cached = cache[destination.cacheKey]
            if (cached != null && now - cached.createdAtMillis <= CACHE_TTL_MS) {
                routeResults[destination.alertId] = cached.info
            } else {
                cache.remove(destination.cacheKey)
                missingDestinations += destination
            }
        }

        missingDestinations.chunked(MAX_DESTINATIONS_PER_REQUEST).forEach { chunk ->
            val response = withTimeoutOrNull(timeoutMs) {
                runCatching {
                    service.computeWalkingMatrix(
                        apiKey = apiKey,
                        request = OpenRouteServiceMatrixRequest(
                            locations = listOf(
                                listOf(origin.longitude, origin.latitude)
                            ) + chunk.map { destination ->
                                listOf(destination.longitude, destination.latitude)
                            },
                            sources = listOf(0),
                            destinations = chunk.indices.map { it + 1 },
                            metrics = listOf(METRIC_DISTANCE, METRIC_DURATION),
                            units = UNIT_METERS
                        )
                    )
                }.getOrNull()
            }

            chunk.forEachIndexed { index, destination ->
                val routeInfo = routeInfoFromMatrixCells(
                    durationSeconds = response?.durations?.firstOrNull()?.getOrNull(index),
                    distanceMeters = response?.distances?.firstOrNull()?.getOrNull(index)
                ) ?: return@forEachIndexed
                cache[destination.cacheKey] = CacheEntry(routeInfo, System.currentTimeMillis())
                routeResults[destination.alertId] = routeInfo
            }
        }

        routeResults
    }

    fun isRoutesApiKeyConfigured(apiKey: String?): Boolean = !apiKey.isNullOrBlank()

    fun buildRouteDisplayInfo(
        straightLineDistanceMeters: Float?,
        routeInfo: WalkingRouteInfo?
    ): RouteDisplayInfo {
        return RouteDisplayInfo(
            straightLineDistanceMeters = straightLineDistanceMeters,
            distanceText = routeInfo?.let { formatDistanceMeters(it.distanceMeters.toFloat()) }
                ?: straightLineDistanceMeters?.let { formatDistanceMeters(it) },
            walkingText = routeInfo?.let { formatWalkingDurationSeconds(it.durationSeconds) }
        )
    }

    fun routeInfoFromMatrixCells(
        durationSeconds: Double?,
        distanceMeters: Double?
    ): WalkingRouteInfo? {
        val parsedDuration = durationSeconds?.takeIf { it >= 0.0 } ?: return null
        val parsedDistance = distanceMeters?.takeIf { it >= 0.0 } ?: return null
        return WalkingRouteInfo(
            distanceMeters = ceil(parsedDistance).toInt(),
            durationSeconds = ceil(parsedDuration).toLong()
        )
    }

    fun formatDistanceMeters(meters: Float): String {
        return if (meters >= 1000f) {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.0f m", meters)
        }
    }

    fun formatWalkingDurationSeconds(seconds: Long): String {
        val minutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1)
        return String.format(Locale.getDefault(), "%d min walk", minutes)
    }

    private fun cacheKey(
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): String {
        return listOf(
            originLatitude.formatForCache(decimals = 4),
            originLongitude.formatForCache(decimals = 4),
            destinationLatitude.formatForCache(decimals = 6),
            destinationLongitude.formatForCache(decimals = 6)
        ).joinToString("|")
    }

    private fun Double.formatForCache(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)
}

private data class CacheEntry(
    val info: WalkingRouteInfo,
    val createdAtMillis: Long
)

private data class RouteDestination(
    val alertId: String,
    val latitude: Double,
    val longitude: Double,
    val cacheKey: String
)

private interface OpenRouteServiceMatrixService {
    @POST("v2/matrix/foot-walking")
    suspend fun computeWalkingMatrix(
        @Header("Authorization") apiKey: String,
        @Body request: OpenRouteServiceMatrixRequest
    ): OpenRouteServiceMatrixResponse
}

@Serializable
private data class OpenRouteServiceMatrixRequest(
    @SerialName("locations") val locations: List<List<Double>>,
    @SerialName("sources") val sources: List<Int>,
    @SerialName("destinations") val destinations: List<Int>,
    @SerialName("metrics") val metrics: List<String>,
    @SerialName("units") val units: String
)

@Serializable
private data class OpenRouteServiceMatrixResponse(
    @SerialName("durations") val durations: List<List<Double?>>? = null,
    @SerialName("distances") val distances: List<List<Double?>>? = null
)
