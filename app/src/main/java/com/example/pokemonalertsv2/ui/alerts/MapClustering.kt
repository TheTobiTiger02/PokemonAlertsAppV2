package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Neighborhood/default view and closer show individual markers; only exact stacks stay grouped. */
internal const val MAP_CLUSTER_MAX_ZOOM = 13.0

/**
 * Cluster distance in *map dp*. [projectMapAlertToScreen] projects with 256 units per tile, which is
 * the density-independent unit both map backends use for a given zoom, so this must never be scaled
 * by the display density. 48 dp matches the rendered cluster icon, i.e. markers are only merged once
 * they would actually overlap.
 */
internal const val MAP_CLUSTER_CELL_DP = 48f

private const val MIN_VISIBLE_RADIUS_DP = 8.0
private const val METERS_PER_MAP_DP_AT_ZOOM_ZERO = 156_543.03392

internal data class MapGeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
)

internal data class MapScreenPoint(
    val x: Double,
    val y: Double
)

internal sealed interface MapMarkerItem {
    val latitude: Double
    val longitude: Double

    data class Alert(
        val alert: PokemonAlert,
        override val latitude: Double,
        override val longitude: Double
    ) : MapMarkerItem

    data class Cluster(
        val id: String,
        val alerts: List<PokemonAlert>,
        override val latitude: Double,
        override val longitude: Double,
        val bounds: MapGeoBounds,
        val sharedCategory: AlertFilter?
    ) : MapMarkerItem
}

internal sealed interface MapClusterInteraction {
    data object ShowMembers : MapClusterInteraction

    data class Expand(
        val alertIds: List<String>,
        val originZoom: Double
    ) : MapClusterInteraction
}

/** Radius of the drawn spawn circle, or null when spawn radii are hidden. */
internal fun spawnRadiusMeters(showSpawnRadius: Boolean, spacialRendEnabled: Boolean): Double? =
    when {
        !showSpawnRadius -> null
        spacialRendEnabled -> 80.0
        else -> 40.0
    }

/**
 * Provider-independent screen-space clustering. At maximum zoom only coincident
 * coordinates are grouped, so stacked alerts remain selectable.
 *
 * When spawn radii are drawn ([spawnRadiusMeters] non-null) two spawn alerts are only merged while
 * their circles still overlap, so visibly separate circles never hide behind a count bubble.
 */
internal fun clusterMapAlerts(
    alerts: List<PokemonAlert>,
    zoom: Double,
    cellDp: Float = MAP_CLUSTER_CELL_DP,
    spawnRadiusMeters: Double? = null,
    expandedAlertIds: Set<String> = emptySet(),
    protectedAlertIds: Set<String> = emptySet()
): List<MapMarkerItem> {
    val positioned = alerts.mapNotNull { alert ->
        val latitude = alert.latitude ?: return@mapNotNull null
        val longitude = alert.longitude ?: return@mapNotNull null
        if (latitude !in -85.0..85.0 || longitude !in -180.0..180.0) return@mapNotNull null
        PositionedAlert(alert, latitude, longitude)
    }.sortedBy { it.alert.uniqueId }
    if (positioned.isEmpty()) return emptyList()

    val protectedGroups = positioned
        .filter { it.alert.uniqueId in protectedAlertIds }
        .map(::listOf)
    val clusterable = positioned.filterNot { it.alert.uniqueId in protectedAlertIds }
    val groups = if (zoom < MAP_CLUSTER_MAX_ZOOM) {
        val scale = 256.0 * 2.0.pow(zoom)
        val thresholdDp = max(1.0, cellDp.toDouble())
        val radiusGuardDp = spawnCircleGuardDp(clusterable, zoom, spawnRadiusMeters)
        val guardedThresholdDp = min(thresholdDp, radiusGuardDp ?: thresholdDp)
        val normalAlerts = clusterable.filterNot { it.alert.uniqueId in expandedAlertIds }
        val expandedGroups = clusterable
            .filter { it.alert.uniqueId in expandedAlertIds }
            .groupBy(::exactCoordinateKey)
            .values
            .map { it.toList() }
        val normalPoints = normalAlerts.map { projectMapAlertToScreen(it, scale) }
        val guarded = BooleanArray(normalAlerts.size) { index ->
            radiusGuardDp != null && normalAlerts[index].alert.isSpawnAlert
        }
        val normalGroups = connectedMapScreenComponents(normalPoints) { first, second ->
            if (guarded[first] && guarded[second]) guardedThresholdDp else thresholdDp
        }.map { indices ->
            indices.map(normalAlerts::get)
        }
        (normalGroups + expandedGroups + protectedGroups).sortedBy { members ->
            members.minOf { it.alert.uniqueId }
        }
    } else {
        (clusterable.groupBy(::exactCoordinateKey).values.map { it.toList() } + protectedGroups)
            .sortedBy { members -> members.minOf { it.alert.uniqueId } }
    }

    return groups.map { members ->
        if (members.size == 1) {
            val item = members.first()
            MapMarkerItem.Alert(item.alert, item.latitude, item.longitude)
        } else {
            val south = members.minOf { it.latitude }
            val west = members.minOf { it.longitude }
            val north = members.maxOf { it.latitude }
            val east = members.maxOf { it.longitude }
            val categories = members.map { categoryForMapAlert(it.alert) }.distinct()
            MapMarkerItem.Cluster(
                id = members.map { it.alert.uniqueId }.sorted().joinToString("|").hashCode().toString(),
                alerts = members.map { it.alert },
                latitude = (south + north) / 2.0,
                longitude = (west + east) / 2.0,
                bounds = MapGeoBounds(south, west, north, east),
                sharedCategory = categories.singleOrNull()
            )
        }
    }
}

