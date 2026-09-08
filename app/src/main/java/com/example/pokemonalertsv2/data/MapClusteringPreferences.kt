package com.example.pokemonalertsv2.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class MapGrouping { CURRENT, DISTANCE, GRID, COINCIDENT }

/**
 * Marker limits count markers *on screen*.
 *
 * They used to be counted over the padded box markers are prepared for, which on a phone held
 * roughly eight screenfuls - so every number here meant about an eighth of itself in practice,
 * and "350" engaged at around forty visible markers. Now that the count is honest the numbers
 * have been brought down to match, and then some: a portrait phone fits about 150 marker-sized
 * pins edge to edge, so a close-zoom limit in the low hundreds is already a busy map.
 */
@Serializable
data class MapClusteringConfig(
    val grouping: MapGrouping = MapGrouping.DISTANCE,
    val distanceDp: Int = 24,
    val zoomCutoff: Int = 11,
    val overviewLimit: Int = 120,
    val closeLimit: Int = 400
) {
    fun normalized() = copy(distanceDp = distanceDp.coerceIn(8, 80),
        zoomCutoff = zoomCutoff.coerceIn(8, 18), overviewLimit = overviewLimit.coerceIn(25, 600),
        closeLimit = closeLimit.coerceIn(50, 1200))
}

enum class MapClusteringPreset(val label: String, val config: MapClusteringConfig) {
    CURRENT("Current behavior", MapClusteringConfig(MapGrouping.CURRENT, 40, 12, 80, 250)),
    LESS("Less clustering", MapClusteringConfig()),
    OVERLAP("Overlap only", MapClusteringConfig(MapGrouping.DISTANCE, 12, 14)),
    STACKS("Same-location stacks", MapClusteringConfig(grouping = MapGrouping.COINCIDENT)),
    PERFORMANCE("Performance", MapClusteringConfig(MapGrouping.GRID, 48, 14, 60, 150))
}

@Serializable
data class MapClusteringSettings(
    val preset: String = MapClusteringPreset.LESS.name,
    val custom: MapClusteringConfig = MapClusteringConfig()
) {
    val config: MapClusteringConfig get() = if (preset == "CUSTOM") custom.normalized()
        else MapClusteringPreset.entries.firstOrNull { it.name == preset }?.config ?: MapClusteringConfig()
    val label: String get() = if (preset == "CUSTOM") "Custom" else
        MapClusteringPreset.entries.firstOrNull { it.name == preset }?.label ?: "Less clustering"
}

/** Uses the existing preference store so normal settings backups include both preset and custom values. */
class MapClusteringPreferences(private val store: DataStore<Preferences>) {
    val settings = store.data.map { it[KEY] }.distinctUntilChanged().map(::decode)
    suspend fun select(preset: String) = store.edit { prefs ->
        prefs[KEY] = JSON.encodeToString(MapClusteringSettings.serializer(), decode(prefs[KEY]).copy(preset = preset))
    }
    suspend fun customize(config: MapClusteringConfig) = store.edit { prefs ->
        prefs[KEY] = JSON.encodeToString(MapClusteringSettings.serializer(),
            MapClusteringSettings("CUSTOM", config.normalized()))
    }
    suspend fun reset() { store.edit { it.remove(KEY) } }
    companion object {
        val KEY = stringPreferencesKey("map_clustering_settings")
        private val JSON = Json { ignoreUnknownKeys = true }
        fun decode(value: String?): MapClusteringSettings = value?.let {
            runCatching { JSON.decodeFromString(MapClusteringSettings.serializer(), it) }.getOrNull()
        } ?: MapClusteringSettings()
    }
}
