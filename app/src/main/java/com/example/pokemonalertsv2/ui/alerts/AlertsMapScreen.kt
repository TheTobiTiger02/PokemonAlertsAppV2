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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.OutlinedButton
import com.example.pokemonalertsv2.BuildConfig

internal fun mapCountdownRefreshKey(
    showTimeLabels: Boolean,
    nowMillis: Long,
    refreshIntervalMillis: Long = if (showTimeLabels) 1_000L else 30_000L
): Long = nowMillis / refreshIntervalMillis.coerceAtLeast(1L)

internal fun mapCountdownLabel(
    endTime: String?,
    nowMillis: Long,
    minutePrecision: Boolean = false
): String {
    val remaining = (TimeUtils.parseEndTimeToMillis(endTime) ?: Long.MAX_VALUE) - nowMillis
    if (remaining <= 0L) return "Expired"
    if (minutePrecision) {
        // Rounds up like the seconds countdown does; the label only changes once a minute,
        // which is what lets a crowded map skip per-second marker bitmap rebuilds.
        val minutes = ((remaining + 59_999) / 60_000).toInt().coerceAtLeast(1)
        return if (minutes >= 60) {
            String.format("%dh %02dm", minutes / 60, minutes % 60)
        } else {
            "${minutes}m"
        }
    }
    return TimeUtils.formatDurationShort(remaining)
}

/**
 * Above this marker count, marker countdowns drop to minute precision to stay smooth.
 * Every second-precision marker re-renders its bitmap once a second, so the ceiling is
 * deliberately conservative: a dense Darmstadt rocket field crosses it easily.
 */
internal const val ADAPTIVE_COUNTDOWN_PRECISION_THRESHOLD = 48

enum class MapPresentationMode {
    FULL,
    COMPACT_PICTURE_IN_PICTURE
}

internal fun mapFilterForPresentation(
    selectedCategories: Set<AlertCategory>,
    presentationMode: MapPresentationMode
): Set<AlertCategory> = if (presentationMode == MapPresentationMode.COMPACT_PICTURE_IN_PICTURE) {
    // The floating map always shows everything: it exists to track one alert, and a muted
    // category silently hiding that alert's neighbours would read as a glitch.
    emptySet()
} else {
    selectedCategories
}

internal fun mapAlertsForPresentation(
    filteredAlerts: List<PokemonAlert>,
    trackedAlert: PokemonAlert?,
    compactPictureInPicture: Boolean,
    nowMillis: Long
): List<PokemonAlert> {
    if (!compactPictureInPicture || trackedAlert == null) return filteredAlerts
    val trackedIsActiveAndMappable = trackedAlert.mapCoordinatesOrNull() != null &&
        !trackedAlert.isInvalidated &&
        (TimeUtils.parseEndTimeToMillis(trackedAlert.endTime) ?: Long.MAX_VALUE) > nowMillis
    if (!trackedIsActiveAndMappable || filteredAlerts.any { it.uniqueId == trackedAlert.uniqueId }) {
        return filteredAlerts
    }
    return filteredAlerts + trackedAlert
}

internal fun mapPipProtectedAlertIds(
    compactPictureInPicture: Boolean,
    trackedAlertId: String?,
    renderedAlerts: List<PokemonAlert>
): Set<String> = if (
    compactPictureInPicture &&
    trackedAlertId != null &&
    renderedAlerts.any { it.uniqueId == trackedAlertId }
) {
    setOf(trackedAlertId)
} else {
    emptySet()
}

internal fun mapPipEmphasizedAlertIds(
    compactPictureInPicture: Boolean,
    trackedAlertId: String?,
    browsedAlertId: String?
): Set<String> = if (compactPictureInPicture) {
    setOfNotNull(trackedAlertId, browsedAlertId)
} else {
    emptySet()
}

internal fun initialMapPipBrowsedAlertId(
    selectedAlertId: String?,
    trackedAlertId: String?,
    renderedAlerts: List<PokemonAlert>
): String? {
    val availableIds = renderedAlerts.mapTo(mutableSetOf(), PokemonAlert::uniqueId)
    return selectedAlertId?.takeIf { it in availableIds }
        ?: trackedAlertId?.takeIf { it in availableIds }
}

internal fun normalizeMapPictureInPictureZoom(zoom: Double?): Double =
    zoom?.takeIf(Double::isFinite)?.coerceIn(3.0, 20.0) ?: USER_LOCATION_ZOOM.toDouble()

internal fun mapPictureInPictureZoom(
    mapSource: MapDisplaySource,
    googleMapZoom: Double,
    retainedZoom: Double
): Double = normalizeMapPictureInPictureZoom(
    if (mapSource == MapDisplaySource.GOOGLE) googleMapZoom else retainedZoom
)

