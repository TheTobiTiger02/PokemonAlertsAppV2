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

/** Hard ceiling on individual pins plus cluster bubbles, including protected tracking pins. */
internal const val MAX_RENDERED_MAP_MARKERS = 350

/**
 * Spawn circles are shown only at this zoom and closer, with a separate rendering cap.
 */
internal const val SPAWN_CIRCLE_MIN_ZOOM = 14.0
internal const val MAX_SPAWN_CIRCLES = 60

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
) {
    /** Grid bucket coordinates for [cellSize]; floor keeps negative coordinates correct. */
    internal fun cellX(cellSize: Double): Int = kotlin.math.floor(x / cellSize).toInt()
    internal fun cellY(cellSize: Double): Int = kotlin.math.floor(y / cellSize).toInt()
}

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
        val sharedCategory: AlertCategory?
    ) : MapMarkerItem
}

internal sealed interface MapClusterInteraction {
    data object ShowMembers : MapClusterInteraction

    data class ZoomTo(val target: MapCameraSnapshot) : MapClusterInteraction
}

/** Radius of the drawn spawn circle, or null when spawn radii are hidden. */
internal fun spawnRadiusMeters(showSpawnRadius: Boolean, spacialRendEnabled: Boolean): Double? =
    when {
        !showSpawnRadius -> null
        spacialRendEnabled -> 80.0
        else -> 40.0
    }

/**
 * Small inputs retain overlap clustering and exact stacks at neighborhood zoom. Dense inputs
 * use bounded grid cells instead of transitive components: a chain of nearby alerts must not
 * join an entire city or require comparing every pair in a crowded bucket.
 * [checkActive] lets background callers cancel obsolete work between preprocessing/grouping steps.
 */
