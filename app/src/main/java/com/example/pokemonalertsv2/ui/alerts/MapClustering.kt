package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.MapClusteringConfig
import com.example.pokemonalertsv2.data.MapClusteringPreset
import com.example.pokemonalertsv2.data.MapGrouping
import com.example.pokemonalertsv2.util.TimeUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Neighborhood/street view and closer show individual markers; only coincident markers group into stacks. */
internal const val MAP_CLUSTER_MAX_ZOOM = 12.0

/** Legacy overview budget, retained by the Current behavior preset. */
internal const val MAX_RENDERED_MAP_MARKERS = 350

/**
 * Legacy close-zoom budget, retained by the Current behavior preset.
 */
internal const val MAX_RENDERED_MAP_MARKERS_ZOOMED_IN = 900

/**
 * Spawn circles are shown only at this zoom and closer, with a separate rendering cap.
 */
internal const val SPAWN_CIRCLE_MIN_ZOOM = 14.0
internal const val MAX_SPAWN_CIRCLES = 60

/**
 * Cluster distance in *map dp* for overview zoom (< [MAP_CLUSTER_MAX_ZOOM]).
 */
internal const val MAP_CLUSTER_CELL_DP = 40f

/**
 * How far apart a cluster's members must land, in map dp, before zooming into it beats opening
 * the member list. Comfortably above the sub-pixel spread of a coincident-in-practice stack and
 * far below the tens of dp a budget-forced cluster covers.
 */
internal const val MAP_CLUSTER_SPLIT_MIN_DP = 8.0

/**
 * Grid cell for dense inputs at [MAP_CLUSTER_MAX_ZOOM] and closer.
 */
internal const val MAP_CLUSTER_ZOOMED_IN_CELL_DP = 32f

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
        val sharedCategory: AlertCategory?,
        val topAlert: PokemonAlert = alerts.first(),
        val isOverviewCluster: Boolean = false,
        val markerLimitActive: Boolean = false
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
 * Compares two alerts by player value priority:
 * Hundo/Nundo/PvP > Raids > Rares > Rocket > other alerts > Quest-only.
 * Within each category rank: highest IV/CP, soonest despawn, stable ID.
 */
internal fun compareAlertPriority(a: PokemonAlert, b: PokemonAlert): Int {
    val rankA = mapAlertCategoryRank(a.alertCategories())
    val rankB = mapAlertCategoryRank(b.alertCategories())
    if (rankA != rankB) return rankA.compareTo(rankB)

    val ivA = a.ivPercentage ?: -1
    val ivB = b.ivPercentage ?: -1
    if (ivA != ivB) return ivB.compareTo(ivA)

    val cpA = a.cp ?: -1
    val cpB = b.cp ?: -1
    if (cpA != cpB) return cpB.compareTo(cpA)

    val endA = TimeUtils.parseEndTimeToMillis(a.endTime) ?: Long.MAX_VALUE
    val endB = TimeUtils.parseEndTimeToMillis(b.endTime) ?: Long.MAX_VALUE
    if (endA != endB) return endA.compareTo(endB)

    return a.uniqueId.compareTo(b.uniqueId)
}

/**
 * At neighborhood/street zoom (zoom >= [MAP_CLUSTER_MAX_ZOOM]), markers with distinct coordinates remain
 * individual. Only coincident / stacked markers are grouped into stacks showing the top Pokémon sprite.
 * At overview zoom (zoom < [MAP_CLUSTER_MAX_ZOOM]), density-based clustering groups markers into overview bubbles.
 */