@Composable
fun AlertsMapRoute(
    viewModel: PokemonAlertsViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    presentationMode: MapPresentationMode = MapPresentationMode.FULL,
    initialZoom: Double? = null,
    onEnterPictureInPicture: (() -> Unit)? = null,
    pipCommands: Flow<MapPipCommand>? = null,
    onPipStateChanged: ((MapPipMode, Boolean) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedMapCategories by viewModel.selectedMapCategories.collectAsStateWithLifecycle()
    val mapShowDismissed by viewModel.mapShowDismissed.collectAsStateWithLifecycle()
    val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
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

    val autoEnterMapPip by viewModel.autoEnterMapPip.collectAsStateWithLifecycle()
    val showSpawnRadius by viewModel.showSpawnRadius.collectAsStateWithLifecycle()
    val spacialRendEnabled by viewModel.spacialRendEnabled.collectAsStateWithLifecycle()
    val dismissedAlertIds by viewModel.dismissedAlertIds.collectAsStateWithLifecycle()

    val effectiveCategories = mapFilterForPresentation(selectedMapCategories, presentationMode)

    AlertsMapScreen(
        alerts = uiState.alerts,
        onBack = onBack,
        onRefresh = viewModel::refreshAlerts,
        syncStatus = uiState.toSyncStatus(),
        selectedCategories = effectiveCategories,
        onSelectedCategoriesChange = if (presentationMode == MapPresentationMode.FULL) {
            viewModel::updateSelectedMapCategories
        } else {
            {}
        },
        mapShowDismissed = mapShowDismissed,
        onMapShowDismissedChange = if (presentationMode == MapPresentationMode.FULL) {
            viewModel::updateMapShowDismissed
        } else {
            {}
        },
        categoryCounts = categoryCounts,
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
        onToggleSpacialRend = { viewModel.updateSpacialRendEnabled(!spacialRendEnabled) },
        dismissedAlertIds = dismissedAlertIds,
        onDismissAlert = viewModel::dismissAlert,
        onRestoreAlert = viewModel::undoDismissAlert,
        presentationMode = presentationMode,
        initialZoom = initialZoom,
        onEnterPictureInPicture = onEnterPictureInPicture,
        autoEnterPictureInPicture = autoEnterMapPip,
        onToggleAutoEnterPictureInPicture = {
            viewModel.updateAutoEnterMapPip(!autoEnterMapPip)
        },
        pipCommands = pipCommands,
        onPipStateChanged = onPipStateChanged
    )
}

@Composable
fun AlertsMapScreen(
    alerts: List<PokemonAlert>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    syncStatus: SyncStatus = SyncStatus.Live(null),
    selectedCategories: Set<AlertCategory> = emptySet(),
    onSelectedCategoriesChange: (Set<AlertCategory>) -> Unit = {},
    mapShowDismissed: Boolean = false,
    onMapShowDismissedChange: (Boolean) -> Unit = {},
    categoryCounts: Map<AlertCategory, Int> = emptyMap(),
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
    dismissedAlertIds: Set<String> = emptySet(),
    onDismissAlert: (String) -> Unit = {},
    onRestoreAlert: (String) -> Unit = {},
    presentationMode: MapPresentationMode = MapPresentationMode.FULL,
    initialZoom: Double? = null,
    onEnterPictureInPicture: (() -> Unit)? = null,
    autoEnterPictureInPicture: Boolean = false,
    onToggleAutoEnterPictureInPicture: () -> Unit = {},
    pipCommands: Flow<MapPipCommand>? = null,
    onPipStateChanged: ((MapPipMode, Boolean) -> Unit)? = null
) {
    AlertsMapScreenContent(
        alerts = alerts,
        onBack = onBack,
        onRefresh = onRefresh,
        syncStatus = syncStatus,
        selectedCategories = selectedCategories,
        onSelectedCategoriesChange = onSelectedCategoriesChange,
        mapShowDismissed = mapShowDismissed,
        onMapShowDismissedChange = onMapShowDismissedChange,
        categoryCounts = categoryCounts,
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
        dismissedAlertIds = dismissedAlertIds,
        onDismissAlert = onDismissAlert,
        onRestoreAlert = onRestoreAlert,
        presentationMode = presentationMode,
        initialZoom = initialZoom,
        onEnterPictureInPicture = onEnterPictureInPicture,
        autoEnterPictureInPicture = autoEnterPictureInPicture,
        onToggleAutoEnterPictureInPicture = onToggleAutoEnterPictureInPicture,
        pipCommands = pipCommands,
        onPipStateChanged = onPipStateChanged,
        locationTrackerFactory = DefaultMapPoseTrackerFactory
    )
}

@Composable
internal fun AlertsMapScreenContent(
    alerts: List<PokemonAlert>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    syncStatus: SyncStatus = SyncStatus.Live(null),
    selectedCategories: Set<AlertCategory> = emptySet(),
    onSelectedCategoriesChange: (Set<AlertCategory>) -> Unit = {},
    mapShowDismissed: Boolean = false,
    onMapShowDismissedChange: (Boolean) -> Unit = {},
    categoryCounts: Map<AlertCategory, Int> = emptyMap(),
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
    dismissedAlertIds: Set<String> = emptySet(),
    onDismissAlert: (String) -> Unit = {},
    onRestoreAlert: (String) -> Unit = {},
    presentationMode: MapPresentationMode = MapPresentationMode.FULL,
    initialZoom: Double? = null,
    onEnterPictureInPicture: (() -> Unit)? = null,
    autoEnterPictureInPicture: Boolean = false,
    onToggleAutoEnterPictureInPicture: () -> Unit = {},
    pipCommands: Flow<MapPipCommand>? = null,
    onPipStateChanged: ((MapPipMode, Boolean) -> Unit)? = null,
    locationTrackerFactory: MapPoseTrackerFactory = DefaultMapPoseTrackerFactory
) {
    val context = LocalContext.current
    val arrivalTracking = rememberArrivalTrackingUiController()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val darkTheme = LocalAppDarkTheme.current

    val compactPictureInPicture =
        presentationMode == MapPresentationMode.COMPACT_PICTURE_IN_PICTURE
    val startingZoom = remember(initialZoom) { normalizeMapPictureInPictureZoom(initialZoom) }
    val defaultLatLng = remember { LatLng(ALSBACH_LATITUDE, ALSBACH_LONGITUDE) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, startingZoom.toFloat())
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
    val selectedAlert = remember(alerts, selectedAlertId, arrivalTracking.activeDestination) {
        alerts.firstOrNull { it.uniqueId == selectedAlertId }
            ?: arrivalTracking.activeDestination?.alert
                ?.takeIf { it.uniqueId == selectedAlertId }
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
    // Not keyed on the presentation mode: the camera has to survive the picture-in-picture
    // window being expanded back to full screen, which changes that mode.
    var retainedZoom by rememberSaveable { mutableStateOf(startingZoom) }
    var pipZoom by rememberSaveable { mutableStateOf(startingZoom) }
    var pipMode by rememberSaveable { mutableStateOf(MapPipMode.FOLLOW) }
    val openStreetMapController = remember { OpenStreetMapController() }

    /**
     * The Google camera as of the last time it stopped moving. Clustering, culling and the
     * adaptive countdown all key off this instead of the live camera position: the live value
     * changes on every gesture frame, which used to rerun the cluster merge thousands of
     * times during a single pinch. The OpenStreetMap layer only reports camera idle anyway.
     */
    val googleCameraAnchor by produceState(
        initialValue = MapCameraSnapshot(defaultLatLng.latitude, defaultLatLng.longitude, startingZoom),
        cameraPositionState
    ) {
        snapshotFlow { cameraPositionState.isMoving }
            .collectLatest { moving ->
                if (!moving) {
                    val position = cameraPositionState.position
                    position.target?.let { target ->
                        value = MapCameraSnapshot(
                            latitude = target.latitude,
                            longitude = target.longitude,
                            zoom = position.zoom.toDouble()
                        )
                    }
                }
            }
    }

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
    // Seeded from the presentation mode the screen first composed in, then left alone so
    // expanding out of the window keeps whatever the user had set up in it.
    var trackingRequested by rememberSaveable { mutableStateOf(compactPictureInPicture) }
    var cameraFollowEnabled by rememberSaveable { mutableStateOf(compactPictureInPicture) }
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
                lifecycleOwner.lifecycle.currentState.isAtLeast(
                    if (compactPictureInPicture) Lifecycle.State.STARTED else Lifecycle.State.RESUMED
                )
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

    var pendingPictureInPictureLaunch by rememberSaveable { mutableStateOf(false) }

    fun launchPictureInPicture() {
        onEnterPictureInPicture?.invoke()
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
            if (pendingPictureInPictureLaunch) {
                pendingPictureInPictureLaunch = false
                launchPictureInPicture()
            } else {
                centerOnUserLocation()
            }
        } else {
            pendingPictureInPictureLaunch = false
            Toast.makeText(
                context,
                context.getString(R.string.map_location_permission_needed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val expirationClock = rememberCountdownClock(30_000L)

    // Marker countdown refresh, decided below once the marker count is known — the clock
    // itself is created after the marker list so the interval can adapt to map density.
    // (The old declaration here re-created marker bitmaps every second at any density.)

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
    val filteredAlerts = remember(alerts, selectedCategories, expirationNow, dismissedAlertIds, mapShowDismissed) {
        visibleMapAlerts(
            alerts = alerts,
            selectedCategories = selectedCategories,
            dismissedAlertIds = dismissedAlertIds,
            showDismissed = mapShowDismissed,
            nowMillis = expirationNow
        )
    }
    val renderedAlerts = remember(
        filteredAlerts,
        arrivalTracking.activeDestination,
        compactPictureInPicture,
        expirationNow
    ) {
        mapAlertsForPresentation(
            filteredAlerts = filteredAlerts,
            trackedAlert = arrivalTracking.activeDestination?.alert,
            compactPictureInPicture = compactPictureInPicture,
            nowMillis = expirationNow
        )
    }
    val protectedAlertIds = remember(
        compactPictureInPicture,
        arrivalTracking.activeDestination?.uniqueId,
        renderedAlerts
    ) {
        mapPipProtectedAlertIds(
            compactPictureInPicture = compactPictureInPicture,
            trackedAlertId = arrivalTracking.activeDestination?.uniqueId,
            renderedAlerts = renderedAlerts
        )
    }
    val emphasizedAlertIds = remember(
        compactPictureInPicture,
        arrivalTracking.activeDestination?.uniqueId,
        selectedAlertId
    ) {
        mapPipEmphasizedAlertIds(
            compactPictureInPicture = compactPictureInPicture,
            trackedAlertId = arrivalTracking.activeDestination?.uniqueId,
            browsedAlertId = selectedAlertId
        )
    }
    val goDexMatches = rememberGoDexMatchResults(
        alerts = renderedAlerts,
        entries = goDexEntries,
        configured = goDexConfig.isConnected
    )

    LaunchedEffect(selectedCategories, mapSource) {
        expandedClusterAlertIds = emptyList()
        expandedClusterOriginZoom = null
    }

    LaunchedEffect(renderedAlerts) {
        val retainedIds = retainActiveExpandedAlertIds(
            expandedAlertIds = expandedClusterAlertIds,
            activeAlertIds = renderedAlerts.mapTo(mutableSetOf(), PokemonAlert::uniqueId)
        )
        if (retainedIds.size != expandedClusterAlertIds.size) {
            expandedClusterAlertIds = retainedIds.toList()
        }
        if (retainedIds.isEmpty()) {
            expandedClusterOriginZoom = null
        }
    }

    fun moveMapCamera(latitude: Double, longitude: Double, zoom: Double) {
        val target = MapCameraSnapshot(latitude, longitude, zoom)
        updateRetainedCamera(target)
        if (mapSource == MapDisplaySource.GOOGLE) {
            scope.launch {
                runCatching {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(latitude, longitude),
                            zoom.toFloat()
                        ),
                        400
                    )
                }
            }
        } else {
            openStreetMapController.setCamera(target, animate = true)
        }
    }

    // The window is barely 400 px tall, so the fit padding the full map uses would leave
    // nothing to draw in - and newLatLngBounds rejects padding that large outright.
    // Generous enough that neither marker is drawn flush against an edge or under the chip
    // - the window is only about 145 dp tall, so there is no room for a subtler margin.
    val pipFitPaddingPx = with(density) { 40.dp.roundToPx() }
    var lastFitLatitude by remember { mutableStateOf<Double?>(null) }
    var lastFitLongitude by remember { mutableStateOf<Double?>(null) }

    /**
     * Frames the browsed alert together with the live location, so the window answers both
     * "where is it" and "where am I" at once. Without a fix yet, the alert is all we have.
     */
    fun focusBrowsedAlert(alert: PokemonAlert, from: android.location.Location?) {
        val coordinates = alert.mapCoordinatesOrNull() ?: return
        if (from == null) {
            lastFitLatitude = null
            lastFitLongitude = null
            moveMapCamera(coordinates.latitude, coordinates.longitude, pipZoom)
            return
        }
        lastFitLatitude = from.latitude
        lastFitLongitude = from.longitude
        when (
            val focus = resolveMapPipFocus(
                userLatitude = from.latitude,
                userLongitude = from.longitude,
                alertLatitude = coordinates.latitude,
                alertLongitude = coordinates.longitude
            )
        ) {
            is MapPipFocus.Centre ->
                moveMapCamera(focus.latitude, focus.longitude, focus.zoom)
            is MapPipFocus.Fit -> {
                if (mapSource == MapDisplaySource.GOOGLE) {
                    scope.launch {
                        runCatching {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngBounds(
                                    LatLngBounds(
                                        LatLng(focus.south, focus.west),
                                        LatLng(focus.north, focus.east)
                                    ),
                                    pipFitPaddingPx
                                ),
                                500
                            )
                        }
                        val position = cameraPositionState.position
                        updateRetainedCamera(
                            MapCameraSnapshot(
                                position.target.latitude,
                                position.target.longitude,
                                position.zoom.toDouble()
                            )
                        )
                    }
                } else {
                    openStreetMapController.fitAlerts(
                        listOf(
                            AlertMapCoordinates(from.latitude, from.longitude),
                            AlertMapCoordinates(coordinates.latitude, coordinates.longitude)
                        ),
                        pipFitPaddingPx
                    )
                }
            }
        }
    }

    fun stepBrowseSelection(forward: Boolean) {
        val origin = userLocation
        val ordered = mapPipBrowseOrder(
            alerts = renderedAlerts,
            originLatitude = origin?.latitude ?: retainedLatitude,
            originLongitude = origin?.longitude ?: retainedLongitude
        )
        val nextId = stepMapPipSelection(
            orderedIds = ordered.map(PokemonAlert::uniqueId),
            currentId = selectedAlertId,
            forward = forward
        ) ?: return
        val next = ordered.firstOrNull { it.uniqueId == nextId } ?: return
        selectedAlertId = next.uniqueId
        focusBrowsedAlert(next, userLocation)
    }

    fun handlePipCommand(command: MapPipCommand) {
        when (command) {
            MapPipCommand.TOGGLE_MODE -> {
                val nextMode = pipMode.toggled()
                pipMode = nextMode
                if (nextMode == MapPipMode.BROWSE) {
                    cameraFollowEnabled = false
                    // The camera stops following, but the fix stream has to keep running:
                    // the framing is only live if the location behind it is.
                    trackingRequested = true
                    val current = renderedAlerts.firstOrNull { it.uniqueId == selectedAlertId }
                    if (current != null) {
                        focusBrowsedAlert(current, userLocation)
                    } else {
                        stepBrowseSelection(forward = true)
                    }
                } else {
                    selectedAlertId = null
                    trackingRequested = true
                    cameraFollowEnabled = true
                    userLocation?.let { moveMapCamera(it.latitude, it.longitude, pipZoom) }
                }
            }
            MapPipCommand.PREVIOUS, MapPipCommand.NEXT -> {
                val forward = command == MapPipCommand.NEXT
                if (pipMode == MapPipMode.BROWSE) {
                    stepBrowseSelection(forward)
                } else {
                    pipZoom = normalizeMapPictureInPictureZoom(
                        pipZoom + if (forward) 1.0 else -1.0
                    )
                    val following = userLocation?.takeIf { cameraFollowEnabled }
                    moveMapCamera(
                        following?.latitude ?: retainedLatitude,
                        following?.longitude ?: retainedLongitude,
                        pipZoom
                    )
                }
            }
        }
    }

    val currentPipCommandHandler by rememberUpdatedState(
        newValue = { command: MapPipCommand -> handlePipCommand(command) }
    )
    LaunchedEffect(pipCommands) {
        pipCommands?.collect { command -> currentPipCommandHandler(command) }
    }

    // Entering the window inherits whatever the full map was showing: an alert that is still
    // live means the user was already looking at it, so open browsing rather than following.
    LaunchedEffect(
        compactPictureInPicture,
        arrivalTracking.activeDestination?.uniqueId
    ) {
        if (!compactPictureInPicture) return@LaunchedEffect
        pipZoom = mapPictureInPictureZoom(
            mapSource = mapSource,
            googleMapZoom = cameraPositionState.position.zoom.toDouble(),
            retainedZoom = retainedZoom
        )
        trackingRequested = true
        val browsedAlertId = initialMapPipBrowsedAlertId(
            selectedAlertId = selectedAlertId,
            trackedAlertId = arrivalTracking.activeDestination?.uniqueId,
            renderedAlerts = renderedAlerts
        )
        val browsing = browsedAlertId != null
        pipMode = if (browsing) MapPipMode.BROWSE else MapPipMode.FOLLOW
        cameraFollowEnabled = !browsing
        selectedAlertId = browsedAlertId
        if (browsing) {
            lastFitLatitude = null
            lastFitLongitude = null
        }
    }

    val pipCanStep = renderedAlerts.isNotEmpty()
    val currentPipStateReporter by rememberUpdatedState(onPipStateChanged)
    LaunchedEffect(pipMode, pipCanStep) {
        currentPipStateReporter?.invoke(pipMode, pipCanStep)
    }

    val mapUiSettings = remember(hasLocationPermission, compactPictureInPicture) {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = !compactPictureInPicture,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = !compactPictureInPicture,
            scrollGesturesEnabled = !compactPictureInPicture,
            zoomGesturesEnabled = !compactPictureInPicture
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
        val currentZoom = if (compactPictureInPicture) {
            pipZoom
        } else if (mapSource == MapDisplaySource.GOOGLE) {
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

    val currentFocusBrowsedAlert by rememberUpdatedState(
        newValue = { alert: PokemonAlert, from: android.location.Location? ->
            focusBrowsedAlert(alert, from)
        }
    )
    // Re-frame as the user walks, but only once they have actually moved: the fix stream is
    // about 1 Hz and animating the camera every second would be unreadable.
    LaunchedEffect(compactPictureInPicture, pipMode, selectedAlertId, currentMapLoaded, mapSource) {
        if (!compactPictureInPicture || pipMode != MapPipMode.BROWSE || !currentMapLoaded) {
            return@LaunchedEffect
        }
        val alert = renderedAlerts.firstOrNull { it.uniqueId == selectedAlertId } ?: return@LaunchedEffect
        if (mapSource == MapDisplaySource.OPENSTREETMAP) {
            // MapLibre reports its style loaded while Android is still resizing the map into
            // PiP. Let that transition settle before calculating bounds from its final size.
            delay(600)
        }
        snapshotFlow { userPose?.location }.collect { location ->
            if (location == null) return@collect
            val previousLatitude = lastFitLatitude
            val previousLongitude = lastFitLongitude
            val moved = previousLatitude == null || previousLongitude == null ||
                mapPipDistanceMeters(
                    previousLatitude,
                    previousLongitude,
                    location.latitude,
                    location.longitude
                ) >= MAP_PIP_REFIT_METERS
            if (moved) {
                currentFocusBrowsedAlert(alert, location)
            }
        }
    }

    LaunchedEffect(currentMapLoaded, renderedAlerts, locationLookupComplete, userLocation, mapSource) {
        if (currentMapLoaded && locationLookupComplete && !initialCameraPositioned) {
            val resolvedViewport = resolveInitialMapViewport(
                userLatitude = userLocation?.latitude,
                userLongitude = userLocation?.longitude,
                alerts = renderedAlerts
            )
            val viewport = if (compactPictureInPicture) {
                resolvedViewport.copy(zoom = pipZoom.toFloat())
            } else {
                resolvedViewport
            }
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag(
                if (compactPictureInPicture) "map_pip_content" else "map_full_content"
            )
    ) {
        val useSidePanel = !compactPictureInPicture && maxWidth >= 840.dp
        val controlsEndPadding = if (useSidePanel) 392.dp else 16.dp
        val mapContentPadding = if (compactPictureInPicture) {
            PaddingValues(0.dp)
        } else {
            PaddingValues(
                start = 16.dp,
                top = 72.dp,
                end = if (useSidePanel) 392.dp else 16.dp,
                bottom = if (useSidePanel) 24.dp else 96.dp
            )
        }
        val fitPaddingPx = with(density) {
            (if (useSidePanel) 104.dp else 72.dp).roundToPx()
        }
        val openStreetMapInsets = with(density) {
            MapContentInsets(
                left = if (compactPictureInPicture) 0 else 16.dp.roundToPx(),
                top = if (compactPictureInPicture) 0 else 72.dp.roundToPx(),
                right = if (compactPictureInPicture) {
                    0
                } else {
                    (if (useSidePanel) 392.dp else 16.dp).roundToPx()
                },
                bottom = if (compactPictureInPicture) {
                    0
                } else {
                    (if (useSidePanel) 24.dp else 96.dp).roundToPx()
                }
            )
        }
        val visibleCoordinates = remember(renderedAlerts) { resolveFitAllCoordinates(renderedAlerts) }
        val configuration = LocalConfiguration.current
        val viewportWidthDp = configuration.screenWidthDp.toFloat()
        val viewportHeightDp = configuration.screenHeightDp.toFloat()
        val cameraAnchor = if (mapSource == MapDisplaySource.GOOGLE) {
            googleCameraAnchor
        } else {
            MapCameraSnapshot(retainedLatitude, retainedLongitude, retainedZoom)
        }
        // Half-zoom steps: reclustering twice per zoom step is visually indistinguishable
        // from reclustering on every frame, and survives a pinch in far fewer passes.
        val clusterZoom = kotlin.math.floor(cameraAnchor.zoom * 2.0) / 2.0
        val viewportBounds = remember(cameraAnchor, viewportWidthDp, viewportHeightDp) {
            mapViewportBounds(
                centreLatitude = cameraAnchor.latitude,
                centreLongitude = cameraAnchor.longitude,
                zoom = cameraAnchor.zoom,
                viewportWidthDp = viewportWidthDp,
                viewportHeightDp = viewportHeightDp
            )
        }
        // Cull to the widened viewport before clustering; the tracked PiP alert always
        // survives so the follow marker can never be culled out from under itself.
        val clusteredAlerts = remember(renderedAlerts, viewportBounds, protectedAlertIds) {
            if (viewportBounds == null) {
                renderedAlerts
            } else {
                renderedAlerts.filter { alert ->
                    alert.uniqueId in protectedAlertIds ||
                        alert.mapCoordinatesOrNull()?.let { coords ->
                            viewportBounds.contains(coords.latitude, coords.longitude)
                        } == true
                }
            }
        }
        val expandedAlertIdSet = remember(expandedClusterAlertIds) {
            expandedClusterAlertIds.toSet()
        }
        val spawnRadiusMeters = spawnRadiusMeters(showSpawnRadius, spacialRendEnabled)
        val baseMarkerSizeDp = mapAlertMarkerSizeDp(
            compactPictureInPicture = compactPictureInPicture,
            emphasized = false
        )
        val emphasizedMarkerSizeDp = mapAlertMarkerSizeDp(
            compactPictureInPicture = compactPictureInPicture,
            emphasized = true
        )
        val clusterMarkerSizeDp = mapClusterMarkerSizeDp(compactPictureInPicture)
        val markerItems = remember(
            clusteredAlerts,
            clusterZoom,
            spawnRadiusMeters,
            expandedAlertIdSet,
            protectedAlertIds
        ) {
            clusterMapAlerts(
                alerts = clusteredAlerts,
                zoom = clusterZoom,
                spawnRadiusMeters = spawnRadiusMeters,
                expandedAlertIds = expandedAlertIdSet,
                protectedAlertIds = protectedAlertIds
            )
        }
        val renderedMarkerCount = markerItems.count { it is MapMarkerItem.Alert }
        val minutePrecisionCountdown =
            showTimeLabels && renderedMarkerCount > ADAPTIVE_COUNTDOWN_PRECISION_THRESHOLD
        val markerTickMillis = when {
            !showTimeLabels -> 30_000L
            minutePrecisionCountdown -> 60_000L
            else -> 1_000L
        }
        val markerCountdownClock = rememberCountdownClock(markerTickMillis)

        LaunchedEffect(cameraAnchor.zoom, expandedClusterOriginZoom) {
            if (shouldClearExpandedMapCluster(expandedClusterOriginZoom, cameraAnchor.zoom)) {
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
                if (spawnRadiusMeters != null && cameraAnchor.zoom >= SPAWN_CIRCLE_MIN_ZOOM) {
                    // Zoom-gated and capped: at Darmstadt density, drawing a circle per spawn
                    // below neighborhood zoom repaints the whole map and drags the frame rate.
                    val circledAlerts = remember(
                        renderedAlerts,
                        cameraAnchor,
                        spawnRadiusMeters
                    ) {
                        renderedAlerts
                            .filter { it.isSpawnAlert }
                            .sortedBy { alert ->
                                val coords = alert.mapCoordinatesOrNull()
                                val latitudeDelta =
                                    (coords?.latitude ?: cameraAnchor.latitude) - cameraAnchor.latitude
                                val longitudeDelta =
                                    (coords?.longitude ?: cameraAnchor.longitude) - cameraAnchor.longitude
                                latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta
                            }
                            .take(MAX_SPAWN_CIRCLES)
                    }
                    circledAlerts.forEach { alert ->
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
                            val emphasized = item.alert.uniqueId in emphasizedAlertIds
                            MapMarker(
                                alert = item.alert,
                                countdownClock = markerCountdownClock,
                                density = density,
                                showTimeLabel = showTimeLabels,
                                minutePrecisionCountdown = minutePrecisionCountdown,
                                goDexMatchResult = goDexMatches[item.alert.uniqueId]
                                    ?: GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED),
                                onClick = {
                                    if (!compactPictureInPicture) {
                                        selectedAlertId = item.alert.uniqueId
                                    }
                                },
                                markerSizeDp = if (emphasized) {
                                    emphasizedMarkerSizeDp
                                } else {
                                    baseMarkerSizeDp
                                },
                                emphasized = emphasized
                            )
                        }
                        is MapMarkerItem.Cluster -> key("cluster-${item.id}") {
                            Marker(
                                state = MarkerState(LatLng(item.latitude, item.longitude)),
                                icon = BitmapDescriptorFactory.fromBitmap(
                                    remember(
                                        item.id,
                                        item.sharedCategory,
                                        item.alerts.size,
                                        clusterMarkerSizeDp
                                    ) {
                                        createClusterMarkerBitmap(
                                            context = context,
                                            count = item.alerts.size,
                                            sharedCategory = item.sharedCategory,
                                            sizeDp = clusterMarkerSizeDp
                                        )
                                    }
                                ),
                                anchor = Offset(0.5f, 0.5f),
                                zIndex = MAP_CLUSTER_MARKER_Z_INDEX,
                                onClick = {
                                    when (
                                        val interaction = if (compactPictureInPicture) {
                                            null
                                        } else {
                                            resolveMapClusterInteraction(
                                                cluster = item,
                                                currentZoom = cameraAnchor.zoom
                                            )
                                        }
                                    ) {
                                        null -> Unit
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
                    alerts = clusteredAlerts,
                    markerItems = markerItems,
                    countdownTickMillis = markerTickMillis,
                    minutePrecisionCountdown = minutePrecisionCountdown,
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
                    onAlertClick = {
                        if (!compactPictureInPicture) selectedAlertId = it.uniqueId
                    },
                    onClusterClick = { cluster ->
                        when (
                            val interaction = if (compactPictureInPicture) {
                                null
                            } else {
                                resolveMapClusterInteraction(
                                    cluster = cluster,
                                    currentZoom = retainedZoom
                                )
                            }
                        ) {
                            null -> Unit
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
                    spacialRendEnabled = spacialRendEnabled,
                    interactive = !compactPictureInPicture,
                    protectedAlertIds = protectedAlertIds,
                    emphasizedAlertIds = emphasizedAlertIds,
                    baseMarkerSizeDp = baseMarkerSizeDp,
                    emphasizedMarkerSizeDp = emphasizedMarkerSizeDp,
                    clusterMarkerSizeDp = clusterMarkerSizeDp
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

        // The window has no room for a card, so the browse cursor gets a single line.
        if (compactPictureInPicture && pipMode == MapPipMode.BROWSE) {
            Surface(
                modifier = Modifier
                    // Top, not bottom: the OpenStreetMap attribution owns the bottom
                    // edge and the two would sit on top of each other in this window.
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("map_pip_browse_chip"),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = selectedAlert
                        ?.let { alert -> mapPipBrowseLabel(alert, markerCountdownClock.value) }
                        ?: stringResource(R.string.map_pip_no_alerts),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (!compactPictureInPicture) {
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
                    onEnterPictureInPicture = onEnterPictureInPicture?.let {
                        {
                            if (hasLocationPermissionNow()) {
                                hasLocationPermission = true
                                showPreciseLocationGuidanceIfNeeded()
                                launchPictureInPicture()
                            } else {
                                pendingPictureInPictureLaunch = true
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    },
                    onOpenLayers = { showLayersSheet = true }
                )

                MapFilterRow(
                    selectedCategories = selectedCategories,
                    categoryCounts = categoryCounts,
                    visibleAlertCount = filteredAlerts.size,
                    onCategoryToggled = { category, shownAfter ->
                        // The selection holds muted categories: shown means remove from it.
                        onSelectedCategoriesChange(
                            if (shownAfter) selectedCategories - category
                            else selectedCategories + category
                        )
                    },
                    onShowAllCategories = { onSelectedCategoriesChange(emptySet()) }
                )
                MapSyncStatus(status = syncStatus, onRetry = onRefresh)

                // Weather for the area the user is actually standing in, mirroring the game's own
                // corner badge. Nothing renders when they are outside every scanned area.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    MapWeatherBadge(userLocation = userLocation)
                }
            }
        }

        AnimatedVisibility(
            visible = !compactPictureInPicture &&
                selectedCategories.isNotEmpty() && filteredAlerts.isEmpty(),
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
                    val hidden = FILTERABLE_ALERT_CATEGORIES
                        .filter { it in selectedCategories }
                        .joinToString(", ") { it.filterLabel.lowercase() }
                    Text("Every ${hidden} alert is hidden")
                    TextButton(onClick = { onSelectedCategoriesChange(emptySet()) }) {
                        Text("Show all alerts")
                    }
                }
            }
        }

        if (!compactPictureInPicture && showLayersSheet) {
            MapLayersSheet(
                // The retained camera, not the Google camera state: that one is not driven
                // while the OpenStreetMap layer is the visible one.
                mapCentre = retainedCamera(),
                mapStyle = mapStyle,
                showTimeLabels = showTimeLabels,
                showSpawnRadius = showSpawnRadius,
                spacialRendEnabled = spacialRendEnabled,
                showDismissed = mapShowDismissed,
                onDismiss = { showLayersSheet = false },
                onMapStyleChanged = {
                    mapStyle = it
                    onMapStyleChanged(it)
                },
                onToggleTimeLabels = { onShowTimeLabelsChanged(!showTimeLabels) },
                onToggleSpawnRadius = onToggleSpawnRadius,
                onToggleSpacialRend = onToggleSpacialRend,
                onToggleDismissed = { onMapShowDismissedChange(!mapShowDismissed) },
                autoEnterPictureInPicture = autoEnterPictureInPicture,
                onToggleAutoEnterPictureInPicture = if (onEnterPictureInPicture != null) {
                    onToggleAutoEnterPictureInPicture
                } else {
                    null
                }
            )
        }

        if (!compactPictureInPicture && selectedClusterAlerts.isNotEmpty()) {
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
            visible = !compactPictureInPicture &&
                currentMapLoaded && (useSidePanel || selectedAlert == null),
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

        if (!compactPictureInPicture) selectedAlert?.let { alert ->
            val distanceInfo = remember(
                alert.uniqueId,
                userLocation?.latitude,
                userLocation?.longitude,
                selectedWalkingRoute
            ) {
                resolveMapAlertDistanceInfo(userLocation, alert, selectedWalkingRoute)
            }
            val isDismissed = alert.uniqueId in dismissedAlertIds
            val dismissFromMap = {
                onDismissAlert(alert.uniqueId)
                // The marker is gone once the card closes, unless dismissed alerts are shown.
                if (!mapShowDismissed) selectedAlertId = null
                Toast.makeText(context, "Alert dismissed", Toast.LENGTH_SHORT).show()
            }
            val restoreFromMap = {
                onRestoreAlert(alert.uniqueId)
                Toast.makeText(context, "Alert restored", Toast.LENGTH_SHORT).show()
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
                    modifier = Modifier.align(Alignment.TopEnd),
                    isDismissed = isDismissed,
                    onDismissAlert = dismissFromMap,
                    onRestoreAlert = restoreFromMap
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
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        isDismissed = isDismissed,
                        onDismissAlert = dismissFromMap,
                        onRestoreAlert = restoreFromMap
                    )
                }
            }
        }
    }
}

/**
 * The markers the map should draw: mappable, still valid and unexpired alerts, minus the ones
 * the user dismissed unless [showDismissed] opts them back in, narrowed to the map's own
 * [selectedCategories] (independent of the feed's selection).
 */
internal fun visibleMapAlerts(
    alerts: List<PokemonAlert>,
    selectedCategories: Set<AlertCategory>,
    dismissedAlertIds: Set<String>,
    showDismissed: Boolean,
    nowMillis: Long
): List<PokemonAlert> =
    alerts.filter { alert ->
        alert.mapCoordinatesOrNull() != null &&
            !alert.isInvalidated &&
            (showDismissed || alert.uniqueId !in dismissedAlertIds) &&
            (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE) > nowMillis &&
            matchesCategorySelection(alert, selectedCategories)
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
