package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal data class MapCameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

internal data class MapContentInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * One symbol on the map's alert layer.
 *
 * [iconId] is the style image the symbol points at rather than a bitmap of its own, so every
 * marker drawing the same pin shares one GPU texture; [labelId] is the countdown strip's own
 * shared image. A countdown tick changes which image a symbol points at and nothing else, which
 * is why the pin is uploaded once and then reused for as long as it stays on screen.
 */
internal data class OpenStreetMapMarker(
    val item: MapMarkerItem,
    val iconId: String,
    val icon: MapMarkerIcon,
    val labelId: String? = null,
    val labelBitmap: Bitmap? = null,
    val zIndex: Float = 0f
) {
    val kind: String
        get() = when {
            item is MapMarkerItem.Cluster -> MARKER_KIND_CLUSTER
            zIndex >= MAP_EMPHASIZED_MARKER_Z_INDEX -> MARKER_KIND_EMPHASIZED
            else -> MARKER_KIND_PIN
        }
}

internal const val MARKER_KIND_PIN = "pin"
internal const val MARKER_KIND_EMPHASIZED = "emph"
internal const val MARKER_KIND_CLUSTER = "cluster"

/**
 * Where a pin's artwork sits relative to the coordinate it marks, in pixels.
 *
 * The pin bitmaps put the ground point a little above their bottom edge, to leave room for the
 * shadow, and the countdown strip hangs below it. Both are a fixed fraction of the marker size,
 * so one of these per size covers every marker drawn at that size and the layers can carry the
 * offsets as constants instead of every feature carrying its own.
 */
internal data class MapSymbolGeometry(
    val pinBottomOffsetPx: Float,
    val labelTopOffsetPx: Float
) {
    companion object {
        fun forMarkerSize(sizePx: Int): MapSymbolGeometry = MapSymbolGeometry(
            pinBottomOffsetPx = (sizePx * 0.12f).toInt().toFloat(),
            labelTopOffsetPx = sizePx * 0.05f
        )
    }
}

internal class OpenStreetMapLifecycleGuard(
    private val onStart: () -> Unit,
    private val onResume: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onDestroy: () -> Unit
) {
    private var started = false
    private var resumed = false
    private var destroyed = false

    val isActive: Boolean
        get() = !destroyed

    fun start() {
        if (destroyed || started) return
        onStart()
        started = true
    }

    fun resume() {
        if (destroyed || resumed) return
        start()
        onResume()
        resumed = true
    }

    fun pause() {
        if (destroyed || !resumed) return
        resumed = false
        onPause()
    }

    fun stop() {
        if (destroyed) return
        pause()
        if (!started) return
        started = false
        onStop()
    }

    fun destroy() {
        if (destroyed) return
        pause()
        stop()
        destroyed = true
        onDestroy()
    }

    fun runIfActive(block: () -> Unit): Boolean {
        if (destroyed) return false
        block()
        return true
    }
}

internal class OpenStreetMapController {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var pendingMarkers: List<OpenStreetMapMarker> = emptyList()
    private var pendingAlerts: List<PokemonAlert> = emptyList()
    private var pendingShowSpawnRadius = false
    private var pendingSpacialRendEnabled = false
    private var pendingUserPose: MapUserPose? = null
    private var pendingWeatherCells: List<MapWeatherCell> = emptyList()
    /** Held so weather glyphs can be rasterised whenever the cell set changes, not just on attach. */
    private var imageContext: android.content.Context? = null
    private val registeredWeatherImages = HashSet<String>()
    private var pendingContentInsets = MapContentInsets(0, 0, 0, 0)
    /** Live camera zoom, tracked only to gate the spawn-radius polygons. */
    private var pendingZoom: Double = 0.0
    private var pendingPinGeometry = MapSymbolGeometry(0f, 0f)
    private var pendingEmphasizedGeometry = MapSymbolGeometry(0f, 0f)
    val maximumZoom: Double get() = map?.maxZoomLevel ?: 20.0
    var onAlertClick: (PokemonAlert) -> Unit = {}
    var onClusterClick: (MapMarkerItem.Cluster) -> Unit = {}
    var onCameraChanged: (MapCameraSnapshot) -> Unit = {}
    var onUserGesture: () -> Unit = {}
    var onWeatherCellClick: (String) -> Unit = {}
    private var pendingGesturesEnabled = true

    /** Style images this controller has registered, so each pin is uploaded exactly once. */
    private val registeredImages = HashSet<String>()
    private val markerIndex = HashMap<String, MapMarkerItem>()

    fun attach(map: MapLibreMap, context: android.content.Context) {
        this.map = map
        // Everything the map draws is a style layer now, so everything is hit-tested the same
        // way. The alert pins used to be legacy annotations with their own click listener, and
        // the weather glyphs - already a SymbolLayer - were completely dead to touch until this
        // path was added for them; one query serves both.
        map.addOnMapClickListener { point ->
            if (this.map !== map) return@addOnMapClickListener false
            val screenPoint = map.projection.toScreenLocation(point)
            // A finger is not a pixel: query a box around the touch, in the same order the
            // layers are stacked, so an emphasized pin or a cluster wins over a pin beneath it.
            val touchBox = android.graphics.RectF(
                screenPoint.x - MARKER_TOUCH_SLOP_PX,
                screenPoint.y - MARKER_TOUCH_SLOP_PX,
                screenPoint.x + MARKER_TOUCH_SLOP_PX,
                screenPoint.y + MARKER_TOUCH_SLOP_PX
            )
            val markerHit = listOf(ALERT_EMPHASIS_LAYER, ALERT_CLUSTER_LAYER, ALERT_PIN_LAYER)
                .asSequence()
                .flatMap { layer -> map.queryRenderedFeatures(touchBox, layer).asSequence() }
                .mapNotNull { feature ->
                    feature.getStringProperty(ALERT_ID_PROPERTY)?.let(markerIndex::get)
                }
                .firstOrNull()
            if (markerHit != null) {
                when (markerHit) {
                    is MapMarkerItem.Alert -> onAlertClick(markerHit.alert)
                    is MapMarkerItem.Cluster -> onClusterClick(markerHit)
                }
                return@addOnMapClickListener true
            }
            val hit = map.queryRenderedFeatures(screenPoint, WEATHER_GLYPH_LAYER)
                .firstOrNull { it.hasProperty(WEATHER_AREA_PROPERTY) }
                ?.getStringProperty(WEATHER_AREA_PROPERTY)
            if (hit != null) {
                onWeatherCellClick(hit)
                true
            } else {
                false
            }
        }
        map.addOnCameraIdleListener {
            if (this.map !== map) return@addOnCameraIdleListener
            val position = map.cameraPosition
            val target = position.target ?: return@addOnCameraIdleListener
            pendingZoom = position.zoom
            // Spawn circles re-render through the marker pass on every camera change; the
            // weather cells have no such pass, so their zoom gate is evaluated here.
            renderWeatherCells()
            onCameraChanged(
                MapCameraSnapshot(
                    latitude = target.latitude,
                    longitude = target.longitude,
                    zoom = position.zoom
                )
            )
        }
        map.addOnCameraMoveStartedListener { reason ->
            if (this.map === map && reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                onUserGesture()
            }
        }
        applyContentInsets()
        applyGestureSettings()
        renderMarkers()
    }

