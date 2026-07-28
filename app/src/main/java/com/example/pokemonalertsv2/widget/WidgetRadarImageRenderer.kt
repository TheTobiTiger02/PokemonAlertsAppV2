package com.example.pokemonalertsv2.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Location
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import com.example.pokemonalertsv2.util.MapFallbackImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

internal data class RadarRenderInsets(
    val leftPx: Int = 28,
    val topPx: Int = 72,
    val rightPx: Int = 28,
    val bottomPx: Int = 152
)

internal data class RadarRenderInput(
    val alerts: List<PokemonAlert>,
    val location: Location?,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val insets: RadarRenderInsets = RadarRenderInsets(),
    val selectedAlertId: String? = null,
    val viewMode: RadarViewMode = RadarViewMode.FOCUS
)

internal data class RadarGeoPoint(val latitude: Double, val longitude: Double)

internal data class RadarVisualMarker(
    val alertId: String,
    val point: RadarGeoPoint
)

internal data class RadarMarkerPartition(
    val selectedIndex: Int?,
    val clusterableIndices: List<Int>
)

internal data class RadarMarkerVisualMetrics(
    val alertDiameterPx: Float,
    val selectedOuterDiameterPx: Float,
    val clusterDiameterPx: Float,
    val collisionDistancePx: Float,
    val clipMarginPx: Float
)

internal data class RadarAlertMarkerVisual(
    val centerX: Float,
    val centerY: Float,
    val contentDiameterPx: Float,
    val outerDiameterPx: Float
) {
    val outerLeft: Float get() = centerX - outerDiameterPx / 2f
    val outerTop: Float get() = centerY - outerDiameterPx / 2f
    val outerRight: Float get() = centerX + outerDiameterPx / 2f
    val outerBottom: Float get() = centerY + outerDiameterPx / 2f
}

internal data class RadarViewport(
    val center: RadarGeoPoint,
    val zoom: Int
)

internal data class RadarSnapshot(
    val bitmap: Bitmap,
    val mapAvailable: Boolean,
    val viewport: RadarViewport
)

internal fun radarRenderableAlerts(
    alerts: List<PokemonAlert>,
    selectedAlertId: String?,
    limit: Int = 8
): List<PokemonAlert> {
    if (limit <= 0) return emptyList()
    val selected = alerts.firstOrNull { it.uniqueId == selectedAlertId }
    val unselected = alerts.asSequence()
        .filterNot { it.uniqueId == selectedAlertId }
        .take(if (selected == null) limit else limit - 1)
        .toList()
    return if (selected == null) unselected else listOf(selected) + unselected
}

internal fun resolveRadarViewportPoints(
    alertMarkers: List<RadarVisualMarker>,
    selectedAlertId: String?,
    viewMode: RadarViewMode,
    locationPoint: RadarGeoPoint?,
    accuracyBounds: List<RadarGeoPoint> = emptyList()
): List<RadarGeoPoint> {
    val alertPoints = if (viewMode == RadarViewMode.FOCUS) {
        alertMarkers.firstOrNull { it.alertId == selectedAlertId }
            ?.let { listOf(it.point) }
            ?: alertMarkers.map { it.point }
    } else {
        alertMarkers.map { it.point }
    }
    return buildList {
        addAll(alertPoints)
        locationPoint?.let(::add)
        addAll(accuracyBounds)
    }
}

internal fun partitionRadarMarkers(
    alertIds: List<String>,
    selectedAlertId: String?
): RadarMarkerPartition {
    val selectedIndex = alertIds.indexOfFirst { it == selectedAlertId }
        .takeIf { it >= 0 }
    return RadarMarkerPartition(
        selectedIndex = selectedIndex,
        clusterableIndices = alertIds.indices.filterNot { it == selectedIndex }
    )
}

internal fun radarMarkerVisualMetrics(density: Float): RadarMarkerVisualMetrics {
    val safeDensity = density.coerceAtLeast(0.1f)
    return RadarMarkerVisualMetrics(
        alertDiameterPx = 32f * safeDensity,
        selectedOuterDiameterPx = 38f * safeDensity,
        clusterDiameterPx = 36f * safeDensity,
        collisionDistancePx = 36f * safeDensity,
        clipMarginPx = 19f * safeDensity
    )
}

