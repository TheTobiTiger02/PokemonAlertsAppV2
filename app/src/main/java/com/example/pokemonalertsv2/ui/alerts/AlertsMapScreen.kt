@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.LruCache
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatcher
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors
import com.example.pokemonalertsv2.ui.theme.LocalAppDarkTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerInfoWindowContent
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest

internal fun mapCountdownRefreshKey(showTimeLabels: Boolean, nowMillis: Long): Long =
    nowMillis / if (showTimeLabels) 1_000L else 30_000L

internal fun mapCountdownLabel(endTime: String?, nowMillis: Long): String {
    val remaining = (TimeUtils.parseEndTimeToMillis(endTime) ?: Long.MAX_VALUE) - nowMillis
    return if (remaining <= 0L) "Expired" else TimeUtils.formatDurationShort(remaining)
}

@Composable
fun AlertsMapRoute(
    viewModel: PokemonAlertsViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedMapStyle by viewModel.mapStylePreference.collectAsStateWithLifecycle(
        initialValue = MapStylePreference.GOOGLE_STANDARD
    )
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh while the map is visible without duplicating the main feed cadence.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshAlertsInBackground()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    val context = LocalContext.current
    val goDexRepository = remember(context) { GoDexRepository.getInstance(context) }
    val goDexEntries by goDexRepository.entries.collectAsStateWithLifecycle()
    val goDexConfig by goDexRepository.config.collectAsStateWithLifecycle()

    val showSpawnRadius by viewModel.showSpawnRadius.collectAsStateWithLifecycle(initialValue = false)
    val spacialRendEnabled by viewModel.spacialRendEnabled.collectAsStateWithLifecycle(initialValue = false)

    AlertsMapScreen(
        alerts = uiState.alerts,
        onBack = onBack,
        onRefresh = viewModel::refreshAlerts,
        initialMapStyle = savedMapStyle,
        onMapStyleChanged = viewModel::updateMapStylePreference,
        showBackButton = showBackButton,
        goDexEntries = goDexEntries,
        goDexConfig = goDexConfig,
        showSpawnRadius = showSpawnRadius,
        spacialRendEnabled = spacialRendEnabled,
        onToggleSpawnRadius = { viewModel.updateShowSpawnRadius(!showSpawnRadius) },
        onToggleSpacialRend = { viewModel.updateSpacialRendEnabled(!spacialRendEnabled) }
    )
}

@Composable
fun AlertsMapScreen(
    alerts: List<PokemonAlert>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    initialMapStyle: MapStylePreference = MapStylePreference.GOOGLE_STANDARD,
    onMapStyleChanged: (MapStylePreference) -> Unit = {},
    showBackButton: Boolean = true,
    goDexEntries: List<GoDexEntryEntity> = emptyList(),
    goDexConfig: GoDexConfig = GoDexConfig(),
    showSpawnRadius: Boolean = false,
    spacialRendEnabled: Boolean = false,
    onToggleSpawnRadius: () -> Unit = {},
    onToggleSpacialRend: () -> Unit = {}
) {
    AlertsMapScreenContent(
        alerts = alerts,
        onBack = onBack,
        onRefresh = onRefresh,
        initialMapStyle = initialMapStyle,
        onMapStyleChanged = onMapStyleChanged,
        showBackButton = showBackButton,
        goDexEntries = goDexEntries,
        goDexConfig = goDexConfig,
        showSpawnRadius = showSpawnRadius,
        spacialRendEnabled = spacialRendEnabled,
        onToggleSpawnRadius = onToggleSpawnRadius,
        onToggleSpacialRend = onToggleSpacialRend,
        locationTrackerFactory = DefaultMapPoseTrackerFactory
    )
}

