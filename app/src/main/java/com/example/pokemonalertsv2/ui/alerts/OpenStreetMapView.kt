package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
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
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
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

internal data class OpenStreetMapMarker(
    val alert: PokemonAlert,
    val icon: MapMarkerIcon
)

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
    private var pendingContentInsets = MapContentInsets(0, 0, 0, 0)
    var onAlertClick: (PokemonAlert) -> Unit = {}
    var onCameraChanged: (MapCameraSnapshot) -> Unit = {}
    var onUserGesture: () -> Unit = {}

    fun attach(map: MapLibreMap, context: android.content.Context) {
        this.map = map
        map.setOnMarkerClickListener { marker ->
            if (this.map !== map) {
                false
            } else {
                pendingMarkers.firstOrNull { it.alert.uniqueId == marker.title }
                    ?.alert
                    ?.let { onAlertClick(it) }
                true
            }
        }
        map.addOnCameraIdleListener {
            if (this.map !== map) return@addOnCameraIdleListener
            val position = map.cameraPosition
            val target = position.target ?: return@addOnCameraIdleListener
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
        renderMarkers(context)
    }

    fun attachStyle(style: Style, context: android.content.Context) {
        this.style = style
        style.addImage(USER_DOT_IMAGE, createMapUserMarkerBitmap(context, directional = false))
        style.addImage(USER_ARROW_IMAGE, createMapUserMarkerBitmap(context, directional = true))
        style.addSource(GeoJsonSource(SPAWN_RADIUS_SOURCE))
        style.addSource(GeoJsonSource(USER_ACCURACY_SOURCE))
        style.addSource(GeoJsonSource(USER_POSE_SOURCE))
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
        renderUserPose()
        renderSpawnRadii()
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
        pendingMarkers = markers
        pendingAlerts = rawAlerts
        renderMarkers(context)
        renderSpawnRadii()
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

    private fun renderMarkers(context: android.content.Context) {
        val currentMap = map ?: return
        currentMap.removeAnnotations()
        val iconFactory = IconFactory.getInstance(context)
        pendingMarkers.forEach { model ->
            val coordinates = model.alert.mapCoordinatesOrNull() ?: return@forEach
            currentMap.addMarker(
                MarkerOptions()
                    .position(LatLng(coordinates.latitude, coordinates.longitude))
                    .title(model.alert.uniqueId)
                    .icon(iconFactory.fromBitmap(model.icon.bitmap))
            )
        }
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

    private fun renderSpawnRadii() {
        val currentStyle = style ?: return
        val radiusSource = currentStyle.getSourceAs<GeoJsonSource>(SPAWN_RADIUS_SOURCE) ?: return
        if (!pendingShowSpawnRadius) {
            radiusSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val radiusMeters = if (pendingSpacialRendEnabled) 80.0 else 40.0
        val features = pendingAlerts.filter { it.isSpawnAlert }.mapNotNull { alert ->
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
    userPose: MapUserPose?,
    cameraSnapshot: MapCameraSnapshot,
    contentInsets: MapContentInsets,
    showTimeLabels: Boolean,
    now: Long,
    controller: OpenStreetMapController,
    onMapLoaded: () -> Unit,
    onLoadError: () -> Unit,
    onAlertClick: (PokemonAlert) -> Unit,
    onCameraChanged: (MapCameraSnapshot) -> Unit,
    onUserGesture: () -> Unit,
    goDexEntries: List<GoDexEntryEntity> = emptyList(),
    goDexConfig: GoDexConfig = GoDexConfig(),
    showSpawnRadius: Boolean = false,
    spacialRendEnabled: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val markerSizePx = remember(density) { with(density) { 68.dp.toPx().toInt() } }
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
                        isCompassEnabled = true
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

    LaunchedEffect(alerts, mapCountdownRefreshKey(showTimeLabels, now), basePalette, goDexEntries, goDexConfig) {
        val markers = withContext(Dispatchers.IO) {
            val goDexRepository = GoDexRepository.getInstance(context)
            alerts.mapNotNull { alert ->
                val visualStyle = resolveAlertVisualStyle(alert)
                val matchResult = if (alert.hasType("hundo")) {
                    goDexRepository.match(alert, goDexEntries, goDexConfig.isConnected)
                } else {
                    GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED)
                }
                val markerLabel = alert.displayCp?.let { "CP $it" } ?: when (visualStyle.category) {
                    AlertCategory.HUNDO -> "100%"
                    AlertCategory.NUNDO -> "0%"
                    else -> visualStyle.shortCode
                }
                val timeLabel = mapCountdownLabel(alert.endTime, now)
                val icon = createMapMarkerIcon(
                    context = context,
                    sizePx = markerSizePx,
                    categoryCode = markerLabel,
                    speciesName = alert.pokemon?.takeIf { it.isNotBlank() } ?: alert.cleanPokemonName,
                    speciesImageUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: alert.imageUrl?.takeIf { it.isNotBlank() },
                    endTime = alert.endTime,
                    showTimeLabel = showTimeLabels,
                    timeLabel = if (showTimeLabels) timeLabel else null,
                    palette = basePalette.copy(primary = visualStyle.category.accentArgb.toInt()),
                    goDexStatus = matchResult.status
                ) ?: return@mapNotNull null
                OpenStreetMapMarker(alert, icon)
            }
        }
        currentCoroutineContext().ensureActive()
        controller.setMarkers(context, markers, alerts)
    }

    LaunchedEffect(showSpawnRadius, spacialRendEnabled, alerts) {
        controller.setSpawnRadiusOptions(showSpawnRadius, spacialRendEnabled)
    }

    LaunchedEffect(userPose) {
        controller.setUserPose(userPose)
    }
}

private fun openStreetMapStyleJson(): String {
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