internal fun radarAlertMarkerVisual(
    centerX: Float,
    centerY: Float,
    selected: Boolean,
    metrics: RadarMarkerVisualMetrics
): RadarAlertMarkerVisual = RadarAlertMarkerVisual(
    centerX = centerX,
    centerY = centerY,
    contentDiameterPx = metrics.alertDiameterPx,
    outerDiameterPx = if (selected) {
        metrics.selectedOuterDiameterPx
    } else {
        metrics.alertDiameterPx
    }
)

internal fun fitRadarViewport(
    points: List<RadarGeoPoint>,
    widthPx: Int,
    heightPx: Int,
    insets: RadarRenderInsets = RadarRenderInsets(),
    minZoom: Int = 3,
    maxZoom: Int = 17
): RadarViewport {
    val validPoints = points.filter {
        it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -85.0..85.0 && it.longitude in -180.0..180.0
    }
    if (validPoints.isEmpty()) {
        return RadarViewport(RadarGeoPoint(49.74677, 8.62492), 13)
    }
    if (validPoints.size == 1) return RadarViewport(validPoints.first(), 16)

    val worldPoints = validPoints.map { mercatorWorld(it.latitude, it.longitude) }
    val minX = worldPoints.minOf { it.first }
    val maxX = worldPoints.maxOf { it.first }
    val minY = worldPoints.minOf { it.second }
    val maxY = worldPoints.maxOf { it.second }
    val availableWidth = (widthPx - insets.leftPx - insets.rightPx).coerceAtLeast(64)
    val availableHeight = (heightPx - insets.topPx - insets.bottomPx).coerceAtLeast(64)
    val zoom = (maxZoom downTo minZoom).firstOrNull { candidate ->
        val scale = 256.0 * 2.0.pow(candidate)
        (maxX - minX) * scale <= availableWidth &&
            (maxY - minY) * scale <= availableHeight
    } ?: minZoom

    val scale = 256.0 * 2.0.pow(zoom)
    val contentCenterX = insets.leftPx + availableWidth / 2.0
    val contentCenterY = insets.topPx + availableHeight / 2.0
    val centerWorldX = (minX + maxX) / 2.0 - (contentCenterX - widthPx / 2.0) / scale
    val centerWorldY = (minY + maxY) / 2.0 - (contentCenterY - heightPx / 2.0) / scale
    return RadarViewport(
        center = inverseMercatorWorld(centerWorldX, centerWorldY),
        zoom = zoom
    )
}

internal fun radarLocationAgeLabel(
    location: Location?,
    nowMillis: Long = System.currentTimeMillis()
): String = formatRadarLocationAge(
    locationTimeMillis = location?.time,
    accuracyMeters = location?.accuracy,
    nowMillis = nowMillis
)

internal fun formatRadarLocationAge(
    locationTimeMillis: Long?,
    accuracyMeters: Float?,
    nowMillis: Long
): String = when {
    locationTimeMillis == null -> "Location unavailable"
    locationTimeMillis <= 0L -> "Location updated"
    nowMillis - locationTimeMillis < 60_000L -> {
        if ((accuracyMeters ?: 0f) > 100f) "Approximate location · now" else "Location · now"
    }
    else -> {
        val minutes = ((nowMillis - locationTimeMillis) / 60_000L).coerceAtLeast(1L)
        val prefix = if ((accuracyMeters ?: 0f) > 100f) "Approximate" else "Location"
        "$prefix · ${minutes}m ago"
    }
}

