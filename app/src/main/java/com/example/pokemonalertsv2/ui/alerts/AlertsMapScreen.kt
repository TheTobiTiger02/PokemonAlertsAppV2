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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.testTag
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
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.tracking.rememberArrivalTrackingUiController
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatcher
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pokemonalertsv2.ui.theme.LocalAppDarkTheme
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.motion.appFadeIn
import com.example.pokemonalertsv2.ui.motion.appFadeOut
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
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
import androidx.compose.material3.OutlinedButton
import com.example.pokemonalertsv2.BuildConfig

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
    val selectedFilter by viewModel.selectedAlertFilter.collectAsStateWithLifecycle()
    val savedMapStyle by viewModel.mapStylePreference.collectAsStateWithLifecycle()
    val showMapCountdowns by viewModel.showMapCountdowns.collectAsStateWithLifecycle()
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

    val showSpawnRadius by viewModel.showSpawnRadius.collectAsStateWithLifecycle()
    val spacialRendEnabled by viewModel.spacialRendEnabled.collectAsStateWithLifecycle()

    AlertsMapScreen(
        alerts = uiState.alerts,
        onBack = onBack,
        onRefresh = viewModel::refreshAlerts,
        syncStatus = uiState.toSyncStatus(),
        selectedFilter = selectedFilter,
        onSelectedFilterChange = viewModel::updateSelectedAlertFilter,
        initialMapStyle = savedMapStyle,
        onMapStyleChanged = viewModel::updateMapStylePreference,
        showTimeLabels = showMapCountdowns,
        onShowTimeLabelsChanged = viewModel::updateShowMapCountdowns,
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
    syncStatus: SyncStatus = SyncStatus.Live(null),
    selectedFilter: AlertFilter = AlertFilter.ALL,
    onSelectedFilterChange: (AlertFilter) -> Unit = {},
    initialMapStyle: MapStylePreference = MapStylePreference.GOOGLE_STANDARD,
    onMapStyleChanged: (MapStylePreference) -> Unit = {},
    showTimeLabels: Boolean = false,
    onShowTimeLabelsChanged: (Boolean) -> Unit = {},
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
        syncStatus = syncStatus,
        selectedFilter = selectedFilter,
        onSelectedFilterChange = onSelectedFilterChange,
        initialMapStyle = initialMapStyle,
        onMapStyleChanged = onMapStyleChanged,
        showTimeLabels = showTimeLabels,
        onShowTimeLabelsChanged = onShowTimeLabelsChanged,
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
    syncStatus: SyncStatus = SyncStatus.Live(null),
    selectedFilter: AlertFilter = AlertFilter.ALL,
    onSelectedFilterChange: (AlertFilter) -> Unit = {},
    initialMapStyle: MapStylePreference = MapStylePreference.GOOGLE_STANDARD,
    onMapStyleChanged: (MapStylePreference) -> Unit = {},
    showTimeLabels: Boolean = false,
    onShowTimeLabelsChanged: (Boolean) -> Unit = {},
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
    val arrivalTracking = rememberArrivalTrackingUiController()
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
    var mapLoadFailed by remember { mutableStateOf(false) }
    var mapLoadAttempt by rememberSaveable { mutableIntStateOf(0) }
    var selectedAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedClusterAlerts by remember { mutableStateOf<List<PokemonAlert>>(emptyList()) }
    var expandedClusterAlertIds by rememberSaveable {
        mutableStateOf<List<String>>(emptyList())
    }
    var expandedClusterOriginZoom by rememberSaveable {
        mutableStateOf<Double?>(null)
    }
    val selectedAlert = remember(alerts, selectedAlertId) {
        alerts.firstOrNull { it.uniqueId == selectedAlertId }
    }
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
    var showLayersSheet by rememberSaveable { mutableStateOf(false) }
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

    val expirationClock = rememberCountdownClock(30_000L)
    val markerCountdownClock = rememberCountdownClock(if (showTimeLabels) 1_000L else 30_000L)

    var selectedWalkingRoute by remember { mutableStateOf<WalkingRouteInfo?>(null) }
    LaunchedEffect(
        selectedAlert?.uniqueId,
        userLocation?.latitude,
        userLocation?.longitude,
        userLocation?.accuracy
    ) {
        val alert = selectedAlert
        val location = userLocation
        selectedWalkingRoute = if (alert == null || location == null) {
            null
        } else {
            WalkingRouteRepository.getInstance()
                .getWalkingRoutes(location, listOf(alert))[alert.uniqueId]
        }
    }

    val expirationNow = expirationClock.value
    val filteredAlerts = remember(alerts, selectedFilter, expirationNow) {
        val activeAlerts = alerts.filter {
            it.mapCoordinatesOrNull() != null &&
                !it.isInvalidated &&
                (TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE) > expirationNow
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
    val goDexMatches = rememberGoDexMatchResults(
        alerts = filteredAlerts,
        entries = goDexEntries,
        configured = goDexConfig.isConnected
    )

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
        if (selectedFilter !in availableFilters) onSelectedFilterChange(AlertFilter.ALL)
    }

    LaunchedEffect(selectedFilter, mapSource) {
        expandedClusterAlertIds = emptyList()
        expandedClusterOriginZoom = null
    }

    LaunchedEffect(filteredAlerts) {
        val retainedIds = retainActiveExpandedAlertIds(
            expandedAlertIds = expandedClusterAlertIds,
            activeAlertIds = filteredAlerts.mapTo(mutableSetOf(), PokemonAlert::uniqueId)
        )
        if (retainedIds.size != expandedClusterAlertIds.size) {
            expandedClusterAlertIds = retainedIds.toList()
        }
        if (retainedIds.isEmpty()) {
            expandedClusterOriginZoom = null
        }
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
    val mapLoadState = resolveMapLoadState(currentMapLoaded, mapLoadFailed)

    LaunchedEffect(mapSource, mapLoadAttempt, currentMapLoaded) {
        if (currentMapLoaded) {
            mapLoadFailed = false
            return@LaunchedEffect
        }
        mapLoadFailed = false
        kotlinx.coroutines.delay(12_000)
        if (!currentMapLoaded) mapLoadFailed = true
    }

    fun retryMapLoad() {
        if (mapSource == MapDisplaySource.GOOGLE) {
            googleMapLoaded = false
        } else {
            openStreetMapLoaded = false
        }
        mapLoadFailed = false
        mapLoadAttempt += 1
    }

    fun toggleMapSource() {
        mapLoadFailed = false
        mapLoadAttempt += 1
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
        val mapContentPadding = PaddingValues(
            start = 16.dp,
            top = 72.dp,
            end = if (useSidePanel) 392.dp else 16.dp,
            bottom = if (useSidePanel) 24.dp else 96.dp
        )
        val fitPaddingPx = with(density) {
            (if (useSidePanel) 104.dp else 72.dp).roundToPx()
        }
        val openStreetMapInsets = with(density) {
            MapContentInsets(
                left = 16.dp.roundToPx(),
                top = 72.dp.roundToPx(),
                right = (if (useSidePanel) 392.dp else 16.dp).roundToPx(),
                bottom = (if (useSidePanel) 24.dp else 96.dp).roundToPx()
            )
        }
        val visibleCoordinates = remember(filteredAlerts) { resolveFitAllCoordinates(filteredAlerts) }
        val displayZoom = if (mapSource == MapDisplaySource.GOOGLE) {
            cameraPositionState.position.zoom.toDouble()
        } else {
            retainedZoom
        }
        val expandedAlertIdSet = remember(expandedClusterAlertIds) {
            expandedClusterAlertIds.toSet()
        }
        val spawnRadiusMeters = spawnRadiusMeters(showSpawnRadius, spacialRendEnabled)
        val markerItems = remember(
            filteredAlerts,
            displayZoom,
            spawnRadiusMeters,
            expandedAlertIdSet
        ) {
            clusterMapAlerts(
                alerts = filteredAlerts,
                zoom = displayZoom,
                spawnRadiusMeters = spawnRadiusMeters,
                expandedAlertIds = expandedAlertIdSet
            )
        }

        LaunchedEffect(displayZoom, expandedClusterOriginZoom) {
            if (shouldClearExpandedMapCluster(expandedClusterOriginZoom, displayZoom)) {
                expandedClusterAlertIds = emptyList()
                expandedClusterOriginZoom = null
            }
        }

        fun fitVisibleAlerts() {
            expandedClusterAlertIds = emptyList()
            expandedClusterOriginZoom = null
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
            key(mapLoadAttempt) {
                GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = mapContentPadding,
                onMapLoaded = {
                    googleMapLoaded = true
                    mapLoadFailed = false
                },
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
                if (spawnRadiusMeters != null) {
                    filteredAlerts.filter { it.isSpawnAlert }.forEach { alert ->
                        val coords = alert.mapCoordinatesOrNull() ?: return@forEach
                        Circle(
                            center = LatLng(coords.latitude, coords.longitude),
                            radius = spawnRadiusMeters,
                            fillColor = Color(0x471A73E8),
                            strokeColor = Color(0xFF1A73E8),
                            strokeWidth = 2.5f * density.density,
                            zIndex = 800f
                        )
                    }
                }
                markerItems.forEach { item ->
                    when (item) {
                        is MapMarkerItem.Alert -> key(item.alert.uniqueId) {
                            MapMarker(
                                alert = item.alert,
                                countdownClock = markerCountdownClock,
                                density = density,
                                showTimeLabel = showTimeLabels,
                                goDexMatchResult = goDexMatches[item.alert.uniqueId]
                                    ?: GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED),
                                onClick = { selectedAlertId = item.alert.uniqueId }
                            )
                        }
                        is MapMarkerItem.Cluster -> key("cluster-${item.id}") {
                            Marker(
                                state = MarkerState(LatLng(item.latitude, item.longitude)),
                                icon = BitmapDescriptorFactory.fromBitmap(
                                    remember(item.id, item.sharedCategory, item.alerts.size) {
                                        createClusterMarkerBitmap(
                                            context = context,
                                            count = item.alerts.size,
                                            sharedCategory = item.sharedCategory
                                        )
                                    }
                                ),
                                anchor = Offset(0.5f, 0.5f),
                                zIndex = 850f,
                                onClick = {
                                    when (
                                        val interaction = resolveMapClusterInteraction(
                                            cluster = item,
                                            currentZoom = displayZoom
                                        )
                                    ) {
                                        MapClusterInteraction.ShowMembers -> {
                                            selectedClusterAlerts = item.alerts
                                        }
                                        is MapClusterInteraction.Expand -> {
                                            expandedClusterAlertIds = interaction.alertIds.toList()
                                            expandedClusterOriginZoom = interaction.originZoom
                                            scope.launch {
                                                val bounds = LatLngBounds(
                                                    LatLng(item.bounds.south, item.bounds.west),
                                                    LatLng(item.bounds.north, item.bounds.east)
                                                )
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngBounds(bounds, fitPaddingPx),
                                                    600
                                                )
                                            }
                                        }
                                    }
                                    true
                                }
                            )
                        }
                    }
                }
                }
            }
        } else {
            key(mapLoadAttempt) {
                OpenStreetMapView(
                    modifier = Modifier.fillMaxSize(),
                    alerts = filteredAlerts,
                    userPose = userPose,
                    cameraSnapshot = retainedCamera(),
                    contentInsets = openStreetMapInsets,
                    showTimeLabels = showTimeLabels,
                    countdownClock = markerCountdownClock,
                    goDexMatches = goDexMatches,
                    controller = openStreetMapController,
                    onMapLoaded = {
                        openStreetMapLoaded = true
                        mapLoadFailed = false
                    },
                    onLoadError = {
                        mapLoadFailed = true
                        Toast.makeText(context, R.string.map_openstreetmap_unavailable, Toast.LENGTH_LONG).show()
                    },
                    onAlertClick = { selectedAlertId = it.uniqueId },
                    onClusterClick = { cluster ->
                        when (
                            val interaction = resolveMapClusterInteraction(
                                cluster = cluster,
                                currentZoom = retainedZoom
                            )
                        ) {
                            MapClusterInteraction.ShowMembers -> {
                                selectedClusterAlerts = cluster.alerts
                            }
                            is MapClusterInteraction.Expand -> {
                                expandedClusterAlertIds = interaction.alertIds.toList()
                                expandedClusterOriginZoom = interaction.originZoom
                                openStreetMapController.fitAlerts(
                                    cluster.alerts.mapNotNull(PokemonAlert::mapCoordinatesOrNull),
                                    fitPaddingPx
                                )
                            }
                        }
                    },
                    onCameraChanged = ::updateRetainedCamera,
                    onUserGesture = {
                        applyTrackingInteraction(trackingInteraction().onUserCameraGesture())
                    },
                    expandedAlertIds = expandedAlertIdSet,
                    showSpawnRadius = showSpawnRadius,
                    spacialRendEnabled = spacialRendEnabled
                )
            }

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

        AnimatedVisibility(
            visible = mapLoadState != MapLoadState.READY,
            enter = appFadeIn(),
            exit = appFadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.large,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedContent(
                            targetState = mapLoadState,
                            transitionSpec = { appFadeThrough() },
                            label = "map_load_state"
                        ) { state ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (state == MapLoadState.LOADING) {
                                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                    Text(
                                        text = "Loading ${
                                            if (mapSource == MapDisplaySource.GOOGLE) "Google Maps" else "OpenStreetMap"
                                        }\u2026",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                } else {
                                    Text(
                                        text = "Map couldn\u2019t be loaded",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Check your connection, retry, or use the other map provider.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = ::retryMapLoad) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Retry")
                                    }
                                    TextButton(onClick = ::toggleMapSource) {
                                        Text(
                                            if (mapSource == MapDisplaySource.GOOGLE) {
                                                "Use OpenStreetMap"
                                            } else {
                                                "Use Google Maps"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = controlsEndPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MapTopAppBar(
                visibleAlertCount = filteredAlerts.size,
                showBackButton = showBackButton,
                refreshing = syncStatus is SyncStatus.Loading || syncStatus is SyncStatus.Refreshing,
                activeLayerCount = listOf(
                    mapStyle != MapStylePreference.GOOGLE_STANDARD,
                    showTimeLabels,
                    showSpawnRadius,
                    spacialRendEnabled
                ).count { it },
                onBack = onBack,
                onRefresh = onRefresh,
                onOpenLayers = { showLayersSheet = true }
            )

            MapFilterRow(
                filters = availableFilters,
                selectedFilter = selectedFilter,
                visibleAlertCount = filteredAlerts.size,
                onFilterSelected = onSelectedFilterChange
            )
            MapSyncStatus(status = syncStatus, onRetry = onRefresh)
        }

        AnimatedVisibility(
            visible = selectedFilter != AlertFilter.ALL && filteredAlerts.isEmpty(),
            enter = appExpandIn(),
            exit = appCollapseOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                modifier = Modifier
                    .padding(24.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No ${selectedFilter.label.lowercase()} alerts nearby")
                    TextButton(onClick = { onSelectedFilterChange(AlertFilter.ALL) }) {
                        Text("Clear filter")
                    }
                }
            }
        }

        if (showLayersSheet) {
            MapLayersSheet(
                // The retained camera, not the Google camera state: that one is not driven
                // while the OpenStreetMap layer is the visible one.
                mapCentre = retainedCamera(),
                mapStyle = mapStyle,
                showTimeLabels = showTimeLabels,
                showSpawnRadius = showSpawnRadius,
                spacialRendEnabled = spacialRendEnabled,
                onDismiss = { showLayersSheet = false },
                onMapStyleChanged = {
                    mapStyle = it
                    onMapStyleChanged(it)
                },
                onToggleTimeLabels = { onShowTimeLabelsChanged(!showTimeLabels) },
                onToggleSpawnRadius = onToggleSpawnRadius,
                onToggleSpacialRend = onToggleSpacialRend
            )
        }

        if (selectedClusterAlerts.isNotEmpty()) {
            ModalBottomSheet(onDismissRequest = { selectedClusterAlerts = emptyList() }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${selectedClusterAlerts.size} alerts here",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    selectedClusterAlerts.take(12).forEach { alert ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedClusterAlerts = emptyList()
                                selectedAlertId = alert.uniqueId
                            }
                        ) {
                            Text(alert.name, modifier = Modifier.weight(1f))
                            MapCountdownText(alert.endTime, markerCountdownClock)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = currentMapLoaded && (useSidePanel || selectedAlert == null),
            enter = appFadeIn(),
            exit = appFadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier
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
            val distanceInfo = remember(
                alert.uniqueId,
                userLocation?.latitude,
                userLocation?.longitude,
                selectedWalkingRoute
            ) {
                resolveMapAlertDistanceInfo(userLocation, alert, selectedWalkingRoute)
            }
            if (useSidePanel) {
                MapAlertSidePanel(
                    alert = alert,
                    goDexStatus = goDexMatches[alert.uniqueId]
                        ?: GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED),
                    distanceInfo = distanceInfo,
                    onDismiss = { selectedAlertId = null },
                    isGoing = arrivalTracking.isTracking(alert),
                    onGoing = { arrivalTracking.onToggle(alert) },
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
                    onDismissRequest = { selectedAlertId = null },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    MapAlertDetailContent(
                        alert = alert,
                        goDexStatus = goDexMatches[alert.uniqueId]
                            ?: GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED),
                        distanceInfo = distanceInfo,
                        onDismiss = { selectedAlertId = null },
                        isGoing = arrivalTracking.isTracking(alert),
                        onGoing = { arrivalTracking.onToggle(alert) },
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

internal enum class MapLoadState {
    LOADING,
    READY,
    ERROR
}

internal fun resolveMapLoadState(loaded: Boolean, failed: Boolean): MapLoadState = when {
    loaded -> MapLoadState.READY
    failed -> MapLoadState.ERROR
    else -> MapLoadState.LOADING
}

@Composable
internal fun MapSyncStatus(status: SyncStatus, onRetry: () -> Unit) {
    val text = status.mapStatusMessage()
    val problem = status is SyncStatus.Cached || status is SyncStatus.Failed
    AnimatedContent(
        targetState = text to problem,
        transitionSpec = { appFadeThrough() },
        label = "map_sync_status"
    ) { (animatedText, animatedProblem) ->
        if (animatedText != null) {
            Surface(
                color = if (animatedProblem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = if (animatedProblem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(animatedText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    if (animatedProblem) TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}
