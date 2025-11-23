@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
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
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import com.example.pokemonalertsv2.ui.theme.EmberGradientEnd
import com.example.pokemonalertsv2.ui.theme.EmberGradientStart
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.delay
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
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val highlightedAlert = remember(uiState.alerts, uiState.highlightedAlertId) {
        uiState.alerts.firstOrNull { it.uniqueId == uiState.highlightedAlertId }
    }

    val onShareClick: (PokemonAlert) -> Unit = { alert ->
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Pokemon Alert: ${alert.name}")
            val text = "Check out this ${alert.name}!\nEnds at: ${alert.endTime}\n${alert.googleMapsUri}"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Alert"))
    }

    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    PokemonAlertsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refreshAlerts,
        onAlertSelected = {
            viewModel.highlightAlert(it.uniqueId)
        },
        onShareClick = onShareClick,
        onSettingsClick = onSettingsClick,
        onHistoryClick = onHistoryClick
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshAlerts()
                delay(30_000)
            }
        }
    }

    if (highlightedAlert != null) {
        AlertDetailDialog(
            alert = highlightedAlert, 
            onDismiss = { viewModel.highlightAlert(null) },
            onShareClick = { onShareClick(highlightedAlert) }
        )
    }
}

@Composable
fun PokemonAlertsScreen(
    uiState: AlertsUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    onShareClick: (PokemonAlert) -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var lastUpdated by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
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
    LaunchedEffect(uiState.alerts) {
        if (uiState.alerts.isNotEmpty()) {
            lastUpdated = System.currentTimeMillis()
        }
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
    val activeAlerts = remember(alertsWithDistance, lastUpdated) {
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
        modifier = modifier
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
                AuroraToolbar(
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    },
                    onOpenMap = { context.startActivity(Intent(context, AlertsMapActivity::class.java)) },
                    onSettingsClick = onSettingsClick,
                    onHistoryClick = onHistoryClick,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
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

        items(filteredAlerts, key = { it.alert.uniqueId }) { model ->
            Box(modifier = Modifier.animateItemPlacement()) {
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AlertFilter.values().filter { it in availableFilters }.forEach { filter ->
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
    onHistoryClick: () -> Unit,
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
            FilledIconButton(onClick = onHistoryClick, shape = CircleShape) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = "History"
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