internal fun clusterMapAlerts(
    alerts: List<PokemonAlert>,
    zoom: Double,
    cellDp: Float = MAP_CLUSTER_CELL_DP,
    spawnRadiusMeters: Double? = null,
    protectedAlertIds: Set<String> = emptySet(),
    checkActive: () -> Unit = {}
): List<MapMarkerItem> {
    val positioned = alerts.mapNotNull { alert ->
        checkActive()
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
    // The UI protects at most the active tracked alert. Reserve its slot before grouping.
    val budget = MAX_RENDERED_MAP_MARKERS - protectedGroups.size
    require(budget >= 0 && (clusterable.isEmpty() || budget > 0)) {
        "Protected alerts must leave room for the clustered alerts"
    }
    val scale = 256.0 * 2.0.pow(zoom.coerceIn(0.0, 24.0))
    val normalGroups = when {
        positioned.size > MAX_RENDERED_MAP_MARKERS -> {
            val points = clusterable.map { checkActive(); projectMapAlertToScreen(it, scale) }
            var cellSize = max(1.0, cellDp.toDouble())
            var cells: Collection<List<PositionedAlert>>
            do {
                checkActive()
                val buckets = linkedMapOf<Long, MutableList<PositionedAlert>>()
                points.forEachIndexed { index, point ->
                    checkActive()
                    buckets.getOrPut(packCellKey(point.cellX(cellSize), point.cellY(cellSize))) {
                        mutableListOf()
                    }.add(clusterable[index])
                }
                cells = buckets.values
                cellSize *= 2.0
            } while (cells.size > budget)
            cells.toList()
        }
        zoom < MAP_CLUSTER_MAX_ZOOM -> {
            val thresholdDp = max(1.0, cellDp.toDouble())
            val radiusGuardDp = spawnCircleGuardDp(clusterable, zoom, spawnRadiusMeters)
            val guardedThresholdDp = min(thresholdDp, radiusGuardDp ?: thresholdDp)
            val points = clusterable.map { projectMapAlertToScreen(it, scale) }
            val guarded = BooleanArray(clusterable.size) { index ->
                radiusGuardDp != null && clusterable[index].alert.isSpawnAlert
            }
            connectedMapScreenComponents(
                points = points,
                thresholdFor = { first, second ->
                    checkActive()
                    if (guarded[first] && guarded[second]) guardedThresholdDp else thresholdDp
                },
                cellSize = thresholdDp
            ).map { indices -> indices.map(clusterable::get) }
        }
        else -> clusterable.groupBy(::exactCoordinateKey).values.toList()
    }
    val groups = (normalGroups + protectedGroups).sortedBy { it.first().alert.uniqueId }

    return groups.map { members ->
        checkActive()
        if (members.size == 1) {
            val item = members.first()
            MapMarkerItem.Alert(item.alert, item.latitude, item.longitude)
        } else {
            val south = members.minOf { it.latitude }
            val west = members.minOf { it.longitude }
            val north = members.maxOf { it.latitude }
            val east = members.maxOf { it.longitude }
            val sharedCategory = members
                .map { checkActive(); it.alert.alertCategories() }
                .reduce { common, categories -> common intersect categories }
                .singleOrNull()
            MapMarkerItem.Cluster(
                id = members.joinToString("|") { it.alert.uniqueId }.hashCode().toString(),
                alerts = members.map { it.alert },
                latitude = (south + north) / 2.0,
                longitude = (west + east) / 2.0,
                bounds = MapGeoBounds(south, west, north, east),
                sharedCategory = sharedCategory
            )
        }
    }
}

internal fun connectedMapScreenComponents(
    points: List<MapScreenPoint>,
    thresholdPx: Double
): List<List<Int>> = connectedMapScreenComponents(
    points = points,
    thresholdFor = { _, _ -> thresholdPx },
    cellSize = thresholdPx
)

/**
 * Connected components under a "merge when closer than the pair's threshold" rule.
 *
 * Pairs are found through a spatial hash grid with [cellSize] cells instead of comparing
 * every point with every other point: any pair within threshold distance necessarily sits in
 * the same or an adjacent cell, so scanning each point's 3x3 neighbourhood finds exactly the
 * same merges as the all-pairs loop. Crowded buckets still have quadratic worst-case cost;
 * this routine is therefore used only for inputs within the rendering budget.
 *
 * [cellSize] must be at least the maximum threshold [thresholdFor] can return.
 */
internal fun connectedMapScreenComponents(
    points: List<MapScreenPoint>,
    thresholdFor: (Int, Int) -> Double,
    cellSize: Double
): List<List<Int>> {
    if (points.isEmpty()) return emptyList()
    val cell = cellSize.coerceAtLeast(1.0)
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

    val cellX = IntArray(points.size)
    val cellY = IntArray(points.size)
    val buckets = HashMap<Long, MutableList<Int>>()
    points.indices.forEach { index ->
        cellX[index] = points[index].cellX(cell)
        cellY[index] = points[index].cellY(cell)
        buckets.getOrPut(packCellKey(cellX[index], cellY[index])) { mutableListOf() }.add(index)
    }

    points.indices.forEach { firstIndex ->
        for (deltaX in -1..1) {
            for (deltaY in -1..1) {
                val bucket = buckets[packCellKey(cellX[firstIndex] + deltaX, cellY[firstIndex] + deltaY)]
                    ?: continue
                bucket.forEach { secondIndex ->
                    // Each unordered pair is examined once: the cell (or neighbour cell) of the
                    // smaller index always contains the larger one.
                    if (secondIndex <= firstIndex) return@forEach
                    val first = points[firstIndex]
                    val second = points[secondIndex]
                    val deltaXpx = first.x - second.x
                    val deltaYpx = first.y - second.y
                    val threshold = thresholdFor(firstIndex, secondIndex).coerceAtLeast(1.0)
                    if (deltaXpx * deltaXpx + deltaYpx * deltaYpx <= threshold * threshold) {
                        union(firstIndex, secondIndex)
                    }
                }
            }
        }
    }

    val groups = linkedMapOf<Int, MutableList<Int>>()
    points.indices.forEach { index ->
        groups.getOrPut(find(index)) { mutableListOf() }.add(index)
    }
    return groups.values.map { it.toList() }
}

private fun packCellKey(cellX: Int, cellY: Int): Long =
    (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFF_FFFFL)

/**
 * The geographic rectangle a camera view covers, grown by a [marginFactor] on each side so
 * markers just off-screen are already rendered before the user pans to them. Tolerance for
 * rotation comes free: a diagonal-sized rectangle always contains the (possibly rotated)
 * viewport.
 *
 * Null when the viewport has no measurable size — callers then skip culling.
 */
internal fun mapViewportBounds(
    centreLatitude: Double,
    centreLongitude: Double,
    zoom: Double,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    marginFactor: Double = 0.75
): MapGeoBounds? {
    if (viewportWidthDp <= 0f || viewportHeightDp <= 0f) return null
    val latitudeRadians = centreLatitude * PI / 180.0
    val metersPerDp =
        METERS_PER_MAP_DP_AT_ZOOM_ZERO * cos(latitudeRadians) / 2.0.pow(zoom)
    if (metersPerDp <= 0.0) return null
    val halfWidthMeters = viewportWidthDp / 2f * metersPerDp
    val halfHeightMeters = viewportHeightDp / 2f * metersPerDp
    val halfDiagonalMeters = (1.0 + marginFactor) *
        kotlin.math.sqrt(halfWidthMeters * halfWidthMeters + halfHeightMeters * halfHeightMeters)
    val latitudeDelta = halfDiagonalMeters / METERS_PER_DEGREE_LATITUDE
    val longitudeDelta = halfDiagonalMeters /
        (METERS_PER_DEGREE_LATITUDE * cos(latitudeRadians).coerceAtLeast(0.01))
    return MapGeoBounds(
        south = (centreLatitude - latitudeDelta).coerceIn(-85.0, 85.0),
        west = centreLongitude - longitudeDelta,
        north = (centreLatitude + latitudeDelta).coerceIn(-85.0, 85.0),
        east = centreLongitude + longitudeDelta
    )
}

/** Meters of latitude per degree; longitude spans this times cos(latitude). */
private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

internal fun MapGeoBounds.contains(latitude: Double, longitude: Double): Boolean =
    latitude in south..north && longitude in west..east

internal fun resolveMapClusterInteraction(
    cluster: MapMarkerItem.Cluster,
    currentZoom: Double,
    maximumZoom: Double
): MapClusterInteraction =
    if (currentZoom >= maximumZoom - 0.05 ||
        // Bounds make coincidence checking constant-time even for a 10,000-member cluster.
        exactCoordinateKey(cluster.bounds.south, cluster.bounds.west) ==
        exactCoordinateKey(cluster.bounds.north, cluster.bounds.east)
    ) {
        MapClusterInteraction.ShowMembers
    } else {
        MapClusterInteraction.ZoomTo(
            MapCameraSnapshot(cluster.latitude, cluster.longitude, min(currentZoom + 2.0, maximumZoom))
        )
    }

/**
 * Cluster bubbles take their colour from the one category every member shares. Priority
 * order picks a single category for alerts that belong to several (a hundo spawn keeps its
 * hundo colour, matching the marker styling elsewhere).
 */
internal fun categoryForMapAlert(alert: PokemonAlert): AlertCategory {
    val categories = alert.alertCategories()
    return when {
        AlertCategory.RAID in categories -> AlertCategory.RAID
        AlertCategory.QUEST in categories -> AlertCategory.QUEST
        AlertCategory.HUNDO in categories -> AlertCategory.HUNDO
        AlertCategory.NUNDO in categories -> AlertCategory.NUNDO
        AlertCategory.PVP in categories -> AlertCategory.PVP
        AlertCategory.ROCKET in categories -> AlertCategory.ROCKET
        AlertCategory.KECLEON in categories -> AlertCategory.KECLEON
        AlertCategory.WEATHER in categories -> AlertCategory.WEATHER
        AlertCategory.RARE in categories -> AlertCategory.RARE
        else -> AlertCategory.SPAWN
    }
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