    /**
     * Picture-in-picture windows swallow touch events, so leaving the gestures on there
     * only lets a stray system interaction drag the camera away from the live position.
     */
    fun setGesturesEnabled(enabled: Boolean) {
        pendingGesturesEnabled = enabled
        applyGestureSettings()
    }

    private fun applyGestureSettings() {
        map?.uiSettings?.apply {
            isScrollGesturesEnabled = pendingGesturesEnabled
            isZoomGesturesEnabled = pendingGesturesEnabled
            isCompassEnabled = pendingGesturesEnabled
        }
    }

    fun attachStyle(style: Style, context: android.content.Context) {
        this.style = style
        this.imageContext = context.applicationContext
        registeredWeatherImages.clear()
        // A new style starts with an empty image table, so anything this controller registered
        // against the old one is gone whether it is remembered here or not.
        registeredImages.clear()
        style.addImage(USER_DOT_IMAGE, createMapUserMarkerBitmap(context, directional = false))
        style.addImage(USER_ARROW_IMAGE, createMapUserMarkerBitmap(context, directional = true))
        style.addSource(GeoJsonSource(WEATHER_CELL_SOURCE))
        style.addSource(GeoJsonSource(WEATHER_GLYPH_SOURCE))
        style.addSource(GeoJsonSource(SPAWN_RADIUS_SOURCE))
        style.addSource(GeoJsonSource(USER_ACCURACY_SOURCE))
        style.addSource(GeoJsonSource(USER_POSE_SOURCE))
        // Weather cells sit at the very bottom: they are ~10km across, so anything drawn
        // over them would otherwise be tinted by a fill that covers most of the screen.
        style.addLayer(
            FillLayer(WEATHER_CELL_LAYER, WEATHER_CELL_SOURCE).withProperties(
                fillColor(AlertCategory.WEATHER.accentArgb.toInt()),
                fillOpacity(0.07f)
            )
        )
        style.addLayer(
            LineLayer(WEATHER_CELL_LINE_LAYER, WEATHER_CELL_SOURCE).withProperties(
                lineColor(AlertCategory.WEATHER.accentArgb.toInt()),
                lineWidth(1.6f),
                lineOpacity(0.85f)
            )
        )
        style.addLayer(
            FillLayer(SPAWN_RADIUS_LAYER, SPAWN_RADIUS_SOURCE).withProperties(
                fillColor(AndroidColor.parseColor("#1A73E8")),
                fillOpacity(0.28f),
                fillOutlineColor(AndroidColor.parseColor("#1A73E8"))
            )
        )
        style.addLayer(
            LineLayer(SPAWN_RADIUS_LINE_LAYER, SPAWN_RADIUS_SOURCE).withProperties(
                lineColor(AndroidColor.parseColor("#1A73E8")),
                lineWidth(2.5f),
                lineOpacity(0.85f)
            )
        )
        style.addLayer(
            FillLayer(USER_ACCURACY_LAYER, USER_ACCURACY_SOURCE).withProperties(
                fillColor(MAP_USER_LOCATION_BLUE),
                fillOpacity(0.14f),
                fillOutlineColor(MAP_USER_LOCATION_BLUE)
            )
        )
        style.addLayer(
            SymbolLayer(USER_POSE_LAYER, USER_POSE_SOURCE).withProperties(
                iconImage(Expression.get("icon")),
                iconRotate(Expression.get("heading")),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )
        style.addLayer(
            SymbolLayer(WEATHER_GLYPH_LAYER, WEATHER_GLYPH_SOURCE).withProperties(
                iconImage(Expression.get("icon")),
                iconOpacity(Expression.get("opacity")),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )
        // The alert layers go on last, so they sit above the circles and cells without any
        // reordering pass. The old annotation layer was created by the SDK whenever the first
        // marker appeared, which is why the overlays underneath it had to be lifted and
        // reinserted after the fact.
        style.addSource(GeoJsonSource(ALERT_SOURCE))
        style.addLayer(alertSymbolLayer(ALERT_PIN_LAYER, MARKER_KIND_PIN))
        style.addLayer(alertLabelLayer(ALERT_LABEL_LAYER, MARKER_KIND_PIN))
        style.addLayer(alertSymbolLayer(ALERT_CLUSTER_LAYER, MARKER_KIND_CLUSTER))
        style.addLayer(alertSymbolLayer(ALERT_EMPHASIS_LAYER, MARKER_KIND_EMPHASIZED))
        style.addLayer(alertLabelLayer(ALERT_EMPHASIS_LABEL_LAYER, MARKER_KIND_EMPHASIZED))
        applySymbolGeometry()

        renderUserPose()
        renderSpawnRadii()
        renderWeatherCells()
        renderMarkers()
    }

    /**
     * A pin layer for one kind of marker.
     *
     * Overlap and placement checks are off because these are alerts, not map labels: a pin that
     * MapLibre decided to hide for being too close to another one is an alert the user cannot
     * see or tap. Clusters anchor at their centre because their artwork is a circle; pins anchor
     * at the bottom and are nudged down by the shadow padding, so the ground point lands on the
     * coordinate.
     */
    private fun alertSymbolLayer(layerId: String, kind: String): SymbolLayer =
        SymbolLayer(layerId, ALERT_SOURCE).withProperties(
            iconImage(Expression.get(ALERT_ICON_PROPERTY)),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconAnchor(
                if (kind == MARKER_KIND_CLUSTER) {
                    Property.ICON_ANCHOR_CENTER
                } else {
                    Property.ICON_ANCHOR_BOTTOM
                }
            )
        ).withFilter(
            Expression.eq(Expression.get(ALERT_KIND_PROPERTY), Expression.literal(kind))
        )

    /** The countdown strip, hung below the pin's ground point as an image of its own. */
    private fun alertLabelLayer(layerId: String, kind: String): SymbolLayer =
        SymbolLayer(layerId, ALERT_SOURCE).withProperties(
            iconImage(Expression.get(ALERT_LABEL_PROPERTY)),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconAnchor(Property.ICON_ANCHOR_TOP)
        ).withFilter(
            Expression.all(
                Expression.eq(Expression.get(ALERT_KIND_PROPERTY), Expression.literal(kind)),
                Expression.has(ALERT_LABEL_PROPERTY)
            )
        )

    /**
     * Marker artwork is sized in zoom bands, so these change rarely - but when they do, every
     * symbol on the layer moves with them, which is the whole reason the offsets live on the
     * layer instead of on each feature.
     */
    fun setSymbolGeometry(pin: MapSymbolGeometry, emphasized: MapSymbolGeometry) {
        if (pin == pendingPinGeometry && emphasized == pendingEmphasizedGeometry) return
        pendingPinGeometry = pin
        pendingEmphasizedGeometry = emphasized
        applySymbolGeometry()
    }

    private fun applySymbolGeometry() {
        val currentStyle = style ?: return
        fun offset(layerId: String, x: Float, y: Float) {
            currentStyle.getLayerAs<SymbolLayer>(layerId)
                ?.setProperties(iconOffset(arrayOf(x, y)))
        }
        offset(ALERT_PIN_LAYER, 0f, pendingPinGeometry.pinBottomOffsetPx)
        offset(ALERT_LABEL_LAYER, 0f, pendingPinGeometry.labelTopOffsetPx)
        offset(ALERT_EMPHASIS_LAYER, 0f, pendingEmphasizedGeometry.pinBottomOffsetPx)
        offset(ALERT_EMPHASIS_LABEL_LAYER, 0f, pendingEmphasizedGeometry.labelTopOffsetPx)
    }

    fun detach() {
        map = null
        style = null
    }

    fun setCamera(snapshot: MapCameraSnapshot, animate: Boolean = false) {
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(snapshot.latitude, snapshot.longitude))
                .zoom(snapshot.zoom)
                .build()
        )
        map?.let { currentMap ->
            if (animate) currentMap.animateCamera(update, 750) else currentMap.moveCamera(update)
        }
    }