internal fun clusterMapAlerts(
    alerts: List<PokemonAlert>,
    zoom: Double,
    cellDp: Float = MAP_CLUSTER_CELL_DP,
    spawnRadiusMeters: Double? = null,
    protectedAlertIds: Set<String> = emptySet(),
    config: MapClusteringConfig = MapClusteringPreset.CURRENT.config,
    budgetBounds: MapGeoBounds? = null,
    checkActive: () -> Unit = {}
): List<MapMarkerItem> {
    val tuning = config.normalized()
    val cutoff = tuning.zoomCutoff.toDouble()
    val effectiveCellDp = if (config.grouping == MapGrouping.CURRENT && cellDp != MAP_CLUSTER_CELL_DP) cellDp else tuning.distanceDp.toFloat()
    var limitActive = false
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
    // The UI protects at most the tracked destination and the browsed alert. Reserve their
    // slots before grouping.
    val budget = (if (zoom >= cutoff) tuning.closeLimit else tuning.overviewLimit) - protectedGroups.size
    require(budget >= 0 && (clusterable.isEmpty() || budget > 0)) {
        "Protected alerts must leave room for the clustered alerts"
    }
    val densePathBudget = budget
    val scale = 256.0 * 2.0.pow(zoom.coerceIn(0.0, 24.0))

    // The marker limit is a statement about the screen, so it is counted on the screen. Markers
    // are prepared for a box padded beyond the viewport so panning has something ready, and
    // counting that padded set is what used to make a 350 limit engage at a few dozen visible
    // markers. Groups with no member inside [budgetBounds] are rendered but do not spend budget.
    fun Collection<List<PositionedAlert>>.onScreenSize(): Int =
        if (budgetBounds == null) size
        else count { group ->
            checkActive()
            group.any { budgetBounds.contains(it.latitude, it.longitude) }
        }

    val onScreenAlertCount = if (budgetBounds == null) clusterable.size
    else clusterable.count { checkActive(); budgetBounds.contains(it.latitude, it.longitude) }

    val normalGroups = when {
        zoom >= cutoff || tuning.grouping == MapGrouping.COINCIDENT -> {
            val exactGroups = clusterable.groupBy(::exactCoordinateKey).values.toList()
            if (exactGroups.onScreenSize() > densePathBudget) {
                limitActive = true
                val points = clusterable.map { checkActive(); projectMapAlertToScreen(it, scale) }
                var cellSize = max(1.0, denseCellDp(config, effectiveCellDp).toDouble())
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
                } while (cells.onScreenSize() > densePathBudget)
                cells.toList()
            } else {
                exactGroups
            }
        }
        tuning.grouping == MapGrouping.GRID || onScreenAlertCount > tuning.overviewLimit -> {
            // Overview zoom with dense dataset: grid-based clustering
            val points = clusterable.map { checkActive(); projectMapAlertToScreen(it, scale) }
            var cellSize = max(1.0, effectiveCellDp.toDouble())
            var cells: Collection<List<PositionedAlert>>
            var overBudget: Boolean
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
                overBudget = cells.onScreenSize() > densePathBudget
                if (overBudget) limitActive = true
                cellSize *= 2.0
            } while (overBudget)
            cells.toList()
        }
        else -> {
            // Overview zoom with standard dataset: distance-based clustering
            val thresholdDp = max(1.0, effectiveCellDp.toDouble())
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
    }
    val groups = (normalGroups + protectedGroups).sortedBy { it.first().alert.uniqueId }
    val isOverview = zoom < cutoff && tuning.grouping != MapGrouping.COINCIDENT

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
                .map { checkActive(); it.categories }
                .reduce { common, categories -> common intersect categories }
                .singleOrNull()
            val topAlert = members.minWith(::comparePositionedAlertPriority).alert
            val latitude = if (isOverview) (south + north) / 2.0 else (topAlert.latitude ?: ((south + north) / 2.0))
            val longitude = if (isOverview) (west + east) / 2.0 else (topAlert.longitude ?: ((west + east) / 2.0))
            // Identity by place, not by membership. Hashing the member ids meant one alert
            // expiring - or one pan that pulled a neighbour into the padded box - renamed every
            // cluster, so the map tore down and re-added every annotation instead of swapping
            // the icons of clusters that had not moved.
            val key = exactCoordinateKey(latitude, longitude)
            MapMarkerItem.Cluster(
                id = "c:${key.latitude}:${key.longitude}",
                alerts = members.map { it.alert },
                latitude = latitude,
                longitude = longitude,
                bounds = MapGeoBounds(south, west, north, east),
                sharedCategory = sharedCategory,
                topAlert = topAlert,
                isOverviewCluster = isOverview,
                markerLimitActive = limitActive
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

/**
 * Starting grid cell for the dense close-zoom path.
 *
 * This used to be [MAP_CLUSTER_ZOOMED_IN_CELL_DP] unconditionally, so raising the close-zoom
 * limit helped but the cluster *distance* setting did nothing here. A customised distance now
 * applies; the untouched presets keep the cell they have always used.
 */
private fun denseCellDp(config: MapClusteringConfig, effectiveCellDp: Float): Float =
    if (config.grouping == MapGrouping.CURRENT) MAP_CLUSTER_ZOOMED_IN_CELL_DP else effectiveCellDp

private fun packCellKey(cellX: Int, cellY: Int): Long =
    (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFF_FFFFL)

/**
 * The geographic rectangle a camera view covers, grown by a [marginFactor] on each side so
 * markers just off-screen are already rendered before the user pans to them.
 *
 * Padding is applied *per axis*. It used to be a square built from the viewport half-diagonal,
 * which bought tolerance for a rotated camera - except rotation gestures are disabled on both
 * map layers, so all it ever did was inflate the box. On a 360x800 dp phone the diagonal square
 * covered about eight times the visible area, and since the render budget was measured over
 * whatever landed in this box, a "350 marker" limit engaged at roughly 43 markers on screen.
 *
 * Null when the viewport has no measurable size — callers then skip culling.
 */
internal fun mapViewportBounds(
    centreLatitude: Double,
    centreLongitude: Double,
    zoom: Double,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    marginFactor: Double = MAP_VIEWPORT_MARGIN_FACTOR
): MapGeoBounds? {
    if (viewportWidthDp <= 0f || viewportHeightDp <= 0f) return null
    val latitudeRadians = centreLatitude * PI / 180.0
    val metersPerDp =
        METERS_PER_MAP_DP_AT_ZOOM_ZERO * cos(latitudeRadians) / 2.0.pow(zoom)
    if (metersPerDp <= 0.0) return null
    val padding = 1.0 + marginFactor.coerceAtLeast(0.0)
    val halfWidthMeters = viewportWidthDp / 2f * metersPerDp * padding
    val halfHeightMeters = viewportHeightDp / 2f * metersPerDp * padding
    val latitudeDelta = halfHeightMeters / METERS_PER_DEGREE_LATITUDE
    val longitudeDelta = halfWidthMeters /
        (METERS_PER_DEGREE_LATITUDE * cos(latitudeRadians).coerceAtLeast(0.01))
    return MapGeoBounds(
        south = (centreLatitude - latitudeDelta).coerceIn(-85.0, 85.0),
        west = centreLongitude - longitudeDelta,
        north = (centreLatitude + latitudeDelta).coerceIn(-85.0, 85.0),
        east = centreLongitude + longitudeDelta
    )
}

/**
 * How far past the screen edge markers are prepared, as a fraction of the viewport.
 *
 * Tilt is still enabled outside picture-in-picture, which pushes the horizon further than the
 * flat projection suggests, so this keeps real headroom - it just no longer pays for rotation
 * tolerance nothing asks for.
 */
internal const val MAP_VIEWPORT_MARGIN_FACTOR = 0.35

/**
 * How far the camera may drift from the anchor markers were prepared for, as a fraction of the
 * viewport, before the whole set is prepared again. Comfortably inside
 * [MAP_VIEWPORT_MARGIN_FACTOR], so everything on screen is always within what was prepared.
 */
private const val MAP_ANCHOR_DRIFT_FRACTION = 0.25

/**
 * The camera position clustering is keyed on, held steady through small pans.
 *
 * Clustering is already sampled at camera idle rather than per gesture frame, but a scroll
 * produces an idle at every stop, and each one used to re-cull, re-cluster and hand the map an
 * entirely new marker set - the dominant cost of a fast scroll, and the source of most of the
 * garbage it generated. A pan only uncovers genuinely new markers once it approaches the edge
 * of what was prepared, so [previous] is kept until the camera drifts
 * [MAP_ANCHOR_DRIFT_FRACTION] of a viewport away from it or the quantised zoom changes.
 */
internal fun retainedMapAnchor(
    previous: MapCameraSnapshot?,
    latitude: Double,
    longitude: Double,
    zoom: Double,
    viewportWidthDp: Float,
    viewportHeightDp: Float
): MapCameraSnapshot {
    val fresh = MapCameraSnapshot(latitude, longitude, zoom)
    if (previous == null || previous.zoom != zoom) return fresh
    if (viewportWidthDp <= 0f || viewportHeightDp <= 0f) return fresh
    val latitudeRadians = latitude * PI / 180.0
    val metersPerDp = METERS_PER_MAP_DP_AT_ZOOM_ZERO * cos(latitudeRadians) / 2.0.pow(zoom)
    if (metersPerDp <= 0.0) return fresh
    val latitudeDrift = viewportHeightDp * MAP_ANCHOR_DRIFT_FRACTION * metersPerDp /
        METERS_PER_DEGREE_LATITUDE
    val longitudeDrift = viewportWidthDp * MAP_ANCHOR_DRIFT_FRACTION * metersPerDp /
        (METERS_PER_DEGREE_LATITUDE * cos(latitudeRadians).coerceAtLeast(0.01))
    val withinDrift = kotlin.math.abs(latitude - previous.latitude) <= latitudeDrift &&
        kotlin.math.abs(longitude - previous.longitude) <= longitudeDrift
    return if (withinDrift) previous else fresh
}

/** Meters of latitude per degree; longitude spans this times cos(latitude). */
private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

internal fun MapGeoBounds.contains(latitude: Double, longitude: Double): Boolean =
    latitude in south..north && longitude in west..east

internal fun resolveMapClusterInteraction(
    cluster: MapMarkerItem.Cluster,
    currentZoom: Double,
    maximumZoom: Double,
    clusteringCutoff: Double = MAP_CLUSTER_MAX_ZOOM
): MapClusterInteraction {
    val targetZoom = min(currentZoom + 2.0, maximumZoom)
    // Already as close as this map goes.
    if (targetZoom <= currentZoom + 0.05) return MapClusterInteraction.ShowMembers
    // Bounds make coincidence checking constant-time even for a 10,000-member cluster, and
    // members on one exact coordinate never come apart however far you zoom.
    if (exactCoordinateKey(cluster.bounds.south, cluster.bounds.west) ==
        exactCoordinateKey(cluster.bounds.north, cluster.bounds.east)
    ) {
        return MapClusterInteraction.ShowMembers
    }
    // At [MAP_CLUSTER_MAX_ZOOM] and closer the map is meant to be drawing individual markers,
    // so a cluster here is either a coincident stack - handled above - or one the render budget
    // forced on a dense area. Those *do* come apart, and dumping several hundred rows into a
    // list is a poor answer to a tap that one zoom step would resolve. Zoom when the members
    // would actually land apart, and only fall back to the list when they would not.
    if (currentZoom >= clusteringCutoff &&
        clusterSpreadDp(cluster.bounds, targetZoom) < MAP_CLUSTER_SPLIT_MIN_DP
    ) {
        return MapClusterInteraction.ShowMembers
    }
    return MapClusterInteraction.ZoomTo(
        MapCameraSnapshot(cluster.latitude, cluster.longitude, targetZoom)
    )
}

/** The cluster's diagonal in map dp at [zoom] — how far apart zooming would place its members. */
private fun clusterSpreadDp(bounds: MapGeoBounds, zoom: Double): Double {
    val meanLatitudeRadians = (bounds.south + bounds.north) / 2.0 * PI / 180.0
    val metersPerDp = METERS_PER_MAP_DP_AT_ZOOM_ZERO * cos(meanLatitudeRadians) / 2.0.pow(zoom)
    if (metersPerDp <= 0.0) return 0.0
    val heightMeters = (bounds.north - bounds.south) * METERS_PER_DEGREE_LATITUDE
    val widthMeters = (bounds.east - bounds.west) *
        METERS_PER_DEGREE_LATITUDE * cos(meanLatitudeRadians)
    return sqrt(heightMeters * heightMeters + widthMeters * widthMeters) / metersPerDp
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

private fun mapAlertCategoryRank(categories: Set<AlertCategory>): Int = when {
    AlertCategory.HUNDO in categories || AlertCategory.NUNDO in categories || AlertCategory.PVP in categories -> 0
    AlertCategory.RAID in categories -> 1
    AlertCategory.RARE in categories -> 2
    AlertCategory.ROCKET in categories -> 3
    categories.size == 1 && AlertCategory.QUEST in categories -> 5
    else -> 4
}

private data class PositionedAlert(
    val alert: PokemonAlert,
    val latitude: Double,
    val longitude: Double
) {
    val categories by lazy(LazyThreadSafetyMode.NONE) { alert.alertCategories() }
    val rank by lazy(LazyThreadSafetyMode.NONE) { mapAlertCategoryRank(categories) }
    val iv by lazy(LazyThreadSafetyMode.NONE) { alert.ivPercentage ?: -1 }
}

private fun comparePositionedAlertPriority(a: PositionedAlert, b: PositionedAlert): Int {
    if (a.rank != b.rank) return a.rank.compareTo(b.rank)
    if (a.iv != b.iv) return b.iv.compareTo(a.iv)
    val cpA = a.alert.cp ?: -1
    val cpB = b.alert.cp ?: -1
    if (cpA != cpB) return cpB.compareTo(cpA)
    val endA = TimeUtils.parseEndTimeToMillis(a.alert.endTime) ?: Long.MAX_VALUE
    val endB = TimeUtils.parseEndTimeToMillis(b.alert.endTime) ?: Long.MAX_VALUE
    if (endA != endB) return endA.compareTo(endB)
    return a.alert.uniqueId.compareTo(b.alert.uniqueId)
}

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

private fun exactCoordinateKey(alert: PositionedAlert): MapCoordinateKey =
    exactCoordinateKey(alert.latitude, alert.longitude)

internal data class MapCoordinateKey(val latitude: Long, val longitude: Long)

/** Six-decimal HALF_UP grouping, including signed zero. Allocation-free: this runs per pin. */
internal fun exactCoordinateKey(latitude: Double, longitude: Double): MapCoordinateKey =
    MapCoordinateKey(coordinateMillionths(latitude), coordinateMillionths(longitude))

private fun coordinateMillionths(value: Double): Long {
    // HALF_UP is "away from zero at the halfway point", which is what rounding the magnitude
    // and reapplying the sign gives. This runs once per coordinate per alert per clustering
    // pass; the BigDecimal pair it replaces was the single largest allocator on that path.
    val magnitude = kotlin.math.abs(value) * 1_000_000.0
    val rounded = kotlin.math.floor(magnitude + 0.5).toLong()
    val signed = if (value < 0.0) -rounded else rounded
    return if (signed == 0L && java.lang.Double.doubleToRawLongBits(value) < 0L) Long.MIN_VALUE else signed
}

/** Preserve the existing six-decimal coordinate equivalence, including nearby distinct stops. */
internal fun sameMapLocation(a: PokemonAlert, b: PokemonAlert): Boolean =
    a.latitude != null && a.longitude != null && b.latitude != null && b.longitude != null &&
        a.latitude.isFinite() && a.longitude.isFinite() && b.latitude.isFinite() && b.longitude.isFinite() &&
        exactCoordinateKey(a.latitude, a.longitude) == exactCoordinateKey(b.latitude, b.longitude)

internal fun MapMarkerItem.Cluster.isCoincident(): Boolean =
    exactCoordinateKey(bounds.south, bounds.west) == exactCoordinateKey(bounds.north, bounds.east)

internal fun sameStopCompanions(alert: PokemonAlert, eligible: List<PokemonAlert>): List<PokemonAlert> {
    val latitude = alert.latitude ?: return emptyList()
    val longitude = alert.longitude ?: return emptyList()
    if (!latitude.isFinite() || !longitude.isFinite()) return emptyList()
    val location = exactCoordinateKey(latitude, longitude)
    return eligible.filter { candidate ->
        candidate.uniqueId != alert.uniqueId && candidate.latitude?.isFinite() == true &&
            candidate.longitude?.isFinite() == true && exactCoordinateKey(candidate.latitude, candidate.longitude) == location
    }.sortedWith(::compareAlertPriority)
}
