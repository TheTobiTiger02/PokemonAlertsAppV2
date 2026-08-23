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
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors
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
    val savedMapStyle by viewModel.mapStylePreference.collectAsStateWithLifecycle(
        initialValue = MapStylePreference.GOOGLE_STANDARD
    )
    val showMapCountdowns by viewModel.showMapCountdowns.collectAsStateWithLifecycle(initialValue = false)
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
        mutableStateOf<ArrayList<String>>(arrayListOf())
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

    // A live timer is only needed while a time label or the selected-alert panel is visible.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showTimeLabels, selectedAlert?.uniqueId) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(if (showTimeLabels || selectedAlert != null) 1_000 else 30_000)
        }
    }

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
        if (selectedFilter !in availableFilters) onSelectedFilterChange(AlertFilter.ALL)
    }

    LaunchedEffect(selectedFilter, mapSource) {
        expandedClusterAlertIds = arrayListOf()
        expandedClusterOriginZoom = null
    }

    LaunchedEffect(filteredAlerts) {
        val retainedIds = retainActiveExpandedAlertIds(
            expandedAlertIds = expandedClusterAlertIds,
            activeAlertIds = filteredAlerts.mapTo(mutableSetOf(), PokemonAlert::uniqueId)
        )
        if (retainedIds.size != expandedClusterAlertIds.size) {
            expandedClusterAlertIds = ArrayList(retainedIds)
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
        val markerItems = remember(
            filteredAlerts,
            displayZoom,
            density.density,
            expandedAlertIdSet
        ) {
            clusterMapAlerts(
                alerts = filteredAlerts,
                zoom = displayZoom,
                density = density.density,
                expandedAlertIds = expandedAlertIdSet
            )
        }

        LaunchedEffect(displayZoom, expandedClusterOriginZoom) {
            if (shouldClearExpandedMapCluster(expandedClusterOriginZoom, displayZoom)) {
                expandedClusterAlertIds = arrayListOf()
                expandedClusterOriginZoom = null
            }
        }

        fun fitVisibleAlerts() {
            expandedClusterAlertIds = arrayListOf()
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
                markerItems.forEach { item ->
                    when (item) {
                        is MapMarkerItem.Alert -> key(item.alert.uniqueId) {
                            MapMarker(
                                alert = item.alert,
                                now = now,
                                density = density,
                                showTimeLabel = showTimeLabels,
                                goDexEntries = goDexEntries,
                                goDexConfig = goDexConfig,
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
                                            expandedClusterAlertIds = ArrayList(interaction.alertIds)
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
                    now = now,
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
                                expandedClusterAlertIds = ArrayList(interaction.alertIds)
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
                    goDexEntries = goDexEntries,
                    goDexConfig = goDexConfig,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Text(mapCountdownLabel(alert.endTime, now))
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
private fun MapSyncStatus(status: SyncStatus, onRetry: () -> Unit) {
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

@Composable
private fun MapTopAppBar(
    visibleAlertCount: Int,
    showBackButton: Boolean,
    refreshing: Boolean,
    activeLayerCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLayers: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    AnimatedContent(
                        targetState = visibleAlertCount,
                        transitionSpec = { appFadeThrough() },
                        label = "map_alert_count"
                    ) { count ->
                        Text(
                            text = "$count",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                AnimatedRefreshIcon(
                    refreshing = refreshing,
                    contentDescription = stringResource(R.string.refresh_alerts)
                )
            }
            Box {
                IconButton(onClick = onOpenLayers, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_layers),
                        contentDescription = "Map layers",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                AnimatedContent(
                    targetState = activeLayerCount,
                    transitionSpec = { appFadeThrough() },
                    modifier = Modifier.align(Alignment.TopEnd),
                    label = "map_layer_count"
                ) { count ->
                    if (count > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapLayersSheet(
    mapStyle: MapStylePreference,
    showTimeLabels: Boolean,
    showSpawnRadius: Boolean,
    spacialRendEnabled: Boolean,
    onDismiss: () -> Unit,
    onMapStyleChanged: (MapStylePreference) -> Unit,
    onToggleTimeLabels: () -> Unit,
    onToggleSpawnRadius: () -> Unit,
    onToggleSpacialRend: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Map layers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Provider and style", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MapStylePreference.values()) { style ->
                    FilterChip(
                        selected = mapStyle == style,
                        onClick = { onMapStyleChanged(style) },
                        label = {
                            Text(
                                when (style) {
                                    MapStylePreference.GOOGLE_STANDARD -> "Google standard"
                                    MapStylePreference.GOOGLE_SATELLITE -> "Google satellite"
                                    MapStylePreference.OPENSTREETMAP -> "OpenStreetMap"
                                }
                            )
                        }
                    )
                }
            }
            FilterChip(
                selected = showTimeLabels,
                onClick = onToggleTimeLabels,
                label = { Text("Countdown labels") },
                leadingIcon = if (showTimeLabels) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
            FilterChip(
                selected = showSpawnRadius,
                onClick = onToggleSpawnRadius,
                label = { Text("Spawn radius") },
                leadingIcon = if (showSpawnRadius) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
            FilterChip(
                selected = spacialRendEnabled,
                enabled = showSpawnRadius,
                onClick = onToggleSpacialRend,
                label = { Text("Spacial Rend") },
                leadingIcon = if (spacialRendEnabled) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun MapFilterRow(
    filters: Collection<AlertFilter>,
    selectedFilter: AlertFilter,
    @Suppress("UNUSED_PARAMETER") visibleAlertCount: Int,
    onFilterSelected: (AlertFilter) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("map_filter_rail"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters.toList(), key = { it.name }) { filter ->
                val selected = selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.label, maxLines = 1) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
    isGoing: Boolean,
    onGoing: () -> Unit,
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
            isGoing = isGoing,
            onGoing = onGoing,
            onOpenMaps = onOpenMaps,
            onShare = onShare,
            onOpenFullDetail = onOpenFullDetail,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
internal fun MapAlertDetailContent(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo?,
    onDismiss: () -> Unit,
    isGoing: Boolean,
    onGoing: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSnoozeDialog by rememberSaveable(alert.uniqueId) { mutableStateOf(false) }
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
        modifier = modifier.heightIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AlertImage(
                alert = alert,
                modifier = Modifier.size(88.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
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
            GoDexCaughtAction(
                alert = alert,
                matchResult = goDexStatus
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onGoing,
                enabled = isGoing || alert.isEligibleArrivalDestination(),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(if (isGoing) "Stop" else "I’m going")
            }
            Button(
                onClick = onOpenMaps,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Directions")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { showSnoozeDialog = true },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Snooze")
            }
            FilledTonalButton(
                onClick = { openAlertInPictureInPicture(context, alert) },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("PiP")
            }
            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Share")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

        if (isGoing) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Arrival tracking active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
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

        }
        FilledTonalButton(
            onClick = onOpenFullDetail,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("map_open_details")
        ) {
            Text("Open details")
        }
    }

    if (showSnoozeDialog) {
        SnoozeDurationDialog(
            defaultMinutes = 10,
            onDismiss = { showSnoozeDialog = false },
            onConfirm = { minutes ->
                showSnoozeDialog = false
                scope.launch { snoozeAlertFromUi(context, alert, minutes) }
            }
        )
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
    alert: PokemonAlert,
    routeInfo: WalkingRouteInfo? = null
): AlertDistanceInfo? = resolveMapAlertDistanceInfo(
    userLatitude = userLocation?.latitude,
    userLongitude = userLocation?.longitude,
    alert = alert,
    routeInfo = routeInfo
)

internal fun resolveMapAlertDistanceInfo(
    userLatitude: Double?,
    userLongitude: Double?,
    alert: PokemonAlert,
    routeInfo: WalkingRouteInfo? = null,
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
    val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(straightLineDistance, routeInfo)
    return AlertDistanceInfo(
        distanceMeters = displayInfo.effectiveDistanceMeters,
        distanceText = displayInfo.distanceText,
        walkingText = displayInfo.walkingText,
        straightLineDistanceMeters = displayInfo.straightLineDistanceMeters,
        routedWalkingDistanceMeters = displayInfo.routedDistanceMeters,
        walkingDurationSeconds = displayInfo.walkingDurationSeconds,
        source = displayInfo.source
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
    val markerIconRequest = remember(
        markerSizePx,
        markerLabel,
        speciesName,
        speciesImageUrl,
        alert.endTime,
        showTimeLabel,
        timeLabel,
        palette,
        matchResult.status
    ) {
        MapMarkerIconRequest(
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
    val markerCacheKey = remember(markerIconRequest) {
        mapMarkerIconCacheKey(markerIconRequest)
    }
    var markerIcon by remember(markerCacheKey) {
        mutableStateOf(resolveInitialMapMarkerIcon(markerIconRequest, markerCacheKey))
    }

    LaunchedEffect(alert.uniqueId, markerIconRequest, markerCacheKey) {
        val renderedIcon = withContext(Dispatchers.IO) {
            createMapMarkerIcon(
                context = context,
                sizePx = markerIconRequest.sizePx,
                categoryCode = markerIconRequest.categoryCode,
                speciesName = markerIconRequest.speciesName,
                speciesImageUrl = markerIconRequest.speciesImageUrl,
                endTime = markerIconRequest.endTime,
                showTimeLabel = markerIconRequest.showTimeLabel,
                timeLabel = markerIconRequest.timeLabel,
                palette = markerIconRequest.palette,
                goDexStatus = markerIconRequest.goDexStatus
            )
        }
        currentCoroutineContext().ensureActive()
        if (renderedIcon != null) markerIcon = renderedIcon
    }
    val googleMarkerDescriptor = remember(markerIcon) {
        BitmapDescriptorFactory.fromBitmap(markerIcon.bitmap)
    }

    MarkerInfoWindowContent(
        state = remember(position) { MarkerState(position = position) },
        icon = googleMarkerDescriptor,
        anchor = markerIcon.anchor,
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

internal data class MapMarkerIconRequest(
    val sizePx: Int,
    val categoryCode: String,
    val speciesName: String,
    val speciesImageUrl: String?,
    val endTime: String?,
    val showTimeLabel: Boolean,
    val timeLabel: String?,
    val palette: MapMarkerPalette,
    val goDexStatus: GoDexMatchStatus
)

private val markerIconCache = LruCache<String, MapMarkerIcon>(256)

internal fun mapMarkerIconCacheKey(
    request: MapMarkerIconRequest,
    nowMillis: Long = System.currentTimeMillis()
): String = listOf(
    "material3-marker-compact",
    request.sizePx,
    request.categoryCode,
    request.speciesName,
    request.speciesImageUrl.orEmpty(),
    request.showTimeLabel,
    request.timeLabel.orEmpty(),
    isMapMarkerUrgent(request.endTime, nowMillis),
    request.palette,
    request.goDexStatus
).joinToString("|")

internal fun resolveInitialMapMarkerIcon(
    request: MapMarkerIconRequest,
    cacheKey: String = mapMarkerIconCacheKey(request)
): MapMarkerIcon = markerIconCache.get(cacheKey) ?: createFallbackMapMarkerIcon(request)

internal fun createFallbackMapMarkerIcon(
    request: MapMarkerIconRequest,
    nowMillis: Long = System.currentTimeMillis()
): MapMarkerIcon {
    val sizePx = request.sizePx
    val padding = (sizePx * 0.14f).toInt()
    val pinRadius = sizePx * 0.33f
    val tailHeight = sizePx * 0.20f
    val labelGap = (sizePx * 0.07f).toInt()
    val timeHeight = if (request.showTimeLabel && request.timeLabel != null) {
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
    val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = request.palette.primary
    }
    canvas.drawPath(tailPath, primaryPaint)
    canvas.drawCircle(centerX, centerY, pinRadius, primaryPaint)
    val innerRadius = pinRadius * 0.71f
    canvas.drawCircle(
        centerX,
        centerY,
        innerRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = request.palette.surface }
    )
    val initialsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = request.palette.onSurface
        textSize = innerRadius * 0.68f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val initials = request.speciesName
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { request.categoryCode }
    val initialsY = centerY - (initialsPaint.descent() + initialsPaint.ascent()) / 2f
    canvas.drawText(initials, centerX, initialsY, initialsPaint)
    canvas.drawMarkerLabel(
        centerX = centerX,
        top = centerY + innerRadius * 0.34f,
        height = sizePx * 0.18f,
        text = request.categoryCode,
        background = request.palette.surface,
        foreground = request.palette.primary,
        outline = request.palette.primary,
        maxWidth = pinRadius * 1.55f
    )
    if (timeHeight > 0 && request.timeLabel != null) {
        val urgent = isMapMarkerUrgent(request.endTime, nowMillis)
        canvas.drawMarkerLabel(
            centerX = centerX,
            top = pinTipY + labelGap,
            height = timeHeight.toFloat(),
            text = request.timeLabel,
            background = if (urgent) request.palette.error else request.palette.surface,
            foreground = if (urgent) request.palette.onError else request.palette.onSurface,
            outline = if (urgent) request.palette.error else request.palette.outline,
            maxWidth = totalWidth - padding * 2f
        )
    }
    return MapMarkerIcon(
        bitmap = bitmap,
        anchor = Offset(0.5f, (pinTipY / totalHeight).coerceIn(0f, 1f))
    )
}

private fun isMapMarkerUrgent(endTime: String?, nowMillis: Long): Boolean {
    val timeRemainingMs = endTime?.let(TimeUtils::parseEndTimeToMillis)?.let {
        it - nowMillis
    } ?: Long.MAX_VALUE
    return timeRemainingMs < 10 * 60 * 1_000
}

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
        val request = MapMarkerIconRequest(
            sizePx = sizePx,
            categoryCode = categoryCode,
            speciesName = speciesName,
            speciesImageUrl = speciesImageUrl,
            endTime = endTime,
            showTimeLabel = showTimeLabel,
            timeLabel = timeLabel,
            palette = palette,
            goDexStatus = goDexStatus
        )
        val isUrgent = isMapMarkerUrgent(endTime, System.currentTimeMillis())
        val cacheKey = mapMarkerIconCacheKey(request)
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

private fun createClusterMarkerBitmap(
    context: android.content.Context,
    count: Int,
    sharedCategory: AlertFilter?
): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (48f * density).toInt().coerceAtLeast(48)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = sharedCategory
        ?.let { resolveAlertVisualStyle(it.label).category.accentArgb.toInt() }
        ?: 0xFF455A64.toInt()
    canvas.drawCircle(size / 2f, size / 2f, size * 0.44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
    })
    canvas.drawCircle(size / 2f, size / 2f, size * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill
    })
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = size * if (count >= 100) 0.31f else 0.38f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(
        if (count > 999) "999+" else count.toString(),
        size / 2f,
        size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f,
        textPaint
    )
    return bitmap
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
