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
import kotlinx.coroutines.Dispatchers
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
    private var pendingMarkers: List<OpenStreetMapMarker> = emptyList()
    private var pendingUserLocation: android.location.Location? = null
    private var pendingContentInsets = MapContentInsets(0, 0, 0, 0)
    var onAlertClick: (PokemonAlert) -> Unit = {}
    var onCameraChanged: (MapCameraSnapshot) -> Unit = {}

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
        applyContentInsets()
        renderMarkers(context)
    }

    fun detach() {
        map = null
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
        userLocation: android.location.Location?
    ) {
        pendingMarkers = markers
        pendingUserLocation = userLocation
        renderMarkers(context)
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
        pendingUserLocation?.let { location ->
            currentMap.addMarker(
                MarkerOptions()
                    .position(LatLng(location.latitude, location.longitude))
                    .title(USER_LOCATION_MARKER_TITLE)
                    .icon(iconFactory.fromBitmap(createUserLocationBitmap(context)))
            )
        }
    }

    private fun createUserLocationBitmap(context: android.content.Context): Bitmap {
        val size = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val center = size / 2f
            canvas.drawCircle(
                center,
                center,
                size * 0.42f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
            )
            canvas.drawCircle(
                center,
                center,
                size * 0.30f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(33, 150, 243) }
            )
        }
    }

    private companion object {
        const val USER_LOCATION_MARKER_TITLE = "__user_location__"
    }
}

@Composable
internal fun OpenStreetMapView(
    modifier: Modifier,
    alerts: List<PokemonAlert>,
    userLocation: android.location.Location?,
    cameraSnapshot: MapCameraSnapshot,
    contentInsets: MapContentInsets,
    showTimeLabels: Boolean,
    now: Long,
    controller: OpenStreetMapController,
    onMapLoaded: () -> Unit,
    onLoadError: () -> Unit,
    onAlertClick: (PokemonAlert) -> Unit,
    onCameraChanged: (MapCameraSnapshot) -> Unit
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
                    }
                    map.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) styleLoaded@{
                        if (!lifecycleGuard.isActive || mapView.isDestroyed) return@styleLoaded
                        controller.attach(map, context)
                        controller.setCamera(cameraSnapshot)
                        lifecycleGuard.runIfActive(onMapLoaded)
                    }
                }
            }
        }
    )

    LaunchedEffect(alerts, userLocation, showTimeLabels, if (showTimeLabels) now / 30_000L else 0L, basePalette) {
        val markers = withContext(Dispatchers.IO) {
            alerts.mapNotNull { alert ->
                val visualStyle = resolveAlertVisualStyle(alert)
                val markerLabel = alert.displayCp?.let { "CP $it" } ?: when (visualStyle.category) {
                    AlertCategory.HUNDO -> "100%"
                    AlertCategory.NUNDO -> "0%"
                    else -> visualStyle.shortCode
                }
                val timeRemaining = (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE) - now
                val timeLabel = if (timeRemaining <= 0L) "Expired" else TimeUtils.formatDurationShort(timeRemaining)
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
                    palette = basePalette.copy(primary = visualStyle.category.accentArgb.toInt())
                ) ?: return@mapNotNull null
                OpenStreetMapMarker(alert, icon)
            }
        }
        controller.setMarkers(context, markers, userLocation)
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
