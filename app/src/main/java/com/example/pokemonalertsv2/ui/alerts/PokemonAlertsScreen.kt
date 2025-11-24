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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.pokemonalertsv2.ui.components.ShimmerAlertCard
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import com.example.pokemonalertsv2.ui.theme.EmberGradientEnd
import com.example.pokemonalertsv2.ui.theme.EmberGradientStart
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class AlertFilter(val label: String) {
    ALL("All"),
    RAIDS("Raids"),
    QUESTS("Quests"),
    SPAWNS("Spawns"),
    HUNDOS("Hundos"),
    PVP("PvP"),
    NUNDOS("Nundos"),
    ROCKET("Rocket"),
    KECLEON("Kecleon")
}

@Composable
fun PokemonAlertsRoute(
    viewModel: PokemonAlertsViewModel,
    historyViewModel: AlertHistoryViewModel,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val alertsUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyUiState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    val onShareClick: (PokemonAlert) -> Unit = { alert ->
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Pokemon Alert: ${alert.name}")
            val text = "Check out this ${alert.name}!\nEnds at: ${alert.endTime}\n${alert.googleMapsUri}"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Alert"))
    }

    alertsUiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    historyUiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            historyViewModel.consumeError()
        }
    }

    val containerGradient = remember {
        Brush.verticalGradient(
            listOf(
                AuroraGradientStart,
                AuroraGradientMid,
                AuroraGradientEnd.copy(alpha = 0.85f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerGradient)
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState()
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    AuroraToolbar(
                        onRefresh = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (pagerState.currentPage == 0) {
                                viewModel.refreshAlerts()
                            } else {
                                historyViewModel.fetchHistory()
                            }
                        },
                        onOpenMap = { context.startActivity(Intent(context, AlertsMapActivity::class.java)) },
                        onSettingsClick = onSettingsClick,
                        scrollBehavior = scrollBehavior
                    )
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        indicator = { tabPositions ->
                            if (pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text("Active Alerts", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text("History", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> PokemonAlertsPage(
                        uiState = alertsUiState,
                        onRefresh = viewModel::refreshAlerts,
                        onAlertSelected = { alert ->
                            val intent = AlertDetailActivity.createIntent(context, alert)
                            context.startActivity(intent)
                        },
                        onShareClick = onShareClick
                    )
                    1 -> AlertHistoryPage(
                        uiState = historyUiState,
                        onRefresh = historyViewModel::fetchHistory,
                        onAlertClick = { alert ->
                            val intent = AlertDetailActivity.createIntent(context, alert)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(30_000)
                if (pagerState.currentPage == 0) {
                    viewModel.refreshAlerts()
                }
            }
        }
    }
}

@Composable
fun PokemonAlertsPage(
    uiState: AlertsUiState,
    onRefresh: () -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    onShareClick: (PokemonAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    val haptic = LocalHapticFeedback.current

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

    val alertsWithDistance = remember(uiState.alerts, userLocation) {
        uiState.alerts.map { alert ->
            val distanceMeters: Float? = userLocation?.let { loc ->
                val results = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude, alert.longitude, results)
                results.getOrNull(0)?.takeUnless { it.isNaN() }
            }
            val distanceText = distanceMeters?.let { formatDistance(it) }
            val walkingText = distanceMeters?.let { formatWalkingTime(it) }
            AlertUiModel(
                alert = alert, 
                distanceInfo = AlertDistanceInfo(distanceMeters, distanceText, walkingText)
            )
        }
    }

    // Filter out expired alerts first
    val activeAlerts = remember(alertsWithDistance) {
        val now = System.currentTimeMillis()
        alertsWithDistance.filter { model ->
            val end = TimeUtils.parseEndTimeToMillis(model.alert.endTime) ?: Long.MAX_VALUE
            end > now
        }
    }

    // Determine available filters based on active alerts content
    val availableFilters = remember(activeAlerts) {
        val filters = mutableSetOf(AlertFilter.ALL)
        
        if (activeAlerts.any { it.alert.type?.equals("Raid", ignoreCase = true) == true }) {
            filters.add(AlertFilter.RAIDS)
        }
        if (activeAlerts.any { it.alert.type?.equals("Quest", ignoreCase = true) == true }) {
            filters.add(AlertFilter.QUESTS)
        }
        if (activeAlerts.any { 
            val t = it.alert.type
            t?.equals("Rare", ignoreCase = true) == true || t?.equals("Spawn", ignoreCase = true) == true 
        }) {
            filters.add(AlertFilter.SPAWNS)
        }
        if (activeAlerts.any { it.alert.type?.equals("Hundo", ignoreCase = true) == true }) {
            filters.add(AlertFilter.HUNDOS)
        }
        if (activeAlerts.any { it.alert.type?.equals("PvP", ignoreCase = true) == true }) {
            filters.add(AlertFilter.PVP)
        }
        if (activeAlerts.any { it.alert.type?.equals("Nundo", ignoreCase = true) == true }) {
            filters.add(AlertFilter.NUNDOS)
        }
        if (activeAlerts.any { it.alert.type?.equals("Kecleon", ignoreCase = true) == true }) {
            filters.add(AlertFilter.KECLEON)
        }
        if (activeAlerts.any { it.alert.type?.equals("Rocket", ignoreCase = true) == true }) {
            filters.add(AlertFilter.ROCKET)
        }
        filters
    }

    // Auto-reset filter if current selection is invalid
    LaunchedEffect(availableFilters, selectedFilter) {
        if (selectedFilter != AlertFilter.ALL && selectedFilter !in availableFilters) {
            selectedFilter = AlertFilter.ALL
        }
    }

    val filteredAlerts = remember(activeAlerts, selectedFilter) {
        val filtered = when (selectedFilter) {
            AlertFilter.ALL -> activeAlerts
            AlertFilter.RAIDS -> activeAlerts.filter { it.alert.type?.equals("Raid", ignoreCase = true) == true }
            AlertFilter.QUESTS -> activeAlerts.filter { it.alert.type?.equals("Quest", ignoreCase = true) == true }
            AlertFilter.SPAWNS -> activeAlerts.filter { 
                val t = it.alert.type
                t?.equals("Rare", ignoreCase = true) == true || t?.equals("Spawn", ignoreCase = true) == true 
            }
            AlertFilter.HUNDOS -> activeAlerts.filter { it.alert.type?.equals("Hundo", ignoreCase = true) == true }
            AlertFilter.PVP -> activeAlerts.filter { it.alert.type?.equals("PvP", ignoreCase = true) == true }
            AlertFilter.NUNDOS -> activeAlerts.filter { it.alert.type?.equals("Nundo", ignoreCase = true) == true }
            AlertFilter.KECLEON -> activeAlerts.filter { it.alert.type?.equals("Kecleon", ignoreCase = true) == true }
            AlertFilter.ROCKET -> activeAlerts.filter { it.alert.type?.equals("Rocket", ignoreCase = true) == true }
        }
        
        // Sort by distance if available, otherwise time
        filtered.sortedWith(
            compareBy<AlertUiModel> { 
                it.distanceInfo.distanceMeters ?: Float.MAX_VALUE 
            }.thenBy { 
                TimeUtils.parseEndTimeToMillis(it.alert.endTime) ?: Long.MAX_VALUE 
            }
        )
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading && uiState.alerts.isNotEmpty(),
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = rememberPullToRefreshState()
    ) {
        when {
            uiState.isLoading && uiState.alerts.isEmpty() -> LoadingState()
            filteredAlerts.isEmpty() && !uiState.isLoading && selectedFilter == AlertFilter.ALL -> EmptyState(onRefresh = onRefresh)
            else -> AlertsList(
                filteredAlerts = filteredAlerts,
                selectedFilter = selectedFilter,
                onFilterChanged = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedFilter = it 
                },
                onAlertSelected = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAlertSelected(it) 
                },
                onOpenMaps = { alert -> openMapForAlert(context, alert) },
                onShareClick = onShareClick,
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                alertsAvailable = uiState.alerts.isNotEmpty(),
                availableFilters = availableFilters
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlertsList(
    filteredAlerts: List<AlertUiModel>,
    selectedFilter: AlertFilter,
    onFilterChanged: (AlertFilter) -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    onOpenMaps: (PokemonAlert) -> Unit,
    onShareClick: (PokemonAlert) -> Unit,
    onRequestLocationPermission: () -> Unit,
    alertsAvailable: Boolean,
    availableFilters: Set<AlertFilter>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "filters") {
            FilterRow(
                selectedFilter = selectedFilter,
                onFilterChanged = onFilterChanged,
                locationAvailable = alertsAvailable,
                onRequestLocationPermission = onRequestLocationPermission,
                availableFilters = availableFilters
            )
        }
        
        if (filteredAlerts.isEmpty()) {
            item {
                 Text(
                    text = "No alerts found for filter",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        items(filteredAlerts) { model ->
            Box(modifier = Modifier.animateItem()) {
                AlertCard(
                    alert = model.alert,
                    distanceInfo = model.distanceInfo,
                    onOpenMaps = { onOpenMaps(model.alert) },
                    onShowDetails = { onAlertSelected(model.alert) },
                    onShareClick = { onShareClick(model.alert) }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp))
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
            modifier = Modifier.fillMaxWidth()
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
private fun AuroraToolbar(
    onRefresh: () -> Unit,
    onOpenMap: () -> Unit,
    onSettingsClick: () -> Unit,
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
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
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
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(onClick = onOpenMap, shape = CircleShape) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map),
                    contentDescription = stringResource(id = R.string.open_map)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(onClick = onSettingsClick, shape = CircleShape) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            scrolledContainerColor = MaterialTheme.colorScheme.primary
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
            tint = MaterialTheme.colorScheme.primaryContainer
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
    onAlertClick: (PokemonAlert) -> Unit
) {
    val context = LocalContext.current
    var selectedTypeFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current

    val availableFilters = remember(uiState.alerts) {
        val filters = mutableSetOf(AlertFilter.ALL)
        val alerts = uiState.alerts
        
        if (alerts.any { it.type?.equals("Raid", ignoreCase = true) == true }) filters.add(AlertFilter.RAIDS)
        if (alerts.any { it.type?.equals("Quest", ignoreCase = true) == true }) filters.add(AlertFilter.QUESTS)
        if (alerts.any { 
            val t = it.type
            t?.equals("Rare", ignoreCase = true) == true || t?.equals("Spawn", ignoreCase = true) == true 
        }) filters.add(AlertFilter.SPAWNS)
        if (alerts.any { it.type?.equals("Hundo", ignoreCase = true) == true }) filters.add(AlertFilter.HUNDOS)
        if (alerts.any { it.type?.equals("PvP", ignoreCase = true) == true }) filters.add(AlertFilter.PVP)
        if (alerts.any { it.type?.equals("Nundo", ignoreCase = true) == true }) filters.add(AlertFilter.NUNDOS)
        if (alerts.any { it.type?.equals("Kecleon", ignoreCase = true) == true }) filters.add(AlertFilter.KECLEON)
        if (alerts.any { it.type?.equals("Rocket", ignoreCase = true) == true }) filters.add(AlertFilter.ROCKET)
        filters
    }

    val filteredAlerts = remember(uiState.alerts, selectedTypeFilter, selectedDateMillis) {
        var filtered = uiState.alerts

        // Type Filter
        filtered = when (selectedTypeFilter) {
            AlertFilter.ALL -> filtered
            AlertFilter.RAIDS -> filtered.filter { it.type?.equals("Raid", ignoreCase = true) == true }
            AlertFilter.QUESTS -> filtered.filter { it.type?.equals("Quest", ignoreCase = true) == true }
            AlertFilter.SPAWNS -> filtered.filter { 
                val t = it.type
                t?.equals("Rare", ignoreCase = true) == true || t?.equals("Spawn", ignoreCase = true) == true 
            }
            AlertFilter.HUNDOS -> filtered.filter { it.type?.equals("Hundo", ignoreCase = true) == true }
            AlertFilter.PVP -> filtered.filter { it.type?.equals("PvP", ignoreCase = true) == true }
            AlertFilter.NUNDOS -> filtered.filter { it.type?.equals("Nundo", ignoreCase = true) == true }
            AlertFilter.KECLEON -> filtered.filter { it.type?.equals("Kecleon", ignoreCase = true) == true }
            AlertFilter.ROCKET -> filtered.filter { it.type?.equals("Rocket", ignoreCase = true) == true }
        }
        
        // Date Filter
        if (selectedDateMillis != null) {
            val selectedCal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
            filtered = filtered.filter { alert ->
                val alertTime = TimeUtils.parseEndTimeToMillis(alert.endTime)
                if (alertTime != null) {
                    val alertCal = java.util.Calendar.getInstance().apply { timeInMillis = alertTime }
                    alertCal.get(java.util.Calendar.YEAR) == selectedCal.get(java.util.Calendar.YEAR) &&
                    alertCal.get(java.util.Calendar.DAY_OF_YEAR) == selectedCal.get(java.util.Calendar.DAY_OF_YEAR)
                } else {
                    false
                }
            }
        }
        
        filtered.sortedByDescending { TimeUtils.parseEndTimeToMillis(it.endTime) ?: 0L }
    }
    
    val statistics = remember(filteredAlerts) {
        var raids = 0
        var quests = 0
        var spawns = 0
        var hundos = 0
        var pvp = 0
        var nundos = 0
        var rocket = 0
        var kecleon = 0
        var other = 0
        
        filteredAlerts.forEach { alert ->
            when (alert.type?.lowercase()) {
                "raid" -> raids++
                "quest" -> quests++
                "rare", "spawn" -> spawns++
                "hundo" -> hundos++
                "pvp" -> pvp++
                "nundo" -> nundos++
                "rocket" -> rocket++
                "kecleon" -> kecleon++
                else -> other++
            }
        }
        
        mapOf(
            "total" to filteredAlerts.size,
            "raids" to raids,
            "quests" to quests,
            "spawns" to spawns,
            "hundos" to hundos,
            "pvp" to pvp,
            "nundos" to nundos,
            "rocket" to rocket,
            "kecleon" to kecleon,
            "other" to other
        )
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                FilterRow(
                    selectedFilter = selectedTypeFilter,
                    onFilterChanged = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTypeFilter = it 
                    },
                    locationAvailable = false,
                    onRequestLocationPermission = { },
                    availableFilters = availableFilters
                )
            }
            
            // Date Filter Button
            item {
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
                            onClick = { selectedDateMillis = null },
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
            item {
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
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    ),
                    shape = RoundedCornerShape(16.dp)
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
                                    text = "Statistics for $dateText",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${statistics["total"]} total alerts",
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
                                    StatRow("Raids", statistics["raids"] ?: 0, Color(0xFFE91E63))
                                }
                                if ((statistics["quests"] ?: 0) > 0) {
                                    StatRow("Quests", statistics["quests"] ?: 0, Color(0xFF2196F3))
                                }
                                if ((statistics["spawns"] ?: 0) > 0) {
                                    StatRow("Spawns", statistics["spawns"] ?: 0, Color(0xFF4CAF50))
                                }
                                if ((statistics["hundos"] ?: 0) > 0) {
                                    StatRow("Hundos", statistics["hundos"] ?: 0, Color(0xFFFFD700))
                                }
                                if ((statistics["pvp"] ?: 0) > 0) {
                                    StatRow("PvP", statistics["pvp"] ?: 0, Color(0xFF9C27B0))
                                }
                                if ((statistics["nundos"] ?: 0) > 0) {
                                    StatRow("Nundos", statistics["nundos"] ?: 0, Color(0xFF607D8B))
                                }
                                if ((statistics["rocket"] ?: 0) > 0) {
                                    StatRow("Rocket", statistics["rocket"] ?: 0, Color(0xFFFF5722))
                                }
                                if ((statistics["kecleon"] ?: 0) > 0) {
                                    StatRow("Kecleon", statistics["kecleon"] ?: 0, Color(0xFF00BCD4))
                                }
                                if ((statistics["other"] ?: 0) > 0) {
                                    StatRow("Other", statistics["other"] ?: 0, Color(0xFF757575))
                                }
                            }
                        }
                    }
                }
            }

            if (filteredAlerts.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = "No history found.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(filteredAlerts) { alert ->
                AlertCard(
                    alert = alert,
                    distanceInfo = AlertDistanceInfo(null, null, null),
                    onOpenMaps = { openMapForAlert(context, alert) },
                    onShowDetails = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAlertClick(alert) 
                    },
                    onShareClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Pokemon Alert: ${alert.name}")
                            val text = "Check out this ${alert.name}!\nEnds at: ${alert.endTime}\n${alert.googleMapsUri}"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Alert"))
                    }
                )
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