internal object WidgetRadarImageRenderer {
    suspend fun render(context: Context, input: RadarRenderInput): RadarSnapshot =
        withContext(Dispatchers.IO) {
            val renderAlerts = radarRenderableAlerts(
                alerts = input.alerts,
                selectedAlertId = input.selectedAlertId,
                limit = MAX_ALERTS
            )
            val alertMarkers = renderAlerts.mapNotNull { alert ->
                val latitude = alert.latitude ?: return@mapNotNull null
                val longitude = alert.longitude ?: return@mapNotNull null
                RadarVisualMarker(
                    alertId = alert.uniqueId,
                    point = RadarGeoPoint(latitude, longitude)
                )
            }
            val fitPoints = resolveRadarViewportPoints(
                alertMarkers = alertMarkers,
                selectedAlertId = input.selectedAlertId,
                viewMode = input.viewMode,
                locationPoint = input.location?.let {
                    RadarGeoPoint(it.latitude, it.longitude)
                },
                accuracyBounds = input.location?.let(::locationAccuracyBounds).orEmpty()
            )
            val viewport = fitRadarViewport(
                points = fitPoints,
                widthPx = input.widthPx,
                heightPx = input.heightPx,
                insets = input.insets
            )
            val baseMap = MapFallbackImageGenerator.generate(
                context = context,
                latitude = viewport.center.latitude,
                longitude = viewport.center.longitude,
                thumbnailUrl = null,
                outputWidth = input.widthPx,
                outputHeight = input.heightPx,
                zoom = viewport.zoom,
                drawCenterMarker = false
            )
            val mapAvailable = baseMap != null
            val output = baseMap?.copy(Bitmap.Config.ARGB_8888, true)
                ?: Bitmap.createBitmap(
                    input.widthPx,
                    input.heightPx,
                    Bitmap.Config.ARGB_8888
                ).also { Canvas(it).drawColor(0xFFE2E7EC.toInt()) }
            val canvas = Canvas(output)

            input.location?.let { location ->
                drawRadarUserAccuracyRing(canvas, input, viewport, location)
            }

            val markerMetrics = radarMarkerVisualMetrics(input.density)
            val positioned = renderAlerts.mapNotNull { alert ->
                val latitude = alert.latitude ?: return@mapNotNull null
                val longitude = alert.longitude ?: return@mapNotNull null
                val point = projectToRadar(
                    RadarGeoPoint(latitude, longitude),
                    viewport,
                    input.widthPx,
                    input.heightPx
                )
                PositionedRadarAlert(alert, point.first, point.second)
                    .takeIf {
                        isRadarPointVisible(
                            x = it.x,
                            y = it.y,
                            widthPx = input.widthPx,
                            heightPx = input.heightPx,
                            markerMarginPx = markerMetrics.clipMarginPx
                        )
                    }
            }
            val partition = partitionRadarMarkers(
                alertIds = positioned.map { it.alert.uniqueId },
                selectedAlertId = input.selectedAlertId
            )
            val selectedMarker = partition.selectedIndex?.let(positioned::get)
            val clusterableMarkers = partition.clusterableIndices.map(positioned::get)
            val pointClusters = clusterRadarPoints(
                clusterableMarkers.map { it.x to it.y },
                markerMetrics.collisionDistancePx
            )
            val clusters = pointClusters.map { indices -> indices.map(clusterableMarkers::get) }
            val spriteTargets = clusters.map { members ->
                members.singleOrNull()?.alert
            } + selectedMarker?.alert
            val sprites = coroutineScope {
                spriteTargets.map { alert ->
                    async {
                        alert?.let {
                            loadAlertSprite(
                                context = context,
                                alert = it,
                                sizePx = markerMetrics.alertDiameterPx.toInt()
                            )
                        }
                    }
                }.awaitAll()
            }
            clusters.forEachIndexed { index, members ->
                val x = members.map { it.x }.average().toFloat()
                val y = members.map { it.y }.average().toFloat()
                if (members.size == 1) {
                    drawPokemonMarker(
                        canvas = canvas,
                        x = x,
                        y = y,
                        metrics = markerMetrics,
                        alert = members.single().alert,
                        sprite = sprites[index],
                        highlighted = false
                    )
                } else {
                    drawClusterMarker(
                        canvas = canvas,
                        x = x,
                        y = y,
                        diameter = markerMetrics.clusterDiameterPx,
                        alerts = members.map { it.alert }
                    )
                }
            }
            selectedMarker?.let { marker ->
                drawPokemonMarker(
                    canvas = canvas,
                    x = marker.x,
                    y = marker.y,
                    metrics = markerMetrics,
                    alert = marker.alert,
                    sprite = sprites.lastOrNull(),
                    highlighted = true
                )
            }

            if (!mapAvailable) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF263238.toInt()
                    textSize = 14f * input.density
                    isFakeBoldText = true
                }
                canvas.drawText(
                    "Map unavailable · alert positions shown",
                    14f * input.density,
                    input.heightPx - 10f * input.density,
                    paint
                )
            }

            input.location?.let { location ->
                drawRadarUserLocationDot(canvas, input, viewport, location)
            }

            RadarSnapshot(output, mapAvailable, viewport)
        }

    internal fun drawRadarUserAccuracyRing(
        canvas: Canvas,
        input: RadarRenderInput,
        viewport: RadarViewport,
        location: Location
    ) {
        val center = projectToRadar(
            RadarGeoPoint(location.latitude, location.longitude),
            viewport,
            input.widthPx,
            input.heightPx
        )
        val radius = radarAccuracyRadiusPx(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            viewport = viewport,
            widthPx = input.widthPx,
            heightPx = input.heightPx
        ).coerceAtLeast(5f)
        canvas.drawCircle(center.first, center.second, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x331A73E8
        })
        canvas.drawCircle(center.first, center.second, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x991A73E8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = max(2f, 1.5f * input.density)
        })
    }

    internal fun drawRadarUserLocationDot(
        canvas: Canvas,
        input: RadarRenderInput,
        viewport: RadarViewport,
        location: Location
    ) {
        val center = projectToRadar(
            RadarGeoPoint(location.latitude, location.longitude),
            viewport,
            input.widthPx,
            input.heightPx
        )
        canvas.drawCircle(center.first, center.second, 8f * input.density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })
        canvas.drawCircle(center.first, center.second, 5.5f * input.density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A73E8.toInt()
        })
    }

    private suspend fun loadAlertSprite(
        context: Context,
        alert: PokemonAlert,
        sizePx: Int
    ): Bitmap? {
        val loader = PokemonAlertsApplication.imageLoader(context)
        val urls = listOfNotNull(
            alert.thumbnailUrl?.takeIf(String::isNotBlank),
            alert.imageUrl?.takeIf(String::isNotBlank)
        ).distinct()
        for (url in urls) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(sizePx, sizePx)
                .allowHardware(false)
                .build()
            val result = runCatching { loader.execute(request) }.getOrNull()
            if (result is SuccessResult) {
                val drawable = result.drawable
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(Canvas(bitmap))
                return bitmap
            }
        }
        return null
    }

    private fun drawPokemonMarker(
        canvas: Canvas,
        x: Float,
        y: Float,
        metrics: RadarMarkerVisualMetrics,
        alert: PokemonAlert,
        sprite: Bitmap?,
        highlighted: Boolean
    ) {
        val visual = radarAlertMarkerVisual(x, y, highlighted, metrics)
        val category = resolveAlertVisualStyle(alert)
        if (highlighted) {
            canvas.drawCircle(
                visual.centerX,
                visual.centerY,
                visual.outerDiameterPx / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x661A73E8
                }
            )
            canvas.drawCircle(
                visual.centerX,
                visual.centerY,
                visual.outerDiameterPx / 2f - 1f * metrics.alertDiameterPx / 32f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1A73E8.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = max(2f, metrics.alertDiameterPx * 0.055f)
                }
            )
        }
        val radius = visual.contentDiameterPx / 2f
        canvas.drawCircle(visual.centerX, visual.centerY, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.category.accentArgb.toInt()
        })
        canvas.drawCircle(
            visual.centerX,
            visual.centerY,
            radius - metrics.alertDiameterPx * 0.055f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
        )
        val innerRadius = radius * 0.79f
        if (sprite != null) {
            canvas.save()
            canvas.clipPath(Path().apply {
                addCircle(visual.centerX, visual.centerY, innerRadius, Path.Direction.CW)
            })
            canvas.drawBitmap(
                sprite,
                null,
                RectF(
                    visual.centerX - innerRadius,
                    visual.centerY - innerRadius,
                    visual.centerX + innerRadius,
                    visual.centerY + innerRadius
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            canvas.restore()
        } else {
            canvas.drawCircle(
                visual.centerX,
                visual.centerY,
                innerRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFF8FAFC.toInt()
                }
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = category.category.accentArgb.toInt()
                textAlign = Paint.Align.CENTER
                textSize = radius * 0.72f
                isFakeBoldText = true
            }
            canvas.drawText(
                category.shortCode.take(3),
                visual.centerX,
                visual.centerY - (paint.ascent() + paint.descent()) / 2f,
                paint
            )
        }
    }

    private fun drawClusterMarker(
        canvas: Canvas,
        x: Float,
        y: Float,
        diameter: Float,
        alerts: List<PokemonAlert>
    ) {
        val colors = alerts.map { resolveAlertVisualStyle(it).category.accentArgb.toInt() }.distinct()
        val fill = colors.singleOrNull() ?: 0xFF455A64.toInt()
        val outerRadius = diameter / 2f
        val fillRadius = diameter * 0.43f
        canvas.drawCircle(x, y, outerRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })
        canvas.drawCircle(x, y, fillRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = diameter * 0.38f
            isFakeBoldText = true
        }
        canvas.drawText(
            alerts.size.toString(),
            x,
            y - (paint.ascent() + paint.descent()) / 2f,
            paint
        )
    }

    private const val MAX_ALERTS = 8
}