    fun fitAlerts(coordinates: List<AlertMapCoordinates>, paddingPx: Int) {
        val currentMap = map ?: return
        // A live-follow animation can still be finishing as PiP switches into destination
        // browsing. Cancel it so it cannot overwrite the user + destination fit.
        currentMap.cancelTransitions()
        when (coordinates.size) {
            0 -> Unit
            1 -> {
                val point = coordinates.first()
                currentMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0),
                    750
                )
            }
            else -> {
                val bounds = LatLngBounds.Builder()
                    .includes(coordinates.map { LatLng(it.latitude, it.longitude) })
                    .build()
                currentMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx), 750)
            }
        }
    }

    fun setMarkers(
        context: android.content.Context,
        markers: List<OpenStreetMapMarker>,
        rawAlerts: List<PokemonAlert> = emptyList()
    ) {
        imageContext = context.applicationContext
        pendingMarkers = markers
        pendingAlerts = rawAlerts
        renderMarkers()
        renderSpawnRadii()
    }

    fun setWeatherCells(context: android.content.Context, cells: List<MapWeatherCell>) {
        imageContext = context.applicationContext
        pendingWeatherCells = cells
        renderWeatherCells()
    }

    fun setSpawnRadiusOptions(showRadius: Boolean, spacialRend: Boolean) {
        pendingShowSpawnRadius = showRadius
        pendingSpacialRendEnabled = spacialRend
        renderSpawnRadii()
    }

    fun setUserPose(pose: MapUserPose?) {
        pendingUserPose = pose
        renderUserPose()
    }

    fun setContentInsets(insets: MapContentInsets) {
        pendingContentInsets = insets
        applyContentInsets()
    }

    private fun applyContentInsets() {
        val insets = pendingContentInsets
        map?.setPadding(insets.left, insets.top, insets.right, insets.bottom)
    }

    /**
     * Publishes the whole marker set as one GeoJSON update.
     *
     * Each pin's artwork is registered as a style image the first time it is seen and then
     * referenced by name, so markers that draw the same pin share a single GPU texture and a
     * marker that stays on screen is never uploaded twice. Only genuinely new artwork costs
     * anything here; the update itself is a source swap, which is why a countdown tick - which
     * changes only which label image each symbol points at - no longer touches a texture at all.
     *
     * This replaces a per-marker annotation diff. That diff was already careful, but every
     * annotation icon went through `IconFactory`, which mints a *separate* style image per call:
     * a Darmstadt screenful with countdowns on re-uploaded 126 textures per tick, twice.
     */
    private fun renderMarkers() {
        val currentStyle = style ?: return
        val source = currentStyle.getSourceAs<GeoJsonSource>(ALERT_SOURCE) ?: return
        val markers = pendingMarkers
        val features = ArrayList<Feature>(markers.size)
        val used = HashSet<String>(markers.size)
        var uploaded = 0

        markerIndex.clear()
        markers.forEach { model ->
            val id = markerId(model.item)
            if (registeredImages.add(model.iconId)) {
                currentStyle.addImage(model.iconId, model.icon.bitmap)
                uploaded++
            }
            used += model.iconId
            val labelId = model.labelId
            val labelBitmap = model.labelBitmap
            if (labelId != null && labelBitmap != null) {
                if (registeredImages.add(labelId)) {
                    currentStyle.addImage(labelId, labelBitmap)
                    uploaded++
                }
                used += labelId
            }
            features += Feature.fromGeometry(
                Point.fromLngLat(model.item.longitude, model.item.latitude)
            ).apply {
                addStringProperty(ALERT_ID_PROPERTY, id)
                addStringProperty(ALERT_KIND_PROPERTY, model.kind)
                addStringProperty(ALERT_ICON_PROPERTY, model.iconId)
                if (labelId != null) addStringProperty(ALERT_LABEL_PROPERTY, labelId)
            }
            markerIndex[id] = model.item
        }

        source.setGeoJson(FeatureCollection.fromFeatures(features))
        val evicted = evictUnusedImages(currentStyle, used)
        MapPerfLog.event(
            "osm.symbols",
            "total=${features.size} uploaded=$uploaded images=${registeredImages.size} evicted=$evicted"
        )
    }

    /**
     * Drops style images nothing on screen is using once the table grows past its budget.
     *
     * Sharing artwork means the table only grows when genuinely new artwork appears, but panning
     * across a city still accumulates species the user has left behind. Eviction waits for the
     * budget rather than running every frame, because an image dropped the moment it scrolls off
     * is an image re-uploaded the moment it scrolls back.
     */
    private fun evictUnusedImages(currentStyle: Style, inUse: Set<String>): Int {
        // Countdown sprites go as soon as they stop being worn. At second precision the text
        // changes every tick, so keeping them would add a handful of images per second forever;
        // they are a few hundred bytes each and come straight back out of the bitmap cache.
        val stale = registeredImages.filterTo(mutableListOf()) { id ->
            id !in inUse && id.startsWith(COUNTDOWN_IMAGE_PREFIX)
        }
        if (registeredImages.size > MAX_REGISTERED_MARKER_IMAGES) {
            registeredImages.filterNotTo(stale, inUse::contains)
        }
        stale.forEach { id ->
            currentStyle.removeImage(id)
            registeredImages.remove(id)
        }
        return stale.size
    }

    private fun renderUserPose() {
        val currentStyle = style ?: return
        val poseSource = currentStyle.getSourceAs<GeoJsonSource>(USER_POSE_SOURCE) ?: return
        val accuracySource = currentStyle.getSourceAs<GeoJsonSource>(USER_ACCURACY_SOURCE) ?: return
        val pose = pendingUserPose
        if (pose == null) {
            poseSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            accuracySource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }

        val location = pose.location
        val point = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
            addStringProperty("icon", if (pose.headingDegrees == null) USER_DOT_IMAGE else USER_ARROW_IMAGE)
            addNumberProperty("heading", pose.headingDegrees ?: 0f)
        }
        poseSource.setGeoJson(point)
        accuracySource.setGeoJson(
            Feature.fromGeometry(
                createAccuracyPolygon(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = location.accuracy.toDouble().coerceAtLeast(1.0)
                )
            )
        )
    }

    /**
     * Draws one outlined cell per scanned area, with the condition glyph at its centre.
     *
     * Glyph bitmaps are registered with the style lazily and keyed by condition, so switching
     * from rain to clear adds one image rather than rebuilding the layer.
     */
    private fun renderWeatherCells() {
        val currentStyle = style ?: return
        val cellSource = currentStyle.getSourceAs<GeoJsonSource>(WEATHER_CELL_SOURCE) ?: return
        val glyphSource = currentStyle.getSourceAs<GeoJsonSource>(WEATHER_GLYPH_SOURCE) ?: return
        val context = imageContext
        val cells = pendingWeatherCells

        if (cells.isEmpty() || context == null || pendingZoom < WEATHER_CELL_MIN_ZOOM) {
            cellSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            glyphSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }

        val outlines = cells.map { cell ->
            Feature.fromGeometry(
                Polygon.fromLngLats(
                    listOf(cell.boundary.map { Point.fromLngLat(it.longitude, it.latitude) })
                )
            )
        }
        cellSource.setGeoJson(FeatureCollection.fromFeatures(outlines))

        val glyphs = cells.map { cell ->
            val imageId = weatherImageId(cell)
            if (registeredWeatherImages.add(imageId)) {
                currentStyle.addImage(
                    imageId,
                    createWeatherCellBitmap(context, cell.display.glyph, cell.display.confirmed)
                )
            }
            Feature.fromGeometry(
                Point.fromLngLat(cell.centre.longitude, cell.centre.latitude)
            ).apply {
                addStringProperty("icon", imageId)
                addStringProperty(WEATHER_AREA_PROPERTY, cell.area)
                addNumberProperty("opacity", if (cell.display.confirmed) 1f else 0.65f)
            }
        }
        glyphSource.setGeoJson(FeatureCollection.fromFeatures(glyphs))
    }

    private fun weatherImageId(cell: MapWeatherCell): String =
        "weather-${cell.display.glyph}-${cell.display.confirmed}"

    private fun renderSpawnRadii() {
        val currentStyle = style ?: return
        val radiusSource = currentStyle.getSourceAs<GeoJsonSource>(SPAWN_RADIUS_SOURCE) ?: return
        val radiusMeters = spawnRadiusMeters(pendingShowSpawnRadius, pendingSpacialRendEnabled)
        if (radiusMeters == null || pendingZoom < SPAWN_CIRCLE_MIN_ZOOM) {
            // Either hidden, or zoomed too far out for circles to be readable — at Darmstadt
            // density a citywide render is thousands of 48-vertex polygons for visual mush.
            radiusSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val features = pendingAlerts
            .filter { it.isSpawnAlert }
            .take(MAX_SPAWN_CIRCLES)
            .mapNotNull { alert ->
                val coords = alert.mapCoordinatesOrNull() ?: return@mapNotNull null
                Feature.fromGeometry(
                    createAccuracyPolygon(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        radiusMeters = radiusMeters
                    )
                )
            }
        radiusSource.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private companion object {
        const val ALERT_SOURCE = "alert-source"
        const val ALERT_PIN_LAYER = "alert-pin-layer"
        const val ALERT_LABEL_LAYER = "alert-label-layer"
        const val ALERT_CLUSTER_LAYER = "alert-cluster-layer"
        const val ALERT_EMPHASIS_LAYER = "alert-emphasis-layer"
        const val ALERT_EMPHASIS_LABEL_LAYER = "alert-emphasis-label-layer"
        const val ALERT_ID_PROPERTY = "id"
        const val ALERT_KIND_PROPERTY = "kind"
        const val ALERT_ICON_PROPERTY = "icon"
        const val ALERT_LABEL_PROPERTY = "label"

        /**
         * Roughly two screenfuls of distinct artwork. Large enough that panning around a city
         * keeps hitting registered images, small enough that the image table cannot grow without
         * bound over a long session.
         */
        const val MAX_REGISTERED_MARKER_IMAGES = 700

        /** Countdown sprite ids carry this, so eviction can tell them from pin artwork. */
        const val COUNTDOWN_IMAGE_PREFIX = "cd|"

        /** Half a fingertip, so a pin is tappable at its edges and not only dead centre. */
        const val MARKER_TOUCH_SLOP_PX = 28f

        const val WEATHER_CELL_SOURCE = "weather-cell-source"
        const val WEATHER_CELL_LAYER = "weather-cell-layer"
        const val WEATHER_CELL_LINE_LAYER = "weather-cell-line-layer"
        const val WEATHER_GLYPH_SOURCE = "weather-glyph-source"
        const val WEATHER_GLYPH_LAYER = "weather-glyph-layer"
        const val WEATHER_AREA_PROPERTY = "area"
        const val SPAWN_RADIUS_SOURCE = "spawn-radius-source"
        const val SPAWN_RADIUS_LAYER = "spawn-radius-layer"
        const val SPAWN_RADIUS_LINE_LAYER = "spawn-radius-line-layer"
        const val USER_ACCURACY_SOURCE = "user-accuracy-source"
        const val USER_POSE_SOURCE = "user-pose-source"
        const val USER_ACCURACY_LAYER = "user-accuracy-layer"
        const val USER_POSE_LAYER = "user-pose-layer"
        const val USER_DOT_IMAGE = "user-location-dot"
        const val USER_ARROW_IMAGE = "user-location-arrow"
    }

    private fun markerId(item: MapMarkerItem): String = when (item) {
        is MapMarkerItem.Alert -> item.alert.uniqueId
        is MapMarkerItem.Cluster -> "cluster-${item.id}"
    }
}

internal fun createAccuracyPolygon(
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
    points: Int = 48
): Polygon {
    val angularDistance = radiusMeters / 6_371_000.0
    val latitudeRadians = Math.toRadians(latitude)
    val longitudeRadians = Math.toRadians(longitude)
    val ring = (0..points.coerceAtLeast(8)).map { index ->
        val bearing = 2.0 * Math.PI * index / points.coerceAtLeast(8)
        val targetLatitude = asin(
            sin(latitudeRadians) * cos(angularDistance) +
                cos(latitudeRadians) * sin(angularDistance) * cos(bearing)
        )
        val targetLongitude = longitudeRadians + atan2(
            sin(bearing) * sin(angularDistance) * cos(latitudeRadians),
            cos(angularDistance) - sin(latitudeRadians) * sin(targetLatitude)
        )
        Point.fromLngLat(Math.toDegrees(targetLongitude), Math.toDegrees(targetLatitude))
    }
    return Polygon.fromLngLats(listOf(ring))
}

@Composable
internal fun OpenStreetMapView(
    modifier: Modifier,
    alerts: List<PokemonAlert>,
    markerItems: List<MapMarkerItem>,
    countdownTickMillis: Long,
    minutePrecisionCountdown: Boolean,
    userPose: MapUserPose?,
    cameraSnapshot: MapCameraSnapshot,
    contentInsets: MapContentInsets,
    showTimeLabels: Boolean,
    countdownClock: State<Long>,
    goDexMatches: Map<String, GoDexMatchResult>,
    controller: OpenStreetMapController,
    onMapLoaded: () -> Unit,
    onLoadError: () -> Unit,
    onAlertClick: (PokemonAlert) -> Unit,
    onClusterClick: (MapMarkerItem.Cluster) -> Unit = {},
    onCameraChanged: (MapCameraSnapshot) -> Unit,
    onUserGesture: () -> Unit,
    onWeatherCellClick: (String) -> Unit = {},
    showSpawnRadius: Boolean = false,
    spacialRendEnabled: Boolean = false,
    weatherCells: List<MapWeatherCell> = emptyList(),
    interactive: Boolean = true,
    protectedAlertIds: Set<String> = emptySet(),
    emphasizedAlertIds: Set<String> = emptySet(),
    baseMarkerSizeDp: Float = MAP_FULL_MARKER_SIZE_DP,
    emphasizedMarkerSizeDp: Float = MAP_FULL_MARKER_SIZE_DP,
    clusterMarkerSizeDp: Float = MAP_FULL_CLUSTER_SIZE_DP
) {
    val context = LocalContext.current
    val mapLibreReady = remember(context) { MapLibreInitializer.ensureInitialized(context) }
    LaunchedEffect(mapLibreReady) {
        if (!mapLibreReady) onLoadError()
    }
    if (!mapLibreReady) return

    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val now = countdownClock.value
    val baseMarkerSizePx = remember(density, baseMarkerSizeDp) {
        with(density) { baseMarkerSizeDp.dp.toPx().toInt() }
    }
    val emphasizedMarkerSizePx = remember(density, emphasizedMarkerSizeDp) {
        with(density) { emphasizedMarkerSizeDp.dp.toPx().toInt() }
    }
    val clusterMarkerSizePx = remember(density, clusterMarkerSizeDp) {
        with(density) { clusterMarkerSizeDp.dp.toPx().toInt() }
    }
    val basePalette = remember(
        colors.primary,
        colors.onPrimary,
        colors.surface,
        colors.onSurface,
        colors.outline,
        colors.error,
        colors.onError
    ) {
        MapMarkerPalette(
            primary = colors.primary.toArgb(),
            onPrimary = colors.onPrimary.toArgb(),
            surface = colors.surface.toArgb(),
            onSurface = colors.onSurface.toArgb(),
            outline = colors.outline.toArgb(),
            error = colors.error.toArgb(),
            onError = colors.onError.toArgb()
        )
    }
    val mapView = remember(context) {
        MapView(context).apply { onCreate(null) }
    }
    val lifecycleGuard = remember(mapView) {
        OpenStreetMapLifecycleGuard(
            onStart = { if (!mapView.isDestroyed) mapView.onStart() },
            onResume = { if (!mapView.isDestroyed) mapView.onResume() },
            onPause = { if (!mapView.isDestroyed) mapView.onPause() },
            onStop = { if (!mapView.isDestroyed) mapView.onStop() },
            onDestroy = { if (!mapView.isDestroyed) mapView.onDestroy() }
        )
    }

    controller.onAlertClick = onAlertClick
    controller.onClusterClick = onClusterClick
    controller.onCameraChanged = onCameraChanged
    controller.onUserGesture = onUserGesture
    controller.setContentInsets(contentInsets)

    DisposableEffect(lifecycleOwner, mapView, lifecycleGuard) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) lifecycleGuard.start()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) lifecycleGuard.resume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> lifecycleGuard.start()
                Lifecycle.Event.ON_RESUME -> lifecycleGuard.resume()
                Lifecycle.Event.ON_PAUSE -> lifecycleGuard.pause()
                Lifecycle.Event.ON_STOP -> lifecycleGuard.stop()
                Lifecycle.Event.ON_DESTROY -> {
                    controller.detach()
                    lifecycleGuard.destroy()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.detach()
            lifecycleGuard.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                addOnDidFailLoadingMapListener {
                    lifecycleGuard.runIfActive(onLoadError)
                }
                getMapAsync { map ->
                    if (!lifecycleGuard.isActive || mapView.isDestroyed) return@getMapAsync
                    map.setPrefetchesTiles(false)
                    map.setMinZoomPreference(3.0)
                    map.setMaxZoomPreference(20.0)
                    map.uiSettings.apply {
                        isLogoEnabled = false
                        isAttributionEnabled = false
                        isRotateGesturesEnabled = false
                    }
                    map.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) styleLoaded@{
                        if (!lifecycleGuard.isActive || mapView.isDestroyed) return@styleLoaded
                        controller.attach(map, context)
                        controller.attachStyle(it, context)
                        controller.setCamera(cameraSnapshot)
                        lifecycleGuard.runIfActive(onMapLoaded)
                    }
                }
            }
        }
    )

    val clusterSpawnRadiusMeters = spawnRadiusMeters(showSpawnRadius, spacialRendEnabled)

    // Load static artwork independently from the per-second countdown job. Even when a network
    // image is slow, the clock can keep replacing labels with an immediate custom fallback.
    LaunchedEffect(markerItems, baseMarkerSizePx) {
        withContext(Dispatchers.IO) {
            // Distinct urls only, and several at a time. This used to walk every marker in
            // order on one coroutine, so a screen of quest pins - each a separate reward
            // sprite - fetched them strictly one after another.
            val urls = markerItems.asSequence()
                .filterIsInstance<MapMarkerItem.Alert>()
                .mapNotNull { item ->
                    item.alert.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: item.alert.imageUrl?.takeIf { it.isNotBlank() }
                }
                .distinct()
                .toList()
            val gate = kotlinx.coroutines.sync.Semaphore(MAP_ARTWORK_PREFETCH_CONCURRENCY)
            coroutineScope {
                urls.forEach { url ->
                    launch {
                        gate.withPermit { loadMapMarkerArtwork(context, url, baseMarkerSizePx) }
                    }
                }
            }
        }
    }
    // Pins and countdowns are built by separate passes, and only the publish pass below talks to
    // the controller. The countdown used to be part of this effect's key, so every tick rebuilt
    // every pin - the cull, the icon lookups and the whole marker list - to change two digits.
    var pinMarkers by remember { mutableStateOf<List<OpenStreetMapMarker>>(emptyList()) }
    /*
     * Built pins, kept across camera moves.
     *
     * A pan changes which markers are on screen, not what any of them looks like, but the build
     * pass still ran over the whole visible set every time the camera settled - several hundred
     * cache-key constructions and lookups to re-derive pins that were already in hand. Keyed by
     * the alert and by whether it is urgent, so a pan costs only the markers that are genuinely
     * new. The map is recreated whenever anything that changes how a pin is *drawn* changes -
     * palette, marker size, GoDex matches, emphasis - which is what [styleGeneration] tracks.
     */
    val styleGeneration = remember(
        basePalette, goDexMatches, emphasizedAlertIds,
        baseMarkerSizePx, emphasizedMarkerSizePx, clusterMarkerSizePx
    ) { Any() }
    val pinCache = remember(styleGeneration) { HashMap<String, OpenStreetMapMarker>() }
    LaunchedEffect(
        markerItems,
        basePalette,
        goDexMatches,
        emphasizedAlertIds,
        baseMarkerSizePx,
        emphasizedMarkerSizePx,
        clusterMarkerSizePx
    ) {
        val immediateMarkers = withContext(Dispatchers.Default) {
          MapPerfLog.timed("osm.immediate.build", { "n=${markerItems.size}" }) {
            markerItems.map { item ->
                currentCoroutineContext().ensureActive()
                val emphasized = item is MapMarkerItem.Alert &&
                    item.alert.uniqueId in emphasizedAlertIds
                pinCache[openStreetMapPinCacheKey(item, now)]?.let { return@map it }
                createImmediateOpenStreetMapMarker(
                    item = item,
                    markerSizePx = if (emphasized) emphasizedMarkerSizePx else baseMarkerSizePx,
                    clusterMarkerSizePx = clusterMarkerSizePx,
                    showTimeLabels = showTimeLabels,
                    nowMillis = now,
                    minutePrecision = minutePrecisionCountdown,
                    basePalette = basePalette,
                    goDexMatches = goDexMatches,
                    emphasized = emphasized
                )
            }
          }
        }
        currentCoroutineContext().ensureActive()
        pinMarkers = immediateMarkers
        val markers = withContext(Dispatchers.IO) {
          MapPerfLog.timed("osm.full.build", { "n=${markerItems.size}" }) {
            markerItems.mapNotNull { item ->
                currentCoroutineContext().ensureActive()
                val cacheKey = openStreetMapPinCacheKey(item, now)
                pinCache[cacheKey]?.let { return@mapNotNull it }
                // Any group of alerts is a count bubble. It used to borrow the top alert's
                // species pin and wear a "+N" badge, which reads as one alert that happens to
                // carry a number rather than as the several it stands for.
                if (item is MapMarkerItem.Cluster) {
                    return@mapNotNull OpenStreetMapMarker(
                        item = item,
                        iconId = openStreetMapClusterIconId(item, clusterMarkerSizePx),
                        icon = createOpenStreetMapClusterIcon(
                            item.alerts.size,
                            item.sharedCategory,
                            clusterMarkerSizePx
                        ),
                        zIndex = MAP_CLUSTER_MARKER_Z_INDEX
                    ).also { pinCache[cacheKey] = it }
                }
                val alert = (item as MapMarkerItem.Alert).alert
                val emphasized = alert.uniqueId in emphasizedAlertIds
                val itemSizePx = if (emphasized) emphasizedMarkerSizePx else baseMarkerSizePx
                val request = openStreetMapIconRequest(alert, itemSizePx, basePalette, goDexMatches)
                // The pin is drawn without its countdown, so its identity - and the style image
                // it becomes - does not change when the clock ticks.
                val icon = createMapMarkerIcon(
                    context = context,
                    sizePx = request.sizePx,
                    categoryCode = request.categoryCode,
                    speciesName = request.speciesName,
                    speciesImageUrl = request.speciesImageUrl,
                    endTime = request.endTime,
                    showTimeLabel = false,
                    timeLabel = null,
                    palette = request.palette,
                    goDexStatus = request.goDexStatus,
                    category = request.category,
                    isHundo = request.isHundo,
                    isNundo = request.isNundo,
                    isPvp = request.isPvp,
                    isRare = request.isRare,
                    questQuantity = request.questQuantity,
                    raidTier = request.raidTier,
                    isRocket = request.isRocket,
                    isKecleon = request.isKecleon
                ) ?: return@mapNotNull null
                OpenStreetMapMarker(
                    item = item,
                    iconId = mapMarkerBaseIconCacheKey(request, now),
                    icon = icon,
                    zIndex = if (emphasized) MAP_EMPHASIZED_MARKER_Z_INDEX else 0f
                ).also {
                    // Bounded: a long session panning across a city would otherwise hold every
                    // pin it has ever drawn, and these carry bitmap references.
                    if (pinCache.size > MAX_CACHED_PINS) pinCache.clear()
                    pinCache[cacheKey] = it
                }
            }
          }
        }
        currentCoroutineContext().ensureActive()
        pinMarkers = markers
    }

    /*
     * The only thing that talks to the controller.
     *
     * On a tick this runs alone: the pins are already built, so all it does is look up which
     * shared countdown sprite each one should be wearing - almost always a cache hit - and
     * republish. On the map that is a source swap and no texture work at all.
     */
    LaunchedEffect(
        pinMarkers,
        showTimeLabels,
        minutePrecisionCountdown,
        mapCountdownRefreshKey(showTimeLabels, now, countdownTickMillis),
        basePalette
    ) {
        val published = if (!showTimeLabels) {
            pinMarkers.map { it.copy(labelId = null, labelBitmap = null) }
        } else {
            withContext(Dispatchers.Default) {
              MapPerfLog.timed("osm.labels.build", { "n=${pinMarkers.size}" }) {
                pinMarkers.map { marker ->
                    currentCoroutineContext().ensureActive()
                    val alert = (marker.item as? MapMarkerItem.Alert)?.alert
                        ?: return@map marker.copy(labelId = null, labelBitmap = null)
                    val sizePx = if (marker.kind == MARKER_KIND_EMPHASIZED) {
                        emphasizedMarkerSizePx
                    } else {
                        baseMarkerSizePx
                    }
                    val countdown = openStreetMapCountdown(
                        alert, now, minutePrecisionCountdown,
                        mapCountdownLabelHeightPx(sizePx), basePalette
                    )
                    marker.copy(labelId = countdown?.first, labelBitmap = countdown?.second)
                }
              }
            }
        }
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.Main.immediate) {
            MapPerfLog.timed("osm.publish", { "n=${published.size}" }) {
                controller.setMarkers(context, published, alerts)
            }
        }
    }

    // Marker artwork is sized in zoom bands, so this fires rarely - but when it does, every
    // symbol's offset moves with it.
    LaunchedEffect(baseMarkerSizePx, emphasizedMarkerSizePx) {
        withContext(Dispatchers.Main.immediate) {
            controller.setSymbolGeometry(
                pin = MapSymbolGeometry.forMarkerSize(baseMarkerSizePx),
                emphasized = MapSymbolGeometry.forMarkerSize(emphasizedMarkerSizePx)
            )
        }
    }

    LaunchedEffect(interactive) {
        controller.setGesturesEnabled(interactive)
    }

    LaunchedEffect(showSpawnRadius, spacialRendEnabled, alerts) {
        withContext(Dispatchers.Main.immediate) {
            controller.setSpawnRadiusOptions(showSpawnRadius, spacialRendEnabled)
        }
    }

    LaunchedEffect(userPose) {
        withContext(Dispatchers.Main.immediate) {
            controller.setUserPose(userPose)
        }
    }

    LaunchedEffect(weatherCells) {
        withContext(Dispatchers.Main.immediate) {
            controller.setWeatherCells(context, weatherCells)
        }
    }

    LaunchedEffect(onWeatherCellClick) {
        controller.onWeatherCellClick = onWeatherCellClick
    }
}

