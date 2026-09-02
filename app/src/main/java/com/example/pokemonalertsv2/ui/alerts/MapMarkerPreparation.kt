package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class PreparedMapMarkers(
    val alerts: List<PokemonAlert> = emptyList(),
    val items: List<MapMarkerItem> = emptyList(),
    val spawnAlerts: List<PokemonAlert> = emptyList()
)

/** SDK annotation creation is synchronous. Spread new overlays across frames, retaining survivors. */
@Composable
internal fun <T> rememberBatchedMapItems(
    target: List<T>,
    batchSize: Int = 12,
    keyOf: (T) -> String
): State<List<T>> = produceState<List<T>>(emptyList(), target, batchSize) {
    val existingIds = value.mapTo(hashSetOf(), keyOf)
    val retained = target.filter { keyOf(it) in existingIds }
    val additions = target.filterNot { keyOf(it) in existingIds }
    if (additions.isEmpty()) {
        value = target
    } else {
        var count = 0
        while (count < additions.size) {
            currentCoroutineContext().ensureActive()
            count = (count + batchSize.coerceAtLeast(1)).coerceAtMost(additions.size)
            value = if (count == additions.size) target else retained + additions.take(count)
            if (count < additions.size) withFrameNanos { }
        }
    }
}

/** Retain the previous bounded set while preparing the latest camera/data snapshot. */
@Composable
internal fun rememberPreparedMapMarkers(
    alerts: List<PokemonAlert>,
    bounds: MapGeoBounds?,
    zoom: Double,
    spawnRadius: Double?,
    protectedIds: Set<String>
): State<PreparedMapMarkers> = produceState(
    initialValue = PreparedMapMarkers(),
    alerts, bounds, zoom, spawnRadius, protectedIds
) {
    value = prepareMapMarkers(alerts, bounds, zoom, spawnRadius, protectedIds)
}

internal suspend fun prepareMapMarkers(
    alerts: List<PokemonAlert>,
    bounds: MapGeoBounds?,
    zoom: Double,
    spawnRadius: Double?,
    protectedIds: Set<String>
): PreparedMapMarkers = withContext(Dispatchers.Default) {
    val context = currentCoroutineContext()
    val visible = alerts.filter { alert ->
        context.ensureActive()
        alert.uniqueId in protectedIds || (alert.mapCoordinatesOrNull()?.let {
            bounds == null || bounds.contains(it.latitude, it.longitude)
        } == true)
    }
    val items = clusterMapAlerts(
        alerts = visible,
        zoom = zoom,
        spawnRadiusMeters = spawnRadius,
        protectedAlertIds = protectedIds,
        checkActive = { context.ensureActive() }
    )
    val spawnAlerts = if (spawnRadius != null && zoom >= SPAWN_CIRCLE_MIN_ZOOM) {
        val latitude = bounds?.let { (it.south + it.north) / 2.0 } ?: 0.0
        val longitude = bounds?.let { (it.west + it.east) / 2.0 } ?: 0.0
        visible.filter { context.ensureActive(); it.isSpawnAlert }
            .sortedBy {
                val dy = (it.latitude ?: latitude) - latitude
                val dx = (it.longitude ?: longitude) - longitude
                dy * dy + dx * dx
            }.take(MAX_SPAWN_CIRCLES)
    } else emptyList()
    context.ensureActive()
    PreparedMapMarkers(
        visible,
        items.sortedBy { if (it is MapMarkerItem.Alert && it.alert.uniqueId in protectedIds) 0 else 1 },
        spawnAlerts
    )
}
