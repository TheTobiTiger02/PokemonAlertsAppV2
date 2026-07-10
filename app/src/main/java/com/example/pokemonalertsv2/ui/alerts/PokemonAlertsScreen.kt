@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.ui.components.AnimatedEmptyState
import com.example.pokemonalertsv2.ui.components.ShimmerAlertCard
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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

    val onShareClick: (PokemonAlert) -> Unit = { alert ->
        scope.launch {
            AlertShareCard.share(context, alert)
        }
    }

    alertsUiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (showTopBar) {
                AlertsToolbar(
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.refreshAlerts()
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
                val selectedArea by viewModel.selectedArea.collectAsStateWithLifecycle(initialValue = "All")
                val maxDistance by viewModel.maxDistance.collectAsStateWithLifecycle(initialValue = 0)
                val defaultSnoozeMinutes by viewModel.snoozeDuration.collectAsStateWithLifecycle(initialValue = 10)
                
                PokemonAlertsPage(
                    uiState = alertsUiState,
                    dismissedAlertIds = viewModel.dismissedAlertIds.collectAsStateWithLifecycle(initialValue = emptySet()).value,
                    selectedArea = selectedArea,
                    maxDistance = maxDistance,
                    defaultSnoozeMinutes = defaultSnoozeMinutes,
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

    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            consumeError()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Alert History",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Browse past alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        FilledIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRefresh()
                            },
                            shape = CircleShape
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_refresh),
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

private fun PokemonAlert.typeKeys(): Set<String> {
    return type.orEmpty()
        .asSequence()
        .map { it.lowercase(Locale.ROOT) }
        .toSet()
}

private fun AlertUiModel.hasCachedType(typeName: String): Boolean {
    return typeName.lowercase(Locale.ROOT) in typeKeys
}

private enum class HistoryAreaFilter(val label: String, val area: String?) {
    BOTH("Both", null),
    ALSBACH("Alsbach", "Alsbach"),
    DARMSTADT("Darmstadt", "Darmstadt");

    fun includes(alert: PokemonAlert): Boolean {
        val alertArea = alert.area?.trim()
        return area == null || alertArea.equals(area, ignoreCase = true)
    }
}

@Composable
fun PokemonAlertsPage(
    uiState: AlertsUiState,
    dismissedAlertIds: Set<String>,
    onRefresh: () -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    onShareClick: (PokemonAlert) -> Unit,
    onSnoozeAlert: (PokemonAlert, Int) -> Unit,
    onDismissClick: (String) -> Unit,
    onUndoDismiss: (String) -> Unit,
    selectedArea: String,
    maxDistance: Int,
    defaultSnoozeMinutes: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var walkingRoutes by remember { mutableStateOf<Map<String, WalkingRouteInfo>>(emptyMap()) }
    var selectedFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var sortPreference by rememberSaveable { mutableStateOf(SortPreference.POSTED_TIME) }
    var showDismissed by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var alertPendingSnooze by remember { mutableStateOf<PokemonAlert?>(null) }
    val haptic = LocalHapticFeedback.current

    // Expiration filtering only needs a coarse tick; visible countdown rows update themselves.
    var filterNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            filterNow = System.currentTimeMillis()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            userLocation = getLastKnownLocation(context)
        }
    }

    LaunchedEffect(Unit) {
        userLocation = getLastKnownLocation(context)
    }

    LaunchedEffect(userLocation, uiState.alerts) {
        val location = userLocation
        walkingRoutes = if (location == null) {
            emptyMap()
        } else {
            WalkingRouteUtils.getWalkingRoutes(location, uiState.alerts)
        }
    }

    val alertsWithDistance = remember(uiState.alerts, userLocation, walkingRoutes) {
        uiState.alerts.map { alert ->
            val distanceMeters: Float? = userLocation?.let { loc ->
                val latitude = alert.latitude
                val longitude = alert.longitude
                if (latitude != null && longitude != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(loc.latitude, loc.longitude, latitude, longitude, results)
                    results.getOrNull(0)?.takeUnless { it.isNaN() }
                } else {
                    null
                }
            }
            val routeDisplayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
                straightLineDistanceMeters = distanceMeters,
                routeInfo = walkingRoutes[alert.uniqueId]
            )
            AlertUiModel(
                alert = alert, 
                distanceInfo = AlertDistanceInfo(
                    distanceMeters = routeDisplayInfo.straightLineDistanceMeters,
                    distanceText = routeDisplayInfo.distanceText,
                    walkingText = routeDisplayInfo.walkingText
                ),
                endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime),
                typeKeys = alert.typeKeys()
            )
        }
    }

    // Filter out expired and optionally dismissed alerts
    val activeAlerts = remember(alertsWithDistance, dismissedAlertIds, showDismissed, filterNow, selectedArea, maxDistance) {
        alertsWithDistance.filter { model ->
            val end = model.endMillis ?: Long.MAX_VALUE
            // Filter out expired, optionally include dismissed based on toggle
            val notExpired = end > filterNow
            val notDismissed = showDismissed || model.alert.uniqueId !in dismissedAlertIds
            
            // Area Filter
            val areaMatch = selectedArea == "All" || model.alert.area == selectedArea
            
            // Distance Filter (allow if maxDistance is 0 or if location is unknown)
            val distanceMatch = maxDistance == 0 || model.distanceInfo.distanceMeters == null || model.distanceInfo.distanceMeters <= maxDistance * 1000
            
            notExpired && notDismissed && areaMatch && distanceMatch
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
            selectedFilter = AlertFilter.ALL
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
            val query = searchQuery.trim().lowercase()
            filtered = filtered.filter { model ->
                model.alert.name.lowercase().contains(query) ||
                    (model.alert.pokemon?.lowercase()?.contains(query) == true) ||
                    (model.alert.cleanPokemonName.lowercase().contains(query)) ||
                    (model.alert.locationDisplay?.lowercase()?.contains(query) == true)
            }
        }
        
        // Sort based on user preference
        when (sortPreference) {
            SortPreference.POSTED_TIME -> filtered.sortedWith(compareByDescending<AlertUiModel> { 
                it.alert.id?.toLong() ?: Long.MIN_VALUE
            }.thenByDescending { 
                it.endMillis ?: 0L
            })
            SortPreference.DISTANCE -> filtered.sortedBy { 
                it.distanceInfo.distanceMeters ?: Float.MAX_VALUE 
            }
            SortPreference.TIME_REMAINING -> filtered.sortedBy { 
                it.endMillis ?: Long.MAX_VALUE
            }
            SortPreference.NAME -> filtered.sortedBy { 
                it.alert.name.lowercase()
            }
        }
    }
    
    // Expired alerts are filtered by the coarse expiration tick above.

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = rememberPullToRefreshState()
    ) {
        when {
            uiState.isLoading && uiState.alerts.isEmpty() -> LoadingState()
            uiState.alerts.isEmpty() && !uiState.isLoading -> AnimatedEmptyState(
                    title = "All caught up",
                    message = "No active alerts right now. Tap below to check again.",
                    ctaText = "Refresh feed",
                    onAction = onRefresh
                )
            else -> AlertsList(
                filteredAlerts = filteredAlerts,
                selectedFilter = selectedFilter,
                sortPreference = sortPreference,
                showDismissed = showDismissed,
                dismissedAlertIds = dismissedAlertIds,
                onFilterChanged = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedFilter = it 
                },
                onSortChanged = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sortPreference = it
                },
                onShowDismissedChanged = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDismissed = it
                },
                onAlertSelected = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAlertSelected(it) 
                },
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
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                alertsAvailable = uiState.alerts.isNotEmpty(),
                availableFilters = availableFilters,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it }
            )
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
private fun AlertsList(
    filteredAlerts: List<AlertUiModel>,
    selectedFilter: AlertFilter,
    sortPreference: SortPreference,
    showDismissed: Boolean,
    dismissedAlertIds: Set<String>,
    onFilterChanged: (AlertFilter) -> Unit,
    onSortChanged: (SortPreference) -> Unit,
    onShowDismissedChanged: (Boolean) -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    onOpenMaps: (PokemonAlert) -> Unit,
    onShareClick: (PokemonAlert) -> Unit,
    onSnoozeClick: (PokemonAlert) -> Unit,
    onDismissClick: (String) -> Unit,
    onRestoreClick: (String) -> Unit,
    onRequestLocationPermission: () -> Unit,
    alertsAvailable: Boolean,
    availableFilters: Set<AlertFilter>,
    searchQuery: String = "",
    onSearchQueryChanged: (String) -> Unit = {}
) {
    val countdownNow = rememberCountdownNow()
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 840.dp) 2 else 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ALERTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${filteredAlerts.size} active alerts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SortingButton(
                            currentSort = sortPreference,
                            onSortChanged = onSortChanged
                        )
                        IconButton(onClick = { controlsExpanded = !controlsExpanded }) {
                            Icon(
                                imageVector = if (controlsExpanded)
                                    Icons.Filled.KeyboardArrowUp
                                else
                                    Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (controlsExpanded)
                                    "Collapse alert controls"
                                else
                                    "Expand alert controls"
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = controlsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterRow(
                    selectedFilter = selectedFilter,
                    onFilterChanged = onFilterChanged,
                    locationAvailable = alertsAvailable,
                    onRequestLocationPermission = onRequestLocationPermission,
                    availableFilters = availableFilters
                )
                
                // Show dismissed toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showDismissed,
                        onClick = { onShowDismissedChanged(!showDismissed) },
                        label = { Text("Show Dismissed") },
                        leadingIcon = if (showDismissed) {
                            { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
                
                // Search bar
                AlertSearchBar(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    placeholder = "Search Pokémon…"
                )
                    }
                }
                    }
                }
            }

            if (filteredAlerts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "No alerts found for filter",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
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
                },
                enableDismissFromStartToEnd = isDismissed,
                enableDismissFromEndToStart = !isDismissed,
                modifier = Modifier.animateItem()
            ) {
                // Progressive haptic feedback during swipe
                val progress = dismissState.progress
                val hapticFeedback = LocalHapticFeedback.current
                var lastHapticThreshold by remember { mutableStateOf(0) }
                
                LaunchedEffect(progress) {
                    val currentThreshold = when {
                        progress >= 0.6f -> 2
                        progress >= 0.3f -> 1
                        else -> 0
                    }
                    if (currentThreshold > lastHapticThreshold) {
                        when (currentThreshold) {
                            1 -> hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            2 -> hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    lastHapticThreshold = currentThreshold
                }
                
                Box {
                    AlertCard(
                        alert = model.alert,
                        distanceInfo = model.distanceInfo,
                        nowMillis = countdownNow,
                        onOpenMaps = { onOpenMaps(model.alert) },
                        onShowDetails = { onAlertSelected(model.alert) },
                        onShareClick = { onShareClick(model.alert) },
                        onSnoozeClick = { onSnoozeClick(model.alert) }
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
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun AlertSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun SortingButton(
    currentSort: SortPreference,
    onSortChanged: (SortPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        ElevatedAssistChip(
            onClick = { expanded = true },
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Sort",
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when (currentSort) {
                            SortPreference.POSTED_TIME -> "Posted"
                            SortPreference.DISTANCE -> "Distance"
                            SortPreference.TIME_REMAINING -> "Time"
                            SortPreference.NAME -> "Name"
                        }
                    )
                }
            },
            colors = AssistChipDefaults.elevatedAssistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Sort by Posted Time") },
                onClick = {
                    onSortChanged(SortPreference.POSTED_TIME)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by Distance") },
                onClick = {
                    onSortChanged(SortPreference.DISTANCE)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by Time Remaining") },
                onClick = {
                    onSortChanged(SortPreference.TIME_REMAINING)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by Name") },
                onClick = {
                    onSortChanged(SortPreference.NAME)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.Star, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: AlertFilter,
    onFilterChanged: (AlertFilter) -> Unit,
    locationAvailable: Boolean,
    onRequestLocationPermission: () -> Unit,
    availableFilters: Set<AlertFilter>
) {
    Column {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, _ ->
                        // Consume horizontal drag to prevent parent pager from intercepting
                    }
                }
        ) {
            items(AlertFilter.values().filter { it in availableFilters }) { filter ->
                ElevatedAssistChip(
                    onClick = { onFilterChanged(filter) },
                    label = { Text(text = filter.label) },
                    colors = AssistChipDefaults.elevatedAssistChipColors(
                        containerColor = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        labelColor = if (selectedFilter == filter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedFilter == filter) Color.Transparent else MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        AnimatedVisibility(visible = !locationAvailable) {
            TextButton(onClick = onRequestLocationPermission) {
                Text(
                    text = stringResource(id = R.string.alerts_nearby_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryAreaFilterRow(
    selectedFilter: HistoryAreaFilter,
    onFilterChanged: (HistoryAreaFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, _ ->
                    // Consume horizontal drag to prevent parent pager from intercepting
                }
            }
    ) {
        items(HistoryAreaFilter.entries.toList()) { filter ->
            ElevatedAssistChip(
                onClick = { onFilterChanged(filter) },
                label = { Text(text = filter.label) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = AssistChipDefaults.elevatedAssistChipColors(
                    containerColor = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    },
                    labelColor = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    leadingIconContentColor = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selectedFilter == filter) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            )
        }
    }
}

@Composable
private fun AlertsToolbar(
    onRefresh: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.alerts_toolbar_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            FilledIconButton(onClick = onRefresh, shape = CircleShape) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
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

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            ShimmerAlertCard()
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_placeholder),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(id = R.string.alerts_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.no_alerts_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(onClick = onRefresh, shape = RoundedCornerShape(18.dp)) {
            Text(text = stringResource(id = R.string.alerts_empty_cta))
        }
    }
}

@Composable
private fun AlertHistoryPage(
    uiState: com.example.pokemonalertsv2.ui.history.HistoryUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDateChanged: (String?) -> Unit,
    onTypeChanged: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onAlertClick: (PokemonAlert) -> Unit
) {
    val context = LocalContext.current
    var selectedTypeFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var selectedAreaFilter by rememberSaveable { mutableStateOf(HistoryAreaFilter.BOTH) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var sortPreference by rememberSaveable { mutableStateOf(SortPreference.POSTED_TIME) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        userLocation = getLastKnownLocation(context)
    }

    val alertEndTimes = remember(uiState.alerts) {
        uiState.alerts.associate { alert ->
            alert.uniqueId to TimeUtils.parseEndTimeToMillis(alert.endTime)
        }
    }

    // Trigger pagination when the user scrolls near the bottom of the list.
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 5 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !uiState.isLoading && !uiState.isLoadingMore && uiState.canLoadMore) {
            onLoadMore()
        }
    }

    // Always show every type filter — server has alerts of all types but we
    // only load one page at a time, so deriving chips from loaded items is incomplete.
    val availableFilters = remember { AlertFilter.entries.toSet() }

    val filteredAlerts = remember(uiState.alerts, selectedAreaFilter, sortPreference, userLocation) {
        // Type filtering is now server-side — uiState.alerts already contains
        // only the selected type/search result (or all types when no filter is active).
        var filtered = uiState.alerts.filter { selectedAreaFilter.includes(it) }
        
        // Sort based on user preference
        when (sortPreference) {
            SortPreference.POSTED_TIME -> filtered.sortedWith(compareByDescending<PokemonAlert> { 
                // Higher ID = newer alert. Alerts without ID go to the end
                it.id?.toLong() ?: Long.MIN_VALUE
            }.thenByDescending { 
                // Secondary sort by end time for alerts without ID
                alertEndTimes[it.uniqueId] ?: 0L
            })
            SortPreference.DISTANCE -> {
                userLocation?.let { loc ->
                    filtered.sortedBy { alert ->
                        val results = FloatArray(1)
                        Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude ?: 0.0, alert.longitude ?: 0.0, results)
                        results.getOrNull(0)?.takeUnless { it.isNaN() } ?: Float.MAX_VALUE
                    }
                } ?: filtered.sortedWith(compareByDescending<PokemonAlert> { 
                    it.id?.toLong() ?: Long.MIN_VALUE
                }.thenByDescending { 
                    alertEndTimes[it.uniqueId] ?: 0L
                })
            }
            SortPreference.TIME_REMAINING -> filtered.sortedBy { 
                alertEndTimes[it.uniqueId] ?: Long.MAX_VALUE
            }
            SortPreference.NAME -> filtered.sortedBy { 
                it.name.lowercase()
            }
        }
    }
    
    // Statistics: prefer /api/stats/total scoped to the active date, fall back to local counting.
    val statistics = remember(
        filteredAlerts,
        uiState.totalStats,
        uiState.totalStatsDate,
        uiState.selectedDate,
        uiState.selectedType,
        uiState.searchQuery,
        uiState.totalServerCount,
        selectedAreaFilter
    ) {
        val stats = uiState.totalStats.takeIf { uiState.totalStatsDate == uiState.selectedDate }
        val serverTotal = uiState.totalServerCount
        val byType = stats?.byType ?: emptyMap()
        val isAreaScoped = selectedAreaFilter != HistoryAreaFilter.BOTH
        val canUseServerStats = stats != null && !isAreaScoped && uiState.searchQuery.isBlank() && byType.isNotEmpty()

        fun emptyStatistics(total: Int, today: Int = 0) = mutableMapOf(
            "total" to total,
            "today" to today,
            "raids" to 0,
            "quests" to 0,
            "rares" to 0,
            "hundos" to 0,
            "pvp" to 0,
            "nundos" to 0,
            "rocket" to 0,
            "kecleon" to 0,
            "other" to 0
        )

        fun serverBreakdown(total: Int, today: Int = 0) = emptyStatistics(total, today).apply {
            this["raids"] = byType["Raid"] ?: 0
            this["quests"] = byType["Quest"] ?: 0
            this["rares"] = byType["Rare"] ?: 0
            this["hundos"] = byType["Hundo"] ?: 0
            this["pvp"] = byType["PvP"] ?: 0
            this["nundos"] = byType["Nundo"] ?: 0
            this["rocket"] = byType["Rocket"] ?: 0
            this["kecleon"] = byType["Kecleon"] ?: 0
        }

        fun serverCountForSelectedType(type: String): Int? = when (type) {
            "Raid" -> byType["Raid"]
            "Quest" -> byType["Quest"]
            "Rare" -> byType["Rare"]
            "Hundo" -> byType["Hundo"]
            "PvP" -> byType["PvP"]
            "Nundo" -> byType["Nundo"]
            "Rocket" -> byType["Rocket"]
            "Kecleon" -> byType["Kecleon"]
            "WeatherChange" -> byType["WeatherChange"] ?: byType["Weather"]
            else -> null
        }

        fun putSelectedTypeCount(target: MutableMap<String, Int>, type: String, count: Int) {
            when (type) {
                "Raid" -> target["raids"] = count
                "Quest" -> target["quests"] = count
                "Rare" -> target["rares"] = count
                "Hundo" -> target["hundos"] = count
                "PvP" -> target["pvp"] = count
                "Nundo" -> target["nundos"] = count
                "Rocket" -> target["rocket"] = count
                "Kecleon" -> target["kecleon"] = count
                else -> target["other"] = count
            }
        }

        // Local breakdown from loaded alerts (always computed as fallback)
        var raids = 0; var quests = 0; var rares = 0; var hundos = 0
        var pvp = 0; var nundos = 0; var rocket = 0; var kecleon = 0; var other = 0
        filteredAlerts.forEach { alert ->
            var categorized = false
            if (alert.hasType("Raid")) { raids++; categorized = true }
            if (alert.hasType("Quest")) { quests++; categorized = true }
            if (alert.hasType("Rare") || alert.hasType("Spawn")) { rares++; categorized = true }
            if (alert.hasType("Hundo")) { hundos++; categorized = true }
            if (alert.hasType("PvP")) { pvp++; categorized = true }
            if (alert.hasType("Nundo")) { nundos++; categorized = true }
            if (alert.hasType("Rocket")) { rocket++; categorized = true }
            if (alert.hasType("Kecleon")) { kecleon++; categorized = true }
            if (!categorized) other++
        }

        when {
            canUseServerStats && uiState.selectedType == null -> {
                serverBreakdown(
                    total = stats?.totalAlerts ?: serverTotal,
                    today = if (uiState.selectedDate == null) stats?.totalToday ?: 0 else 0
                )
            }
            canUseServerStats && uiState.selectedType != null -> {
                val selectedType = uiState.selectedType
                val selectedTypeTotal = serverCountForSelectedType(selectedType) ?: serverTotal
                emptyStatistics(total = if (serverTotal > 0) serverTotal else selectedTypeTotal).apply {
                    putSelectedTypeCount(this, selectedType, selectedTypeTotal)
                }
            }
            else -> {
                mapOf(
                    "total" to if (!isAreaScoped && serverTotal > 0) serverTotal else filteredAlerts.size,
                    "today" to 0,
                    "raids" to raids,
                    "quests" to quests,
                    "rares" to rares,
                    "hundos" to hundos,
                    "pvp" to pvp,
                    "nundos" to nundos,
                    "rocket" to rocket,
                    "kecleon" to kecleon,
                    "other" to other
                )
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = rememberPullToRefreshState()
    ) {
        val countdownNow = rememberCountdownNow()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = if (maxWidth >= 840.dp) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sort & Filter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SortingButton(
                            currentSort = sortPreference,
                            onSortChanged = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sortPreference = it
                            }
                        )
                    }
                    FilterRow(
                        selectedFilter = selectedTypeFilter,
                        onFilterChanged = { filter ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTypeFilter = filter
                            // Map the UI filter enum to the server-side type string
                            val apiType = when (filter) {
                                AlertFilter.ALL -> null
                                AlertFilter.RAIDS -> "Raid"
                                AlertFilter.QUESTS -> "Quest"
                                AlertFilter.RARES -> "Rare"
                                AlertFilter.HUNDOS -> "Hundo"
                                AlertFilter.PVP -> "PvP"
                                AlertFilter.NUNDOS -> "Nundo"
                                AlertFilter.KECLEON -> "Kecleon"
                                AlertFilter.ROCKET -> "Rocket"
                                AlertFilter.WEATHER_CHANGE -> "WeatherChange"
                            }
                            onTypeChanged(apiType)
                        },
                        locationAvailable = false,
                        onRequestLocationPermission = { },
                        availableFilters = availableFilters
                    )

                    HistoryAreaFilterRow(
                        selectedFilter = selectedAreaFilter,
                        onFilterChanged = { filter ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedAreaFilter = filter
                        }
                    )

                    // Search bar for history
                    AlertSearchBar(
                        query = uiState.searchQuery,
                        onQueryChanged = onSearchChanged,
                        placeholder = "Search history…"
                    )
                }
            }
            
            // Date Filter Button
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val calendar = java.util.Calendar.getInstance()
                            if (selectedDateMillis != null) {
                                calendar.timeInMillis = selectedDateMillis!!
                            }
                            DatePickerDialog(
                                context,
                                { _: DatePicker, year: Int, month: Int, day: Int ->
                                    val selectedCalendar = java.util.Calendar.getInstance()
                                    selectedCalendar.set(year, month, day)
                                    selectedDateMillis = selectedCalendar.timeInMillis
                                    // Format as YYYY-MM-DD for the server
                                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                                    onDateChanged(dateStr)
                                },
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH),
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (selectedDateMillis != null) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Filter by Date",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedDateMillis != null) {
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
                                String.format(
                                    "%02d/%02d/%04d",
                                    cal.get(java.util.Calendar.DAY_OF_MONTH),
                                    cal.get(java.util.Calendar.MONTH) + 1,
                                    cal.get(java.util.Calendar.YEAR)
                                )
                            } else {
                                "Select Date"
                            }
                        )
                    }
                    
                    if (selectedDateMillis != null) {
                        FilledIconButton(
                            onClick = {
                                selectedDateMillis = null
                                onDateChanged(null) // clear server-side date filter
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Date Filter",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Statistics Card
            item(span = { GridItemSpan(maxLineSpan) }) {
                var isExpanded by rememberSaveable { mutableStateOf(true) }
                
                val dateText = if (selectedDateMillis != null) {
                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
                    String.format(
                        "%02d/%02d/%04d",
                        calendar.get(java.util.Calendar.DAY_OF_MONTH),
                        calendar.get(java.util.Calendar.MONTH) + 1,
                        calendar.get(java.util.Calendar.YEAR)
                    )
                } else {
                    "All Time"
                }
                val statsScopeText = if (selectedAreaFilter == HistoryAreaFilter.BOTH) {
                    dateText
                } else {
                    "${selectedAreaFilter.label} - $dateText"
                }
                 
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Statistics for $statsScopeText",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${statistics["total"]} total alerts" +
                                        if ((statistics["today"] ?: 0) > 0) " · ${statistics["today"]} today" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Expandable breakdown
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if ((statistics["raids"] ?: 0) > 0) {
                                    StatRow("Raids", statistics["raids"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["quests"] ?: 0) > 0) {
                                    StatRow("Quests", statistics["quests"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["rares"] ?: 0) > 0) {
                                    StatRow("Rare", statistics["rares"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["hundos"] ?: 0) > 0) {
                                    StatRow("Hundos", statistics["hundos"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["pvp"] ?: 0) > 0) {
                                    StatRow("PvP", statistics["pvp"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["nundos"] ?: 0) > 0) {
                                    StatRow("Nundos", statistics["nundos"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["rocket"] ?: 0) > 0) {
                                    StatRow("Rocket", statistics["rocket"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["kecleon"] ?: 0) > 0) {
                                    StatRow("Kecleon", statistics["kecleon"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                                if ((statistics["other"] ?: 0) > 0) {
                                    StatRow("Other", statistics["other"] ?: 0, MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Shimmer placeholders while the first page is loading
            if (uiState.isLoading && uiState.alerts.isEmpty()) {
                repeat(3) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ShimmerAlertCard() }
                }
            } else if (filteredAlerts.isEmpty() && !uiState.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedEmptyState(
                        title = "No history found",
                        message = "No alerts match your search or filters. Try changing the search, area, date, or type."
                    )
                }
            }

            gridItems(
                items = filteredAlerts,
                key = { it.uniqueId },
                contentType = { "alert_card" }
            ) { alert ->
                AlertCard(
                    alert = alert,
                    distanceInfo = AlertDistanceInfo(null, null, null),
                    nowMillis = countdownNow,
                    onOpenMaps = { openMapForAlert(context, alert) },
                    onShowDetails = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAlertClick(alert) 
                    },
                    onShareClick = {
                        scope.launch {
                            AlertShareCard.share(context, alert)
                        }
                    }
                )
            }

            // Loading indicator while fetching the next page
            if (uiState.isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Builds a styled HTML page for sharing an alert that can be opened in any browser.
 * Uses dark theme styling matching the app's Midnight Sky theme.
 */
private fun buildAlertShareHtml(alert: PokemonAlert): String {
    val title = formatAlertTitle(alert)
    val imageUrl = alert.imageUrl ?: alert.thumbnailUrl ?: ""
    val mapsUrl = alert.googleMapsUri.toString()
    
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pokemon Alert: $title</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #0A0A0F;
            min-height: 100vh;
            padding: 20px;
            color: #E2E8F0;
        }
        .card {
            max-width: 480px;
            margin: 0 auto;
            background: rgba(18, 18, 24, 0.95);
            border-radius: 24px;
            overflow: hidden;
            box-shadow: 0 20px 40px rgba(0,0,0,0.6);
            border: 1px solid rgba(255, 107, 53, 0.15);
        }
        .image-container {
            position: relative;
            height: 280px;
            background: linear-gradient(135deg, #1F120E 0%, #121218 100%);
        }
        .image-container img {
            width: 100%;
            height: 100%;
            object-fit: contain;
            background: linear-gradient(135deg, #1F120E 0%, #121218 100%);
        }
        .content { padding: 24px; }
        h1 {
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 16px;
            background: linear-gradient(90deg, #FF6B35, #FFA809);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }
        .stats {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 12px;
            margin-bottom: 20px;
        }
        .stat {
            background: rgba(255, 107, 53, 0.1);
            padding: 12px;
            border-radius: 12px;
            text-align: center;
            border: 1px solid rgba(255, 107, 53, 0.15);
        }
        .stat-value { font-size: 20px; font-weight: 700; color: #FFA809; }
        .stat-label { font-size: 12px; color: #94A3B8; margin-top: 4px; }
        .time-badge {
            display: inline-block;
            background: linear-gradient(90deg, #FF6B35, #D62828);
            color: white;
            padding: 8px 16px;
            border-radius: 20px;
            font-weight: 600;
            margin-bottom: 20px;
        }
        .location { color: #94A3B8; font-size: 14px; margin-bottom: 16px; }
        .maps-btn {
            display: block;
            width: 100%;
            background: linear-gradient(90deg, #FF6B35, #FFA809);
            color: black;
            text-decoration: none;
            padding: 16px;
            border-radius: 12px;
            text-align: center;
            font-weight: 700;
            font-size: 16px;
        }
        .maps-btn:hover { opacity: 0.9; }
        .footer {
            text-align: center;
            margin-top: 20px;
            color: #64748B;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="image-container">
            ${if (imageUrl.isNotBlank()) "<img src=\"$imageUrl\" alt=\"$title\" onerror=\"this.style.display='none'\">" else ""}
        </div>
        <div class="content">
            <h1>$title</h1>
            <div class="time-badge">⏱ Ends: ${alert.endTime}</div>
            <div class="stats">
                ${alert.formattedIv?.let { "<div class='stat'><div class='stat-value'>$it</div><div class='stat-label'>IV</div></div>" } ?: ""}
                ${alert.cp?.let { "<div class='stat'><div class='stat-value'>$it</div><div class='stat-label'>CP</div></div>" } ?: ""}
                ${alert.level?.let { val v = if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString(); "<div class='stat'><div class='stat-value'>$v</div><div class='stat-label'>Level</div></div>" } ?: ""}
            </div>
            ${alert.locationDisplay?.let { "<div class='location'>📍 $it</div>" } ?: ""}
            <a href="$mapsUrl" class="maps-btn">📍 Open in Google Maps</a>
        </div>
    </div>
    <div class="footer">Shared from Pokemon Alerts</div>
</body>
</html>
    """.trimIndent()
}