/**
 * How one alert wants to be drawn, without its countdown.
 *
 * Both render passes build this, and the countdown is deliberately absent: the strip is its own
 * shared sprite now, so leaving it out is what makes a pin's identity - and therefore its style
 * image - stable while the clock runs.
 */
internal fun openStreetMapIconRequest(
    alert: PokemonAlert,
    markerSizePx: Int,
    basePalette: MapMarkerPalette,
    goDexMatches: Map<String, GoDexMatchResult>
): MapMarkerIconRequest {
    val visualStyle = resolveAlertVisualStyle(alert)
    val markerLabel = alert.displayCp?.let { "CP $it" } ?: when (visualStyle.category) {
        AlertCategory.HUNDO -> "100%"
        AlertCategory.NUNDO -> "0%"
        else -> visualStyle.shortCode
    }
    return MapMarkerIconRequest(
        sizePx = markerSizePx,
        categoryCode = markerLabel,
        speciesName = alert.pokemon?.takeIf { it.isNotBlank() } ?: alert.cleanPokemonName,
        speciesImageUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: alert.imageUrl?.takeIf { it.isNotBlank() },
        endTime = alert.endTime,
        showTimeLabel = false,
        timeLabel = null,
        palette = basePalette.copy(primary = visualStyle.category.accentArgb.toInt()),
        goDexStatus = goDexMatches[alert.uniqueId]?.status ?: GoDexMatchStatus.NOT_CONFIGURED,
        category = visualStyle.category,
        isHundo = visualStyle.category == AlertCategory.HUNDO ||
            alert.formattedIv == "100%" || alert.iv == "100",
        isNundo = visualStyle.category == AlertCategory.NUNDO || alert.formattedIv == "0%",
        isPvp = visualStyle.category == AlertCategory.PVP || !alert.pvpRankings.isNullOrEmpty(),
        isRare = visualStyle.category == AlertCategory.RARE,
        questQuantity = extractQuestQuantity(alert.questReward),
        raidTier = resolveRaidTier(alert, visualStyle.category),
        isRocket = visualStyle.category == AlertCategory.ROCKET ||
            alert.gruntType != null || alert.type?.contains("Rocket") == true,
        isKecleon = alert.pokemon?.contains("Kecleon", ignoreCase = true) == true
    )
}