private data class PositionedRadarAlert(
    val alert: PokemonAlert,
    val x: Float,
    val y: Float
)

internal fun projectToRadar(
    point: RadarGeoPoint,
    viewport: RadarViewport,
    widthPx: Int,
    heightPx: Int
): Pair<Float, Float> {
    val scale = 256.0 * 2.0.pow(viewport.zoom)
    val center = mercatorWorld(viewport.center.latitude, viewport.center.longitude)
    val target = mercatorWorld(point.latitude, point.longitude)
    return (
        widthPx / 2.0 + (target.first - center.first) * scale
        ).toFloat() to (
        heightPx / 2.0 + (target.second - center.second) * scale
        ).toFloat()
}

internal fun clusterRadarPoints(
    points: List<Pair<Float, Float>>,
    cellSizePx: Float
): List<List<Int>> {
    val clusters = mutableListOf<MutableList<Int>>()
    val thresholdSquared = cellSizePx * cellSizePx
    points.indices.forEach { index ->
        val point = points[index]
        val cluster = clusters.firstOrNull { members ->
            members.any { memberIndex ->
                val member = points[memberIndex]
                val dx = point.first - member.first
                val dy = point.second - member.second
                dx * dx + dy * dy <= thresholdSquared
            }
        }
        if (cluster == null) clusters += mutableListOf(index) else cluster += index
    }
    return clusters
}

