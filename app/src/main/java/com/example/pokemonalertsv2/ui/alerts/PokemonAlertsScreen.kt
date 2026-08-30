@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Immutable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.tracking.rememberArrivalTrackingUiController
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.ui.components.AnimatedEmptyState
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.components.ShimmerAlertCard
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.DistanceSource
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.InputChip
import com.example.pokemonalertsv2.data.FilterPreset
import com.example.pokemonalertsv2.data.FilterPresets
import com.example.pokemonalertsv2.util.TravelTime

@Composable
fun PokemonAlertsRoute(
    viewModel: PokemonAlertsViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showTopBar: Boolean = true
) {
    val alertsUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val selectedFilter by viewModel.selectedAlertFilter.collectAsStateWithLifecycle()

    val onShareClick: (PokemonAlert) -> Unit = { alert ->
        scope.launch {
            AlertShareCard.share(context, alert)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (showTopBar) {
                AlertsToolbar(
                    onRefresh = {
                        viewModel.refreshAlerts()
                    },
                    refreshing = alertsUiState.isLoading,
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
                val selectedArea by viewModel.selectedArea.collectAsStateWithLifecycle()
                val maxDistance by viewModel.maxDistance.collectAsStateWithLifecycle()
                val defaultSnoozeMinutes by viewModel.snoozeDuration.collectAsStateWithLifecycle()
                val savedSortPreference by viewModel.sortPreference.collectAsStateWithLifecycle()
                
                val savedPresets by viewModel.filterPresets.collectAsStateWithLifecycle()
                val maxWalkingMinutes by viewModel.maxWalkingMinutes.collectAsStateWithLifecycle()

                PokemonAlertsPage(
                    uiState = alertsUiState,
                    dismissedAlertIds = viewModel.dismissedAlertIds.collectAsStateWithLifecycle().value,
                    presetControls = FilterPresetControls(
                        presets = savedPresets,
                        onApply = viewModel::applyFilterPreset,
                        onSaveCurrent = { name ->
                            viewModel.saveFilterPreset(
                                name = name,
                                filter = selectedFilter,
                                sort = savedSortPreference,
                                area = selectedArea,
                                maxDistance = maxDistance
                            )
                        },
                        onDelete = viewModel::deleteFilterPreset
                    ),
                    maxWalkingMinutes = maxWalkingMinutes,
                    onMaxWalkingMinutesChange = viewModel::updateMaxWalkingMinutes,
                    selectedArea = selectedArea,
                    maxDistance = maxDistance,
                    defaultSnoozeMinutes = defaultSnoozeMinutes,
                    sortPreference = savedSortPreference,
                    selectedFilter = selectedFilter,
                    onSelectedFilterChange = viewModel::updateSelectedAlertFilter,
                    onSelectedAreaChange = viewModel::updateSelectedArea,
                    onMaxDistanceChange = viewModel::updateMaxDistance,
                    onSortPreferenceChange = viewModel::updateSortPreference,
                    onRefresh = viewModel::refreshAlerts,
                    onAlertSelected = { alert ->
                        val intent = AlertDetailActivity.createIntent(context, alert)
                        context.startActivity(intent)
                    },
                    onShareClick = onShareClick,
                    onSnoozeAlert = { alert, minutes ->
                        viewModel.snoozeAlert(alert, minutes) { scheduled ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (scheduled) {
                                        "Snoozed for ${formatSnoozeDurationLabel(minutes)}"
                                    } else {
                                        "Alert ends before that snooze time"
                                    },
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onDismissClick = { alertId ->
                        viewModel.dismissAlert(alertId)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Alert dismissed",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoDismissAlert(alertId)
                            }
                        }
                    },
                    onUndoDismiss = { alertId ->
                        viewModel.undoDismissAlert(alertId)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Alert restored",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
        }
    }

    // Foreground refresh follows the same cadence as the background poll and skips if one is already running.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(30_000)
                viewModel.refreshAlertsInBackground()
            }
        }
    }
}
/**
 * Standalone route for the History tab, used by the bottom navigation bar.
 */
@Composable
fun AlertHistoryRoute(
    uiState: com.example.pokemonalertsv2.ui.history.HistoryUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDateChanged: (String?) -> Unit,
    onTypeChanged: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    consumeError: () -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = {
                        Text(
                            text = "Alert History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            AnimatedRefreshIcon(
                                refreshing = uiState.isLoading,
                                contentDescription = stringResource(id = R.string.refresh_alerts)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AlertHistoryPage(
                uiState = uiState,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onDateChanged = onDateChanged,
                onTypeChanged = onTypeChanged,
                onSearchChanged = onSearchChanged,
                onAlertClick = { alert ->
                    val intent = AlertDetailActivity.createIntent(context, alert)
                    context.startActivity(intent)
                }
            )
        }
    }
}

internal fun PokemonAlert.typeKeys(): Set<String> {
    return type.orEmpty()
        .asSequence()
        .map { it.lowercase(Locale.ROOT) }
        .toSet()
}

internal fun AlertUiModel.hasCachedType(typeName: String): Boolean {
    return typeName.lowercase(Locale.ROOT) in typeKeys
}

internal fun hasForegroundLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

internal enum class HistoryAreaFilter(val label: String, val area: String?) {
    BOTH("Both", null),
    ALSBACH("Alsbach", "Alsbach"),
    DARMSTADT("Darmstadt", "Darmstadt");

    fun includes(alert: PokemonAlert): Boolean {
        val alertArea = alert.area?.trim()
        return area == null || alertArea.equals(area, ignoreCase = true)
    }
}

internal enum class FeedContentState { LOADING, EMPTY, CONTENT }

/**
 * The saved-preset controls, bundled so the feed parameter list does not grow by four more
 * loose lambdas on a composable that already takes thirty.
 */
@Immutable
data class FilterPresetControls(
    val presets: List<FilterPreset> = emptyList(),
    val onApply: (FilterPreset) -> Unit = {},
    val onSaveCurrent: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {}
)

@Composable
fun PokemonAlertsPage(
    uiState: AlertsUiState,
    dismissedAlertIds: Set<String>,
    presetControls: FilterPresetControls = FilterPresetControls(),
    maxWalkingMinutes: Int = TravelTime.NO_LIMIT,
    onMaxWalkingMinutesChange: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    onShareClick: (PokemonAlert) -> Unit,
    onSnoozeAlert: (PokemonAlert, Int) -> Unit,
    onDismissClick: (String) -> Unit,
    onUndoDismiss: (String) -> Unit,
    selectedArea: String,
    maxDistance: Int,
    defaultSnoozeMinutes: Int,
    sortPreference: SortPreference,
    selectedFilter: AlertFilter,
    onSelectedFilterChange: (AlertFilter) -> Unit,
    onSelectedAreaChange: (String) -> Unit,
    onMaxDistanceChange: (Int) -> Unit,
    onSortPreferenceChange: (SortPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationScope = rememberCoroutineScope()
    val walkingRouteRepository = remember { WalkingRouteRepository.getInstance() }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(hasForegroundLocationPermission(context))
    }
    var locationLookupComplete by remember { mutableStateOf(false) }
    var walkingRoutes by remember { mutableStateOf<Map<String, WalkingRouteInfo>>(emptyMap()) }
    var showDismissed by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var alertPendingSnooze by remember { mutableStateOf<PokemonAlert?>(null) }
    val haptic = LocalHapticFeedback.current
    val goDexMatches = rememberGoDexMatchResults(uiState.alerts)

    suspend fun refreshUserLocation() {
        val permissionGranted = hasForegroundLocationPermission(context)
        hasLocationPermission = permissionGranted
        userLocation = if (permissionGranted) {
            CachedLocationProvider.get(
                context = context,
                timeoutMs = 5_000,
                highAccuracy = true
            )?.takeIf { location ->
                validMapCoordinates(location.latitude, location.longitude) != null
            }
        } else {
            null
        }
        locationLookupComplete = true
    }

    // Expiration filtering only needs a coarse lifecycle-aware tick; visible countdown rows
    // subscribe to their own clock so unrelated screen content stays stable.
    val filterNow = rememberCountdownClock(30_000L).value

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val permissionGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasForegroundLocationPermission(context)
        hasLocationPermission = permissionGranted
        if (permissionGranted) {
            locationLookupComplete = false
            locationScope.launch { refreshUserLocation() }
        } else {
            userLocation = null
            locationLookupComplete = true
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            locationLookupComplete = false
            refreshUserLocation()
        }
    }

    LaunchedEffect(userLocation, uiState.alerts) {
        val location = userLocation
        walkingRoutes = if (location == null) {
            emptyMap()
        } else {
            walkingRouteRepository.getWalkingRoutes(
                location,
                uiState.alerts.filter { it.mapCoordinatesOrNull() != null }
            )
        }
    }

    val alertsWithDistance = remember(uiState.alerts, userLocation, walkingRoutes) {
        uiState.alerts.map { alert ->
            val distanceMeters: Float? = userLocation?.let { loc ->
                alert.mapCoordinatesOrNull()?.let { coordinates ->
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        loc.latitude,
                        loc.longitude,
                        coordinates.latitude,
                        coordinates.longitude,
                        results
                    )
                    results.getOrNull(0)?.takeUnless { it.isNaN() }
                }
            }
            val routeDisplayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
                straightLineDistanceMeters = distanceMeters,
                routeInfo = walkingRoutes[alert.uniqueId]
            )
            AlertUiModel(
                alert = alert, 
                distanceInfo = AlertDistanceInfo(
                    distanceMeters = routeDisplayInfo.effectiveDistanceMeters,
                    distanceText = routeDisplayInfo.distanceText,
                    walkingText = routeDisplayInfo.walkingText,
                    straightLineDistanceMeters = routeDisplayInfo.straightLineDistanceMeters,
                    routedWalkingDistanceMeters = routeDisplayInfo.routedDistanceMeters,
                    walkingDurationSeconds = routeDisplayInfo.walkingDurationSeconds,
                    source = routeDisplayInfo.source
                ),
                endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime),
                typeKeys = alert.typeKeys()
            )
        }
    }

    // Filter out expired and optionally dismissed alerts
    val activeAlerts = remember(
        alertsWithDistance,
        dismissedAlertIds,
        showDismissed,
        filterNow,
        selectedArea,
        maxDistance,
        maxWalkingMinutes
    ) {
        alertsWithDistance.filter { model ->
            val end = model.endMillis ?: Long.MAX_VALUE
            // Filter out expired, optionally include dismissed based on toggle
            val notExpired = end > filterNow
            val notDismissed = showDismissed || model.alert.uniqueId !in dismissedAlertIds
            val notInvalidated = !model.alert.isInvalidated
            
            // Area Filter
            val areaMatch = selectedArea == "All" || model.alert.area == selectedArea
            
            // Distance Filter (allow if maxDistance is 0 or if location is unknown)
            val distanceMatch = maxDistance == 0 || model.distanceInfo.distanceMeters == null || model.distanceInfo.distanceMeters <= maxDistance * 1000
            
            // Reachability on foot. Falls back to keeping the alert when no route is
            // available, so a routing outage cannot silently empty the feed.
            val reachable = TravelTime.isReachableWithin(
                walkingDurationSeconds = model.distanceInfo.walkingDurationSeconds,
                maxMinutes = maxWalkingMinutes
            )

            notExpired && notDismissed && notInvalidated && areaMatch && distanceMatch && reachable
        }
    }

    // Determine available filters based on active alerts content
    val availableFilters = remember(activeAlerts) {
        val filters = mutableSetOf(AlertFilter.ALL)
        
        if (activeAlerts.any { it.hasCachedType("Raid") }) {
            filters.add(AlertFilter.RAIDS)
        }
        if (activeAlerts.any { it.hasCachedType("Quest") }) {
            filters.add(AlertFilter.QUESTS)
        }
        if (activeAlerts.any { it.hasCachedType("Rare") || it.hasCachedType("Spawn") }) {
            filters.add(AlertFilter.RARES)
        }
        if (activeAlerts.any { it.hasCachedType("Hundo") }) {
            filters.add(AlertFilter.HUNDOS)
        }
        if (activeAlerts.any { it.hasCachedType("PvP") }) {
            filters.add(AlertFilter.PVP)
        }
        if (activeAlerts.any { it.hasCachedType("Nundo") }) {
            filters.add(AlertFilter.NUNDOS)
        }
        if (activeAlerts.any { it.hasCachedType("Kecleon") }) {
            filters.add(AlertFilter.KECLEON)
        }
        if (activeAlerts.any { it.hasCachedType("Rocket") }) {
            filters.add(AlertFilter.ROCKET)
        }
        if (activeAlerts.any { it.hasCachedType("WeatherChange") }) {
            filters.add(AlertFilter.WEATHER_CHANGE)
        }
        filters
    }

    // Auto-reset filter if current selection is invalid
    LaunchedEffect(availableFilters, selectedFilter) {
        if (selectedFilter != AlertFilter.ALL && selectedFilter !in availableFilters) {
            onSelectedFilterChange(AlertFilter.ALL)
        }
    }

    val filteredAlerts = remember(activeAlerts, selectedFilter, sortPreference, searchQuery) {
        var filtered = when (selectedFilter) {
            AlertFilter.ALL -> activeAlerts
            AlertFilter.RAIDS -> activeAlerts.filter { it.hasCachedType("Raid") }
            AlertFilter.QUESTS -> activeAlerts.filter { it.hasCachedType("Quest") }
            AlertFilter.RARES -> activeAlerts.filter { it.hasCachedType("Rare") || it.hasCachedType("Spawn") }
            AlertFilter.HUNDOS -> activeAlerts.filter { it.hasCachedType("Hundo") }
            AlertFilter.PVP -> activeAlerts.filter { it.hasCachedType("PvP") }
            AlertFilter.NUNDOS -> activeAlerts.filter { it.hasCachedType("Nundo") }
            AlertFilter.KECLEON -> activeAlerts.filter { it.hasCachedType("Kecleon") }
            AlertFilter.ROCKET -> activeAlerts.filter { it.hasCachedType("Rocket") }
            AlertFilter.WEATHER_CHANGE -> activeAlerts.filter { it.hasCachedType("WeatherChange") }
        }
        
        // Apply text search
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { model ->
                model.alert.matchesAlertSearch(searchQuery)
            }
        }
        when (sortPreference) {
            SortPreference.POSTED_TIME -> filtered.sortedWith(compareByDescending<AlertUiModel> { 
                it.alert.id?.toLong() ?: Long.MIN_VALUE
            }.thenByDescending { 
                it.endMillis ?: 0L
            })
            SortPreference.DISTANCE -> filtered.sortedWith(
                compareBy<AlertUiModel> {
                    if (it.distanceInfo.source == DistanceSource.ROUTED) 0 else 1
                }.thenBy {
                    it.distanceInfo.distanceMeters ?: Float.MAX_VALUE
                }
            )
            SortPreference.TIME_REMAINING -> filtered.sortedBy { 
                it.endMillis ?: Long.MAX_VALUE
            }
            SortPreference.NAME -> filtered.sortedBy { 
                it.alert.name.lowercase()
            }
        }
    }
    
    // Expired alerts are filtered by the coarse expiration tick above.

    Column(modifier = modifier.fillMaxSize()) {
    SyncStatusBanner(
        status = uiState.toSyncStatus(),
        onRetry = onRefresh
    )
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = {
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = rememberPullToRefreshState()
    ) {
        val contentState = when {
            uiState.isLoading && uiState.alerts.isEmpty() -> FeedContentState.LOADING
            uiState.alerts.isEmpty() -> FeedContentState.EMPTY
            else -> FeedContentState.CONTENT
        }
        val feedGridState = rememberLazyGridState()
        AnimatedContent(
            targetState = contentState,
            transitionSpec = { appFadeThrough() },
            contentKey = { it.name },
            label = "live_feed_state"
        ) { state ->
        when (state) {
            FeedContentState.LOADING -> LoadingState()
            FeedContentState.EMPTY -> AnimatedEmptyState(
                    title = "All caught up",
                    message = "No active alerts right now. Tap below to check again.",
                    ctaText = "Refresh feed",
                    onAction = onRefresh
                )
            FeedContentState.CONTENT -> AlertsList(
                gridState = feedGridState,
                filteredAlerts = filteredAlerts,
                goDexMatches = goDexMatches,
                selectedFilter = selectedFilter,
                sortPreference = sortPreference,
                showDismissed = showDismissed,
                dismissedAlertIds = dismissedAlertIds,
                onFilterChanged = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectedFilterChange(it)
                },
                onSortChanged = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSortPreferenceChange(it)
                },
                onShowDismissedChanged = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDismissed = it
                },
                onAlertSelected = onAlertSelected,
                onPipClick = { alert -> openAlertInPictureInPicture(context, alert) },
                onOpenMaps = { alert -> openMapForAlert(context, alert) },
                onShareClick = onShareClick,
                onSnoozeClick = { alert ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    alertPendingSnooze = alert
                },
                onDismissClick = { alertId ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismissClick(alertId)
                },
                onRestoreClick = { alertId ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUndoDismiss(alertId)
                },
                onRequestLocationPermission = {
                    if (hasForegroundLocationPermission(context)) {
                        locationLookupComplete = false
                        locationScope.launch { refreshUserLocation() }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                locationAvailable = hasLocationPermission && userLocation != null,
                locationPermissionGranted = hasLocationPermission,
                locationLookupComplete = locationLookupComplete,
                locationPrecisionInsufficient = userLocation?.let {
                    !it.hasAccuracy() || it.accuracy > 100f
                } == true,
                availableFilters = availableFilters,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                selectedArea = selectedArea,
                maxDistance = maxDistance,
                onClearAreaFilter = { onSelectedAreaChange("All") },
                onClearDistanceFilter = { onMaxDistanceChange(0) },
                onClearAllFilters = {
                    onSelectedFilterChange(AlertFilter.ALL)
                    showDismissed = false
                    searchQuery = ""
                    onSelectedAreaChange("All")
                    onMaxDistanceChange(0)
                    onMaxWalkingMinutesChange(TravelTime.NO_LIMIT)
                },
                presetControls = presetControls,
                maxWalkingMinutes = maxWalkingMinutes,
                onMaxWalkingMinutesChange = onMaxWalkingMinutesChange
            )
        }
        }
    }
    }

    alertPendingSnooze?.let { alert ->
        SnoozeDurationDialog(
            defaultMinutes = defaultSnoozeMinutes,
            onDismiss = { alertPendingSnooze = null },
            onConfirm = { minutes ->
                alertPendingSnooze = null
                onSnoozeAlert(alert, minutes)
            }
        )
    }
}