/** The countdown strip this alert should be wearing, as a shared image id and its bitmap. */
private fun openStreetMapCountdown(
    alert: PokemonAlert,
    nowMillis: Long,
    minutePrecision: Boolean,
    labelHeightPx: Int,
    palette: MapMarkerPalette
): Pair<String, Bitmap>? {
    val text = mapCountdownLabel(alert.endTime, nowMillis, minutePrecision, coarsenBeyondWindow = true)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val urgent = isMapMarkerUrgent(alert.endTime, nowMillis)
    return mapCountdownLabelImageKey(text, urgent, labelHeightPx) to
        createMapCountdownLabelBitmap(text, urgent, labelHeightPx, palette)
}

/** Roughly four screenfuls of markers. */
private const val MAX_CACHED_PINS = 1600

/**
 * What makes two requests for the same marker's pin identical.
 *
 * Urgency is in the key because it changes how the pin is drawn - an alert about to expire gains
 * a glow - and it is the one property of an alert that changes on its own over time. Everything
 * else that affects the drawing is tracked by the cache's generation instead.
 */
internal fun openStreetMapPinCacheKey(item: MapMarkerItem, nowMillis: Long): String = when (item) {
    is MapMarkerItem.Alert ->
        "a|${item.alert.uniqueId}|${isMapMarkerUrgent(item.alert.endTime, nowMillis)}"
    is MapMarkerItem.Cluster ->
        "c|${item.id}|${item.alerts.size}|${item.sharedCategory?.name.orEmpty()}"
}