internal fun isRadarPointVisible(
    x: Float,
    y: Float,
    widthPx: Int,
    heightPx: Int,
    markerMarginPx: Float = 0f
): Boolean =
    x >= -markerMarginPx &&
        x <= widthPx + markerMarginPx &&
        y >= -markerMarginPx &&
        y <= heightPx + markerMarginPx

internal fun radarAccuracyRadiusPx(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float,
    viewport: RadarViewport,
    widthPx: Int,
    heightPx: Int
): Float {
    val center = projectToRadar(
        RadarGeoPoint(latitude, longitude),
        viewport,
        widthPx,
        heightPx
    )
    val edge = projectToRadar(
        RadarGeoPoint(latitude + accuracyMeters.coerceAtLeast(1f) / 111_320.0, longitude),
        viewport,
        widthPx,
        heightPx
    )
    return kotlin.math.abs(center.second - edge.second)
}

private fun locationAccuracyBounds(location: Location): List<RadarGeoPoint> {
    val meters = location.accuracy.toDouble().coerceAtLeast(1.0)
    val latitudeDelta = meters / 111_320.0
    val longitudeScale = kotlin.math.cos(Math.toRadians(location.latitude)).coerceAtLeast(0.1)
    val longitudeDelta = meters / (111_320.0 * longitudeScale)
    return listOf(
        RadarGeoPoint(location.latitude - latitudeDelta, location.longitude),
        RadarGeoPoint(location.latitude + latitudeDelta, location.longitude),
        RadarGeoPoint(location.latitude, location.longitude - longitudeDelta),
        RadarGeoPoint(location.latitude, location.longitude + longitudeDelta)
    )
}

private fun mercatorWorld(latitude: Double, longitude: Double): Pair<Double, Double> {
    val clampedLatitude = latitude.coerceIn(-85.0, 85.0)
    val x = (longitude + 180.0) / 360.0
    val sine = sin(clampedLatitude * PI / 180.0)
    val y = 0.5 - ln((1.0 + sine) / (1.0 - sine)) / (4.0 * PI)
    return x to y
}

private fun inverseMercatorWorld(x: Double, y: Double): RadarGeoPoint {
    val longitude = x * 360.0 - 180.0
    val latitude = Math.toDegrees(atan(exp((0.5 - y) * 2.0 * PI)) * 2.0 - PI / 2.0)
    return RadarGeoPoint(latitude.coerceIn(-85.0, 85.0), longitude.coerceIn(-180.0, 180.0))
}