@Composable
internal fun AlertsMapScreenContent(
    alerts: List<PokemonAlert>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    initialMapStyle: MapStylePreference = MapStylePreference.GOOGLE_STANDARD,
    onMapStyleChanged: (MapStylePreference) -> Unit = {},
    showBackButton: Boolean = true,
    goDexEntries: List<GoDexEntryEntity> = emptyList(),
    goDexConfig: GoDexConfig = GoDexConfig(),
    showSpawnRadius: Boolean = false,
    spacialRendEnabled: Boolean = false,
    onToggleSpawnRadius: () -> Unit = {},
    onToggleSpacialRend: () -> Unit = {},
    locationTrackerFactory: MapPoseTrackerFactory = DefaultMapPoseTrackerFactory
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val darkTheme = LocalAppDarkTheme.current

    val defaultLatLng = remember { LatLng(ALSBACH_LATITUDE, ALSBACH_LONGITUDE) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, ALSBACH_ZOOM)
    }
    var googleMapLoaded by remember { mutableStateOf(false) }
    var openStreetMapLoaded by remember { mutableStateOf(false) }
    var selectedAlert by remember { mutableStateOf<PokemonAlert?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var mapStyle by rememberSaveable(initialMapStyle) { mutableStateOf(initialMapStyle) }
    val mapSource = if (mapStyle == MapStylePreference.OPENSTREETMAP) {
        MapDisplaySource.OPENSTREETMAP
    } else {
        MapDisplaySource.GOOGLE
    }
    val mapType = if (mapStyle == MapStylePreference.GOOGLE_SATELLITE) {
        MapType.HYBRID
    } else {
        MapType.NORMAL
    }
    var showTimeLabels by rememberSaveable { mutableStateOf(false) }
    var showFilterMenu by rememberSaveable { mutableStateOf(false) }
    var initialCameraPositioned by rememberSaveable { mutableStateOf(false) }
    var retainedLatitude by rememberSaveable { mutableStateOf(ALSBACH_LATITUDE) }
    var retainedLongitude by rememberSaveable { mutableStateOf(ALSBACH_LONGITUDE) }
    var retainedZoom by rememberSaveable { mutableStateOf(ALSBACH_ZOOM.toDouble()) }
    val openStreetMapController = remember { OpenStreetMapController() }

    fun retainedCamera() = MapCameraSnapshot(retainedLatitude, retainedLongitude, retainedZoom)

    fun updateRetainedCamera(snapshot: MapCameraSnapshot) {
        retainedLatitude = snapshot.latitude
        retainedLongitude = snapshot.longitude
        retainedZoom = snapshot.zoom
    }

    fun hasLocationPermissionNow(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var hasLocationPermission by remember { mutableStateOf(hasLocationPermissionNow()) }
    var userLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var userPose by remember { mutableStateOf<MapUserPose?>(null) }
    var trackingRequested by rememberSaveable { mutableStateOf(false) }
    var cameraFollowEnabled by rememberSaveable { mutableStateOf(false) }
    var trackingStatus by remember { mutableStateOf(MapTrackingStatus.INACTIVE) }
    var locationLookupComplete by remember { mutableStateOf(!hasLocationPermission) }
    var lifecycleLocationRefreshJob by remember { mutableStateOf<Job?>(null) }
    val locationTracker = remember(context, locationTrackerFactory) {
        locationTrackerFactory(
            context,
            { pose ->
                userPose = pose
                userLocation = pose.location
                locationLookupComplete = true
            },
            { trackingStatus = it }
        )
    }

    fun applyTrackingInteraction(state: MapTrackingInteractionState) {
        trackingRequested = state.trackingRequested
        cameraFollowEnabled = state.cameraFollowEnabled
    }

    fun trackingInteraction() = MapTrackingInteractionState(
        trackingRequested = trackingRequested,
        cameraFollowEnabled = cameraFollowEnabled
    )

    suspend fun loadUserLocation(): android.location.Location? = try {
        CachedLocationProvider.get(
            context = context,
            timeoutMs = 5_000,
            highAccuracy = true
        )?.takeIf { location ->
            validMapCoordinates(location.latitude, location.longitude) != null
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Throwable) {
        null
    }

    suspend fun refreshLocationState(): android.location.Location? {
        val permissionGranted = hasLocationPermissionNow()
        hasLocationPermission = permissionGranted
        if (!permissionGranted) {
            userLocation = null
            locationLookupComplete = true
            return null
        }

        locationLookupComplete = false
        return loadUserLocation().also { location ->
            userLocation = location
            locationLookupComplete = true
        }
    }

    fun centerOnUserLocation() {
        applyTrackingInteraction(trackingInteraction().onGpsTapped())
        lifecycleLocationRefreshJob?.cancel()
        scope.launch {
            val location = userPose?.location ?: refreshLocationState()

            if (location == null) {
                Toast.makeText(
                    context,
                    context.getString(
                        if (hasLocationPermission) {
                            R.string.map_current_location_unavailable
                        } else {
                            R.string.map_location_permission_needed
                        }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val target = MapCameraSnapshot(location.latitude, location.longitude, 16.0)
            updateRetainedCamera(target)
            if (mapSource == MapDisplaySource.GOOGLE) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        16f
                    ),
                    1_000
                )
            } else {
                openStreetMapController.setCamera(target, animate = true)
            }
        }
    }

    fun showPreciseLocationGuidanceIfNeeded() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && hasLocationPermissionNow()) {
            Toast.makeText(context, R.string.map_precise_location_recommended, Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                lifecycleLocationRefreshJob?.cancel()
                lifecycleLocationRefreshJob = scope.launch { refreshLocationState() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            lifecycleLocationRefreshJob?.cancel()
        }
    }

    DisposableEffect(lifecycleOwner, trackingRequested, hasLocationPermission, locationTracker) {
        fun updateTrackingForLifecycle() {
            if (
                trackingRequested &&
                hasLocationPermission &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                locationTracker.start()
            } else {
                locationTracker.stop()
            }
        }

        val observer = LifecycleEventObserver { _, _ -> updateTrackingForLifecycle() }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateTrackingForLifecycle()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationTracker.stop()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasLocationPermissionNow()

        if (hasLocationPermission) {
            showPreciseLocationGuidanceIfNeeded()
            centerOnUserLocation()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.map_location_permission_needed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // A live timer is only needed while a time label or the selected-alert panel is visible.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showTimeLabels, selectedAlert?.uniqueId) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(if (showTimeLabels || selectedAlert != null) 1_000 else 30_000)
        }
    }

    val filteredAlerts = remember(alerts, selectedFilter, now) {
        val activeAlerts = alerts.filter {
            it.mapCoordinatesOrNull() != null &&
                !it.isInvalidated &&
                (TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE) > now
        }
        when (selectedFilter) {
            AlertFilter.ALL -> activeAlerts
            AlertFilter.RAIDS -> activeAlerts.filter { it.hasType("Raid") }
            AlertFilter.QUESTS -> activeAlerts.filter { it.hasType("Quest") }
            AlertFilter.RARES -> activeAlerts.filter { it.hasType("Rare") || it.hasType("Spawn") }
            AlertFilter.HUNDOS -> activeAlerts.filter { it.hasType("Hundo") }
            AlertFilter.PVP -> activeAlerts.filter { it.hasType("PvP") }
            AlertFilter.NUNDOS -> activeAlerts.filter { it.hasType("Nundo") }
            AlertFilter.KECLEON -> activeAlerts.filter { it.hasType("Kecleon") }
            AlertFilter.ROCKET -> activeAlerts.filter { it.hasType("Rocket") }
            AlertFilter.WEATHER_CHANGE -> activeAlerts.filter { it.hasType("WeatherChange") }
        }
    }

    val availableFilters = remember(alerts) {
        val mappableAlerts = alerts.filter {
            it.mapCoordinatesOrNull() != null && !it.isInvalidated
        }
        buildSet {
            add(AlertFilter.ALL)
            if (mappableAlerts.any { it.hasType("Raid") }) add(AlertFilter.RAIDS)
            if (mappableAlerts.any { it.hasType("Quest") }) add(AlertFilter.QUESTS)
            if (mappableAlerts.any { it.hasType("Rare") || it.hasType("Spawn") }) add(AlertFilter.RARES)
            if (mappableAlerts.any { it.hasType("Hundo") }) add(AlertFilter.HUNDOS)
            if (mappableAlerts.any { it.hasType("PvP") }) add(AlertFilter.PVP)
            if (mappableAlerts.any { it.hasType("Nundo") }) add(AlertFilter.NUNDOS)
            if (mappableAlerts.any { it.hasType("Kecleon") }) add(AlertFilter.KECLEON)
            if (mappableAlerts.any { it.hasType("Rocket") }) add(AlertFilter.ROCKET)
            if (mappableAlerts.any { it.hasType("WeatherChange") }) add(AlertFilter.WEATHER_CHANGE)
        }
    }

    LaunchedEffect(availableFilters) {
        if (selectedFilter !in availableFilters) selectedFilter = AlertFilter.ALL
    }

    val mapUiSettings = remember(hasLocationPermission) {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = true
        )
    }

    val mapProperties = remember(hasLocationPermission, mapType, darkTheme) {
        MapProperties(
            isMyLocationEnabled = false,
            mapType = mapType,
            minZoomPreference = 3f,
            maxZoomPreference = 20f,
            mapStyleOptions = if (mapType == MapType.NORMAL) {
                MapStyleOptions(if (darkTheme) DarkMapStyle else LightMapStyle)
            } else {
                null
            }
        )
    }

    val currentMapLoaded = if (mapSource == MapDisplaySource.GOOGLE) {
        googleMapLoaded
    } else {
        openStreetMapLoaded
    }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow {
            cameraPositionState.isMoving to cameraPositionState.cameraMoveStartedReason
        }.collectLatest { (isMoving, reason) ->
            if (isMoving && reason == CameraMoveStartedReason.GESTURE) {
                applyTrackingInteraction(trackingInteraction().onUserCameraGesture())
            }
        }
    }

    LaunchedEffect(
        userPose?.location?.elapsedRealtimeNanos,
        cameraFollowEnabled,
        currentMapLoaded,
        mapSource
    ) {
        val pose = userPose ?: return@LaunchedEffect
        if (!cameraFollowEnabled || !currentMapLoaded) return@LaunchedEffect
        val location = pose.location
        val currentZoom = if (mapSource == MapDisplaySource.GOOGLE) {
            cameraPositionState.position.zoom.coerceAtLeast(16f).toDouble()
        } else {
            retainedZoom.coerceAtLeast(16.0)
        }
        val target = MapCameraSnapshot(location.latitude, location.longitude, currentZoom)
        updateRetainedCamera(target)
        if (mapSource == MapDisplaySource.GOOGLE) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    currentZoom.toFloat()
                ),
                500
            )
        } else {
            openStreetMapController.setCamera(target, animate = true)
        }
    }

    LaunchedEffect(currentMapLoaded, filteredAlerts, locationLookupComplete, userLocation, mapSource) {
        if (currentMapLoaded && locationLookupComplete && !initialCameraPositioned) {
            val viewport = resolveInitialMapViewport(
                userLatitude = userLocation?.latitude,
                userLongitude = userLocation?.longitude,
                alerts = filteredAlerts
            )
            val target = MapCameraSnapshot(viewport.latitude, viewport.longitude, viewport.zoom.toDouble())
            updateRetainedCamera(target)
            runCatching {
                if (mapSource == MapDisplaySource.GOOGLE) {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(viewport.latitude, viewport.longitude),
                            viewport.zoom
                        )
                    )
                } else {
                    openStreetMapController.setCamera(target)
                }
            }.onSuccess { initialCameraPositioned = true }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useSidePanel = maxWidth >= 840.dp
        val controlsEndPadding = if (useSidePanel) 392.dp else 16.dp
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val mapContentPadding = PaddingValues(
            start = 16.dp,
            top = statusBarPadding + 88.dp,
            end = if (useSidePanel) 392.dp else 16.dp,
            bottom = if (useSidePanel) 24.dp else 96.dp
        )
        val fitPaddingPx = with(density) {
            (if (useSidePanel) 104.dp else 72.dp).roundToPx()
        }
        val openStreetMapInsets = with(density) {
            MapContentInsets(
                left = 16.dp.roundToPx(),
                top = (statusBarPadding + 88.dp).roundToPx(),
                right = (if (useSidePanel) 392.dp else 16.dp).roundToPx(),
                bottom = (if (useSidePanel) 24.dp else 96.dp).roundToPx()
            )
        }
        val visibleCoordinates = remember(filteredAlerts) { resolveFitAllCoordinates(filteredAlerts) }

        fun fitVisibleAlerts() {
            applyTrackingInteraction(trackingInteraction().onShowAllAlerts())
            if (visibleCoordinates.isEmpty()) {
                Toast.makeText(context, R.string.map_no_alerts_to_show, Toast.LENGTH_SHORT).show()
                return
            }
            if (mapSource == MapDisplaySource.GOOGLE) {
                scope.launch {
                    if (visibleCoordinates.size == 1) {
                        val coordinate = visibleCoordinates.first()
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(coordinate.latitude, coordinate.longitude),
                                16f
                            ),
                            750
                        )
                    } else {
                        val bounds = LatLngBounds.Builder().apply {
                            visibleCoordinates.forEach { include(LatLng(it.latitude, it.longitude)) }
                        }.build()
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(bounds, fitPaddingPx),
                            750
                        )
                    }
                    val position = cameraPositionState.position
                    updateRetainedCamera(
                        MapCameraSnapshot(position.target.latitude, position.target.longitude, position.zoom.toDouble())
                    )
                }
            } else {
                openStreetMapController.fitAlerts(visibleCoordinates, fitPaddingPx)
            }
        }

        if (mapSource == MapDisplaySource.GOOGLE) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = mapContentPadding,
                onMapLoaded = { googleMapLoaded = true },
                properties = mapProperties,
                uiSettings = mapUiSettings
            ) {
                userPose?.let { pose ->
                    val location = pose.location
                    Circle(
                        center = LatLng(location.latitude, location.longitude),
                        radius = location.accuracy.toDouble().coerceAtLeast(1.0),
                        fillColor = Color(MAP_USER_LOCATION_BLUE).copy(alpha = 0.14f),
                        strokeColor = Color(MAP_USER_LOCATION_BLUE).copy(alpha = 0.55f),
                        strokeWidth = 2f,
                        zIndex = 900f
                    )
                    Marker(
                        state = MarkerState(LatLng(location.latitude, location.longitude)),
                        icon = BitmapDescriptorFactory.fromBitmap(
                            remember(pose.headingDegrees != null) {
                                createMapUserMarkerBitmap(context, pose.headingDegrees != null)
                            }
                        ),
                        anchor = Offset(0.5f, 0.5f),
                        flat = true,
                        rotation = pose.headingDegrees ?: 0f,
                        zIndex = 1_000f
                    )
                }
                if (showSpawnRadius) {
                    val radiusMeters = if (spacialRendEnabled) 80.0 else 40.0
                    filteredAlerts.filter { it.isSpawnAlert }.forEach { alert ->
                        val coords = alert.mapCoordinatesOrNull() ?: return@forEach
                        Circle(
                            center = LatLng(coords.latitude, coords.longitude),
                            radius = radiusMeters,
                            fillColor = Color(0x471A73E8),
                            strokeColor = Color(0xFF1A73E8),
                            strokeWidth = 2.5f * density.density,
                            zIndex = 800f
                        )
                    }
                }
                filteredAlerts.forEach { alert ->
                    key(alert.uniqueId) {
                        MapMarker(
                            alert = alert,
                            now = now,
                            density = density,
                            showTimeLabel = showTimeLabels,
                            goDexEntries = goDexEntries,
                            goDexConfig = goDexConfig,
                            onClick = { selectedAlert = alert }
                        )
                    }
                }
            }
        } else {
            OpenStreetMapView(
                modifier = Modifier.fillMaxSize(),
                alerts = filteredAlerts,
                userPose = userPose,
                cameraSnapshot = retainedCamera(),
                contentInsets = openStreetMapInsets,
                showTimeLabels = showTimeLabels,
                now = now,
                controller = openStreetMapController,
                onMapLoaded = { openStreetMapLoaded = true },
                onLoadError = {
                    Toast.makeText(context, R.string.map_openstreetmap_unavailable, Toast.LENGTH_LONG).show()
                },
                onAlertClick = { selectedAlert = it },
                onCameraChanged = ::updateRetainedCamera,
                onUserGesture = {
                    applyTrackingInteraction(trackingInteraction().onUserCameraGesture())
                },
                goDexEntries = goDexEntries,
                goDexConfig = goDexConfig,
                showSpawnRadius = showSpawnRadius,
                spacialRendEnabled = spacialRendEnabled
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp)
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))
                        )
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = stringResource(R.string.map_osm_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(top = 8.dp, start = 16.dp, end = controlsEndPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapTopAppBar(
                visibleAlertCount = filteredAlerts.size,
                showTimeLabels = showTimeLabels,
                hybridMap = mapType == MapType.HYBRID,
                mapSource = mapSource,
                showBackButton = showBackButton,
                showSpawnRadius = showSpawnRadius,
                spacialRendEnabled = spacialRendEnabled,
                onBack = onBack,
                onToggleTimeLabels = { showTimeLabels = !showTimeLabels },
                onToggleSpawnRadius = onToggleSpawnRadius,
                onToggleSpacialRend = onToggleSpacialRend,
                onRefresh = onRefresh,
                onToggleMapType = {
                    val nextStyle = if (mapStyle == MapStylePreference.GOOGLE_SATELLITE) {
                        MapStylePreference.GOOGLE_STANDARD
                    } else {
                        MapStylePreference.GOOGLE_SATELLITE
                    }
                    mapStyle = nextStyle
                    onMapStyleChanged(nextStyle)
                },
                onToggleMapSource = {
                    if (mapSource == MapDisplaySource.GOOGLE) {
                        val position = cameraPositionState.position
                        updateRetainedCamera(
                            MapCameraSnapshot(
                                position.target.latitude,
                                position.target.longitude,
                                position.zoom.toDouble()
                            )
                        )
                        openStreetMapLoaded = false
                        mapStyle = MapStylePreference.OPENSTREETMAP
                        onMapStyleChanged(MapStylePreference.OPENSTREETMAP)
                    } else {
                        googleMapLoaded = false
                        mapStyle = MapStylePreference.GOOGLE_STANDARD
                        onMapStyleChanged(MapStylePreference.GOOGLE_STANDARD)
                        scope.launch {
                            val retained = retainedCamera()
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(retained.latitude, retained.longitude),
                                    retained.zoom.toFloat()
                                )
                            )
                        }
                    }
                },
                onOpenFilters = { showFilterMenu = true }
            )

            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AlertFilter.values().filter { it in availableFilters }.forEach { filter ->
                    val filterAccent = Color(resolveAlertVisualStyle(filter.label).category.accentArgb)
                    DropdownMenuItem(
                        text = { Text(filter.label) },
                        onClick = {
                            selectedFilter = filter
                            showFilterMenu = false
                        },
                        leadingIcon = if (selectedFilter == filter) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(filterAccent, RoundedCornerShape(50))
                                )
                            }
                        } else null
                    )
                }
            }
        }

        if (currentMapLoaded && (useSidePanel || selectedAlert == null)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(
                        if (showBackButton) {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        } else {
                            Modifier
                        }
                    )
                    .padding(end = if (useSidePanel) 392.dp else 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = ::fitVisibleAlerts,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fit_map),
                        contentDescription = stringResource(R.string.map_show_all_alerts)
                    )
                }
                FloatingActionButton(
                    onClick = {
                        if (hasLocationPermissionNow()) {
                            hasLocationPermission = true
                            showPreciseLocationGuidanceIfNeeded()
                            centerOnUserLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    containerColor = when {
                        cameraFollowEnabled -> MaterialTheme.colorScheme.primary
                        trackingRequested -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = when {
                        cameraFollowEnabled -> MaterialTheme.colorScheme.onPrimary
                        trackingRequested -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_my_location),
                        contentDescription = stringResource(
                            when {
                                cameraFollowEnabled && trackingStatus == MapTrackingStatus.DEGRADED ->
                                    R.string.map_location_tracking_degraded
                                cameraFollowEnabled -> R.string.map_location_following
                                trackingRequested -> R.string.map_location_tracking_recenter
                                else -> R.string.map_location_start_tracking
                            }
                        )
                    )
                }
            }
        }

        selectedAlert?.let { alert ->
            val distanceInfo = remember(alert.uniqueId, userLocation?.latitude, userLocation?.longitude) {
                resolveMapAlertDistanceInfo(userLocation, alert)
            }
            if (useSidePanel) {
                MapAlertSidePanel(
                    alert = alert,
                    distanceInfo = distanceInfo,
                    onDismiss = { selectedAlert = null },
                    onOpenMaps = { openMapForAlert(context, alert) },
                    onShare = { scope.launch { AlertShareCard.share(context, alert) } },
                    onOpenFullDetail = {
                        context.startActivity(AlertDetailActivity.createIntent(context, alert))
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            } else {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                ModalBottomSheet(
                    onDismissRequest = { selectedAlert = null },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    MapAlertDetailContent(
                        alert = alert,
                        distanceInfo = distanceInfo,
                        onDismiss = { selectedAlert = null },
                        onOpenMaps = { openMapForAlert(context, alert) },
                        onShare = { scope.launch { AlertShareCard.share(context, alert) } },
                        onOpenFullDetail = {
                            context.startActivity(AlertDetailActivity.createIntent(context, alert))
                        },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapTopAppBar(
    visibleAlertCount: Int,
    showTimeLabels: Boolean,
    hybridMap: Boolean,
    mapSource: MapDisplaySource,
    showBackButton: Boolean,
    showSpawnRadius: Boolean,
    spacialRendEnabled: Boolean,
    onBack: () -> Unit,
    onToggleTimeLabels: () -> Unit,
    onToggleSpawnRadius: () -> Unit,
    onToggleSpacialRend: () -> Unit,
    onRefresh: () -> Unit,
    onToggleMapType: () -> Unit,
    onToggleMapSource: () -> Unit,
    onOpenFilters: () -> Unit
) {
    var moreExpanded by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
            Row(
                modifier = Modifier.weight(1f).padding(start = if (showBackButton) 2.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$visibleAlertCount",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            IconButton(onClick = onOpenFilters, modifier = Modifier.size(44.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_filter),
                    contentDescription = stringResource(R.string.map_filter_alerts)
                )
            }
            Box {
                IconButton(
                    onClick = { moreExpanded = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.map_more_options)
                    )
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (mapSource == MapDisplaySource.GOOGLE) {
                                        R.string.map_use_openstreetmap
                                    } else {
                                        R.string.map_use_google
                                    }
                                )
                            )
                        },
                        onClick = {
                            moreExpanded = false
                            onToggleMapSource()
                        },
                        leadingIcon = {
                            Icon(painterResource(id = R.drawable.ic_map), contentDescription = null)
                        }
                    )
                    if (mapSource == MapDisplaySource.GOOGLE) DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (hybridMap) R.string.map_switch_standard else R.string.map_switch_satellite
                                )
                            )
                        },
                        onClick = {
                            moreExpanded = false
                            onToggleMapType()
                        },
                        leadingIcon = {
                            Icon(painterResource(id = R.drawable.ic_layers), contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (showTimeLabels) R.string.map_hide_countdowns
                                    else R.string.map_show_countdowns
                                )
                            )
                        },
                        onClick = {
                            moreExpanded = false
                            onToggleTimeLabels()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_timer),
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (showSpawnRadius) R.string.map_hide_spawn_radius
                                    else R.string.map_show_spawn_radius
                                )
                            )
                        },
                        onClick = {
                            moreExpanded = false
                            onToggleSpawnRadius()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_my_location),
                                contentDescription = null
                            )
                        }
                    )
                    if (showSpawnRadius) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (spacialRendEnabled) R.string.map_disable_spacial_rend
                                        else R.string.map_enable_spacial_rend
                                    )
                                )
                            },
                            onClick = {
                                moreExpanded = false
                                onToggleSpacialRend()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_fit_map),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.refresh_alerts)) },
                        onClick = {
                            moreExpanded = false
                            onRefresh()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapFilterRow(
    filters: List<AlertFilter>,
    selectedFilter: AlertFilter,
    visibleAlertCount: Int,
    onFilterSelected: (AlertFilter) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filters, key = { it.name }) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        item(key = "map-alert-count") {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = "$visibleAlertCount alerts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MapAlertSidePanel(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo?,
    onDismiss: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier
) {
    val colors = LocalLinearModernColors.current
    Surface(
        modifier = modifier
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(top = 12.dp, end = 16.dp)
            .width(360.dp)
            .border(1.dp, colors.borderDefault, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = colors.bgElevated.copy(alpha = 0.9f),
        tonalElevation = 0.dp
    ) {
        MapAlertDetailContent(
            alert = alert,
            distanceInfo = distanceInfo,
            onDismiss = onDismiss,
            onOpenMaps = onOpenMaps,
            onShare = onShare,
            onOpenFullDetail = onOpenFullDetail,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun MapAlertDetailContent(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo?,
    onDismiss: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTime by remember(alert.uniqueId) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alert.uniqueId) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }

    val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
    val remaining = endMillis?.let { it - currentTime } ?: 0L
    val visualStyle = resolveAlertVisualStyle(alert)
    val categoryAccent = Color(visualStyle.category.accentArgb)
    val isExpired = remaining <= 0L

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AlertImage(
            alert = alert,
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val goDexStatus = rememberGoDexStatus(alert)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formatAlertTitle(alert, goDexStatus.status),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = categoryAccent.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "${visualStyle.shortCode} · ${visualStyle.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = categoryAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    GoDexStatusPill(goDexStatus)
                }
            }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val repository = remember(context) { GoDexRepository.getInstance(context) }
            val config by repository.config.collectAsState()
            var showTargetDialog by remember { mutableStateOf(false) }
            var showUncatchDialog by remember { mutableStateOf(false) }
            var caughtEntries by remember { mutableStateOf<List<GoDexEntryEntity>>(emptyList()) }

            if (config.hasSession && alert.hasType("hundo")) {
                val isCollected = goDexStatus.status == GoDexMatchStatus.COLLECTED
                val isNeeded = goDexStatus.status == GoDexMatchStatus.NEEDED
                val hasDescendantsNeeded = goDexStatus.status == GoDexMatchStatus.EVOLUTION_NEEDED ||
                        goDexStatus.status == GoDexMatchStatus.FORM_CHANGE_NEEDED ||
                        goDexStatus.status == GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val targetKey = goDexStatus.matchedEntryKey
                        android.util.Log.d("GoDexClickMap", "Clicked map checkmark: isCollected=$isCollected, status=${goDexStatus.status}, hasDescendantsNeeded=$hasDescendantsNeeded, targetKey=$targetKey")
                        if (isCollected) {
                            val caught = repository.getCaughtEntries(alert)
                            if (caught.size > 1) {
                                caughtEntries = caught
                                showUncatchDialog = true
                            } else {
                                val keyToUncheck = caught.firstOrNull()?.entryKey ?: targetKey
                                if (keyToUncheck != null) {
                                    scope.launch {
                                        repository.markAsCaught(keyToUncheck, false)
                                    }
                                }
                            }
                        } else {
                            if (hasDescendantsNeeded) {
                                showTargetDialog = true
                            } else {
                                if (targetKey != null) {
                                    scope.launch {
                                        repository.markAsCaught(targetKey, true)
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isCollected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (isCollected) "Mark as needed" else "Mark as caught",
                        tint = if (isCollected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                if (showTargetDialog) {
                    GoDexCatchTargetDialog(
                        pokemonName = alert.pokemon ?: "Unknown",
                        matchResult = goDexStatus,
                        onDismiss = { showTargetDialog = false },
                        onConfirm = { selectedKey ->
                            showTargetDialog = false
                            scope.launch {
                                repository.markAsCaught(selectedKey, true)
                            }
                        }
                    )
                }
                if (showUncatchDialog) {
                    GoDexUncatchTargetDialog(
                        pokemonName = alert.pokemon ?: "Unknown",
                        caughtEntries = caughtEntries,
                        onDismiss = { showUncatchDialog = false },
                        onConfirm = { selectedKey ->
                            showUncatchDialog = false
                            scope.launch {
                                repository.markAsCaught(selectedKey, false)
                            }
                        }
                    )
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        val venue = alert.venueName
        val venueType = alert.venueTypeLabel
        val address = alert.pokemonLocation

        if (venue != null) {
            Text(
                text = if (venueType != null) "$venueType: $venue" else venue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!address.isNullOrBlank() && address != venue) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else alert.locationDisplay?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            val distanceText = distanceInfo?.distanceText
            val walkingText = distanceInfo?.walkingText
            Text(
                text = when {
                    distanceText != null && walkingText != null -> {
                        "$distanceText · ${stringResource(R.string.map_estimated_walk, walkingText)}"
                    }
                    distanceText != null -> distanceText
                    else -> stringResource(R.string.map_distance_unavailable)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }

        alert.displayCp?.let { cp ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = categoryAccent.copy(alpha = 0.18f)
            ) {
                Text(
                    text = "CP $cp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryAccent,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isExpired) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                categoryAccent.copy(alpha = 0.18f)
            }
        ) {
            Text(
                text = if (isExpired) {
                    "Expired"
                } else {
                    "Ends in ${TimeUtils.formatDurationShort(remaining)}"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (isExpired) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    categoryAccent
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onOpenMaps,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Directions")
            }
            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Share")
            }
        }

        FilledTonalButton(
            onClick = onOpenFullDetail,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Open full detail")
        }
    }
}

internal data class AlertMapCoordinates(
    val latitude: Double,
    val longitude: Double
)

internal enum class MapDisplaySource {
    GOOGLE,
    OPENSTREETMAP
}

internal data class MapViewportTarget(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float
)

internal fun validMapCoordinates(
    latitude: Double?,
    longitude: Double?
): AlertMapCoordinates? {
    if (latitude == null || longitude == null) return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    if (latitude == 0.0 && longitude == 0.0) return null
    return AlertMapCoordinates(latitude, longitude)
}

internal fun PokemonAlert.mapCoordinatesOrNull(): AlertMapCoordinates? =
    validMapCoordinates(latitude, longitude)

internal fun resolveFitAllCoordinates(alerts: List<PokemonAlert>): List<AlertMapCoordinates> =
    alerts.mapNotNull(PokemonAlert::mapCoordinatesOrNull)

internal fun resolveMapAlertDistanceInfo(
    userLocation: android.location.Location?,
    alert: PokemonAlert
): AlertDistanceInfo? = resolveMapAlertDistanceInfo(
    userLatitude = userLocation?.latitude,
    userLongitude = userLocation?.longitude,
    alert = alert
)

internal fun resolveMapAlertDistanceInfo(
    userLatitude: Double?,
    userLongitude: Double?,
    alert: PokemonAlert,
    distanceBetween: (AlertMapCoordinates, AlertMapCoordinates) -> Float? = { origin, destination ->
        val results = FloatArray(1)
        runCatching {
            android.location.Location.distanceBetween(
                origin.latitude,
                origin.longitude,
                destination.latitude,
                destination.longitude,
                results
            )
            results[0]
        }.getOrNull()
    }
): AlertDistanceInfo? {
    val origin = validMapCoordinates(userLatitude, userLongitude) ?: return null
    val destination = alert.mapCoordinatesOrNull() ?: return null
    val straightLineDistance = distanceBetween(origin, destination)
        ?.takeUnless { it.isNaN() || it.isInfinite() || it < 0f }
        ?: return null
    val routeInfo = WalkingRouteUtils.estimateWalkingRouteInfo(straightLineDistance)
    val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(straightLineDistance, routeInfo)
    return AlertDistanceInfo(
        distanceMeters = displayInfo.straightLineDistanceMeters,
        distanceText = displayInfo.distanceText,
        walkingText = displayInfo.walkingText
    )
}

internal fun resolveInitialMapViewport(
    userLatitude: Double?,
    userLongitude: Double?,
    alerts: List<PokemonAlert>
): MapViewportTarget {
    validMapCoordinates(userLatitude, userLongitude)?.let { location ->
        return MapViewportTarget(location.latitude, location.longitude, USER_LOCATION_ZOOM)
    }

    alerts.firstNotNullOfOrNull(PokemonAlert::mapCoordinatesOrNull)?.let { alertLocation ->
        return MapViewportTarget(alertLocation.latitude, alertLocation.longitude, ALERT_LOCATION_ZOOM)
    }

    return MapViewportTarget(ALSBACH_LATITUDE, ALSBACH_LONGITUDE, ALSBACH_ZOOM)
}

@Composable
private fun MapMarker(
    alert: PokemonAlert,
    now: Long,
    density: androidx.compose.ui.unit.Density,
    showTimeLabel: Boolean,
    goDexEntries: List<GoDexEntryEntity>,
    goDexConfig: GoDexConfig,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val coordinates = remember(alert.latitude, alert.longitude) {
        alert.mapCoordinatesOrNull()
    }
    if (coordinates == null) return
    val position = remember(coordinates) { LatLng(coordinates.latitude, coordinates.longitude) }
    val visualStyle = resolveAlertVisualStyle(alert)
    
    val goDexRepository = remember(context) { GoDexRepository.getInstance(context) }
    val matchResult = remember(alert, goDexEntries, goDexConfig) {
        if (alert.hasType("hundo")) {
            goDexRepository.match(alert, goDexEntries, goDexConfig.isConnected)
        } else {
            GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED)
        }
    }
    
    val markerLabel = alert.displayCp?.let { "CP $it" } ?: when (visualStyle.category) {
        AlertCategory.HUNDO -> "100%"
        AlertCategory.NUNDO -> "0%"
        else -> visualStyle.shortCode
    }
    val speciesName = alert.pokemon?.takeIf { it.isNotBlank() } ?: alert.cleanPokemonName
    val speciesImageUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: alert.imageUrl?.takeIf { it.isNotBlank() }
    val timeLabel = remember(now, alert.endTime) {
        mapCountdownLabel(alert.endTime, now)
    }
    val markerSizePx = remember(density) { with(density) { 68.dp.toPx().toInt() } }

    val colors = MaterialTheme.colorScheme
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
    val palette = remember(basePalette, visualStyle.category) {
        basePalette.copy(primary = visualStyle.category.accentArgb.toInt())
    }
    var markerIcon by remember(alert.uniqueId) { mutableStateOf<MapMarkerIcon?>(null) }

    LaunchedEffect(
        alert.uniqueId,
        markerLabel,
        visualStyle.category,
        speciesName,
        speciesImageUrl,
        showTimeLabel,
        timeLabel,
        markerSizePx,
        palette,
        matchResult.status
    ) {
        val renderedIcon = withContext(Dispatchers.IO) {
            createMapMarkerIcon(
                context = context,
                sizePx = markerSizePx,
                categoryCode = markerLabel,
                speciesName = speciesName,
                speciesImageUrl = speciesImageUrl,
                endTime = alert.endTime,
                showTimeLabel = showTimeLabel,
                timeLabel = if (showTimeLabel) timeLabel else null,
                palette = palette,
                goDexStatus = matchResult.status
            )
        }
        currentCoroutineContext().ensureActive()
        markerIcon = renderedIcon
    }
    val googleMarkerDescriptor = remember(markerIcon) {
        markerIcon?.bitmap?.let(BitmapDescriptorFactory::fromBitmap)
    }

    MarkerInfoWindowContent(
        state = remember(position) { MarkerState(position = position) },
        icon = googleMarkerDescriptor,
        anchor = markerIcon?.anchor ?: Offset(0.5f, 1f),
        title = formatAlertTitle(alert, matchResult.status),
        visible = true,
        onClick = {
            onClick()
            true
        }
    ) {
        // The map uses the Material bottom sheet/side panel instead of an info window.
    }
}

internal data class MapMarkerPalette(
    val primary: Int,
    val onPrimary: Int,
    val surface: Int,
    val onSurface: Int,
    val outline: Int,
    val error: Int,
    val onError: Int
)

internal data class MapMarkerIcon(
    val bitmap: Bitmap,
    val anchor: Offset
)

private val markerIconCache = LruCache<String, MapMarkerIcon>(256)

internal suspend fun createMapMarkerIcon(
    context: android.content.Context,
    sizePx: Int,
    categoryCode: String,
    speciesName: String,
    speciesImageUrl: String?,
    endTime: String?,
    showTimeLabel: Boolean,
    timeLabel: String?,
    palette: MapMarkerPalette,
    goDexStatus: GoDexMatchStatus = GoDexMatchStatus.NOT_CONFIGURED
): MapMarkerIcon? {
    try {
        val timeRemainingMs = endTime?.let(TimeUtils::parseEndTimeToMillis)?.let {
            it - System.currentTimeMillis()
        } ?: Long.MAX_VALUE
        val isUrgent = timeRemainingMs < 10 * 60 * 1_000
        val cacheKey = listOf(
            "material3-marker-compact",
            sizePx,
            categoryCode,
            speciesName,
            speciesImageUrl.orEmpty(),
            showTimeLabel,
            timeLabel.orEmpty(),
            isUrgent,
            palette,
            goDexStatus
        ).joinToString("|")
        markerIconCache.get(cacheKey)?.let { return it }

        val speciesDrawable = speciesImageUrl?.let { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(sizePx, sizePx)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            (PokemonAlertsApplication.imageLoader(context).execute(request) as? SuccessResult)?.drawable
        }

        val padding = (sizePx * 0.14f).toInt()
        val pinRadius = sizePx * 0.33f
        val tailHeight = sizePx * 0.20f
        val labelGap = (sizePx * 0.07f).toInt()
        val timeHeight = if (showTimeLabel && timeLabel != null) {
            (sizePx * 0.26f).toInt().coerceAtLeast(16)
        } else {
            0
        }
        val totalWidth = sizePx + padding * 2
        val totalHeight = (
            padding + pinRadius * 2 + tailHeight +
                (if (timeHeight > 0) labelGap + timeHeight else 0) + padding
            ).toInt()
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = totalWidth / 2f
        val centerY = padding + pinRadius
        val pinTipY = centerY + pinRadius + tailHeight
        val tailHalfWidth = pinRadius * 0.46f
        val tailPath = android.graphics.Path().apply {
            moveTo(centerX - tailHalfWidth, centerY + pinRadius * 0.56f)
            lineTo(centerX + tailHalfWidth, centerY + pinRadius * 0.56f)
            lineTo(centerX, pinTipY)
            close()
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            alpha = 48
        }
        canvas.save()
        canvas.translate(0f, sizePx * 0.035f)
        canvas.drawPath(tailPath, shadowPaint)
        canvas.drawCircle(centerX, centerY, pinRadius, shadowPaint)
        canvas.restore()

        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
        }
        canvas.drawPath(tailPath, primaryPaint)
        canvas.drawCircle(centerX, centerY, pinRadius, primaryPaint)

        val keylinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surface
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.035f
        }
        canvas.drawCircle(centerX, centerY, pinRadius - sizePx * 0.018f, keylinePaint)

        val innerRadius = pinRadius * 0.71f
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface }
        canvas.drawCircle(centerX, centerY, innerRadius, innerPaint)

        if (speciesDrawable != null) {
            val spritePath = android.graphics.Path().apply {
                addCircle(centerX, centerY, innerRadius, android.graphics.Path.Direction.CW)
            }
            val checkpoint = canvas.save()
            canvas.clipPath(spritePath)
            val intrinsicWidth = speciesDrawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
            val intrinsicHeight = speciesDrawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
            val availableSize = innerRadius * 2.45f
            val scale = minOf(
                availableSize / intrinsicWidth.toFloat(),
                availableSize / intrinsicHeight.toFloat()
            )
            val drawWidth = intrinsicWidth * scale
            val drawHeight = intrinsicHeight * scale
            val left = (centerX - drawWidth / 2f).toInt()
            val top = (centerY - drawHeight / 2f).toInt()
            speciesDrawable.setBounds(left, top, (left + drawWidth).toInt(), (top + drawHeight).toInt())
            speciesDrawable.draw(canvas)
            canvas.restoreToCount(checkpoint)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.onSurface
                textSize = innerRadius * 0.68f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val fallback = speciesName
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
                .ifBlank { categoryCode }
            val textY = centerY - (fallbackPaint.descent() + fallbackPaint.ascent()) / 2f
            canvas.drawText(fallback, centerX, textY, fallbackPaint)
        }

        if (isUrgent) {
            val dotRadius = sizePx * 0.075f
            val dotX = centerX + pinRadius * 0.70f
            val dotY = centerY - pinRadius * 0.66f
            canvas.drawCircle(dotX, dotY, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.error })
            canvas.drawCircle(
                dotX,
                dotY,
                dotRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.surface
                    style = Paint.Style.STROKE
                    strokeWidth = sizePx * 0.022f
                }
            )
        }

        if (goDexStatus == GoDexMatchStatus.NEEDED ||
            goDexStatus == GoDexMatchStatus.EVOLUTION_NEEDED ||
            goDexStatus == GoDexMatchStatus.FORM_CHANGE_NEEDED ||
            goDexStatus == GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED
        ) {
            val badgeRadius = sizePx * 0.16f
            val badgeX = centerX - pinRadius * 0.70f
            val badgeY = centerY - pinRadius * 0.66f

            canvas.drawCircle(badgeX, badgeY, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.surface
            })
            canvas.drawCircle(
                badgeX,
                badgeY,
                badgeRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.outline
                    style = Paint.Style.STROKE
                    strokeWidth = sizePx * 0.022f
                }
            )

            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = badgeRadius * 1.3f
                textAlign = Paint.Align.CENTER
            }
            val emojiStr = if (goDexStatus == GoDexMatchStatus.NEEDED) "🎯" else "🧬"
            val textY = badgeY - (emojiPaint.descent() + emojiPaint.ascent()) / 2f
            canvas.drawText(emojiStr, badgeX, textY, emojiPaint)
        }

        // Keep the category identity on the pin itself. A permanent label below every
        // marker quickly overlaps at real alert density; the optional countdown remains
        // user-controlled and the selected sheet carries the full label and details.
        val categoryHeight = sizePx * 0.18f
        canvas.drawMarkerLabel(
            centerX = centerX,
            top = centerY + innerRadius * 0.34f,
            height = categoryHeight,
            text = categoryCode,
            background = palette.surface,
            foreground = palette.primary,
            outline = palette.primary,
            maxWidth = pinRadius * 1.55f
        )

        if (timeHeight > 0 && timeLabel != null) {
            canvas.drawMarkerLabel(
                centerX = centerX,
                top = pinTipY + labelGap,
                height = timeHeight.toFloat(),
                text = timeLabel,
                background = if (isUrgent) palette.error else palette.surface,
                foreground = if (isUrgent) palette.onError else palette.onSurface,
                outline = if (isUrgent) palette.error else palette.outline,
                maxWidth = totalWidth - padding * 2f
            )
        }

        val icon = MapMarkerIcon(
            bitmap = bitmap,
            anchor = Offset(0.5f, (pinTipY / totalHeight).coerceIn(0f, 1f))
        )
        markerIconCache.put(cacheKey, icon)
        return icon
    } catch (exception: kotlinx.coroutines.CancellationException) {
        throw exception
    } catch (_: Throwable) {
        return null
    }
}

private fun Canvas.drawMarkerLabel(
    centerX: Float,
    top: Float,
    height: Float,
    text: String,
    background: Int,
    foreground: Int,
    outline: Int,
    maxWidth: Float
) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground
        textSize = height * 0.57f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val horizontalPadding = height * 0.55f
    val width = (textPaint.measureText(text) + horizontalPadding * 2)
        .coerceAtLeast(height * 1.75f)
        .coerceAtMost(maxWidth)
    val rect = android.graphics.RectF(
        centerX - width / 2f,
        top,
        centerX + width / 2f,
        top + height
    )
    val cornerRadius = height / 2f
    drawRoundRect(
        rect,
        cornerRadius,
        cornerRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
    )
    drawRoundRect(
        rect,
        cornerRadius,
        cornerRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outline
            style = Paint.Style.STROKE
            strokeWidth = height * 0.07f
        }
    )
    val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    drawText(text, centerX, textY, textPaint)
}

private const val ALSBACH_LATITUDE = 49.74677
private const val ALSBACH_LONGITUDE = 8.62492
private const val USER_LOCATION_ZOOM = 16f
private const val ALERT_LOCATION_ZOOM = 14f
private const val ALSBACH_ZOOM = 13f

private const val LightMapStyle = """
[
  {"elementType":"geometry","stylers":[{"color":"#F7F9FB"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#424754"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#F7F9FB"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#EFF2F4"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#E3E9E4"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#FFFFFF"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#DCE6FF"}]},
  {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#E7EAED"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#DCE8FA"}]}
]
"""

private const val DarkMapStyle = """
[
  {"elementType":"geometry","stylers":[{"color":"#10131F"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#C2C6D6"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#10131F"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#5F6473"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#171B28"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#14291F"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#1B2433"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#263247"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#344B73"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0D1B2D"}]}
]
"""