internal fun connectedMapScreenComponents(
    points: List<MapScreenPoint>,
    thresholdPx: Double
): List<List<Int>> = connectedMapScreenComponents(points) { _, _ -> thresholdPx }

internal fun connectedMapScreenComponents(
    points: List<MapScreenPoint>,
    thresholdFor: (Int, Int) -> Double
): List<List<Int>> {
    if (points.isEmpty()) return emptyList()
    val parent = IntArray(points.size) { it }

    fun find(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }

    fun union(first: Int, second: Int) {
        val firstRoot = find(first)
        val secondRoot = find(second)
        if (firstRoot == secondRoot) return
        if (firstRoot < secondRoot) {
            parent[secondRoot] = firstRoot
        } else {
            parent[firstRoot] = secondRoot
        }
    }

    points.indices.forEach { firstIndex ->
        for (secondIndex in firstIndex + 1 until points.size) {
            val first = points[firstIndex]
            val second = points[secondIndex]
            val deltaX = first.x - second.x
            val deltaY = first.y - second.y
            val threshold = thresholdFor(firstIndex, secondIndex).coerceAtLeast(1.0)
            if (deltaX * deltaX + deltaY * deltaY <= threshold * threshold) {
                union(firstIndex, secondIndex)
            }
        }
    }

    val groups = linkedMapOf<Int, MutableList<Int>>()
    points.indices.forEach { index ->
        groups.getOrPut(find(index)) { mutableListOf() }.add(index)
    }
    return groups.values.map { it.toList() }
}

internal fun shouldClearExpandedMapCluster(
    expansionOriginZoom: Double?,
    currentZoom: Double,
    tolerance: Double = 0.05
): Boolean =
    expansionOriginZoom != null && currentZoom < expansionOriginZoom - tolerance

internal fun retainActiveExpandedAlertIds(
    expandedAlertIds: Collection<String>,
    activeAlertIds: Set<String>
): List<String> = expandedAlertIds.filter(activeAlertIds::contains)

internal fun areMapAlertsCoincident(alerts: List<PokemonAlert>): Boolean {
    val coordinateKeys = alerts.mapNotNull { alert ->
        val latitude = alert.latitude ?: return@mapNotNull null
        val longitude = alert.longitude ?: return@mapNotNull null
        exactCoordinateKey(latitude, longitude)
    }.distinct()
    return coordinateKeys.size == 1 && coordinateKeys.isNotEmpty()
}

internal fun resolveMapClusterInteraction(
    cluster: MapMarkerItem.Cluster,
    currentZoom: Double
): MapClusterInteraction =
    if (areMapAlertsCoincident(cluster.alerts)) {
        MapClusterInteraction.ShowMembers
    } else {
        MapClusterInteraction.Expand(
            alertIds = cluster.alerts.map(PokemonAlert::uniqueId),
            originZoom = currentZoom
        )
    }

internal fun categoryForMapAlert(alert: PokemonAlert): AlertFilter = when {
    alert.hasType("Raid") -> AlertFilter.RAIDS
    alert.hasType("Quest") -> AlertFilter.QUESTS
    alert.hasType("Hundo") -> AlertFilter.HUNDOS
    alert.hasType("PvP") -> AlertFilter.PVP
    alert.hasType("Nundo") -> AlertFilter.NUNDOS
    alert.hasType("Rocket") -> AlertFilter.ROCKET
    alert.hasType("Kecleon") -> AlertFilter.KECLEON
    alert.hasType("WeatherChange") -> AlertFilter.WEATHER_CHANGE
    else -> AlertFilter.RARES
}

private data class PositionedAlert(
    val alert: PokemonAlert,
    val latitude: Double,
    val longitude: Double
)

/**
 * Centre distance in map dp at which two drawn spawn circles stop overlapping, or null when the
 * circles are hidden or too small on screen to tell apart.
 */
private fun spawnCircleGuardDp(
    positioned: List<PositionedAlert>,
    zoom: Double,
    spawnRadiusMeters: Double?
): Double? {
    if (spawnRadiusMeters == null || spawnRadiusMeters <= 0.0) return null
    val meanLatitude = positioned.sumOf { it.latitude } / positioned.size
    val metersPerDp =
        METERS_PER_MAP_DP_AT_ZOOM_ZERO * cos(meanLatitude * PI / 180.0) / 2.0.pow(zoom)
    if (metersPerDp <= 0.0) return null
    val radiusDp = spawnRadiusMeters / metersPerDp
    if (radiusDp < MIN_VISIBLE_RADIUS_DP) return null
    return 2.0 * radiusDp
}

private fun projectMapAlertToScreen(alert: PositionedAlert, scale: Double): MapScreenPoint {
    val sine = kotlin.math.sin(alert.latitude * PI / 180.0)
    return MapScreenPoint(
        x = (alert.longitude + 180.0) / 360.0 * scale,
        y = (0.5 - ln((1.0 + sine) / (1.0 - sine)) / (4.0 * PI)) * scale
    )
}

private fun exactCoordinateKey(alert: PositionedAlert): String =
    exactCoordinateKey(alert.latitude, alert.longitude)

private fun exactCoordinateKey(latitude: Double, longitude: Double): String =
    "%.6f:%.6f".format(java.util.Locale.ROOT, latitude, longitude)