@Composable
internal fun SyncStatusBanner(
    status: SyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = status.alertsStatusMessage()
    val isProblem = status is SyncStatus.Cached || status is SyncStatus.Failed
    AnimatedContent(
        targetState = message to isProblem,
        transitionSpec = { appFadeThrough() },
        modifier = modifier.fillMaxWidth(),
        label = "alerts_sync_status"
    ) { (animatedMessage, animatedProblem) ->
        if (animatedMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (animatedProblem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (animatedProblem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(animatedMessage, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    if (animatedProblem) {
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlertsList(
    gridState: LazyGridState,
    filteredAlerts: List<AlertUiModel>,
    goDexMatches: Map<String, GoDexMatchResult>,
    selectedFilter: AlertFilter,
    sortPreference: SortPreference,
    showDismissed: Boolean,
    dismissedAlertIds: Set<String>,
    onFilterChanged: (AlertFilter) -> Unit,
    onSortChanged: (SortPreference) -> Unit,
    onShowDismissedChanged: (Boolean) -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    onPipClick: (PokemonAlert) -> Unit,
    onOpenMaps: (PokemonAlert) -> Unit,
    onShareClick: (PokemonAlert) -> Unit,
    onSnoozeClick: (PokemonAlert) -> Unit,
    onDismissClick: (String) -> Unit,
    onRestoreClick: (String) -> Unit,
    onRequestLocationPermission: () -> Unit,
    locationAvailable: Boolean,
    locationPermissionGranted: Boolean,
    locationLookupComplete: Boolean,
    locationPrecisionInsufficient: Boolean,
    availableFilters: Set<AlertFilter>,
    searchQuery: String = "",
    onSearchQueryChanged: (String) -> Unit = {},
    selectedArea: String = "All",
    maxDistance: Int = 0,
    onClearAreaFilter: () -> Unit = {},
    onClearDistanceFilter: () -> Unit = {},
    onClearAllFilters: () -> Unit = {},
    presetControls: FilterPresetControls = FilterPresetControls(),
    maxWalkingMinutes: Int = TravelTime.NO_LIMIT,
    onMaxWalkingMinutesChange: (Int) -> Unit = {}
) {
    val arrivalTracking = rememberArrivalTrackingUiController()
    val countdownClock = rememberCountdownClock()
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }
    val activeFilterCount = remember(
        selectedFilter,
        showDismissed,
        searchQuery,
        selectedArea,
        maxDistance,
        maxWalkingMinutes
    ) {
        listOf(
            selectedFilter != AlertFilter.ALL,
            showDismissed,
            searchQuery.isNotBlank(),
            selectedArea != "All",
            maxDistance > 0,
            maxWalkingMinutes > TravelTime.NO_LIMIT
        ).count { it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AlertListControls(
            visibleCount = filteredAlerts.size,
            activeFilterCount = activeFilterCount,
            searchExpanded = searchExpanded,
            onSearchExpandedChange = { searchExpanded = it },
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            sortPreference = sortPreference,
            onSortChanged = onSortChanged,
            onOpenFilters = { showFilterSheet = true },
            locationPrecisionInsufficient = locationPrecisionInsufficient,
            onRequestLocationPermission = onRequestLocationPermission,
            selectedFilter = selectedFilter,
            onFilterChanged = onFilterChanged,
            showDismissed = showDismissed,
            onShowDismissedChanged = onShowDismissedChanged,
            selectedArea = selectedArea,
            onClearAreaFilter = onClearAreaFilter,
            maxDistance = maxDistance,
            onClearDistanceFilter = onClearDistanceFilter,
            maxWalkingMinutes = maxWalkingMinutes,
            onClearWalkingFilter = { onMaxWalkingMinutesChange(TravelTime.NO_LIMIT) }
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
        val columns = if (maxWidth >= 840.dp) 2 else 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredAlerts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = alertEmptyStateMessage(
                                searchQuery = searchQuery,
                                selectedFilter = selectedFilter,
                                selectedArea = selectedArea,
                                maxDistance = maxDistance,
                                showDismissed = showDismissed,
                                locationAvailable = locationAvailable
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (activeFilterCount > 0) {
                            OutlinedButton(onClick = onClearAllFilters) { Text("Clear all filters") }
                        }
                    }
                }
            }

            gridItems(
                items = filteredAlerts,
                key = { it.alert.uniqueId },
                contentType = { "alert_card" }
            ) { model ->
            val isDismissed = model.alert.uniqueId in dismissedAlertIds
            // rememberUpdatedState ensures the lambda inside rememberSwipeToDismissBoxState
            // always reads the CURRENT value, even though the lambda itself is captured once.
            val currentIsDismissed by rememberUpdatedState(isDismissed)
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    when {
                        dismissValue == SwipeToDismissBoxValue.EndToStart && !currentIsDismissed -> {
                            onDismissClick(model.alert.uniqueId)
                        }
                        dismissValue == SwipeToDismissBoxValue.StartToEnd && currentIsDismissed -> {
                            onRestoreClick(model.alert.uniqueId)
                        }
                    }
                    false
                }
            )

            // Force-reset stale swipe state every time this card (re-)enters composition.
            // Without this, rememberSaveable restores EndToStart from a previous dismiss+undo
            // cycle, leaving the card stuck and the gesture handler locked.
            LaunchedEffect(Unit) {
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val showBg = dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                                 dismissState.targetValue != SwipeToDismissBoxValue.Settled
                    if (showBg) {
                        if (isDismissed) {
                            // Restore background - green with check icon
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                        shape = MaterialTheme.shapes.large
                                    )
                                    .padding(start = 24.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Restore",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            // Dismiss background - red with X icon
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        shape = MaterialTheme.shapes.large
                                    )
                                    .padding(end = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                },
                enableDismissFromStartToEnd = isDismissed,
                enableDismissFromEndToStart = !isDismissed,
                modifier = Modifier.animateItem()
            ) {
                // Progressive haptic feedback during swipe.
                // Observed through snapshotFlow rather than read in composition: reading
                // dismissState.progress directly recomposed this row on every frame of the
                // drag, and keying a LaunchedEffect on it relaunched a coroutine just as
                // often.
                val hapticFeedback = LocalHapticFeedback.current
                LaunchedEffect(dismissState) {
                    var lastThreshold = 0
                    snapshotFlow {
                        val progress = dismissState.progress
                        when {
                            progress >= 0.6f -> 2
                            progress >= 0.3f -> 1
                            else -> 0
                        }
                    }.collect { threshold ->
                        if (threshold > lastThreshold) {
                            when (threshold) {
                                1 -> hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                2 -> hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        lastThreshold = threshold
                    }
                }
                
                Box {
                    AlertCard(
                        alert = model.alert,
                        distanceInfo = model.distanceInfo,
                        goDexStatus = goDexMatches[model.alert.uniqueId]
                            ?: GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED),
                        countdownClock = countdownClock,
                        onOpenMaps = { onOpenMaps(model.alert) },
                        onShowDetails = { onAlertSelected(model.alert) },
                        onSecondaryAction = { action ->
                            when (action) {
                                AlertSecondaryAction.SNOOZE -> onSnoozeClick(model.alert)
                                AlertSecondaryAction.PICTURE_IN_PICTURE -> onPipClick(model.alert)
                                AlertSecondaryAction.SHARE -> onShareClick(model.alert)
                                AlertSecondaryAction.DISMISS -> onDismissClick(model.alert.uniqueId)
                                AlertSecondaryAction.RESTORE -> onRestoreClick(model.alert.uniqueId)
                            }
                        },
                        isGoing = arrivalTracking.isTracking(model.alert),
                        onGoingClick = if (model.alert.isEligibleArrivalDestination()) {
                            { arrivalTracking.onToggle(model.alert) }
                        } else {
                            null
                        }
                    )
                    // Dimmed overlay for dismissed alerts
                    if (isDismissed) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.large
                                )
                        )
                    }
                }
            }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Filter alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                FilterPresetsSection(
                    controls = presetControls,
                    currentFilter = selectedFilter,
                    currentSort = sortPreference,
                    currentArea = selectedArea,
                    currentMaxDistance = maxDistance,
                    onApplied = { showFilterSheet = false }
                )
                FilterRow(
                    selectedFilter = selectedFilter,
                    onFilterChanged = onFilterChanged,
                    locationAvailable = locationAvailable,
                    locationPermissionGranted = locationPermissionGranted,
                    locationLookupComplete = locationLookupComplete,
                    onRequestLocationPermission = onRequestLocationPermission,
                    availableFilters = availableFilters
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reachable on foot",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Uses real walking routes, not straight-line distance. " +
                            "Alerts with no route available are always shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TravelTime.PRESET_MINUTES, key = { it }) { minutes ->
                            FilterChip(
                                selected = maxWalkingMinutes == minutes,
                                onClick = { onMaxWalkingMinutesChange(minutes) },
                                label = { Text(TravelTime.label(minutes)) }
                            )
                        }
                    }
                }
                FilterChip(
                    selected = showDismissed,
                    onClick = { onShowDismissedChanged(!showDismissed) },
                    label = { Text("Show dismissed alerts") }
                )
                if (activeFilterCount > 0) {
                    TextButton(onClick = {
                        onClearAllFilters()
                        showFilterSheet = false
                    }) { Text("Clear all") }
                }
                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}
