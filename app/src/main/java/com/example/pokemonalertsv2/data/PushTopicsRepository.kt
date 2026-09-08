package com.example.pokemonalertsv2.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Caches the push topic scheme, mirroring [FilterCatalogRepository].
 *
 * The endpoint is ETagged, so a refresh normally costs a 304 with no body. A failed refresh
 * always falls back to the cached copy: losing the catalog must never silently widen or
 * narrow what the device is subscribed to.
 */
class PushTopicsRepository private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("push_topics_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun cached(): PushTopicCatalog? = preferences.getString(CACHE_KEY, null)?.let { raw ->
        runCatching { json.decodeFromString(PushTopicCatalog.serializer(), raw) }.getOrNull()
    }

    /** Returns the freshest catalog available, or null when there has never been one. */
    suspend fun refresh(): PushTopicCatalog? = runCatching {
        val response = PokemonAlertsApi.pushTopicsService.getPushTopics(preferences.getString(ETAG_KEY, null))
        val body = response.body()
        if (response.code() == HTTP_NOT_MODIFIED || body == null) return@runCatching cached()
        preferences.edit()
            .putString(CACHE_KEY, json.encodeToString(PushTopicCatalog.serializer(), body))
            .putString(ETAG_KEY, response.headers()["ETag"])
            .apply()
        body
    }.getOrElse { cached() }

    companion object {
        private const val CACHE_KEY = "catalog_v1"
        private const val ETAG_KEY = "etag_v1"
        private const val HTTP_NOT_MODIFIED = 304

        @Volatile private var instance: PushTopicsRepository? = null

        fun getInstance(context: Context): PushTopicsRepository {
            return instance ?: synchronized(this) {
                instance ?: PushTopicsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