private fun openStreetMapClusterIconId(item: MapMarkerItem.Cluster, sizePx: Int): String =
    "cluster|$sizePx|${item.sharedCategory?.name.orEmpty()}|${item.alerts.size}"

internal fun createImmediateOpenStreetMapMarker(
    item: MapMarkerItem,
    markerSizePx: Int,
    clusterMarkerSizePx: Int,
    showTimeLabels: Boolean,
    nowMillis: Long,
    minutePrecision: Boolean,
    basePalette: MapMarkerPalette,
    goDexMatches: Map<String, GoDexMatchResult>,
    emphasized: Boolean
): OpenStreetMapMarker {
    if (item is MapMarkerItem.Cluster) {
        return OpenStreetMapMarker(
            item = item,
            iconId = openStreetMapClusterIconId(item, clusterMarkerSizePx),
            icon = createOpenStreetMapClusterIcon(item.alerts.size, item.sharedCategory, clusterMarkerSizePx),
            zIndex = MAP_CLUSTER_MARKER_Z_INDEX
        )
    }
    val alert = (item as MapMarkerItem.Alert).alert
    val request = openStreetMapIconRequest(alert, markerSizePx, basePalette, goDexMatches)
    val key = mapMarkerBaseIconCacheKey(request, nowMillis)
    // A pin already rendered by an earlier pass keeps its own identity, so the full pass has
    // nothing to replace. Only a genuine fallback gets a fallback id, and only that one is
    // swapped out when the real artwork lands.
    val ready = markerIconCache.get(key) ?: markerBaseIconCache.get(key)
    return OpenStreetMapMarker(
        item = item,
        iconId = if (ready != null) key else "fallback|$key",
        icon = ready ?: resolveInitialMapMarkerIcon(request, key),
        zIndex = if (emphasized) MAP_EMPHASIZED_MARKER_Z_INDEX else 0f
    )
}

internal fun openStreetMapStyleJson(): String {
    val tileUrl = BuildConfig.OSM_TILE_URL
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return """
        {
          "version": 8,
          "sources": {
            "openstreetmap": {
              "type": "raster",
              "tiles": ["$tileUrl"],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 19,
              "attribution": "© OpenStreetMap contributors"
            }
          },
          "layers": [
            {
              "id": "openstreetmap",
              "type": "raster",
              "source": "openstreetmap"
            }
          ]
        }
    """.trimIndent()
}

private val openStreetMapClusterIconCache = object : android.util.LruCache<String, MapMarkerIcon>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: MapMarkerIcon): Int = value.bitmap.allocationByteCount
}

private fun createOpenStreetMapClusterIcon(
    count: Int,
    sharedCategory: AlertCategory?,
    sizePx: Int
): MapMarkerIcon {
    val size = sizePx.coerceAtLeast(1)
    val cacheKey = "$count:$sharedCategory:$size"
    openStreetMapClusterIconCache.get(cacheKey)?.let { return it }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val color = sharedCategory?.accentArgb?.toInt()
        ?: AndroidColor.parseColor("#455A64")
    canvas.drawCircle(size / 2f, size / 2f, size * 0.46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
    })
    canvas.drawCircle(size / 2f, size / 2f, size * 0.39f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
    })
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        textSize = size * 0.36f
        isFakeBoldText = true
    }
    canvas.drawText(
        if (count > 999) "999+" else count.toString(),
        size / 2f,
        size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
        textPaint
    )
    return MapMarkerIcon(bitmap, androidx.compose.ui.geometry.Offset(0.5f, 0.5f)).also {
        openStreetMapClusterIconCache.put(cacheKey, it)
    }
}
