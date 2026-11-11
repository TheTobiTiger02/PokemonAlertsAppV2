package com.example.pokemonalertsv2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.pokemonalertsv2.ui.components.KeepAlive
import com.example.pokemonalertsv2.ui.alerts.AlertHistoryRoute
import com.example.pokemonalertsv2.ui.alerts.AlertsSection
import com.example.pokemonalertsv2.ui.alerts.AlertsTab
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import com.example.pokemonalertsv2.ui.theme.Alphas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.ui.alerts.AlertsMapRoute
import com.example.pokemonalertsv2.ui.alerts.MapPipCommand
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.hunt.isUndoOfferLive
import com.example.pokemonalertsv2.hunt.undoLastCatch
import com.example.pokemonalertsv2.ui.alerts.MapPipUiState
import com.example.pokemonalertsv2.ui.alerts.MapPresentationMode
import com.example.pokemonalertsv2.ui.alerts.PokemonAlertsRoute
import com.example.pokemonalertsv2.ui.alerts.PokemonAlertsViewModel
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel
import com.example.pokemonalertsv2.ui.history.SpawnInsightsViewModel
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.settings.SettingsScreen
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.ui.settings.SettingsDestination
import com.example.pokemonalertsv2.ui.settings.SettingsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.UpdateState

// ── Main Scaffold with Bottom Navigation ─────────────────────────────────

/**
 * The icon and label are shared between [NavigationBarItem] and [NavigationRailItem]:
 * two copies of the same selected/unselected treatment is exactly the kind of thing that
 * drifts once one of the branches is edited.
 */
@Composable
private fun NavDestinationIcon(destination: NavDestination, selected: Boolean) {
    val label = stringResource(destination.labelRes)
    AnimatedContent(
        targetState = selected,
        transitionSpec = { appFadeThrough() },
        label = "${label}_nav_icon"
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = label
        )
    }
}

@Composable
private fun NavDestinationLabel(destination: NavDestination, selected: Boolean) {
    Text(
        text = stringResource(destination.labelRes),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
internal fun MainScaffold(
    messages: SharedFlow<String>,
    alertsViewModel: PokemonAlertsViewModel,
    historyViewModelProvider: () -> AlertHistoryViewModel,
    insightsViewModelProvider: () -> SpawnInsightsViewModel,
    settingsViewModel: SettingsViewModel,
    requestedTab: Int?,
    onRequestedTabConsumed: () -> Unit,
    requestedSettingsDestination: SettingsDestination?,
    onRequestedSettingsDestinationConsumed: () -> Unit,
    onManageLocationPermissions: () -> Unit,
    requestedHuntSetup: Boolean = false,
    onRequestedHuntSetupConsumed: () -> Unit = {},
    onOpenUnknownSourcesSettings: () -> Unit,
    pictureInPictureMode: Boolean = false,
    pipCommands: Flow<MapPipCommand>? = null,
    onPipStateChanged: ((MapPipUiState) -> Unit)? = null,
    onMapPipAvailabilityChanged: (Boolean, Boolean) -> Unit = { _, _ -> },
    onEnterPictureInPicture: (() -> Unit)? = null
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(ALERTS_TAB_INDEX) }
    // Where the gear was pressed, so Settings' back arrow returns there.
    var settingsOrigin by rememberSaveable { mutableIntStateOf(ALERTS_TAB_INDEX) }
    var openMapToolsRequested by rememberSaveable { mutableStateOf(false) }
    var openHuntSetupRequested by rememberSaveable { mutableStateOf(false) }
    // Set while a screen opened from the Tools tab is showing, so Back returns to Tools
    // instead of wherever that screen's own tab would send it. Any bar tap clears it.
    var toolsReturn by rememberSaveable { mutableStateOf(false) }
    // The Settings page Tools opened directly; Back from it closes Settings.
    var settingsCloseFrom by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    var alertsSection by rememberSaveable { mutableStateOf(AlertsSection.LIVE) }
    val visitedTabs = remember { mutableStateListOf<Int>() }
    var localSettingsDestination by remember { mutableStateOf<SettingsDestination?>(null) }
    // The feed and map filter buttons edit in place over their own tab; closing the studio
    // returns there instead of stranding the user in Settings.
    var filterStudioSurface by rememberSaveable {
        mutableStateOf<com.example.pokemonalertsv2.data.FilterSurface?>(null)
    }
    if (!pictureInPictureMode) com.example.pokemonalertsv2.ui.WhatsNewSheetHost()
    if (!pictureInPictureMode) filterStudioSurface?.let { surface ->
        com.example.pokemonalertsv2.ui.settings.FilterStudioDialog(surface, settingsViewModel) {
            filterStudioSurface = null
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val updateState by InAppUpdateManager.updateState.collectAsStateWithLifecycle(initialValue = UpdateState.Idle)

    fun openSettings(
        destination: SettingsDestination = SettingsDestination.OVERVIEW,
        fromTools: Boolean = false
    ) {
        if (selectedTab != SETTINGS_TAB_INDEX) settingsOrigin = selectedTab
        settingsCloseFrom = destination.takeIf { fromTools && it != SettingsDestination.OVERVIEW }
        localSettingsDestination = destination
        selectedTab = SETTINGS_TAB_INDEX
    }

    fun selectFromBar(screen: Int) {
        toolsReturn = false
        settingsCloseFrom = null
        selectedTab = screen
    }

    fun returnToTools() {
        toolsReturn = false
        selectedTab = TOOLS_TAB_INDEX
    }

    LaunchedEffect(requestedHuntSetup) {
        if (requestedHuntSetup) {
            toolsReturn = false
            openHuntSetupRequested = true
            selectedTab = MAP_TAB_INDEX
            onRequestedHuntSetupConsumed()
        }
    }

    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            val target = tabForScreen(it)
            if (target == SETTINGS_TAB_INDEX && selectedTab != SETTINGS_TAB_INDEX) settingsOrigin = selectedTab
            selectedTab = target
            when (it) {
                ALERTS_TAB_INDEX -> alertsSection = AlertsSection.LIVE
                HISTORY_SCREEN_INDEX -> alertsSection = AlertsSection.HISTORY
            }
            onRequestedTabConsumed()
        }
    }

    BackHandler(enabled = selectedTab == MAP_TAB_INDEX) {
        if (toolsReturn) returnToTools() else selectedTab = ALERTS_TAB_INDEX
    }
    BackHandler(enabled = selectedTab == SETTINGS_TAB_INDEX) {
        settingsCloseFrom = null
        selectedTab = settingsOrigin
    }

    LaunchedEffect(Unit) {
        messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateState.UpToDate -> {
                snackbarHostState.showSnackbar("App is up to date")
                InAppUpdateManager.resetState()
            }
            is UpdateState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                InAppUpdateManager.resetState()
            }
            else -> {}
        }
    }

    // A hunt owns the window while it runs: same map, but pointed at the quarry
    // and carrying Got it instead of the follow toggle.
    val huntRepository = remember(context) { HuntRepository.getInstance(context) }
    val huntSession by huntRepository.activeHunt.collectAsStateWithLifecycle()

    // Undoing a catch has to be offered where the trainer actually is, which is
    // rarely the map panel that used to hold the only button. Sits above the PiP
    // early return so a catch made in the window is offered the moment it is left.
    val lastCaught by remember(context) {
        AlertPreferences(context.alertPreferencesDataStore).lastCaughtAlert
    }.collectAsStateWithLifecycle(initialValue = null)
    var lastOfferedCatchId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(lastCaught?.id, lastCaught?.caughtAtMillis) {
        val caught = lastCaught ?: return@LaunchedEffect
        // Liveness stops a cold start re-offering an old record; the id guard stops
        // a configuration change offering the same one twice.
        if (!isUndoOfferLive(caught, System.currentTimeMillis())) return@LaunchedEffect
        if (caught.id == lastOfferedCatchId) return@LaunchedEffect
        lastOfferedCatchId = caught.id
        val result = snackbarHostState.showSnackbar(
            message = "Caught ${caught.displayName}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) undoLastCatch(context)
    }

    // The hunt that just ended, until it has been looked at: straight after a stop in the
    // app, or on the next open after one from the floating map.
    val lastHuntSummary by huntRepository.lastSummary.collectAsStateWithLifecycle(initialValue = null)
    if (!pictureInPictureMode) lastHuntSummary?.let { summary ->
        com.example.pokemonalertsv2.hunt.HuntSummarySheet(summary) {
            com.example.pokemonalertsv2.tracking.ArrivalTrackingNotifications.cancelHuntSummary(context)
            scope.launch { huntRepository.clearLastSummary() }
        }
    }

    val autoEnterMapPip by alertsViewModel.autoEnterMapPip.collectAsStateWithLifecycle()
    LaunchedEffect(selectedTab, autoEnterMapPip) {
        onMapPipAvailabilityChanged(selectedTab == MAP_TAB_INDEX, autoEnterMapPip)
    }

    if (pictureInPictureMode) {
        // The window shows the map and nothing else, but composed under the same saveable
        // key as the tab, so camera, zoom, mode and selection carry both ways.
        saveableStateHolder.SaveableStateProvider(MAP_TAB_INDEX) {
            AlertsMapRoute(
                viewModel = alertsViewModel,
                onBack = {},
                showBackButton = false,
                presentationMode = if (huntSession != null) {
                    MapPresentationMode.COMPACT_HUNT_PICTURE_IN_PICTURE
                } else {
                    MapPresentationMode.COMPACT_PICTURE_IN_PICTURE
                },
                pipCommands = pipCommands,
                onPipStateChanged = onPipStateChanged
            )
        }
        return
    }

    LinearModernBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints {
        // navigationLayoutModeForWidth is the single place the breakpoint lives; it is
        // unit-tested, and until now nothing consumed the RAIL branch it returns.
        val layoutMode = navigationLayoutModeForWidth(maxWidth)
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (layoutMode == NavigationLayoutMode.BOTTOM_BAR) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = Alphas.Elevated),
                        modifier = Modifier.border(
                            1.dp,
                            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.outline, Color.Transparent)),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        ),
                        tonalElevation = 0.dp
                    ) {
                        NAV_DESTINATIONS.forEach { destination ->
                            val selected = isNavDestinationSelected(destination, selectedTab, settingsOrigin)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectFromBar(destination.screen) },
                                icon = { NavDestinationIcon(destination, selected) },
                                label = { NavDestinationLabel(destination, selected) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alphas.Elevated),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alphas.Elevated)
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            // The Scaffold's padding already clears the system bars. Consuming it tells the tabs so:
            // a screen that asks for the status-bar inset itself (the map's chips and gear did)
            // would otherwise be pushed down by a second, empty status-bar-high gap.
            Row(modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)) {
                if (layoutMode == NavigationLayoutMode.RAIL) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = Alphas.Elevated),
                        modifier = Modifier.border(
                            1.dp,
                            Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outline, Color.Transparent)),
                            RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                    ) {
                        NAV_DESTINATIONS.forEach { destination ->
                            val selected = isNavDestinationSelected(destination, selectedTab, settingsOrigin)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectFromBar(destination.screen) },
                                icon = { NavDestinationIcon(destination, selected) },
                                label = { NavDestinationLabel(destination, selected) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alphas.Elevated),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alphas.Elevated)
                                )
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Every tab stays composed once opened. Rebuilding the map cost three 150 ms+ frames
                // and the feed two, on every visit; a hidden tab is unplaced and its lifecycle is
                // held at CREATED, so its refresh loops, location updates and map view all stop.
                if (selectedTab !in visitedTabs) visitedTabs.add(selectedTab)
                ROOT_ROUTE_INDICES.forEach { destinationIndex ->
                if (destinationIndex in visitedTabs) {
                KeepAlive(visible = selectedTab == destinationIndex, modifier = Modifier.fillMaxSize()) {
                saveableStateHolder.SaveableStateProvider(destinationIndex) {
                    when (destinationIndex) {
                        MAP_TAB_INDEX -> {
                            AlertsMapRoute(
                                viewModel = alertsViewModel,
                                onBack = { selectedTab = ALERTS_TAB_INDEX },
                                onOpenFilterStudio = {
                                    filterStudioSurface = com.example.pokemonalertsv2.data.FilterSurface.MAP
                                },
                                showBackButton = false,
                                openMapToolsRequested = openMapToolsRequested,
                                onMapToolsRequestConsumed = { openMapToolsRequested = false },
                                openHuntSetupRequested = openHuntSetupRequested,
                                onHuntSetupRequestConsumed = { openHuntSetupRequested = false },
                                // Closing the picker without hunting is backing out of the
                                // Tools entry; starting stays on the map with the hunt.
                                onHuntSetupClosed = { started -> if (!started && toolsReturn) returnToTools() },
                                onOpenSettings = { openSettings() },
                                onEnterPictureInPicture = onEnterPictureInPicture
                            )
                        }
                        ALERTS_TAB_INDEX -> {
                            val historyViewModel = historyViewModelProvider()
                            val insightsViewModel = insightsViewModelProvider()
                            val historyUiState by historyViewModel.uiState.collectAsStateWithLifecycle()
                            val liveUiState by alertsViewModel.uiState.collectAsStateWithLifecycle()
                            LaunchedEffect(alertsSection) {
                                if (alertsSection == AlertsSection.HISTORY) historyViewModel.ensureInitialLoad()
                            }
                            val changeSection: (AlertsSection) -> Unit = { next ->
                                // Insights starts from whatever the history list was showing.
                                if (next == AlertsSection.INSIGHTS && alertsSection == AlertsSection.HISTORY) {
                                    insightsViewModel.seed(
                                        query = historyUiState.searchQuery,
                                        type = historyUiState.selectedType
                                    )
                                }
                                alertsSection = next
                            }
                            AlertsTab(
                                section = alertsSection,
                                onSectionChange = changeSection,
                                onInsightsBack = {
                                    if (toolsReturn) {
                                        // Back to Tools, leaving the feed on Live for next time.
                                        alertsSection = AlertsSection.LIVE
                                        returnToTools()
                                    } else {
                                        changeSection(AlertsSection.HISTORY)
                                    }
                                },
                                onOpenSettings = { openSettings() },
                                refreshing = when (alertsSection) {
                                    AlertsSection.LIVE -> liveUiState.isLoading
                                    AlertsSection.HISTORY -> historyUiState.isLoading
                                    AlertsSection.INSIGHTS -> false
                                },
                                onRefresh = {
                                    when (alertsSection) {
                                        AlertsSection.LIVE -> alertsViewModel.refreshAlerts()
                                        AlertsSection.HISTORY -> historyViewModel.refreshHistoryAndStats()
                                        AlertsSection.INSIGHTS -> Unit
                                    }
                                },
                                live = {
                                    PokemonAlertsRoute(
                                        viewModel = alertsViewModel,
                                        snackbarHostState = snackbarHostState,
                                        showTopBar = false,
                                        onOpenFilterStudio = {
                                            filterStudioSurface = com.example.pokemonalertsv2.data.FilterSurface.FEED
                                        },
                                        onStartManualRaid = {
                                            context.startActivity(
                                                Intent(
                                                    context,
                                                    com.example.pokemonalertsv2.ui.counters.ManualRaidActivity::class.java
                                                )
                                            )
                                        }
                                    )
                                },
                                history = {
                                    AlertHistoryRoute(
                                        uiState = historyUiState,
                                        onRefresh = historyViewModel::refreshHistoryAndStats,
                                        onLoadMore = historyViewModel::loadMore,
                                        onDateChanged = historyViewModel::setDateFilter,
                                        onTypeChanged = historyViewModel::setTypeFilter,
                                        onSearchChanged = historyViewModel::setSearchQuery,
                                        onAreaChanged = historyViewModel::setAreaFilter,
                                        onOpenInsights = { changeSection(AlertsSection.INSIGHTS) },
                                        onExportCsv = historyViewModel::exportCsv
                                    )
                                },
                                insights = {
                                    val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()
                                    // The most common species in the live feed right now.
                                    val suggestions = remember(liveUiState.alerts) {
                                        liveUiState.alerts
                                            .mapNotNull { it.pokemon?.trim()?.takeIf(String::isNotEmpty) }
                                            .groupingBy { it }.eachCount()
                                            .entries.sortedByDescending { it.value }
                                            .take(8).map { it.key }
                                    }
                                    com.example.pokemonalertsv2.ui.history.SpawnInsightsScreen(
                                        state = insightsState,
                                        onQueryChange = insightsViewModel::setQuery,
                                        onRangeChange = insightsViewModel::setRange,
                                        onRun = insightsViewModel::run,
                                        suggestions = suggestions,
                                        onTabChange = insightsViewModel::setTab,
                                        onSpawnRangeChange = insightsViewModel::setSpawnRange,
                                        onSpawnAreaChange = insightsViewModel::setSpawnArea,
                                        onSpawnRetry = insightsViewModel::loadSpawns,
                                        onSpawnSearchChange = insightsViewModel::setSpawnSearch,
                                        onSpawnDayChange = insightsViewModel::setSpawnDay,
                                        onSpawnOpenDay = { day -> insightsViewModel.openDay(day) },
                                        onSpawnOpenHour = { start -> insightsViewModel.openHour(start) }
                                    )
                                }
                            )
                        }
                        EVENTS_TAB_INDEX -> {
                            com.example.pokemonalertsv2.events.EventsRoute(onOpenSettings = { openSettings() })
                        }
                        TOOLS_TAB_INDEX -> {
                            com.example.pokemonalertsv2.ui.tools.ToolsScreen(
                                onOpen = { id ->
                                    when (id) {
                                        "hunt" -> {
                                            // Straight to the target picker -- or, during a
                                            // hunt, to changing its targets.
                                            toolsReturn = true
                                            openHuntSetupRequested = true
                                            selectedTab = MAP_TAB_INDEX
                                        }
                                        "hunthistory" -> context.startActivity(
                                            Intent(context, com.example.pokemonalertsv2.hunt.HuntHistoryActivity::class.java)
                                        )
                                        "routehistory" -> context.startActivity(
                                            Intent(context, com.example.pokemonalertsv2.catchroutes.CatchRouteHistoryActivity::class.java)
                                        )
                                        "routes" -> context.startActivity(
                                            Intent(context, com.example.pokemonalertsv2.catchroutes.CatchRoutesActivity::class.java)
                                        )
                                        "raids" -> context.startActivity(
                                            com.example.pokemonalertsv2.ui.counters.ManualRaidActivity.countersIntent(context)
                                        )
                                        "roster" -> context.startActivity(
                                            Intent(context, com.example.pokemonalertsv2.ui.settings.PokeGenieRosterActivity::class.java)
                                        )
                                        "godex" -> openSettings(SettingsDestination.GODEX, fromTools = true)
                                        "insights" -> {
                                            insightsViewModelProvider().seed(query = "", type = null)
                                            toolsReturn = true
                                            alertsSection = AlertsSection.INSIGHTS
                                            selectedTab = ALERTS_TAB_INDEX
                                        }
                                    }
                                },
                                onOpenSettings = { openSettings() }
                            )
                        }
                        SETTINGS_TAB_INDEX -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onClose = {
                                    settingsCloseFrom = null
                                    selectedTab = settingsOrigin
                                },
                                closeFrom = settingsCloseFrom,
                                onManageLocationPermissions = onManageLocationPermissions,
                                requestedDestination = localSettingsDestination ?: requestedSettingsDestination,
                                onRequestedDestinationConsumed = {
                                    if (localSettingsDestination != null) localSettingsDestination = null
                                    else onRequestedSettingsDestinationConsumed()
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
        }
        }

        // Dialogs for update flow
        when (val state = updateState) {
            is UpdateState.UpdateAvailable -> {
                AlertDialog(
                    onDismissRequest = { InAppUpdateManager.resetState() },
                    title = { Text("Update Available") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.mainscaffoldkt_a_new_version_of_pokemonalerts_x, state.release.tagName))
                            if (!state.release.body.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.mainscaffoldkt_release_notes_x, state.release.body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.05f), MaterialTheme.shapes.small)
                                        .padding(8.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                downloadJob = scope.launch {
                                    InAppUpdateManager.downloadAndInstall(context, state.release)
                                }
                            }
                        ) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { InAppUpdateManager.resetState() }) {
                            Text(stringResource(R.string.mainscaffoldkt_cancel))
                        }
                    }
                )
            }
            is UpdateState.Downloading -> {
                AlertDialog(
                    // Not dismissable by tapping outside, but Cancel below is always
                    // reachable so a stalled download cannot trap the user.
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.mainscaffoldkt_downloading_update)) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                downloadJob?.cancel()
                                downloadJob = null
                                InAppUpdateManager.cancelPendingInstall(context)
                            }
                        ) {
                            Text(stringResource(R.string.mainscaffoldkt_cancel))
                        }
                    }
                )
            }
            is UpdateState.AwaitingInstallPermission -> {
                AlertDialog(
                    onDismissRequest = { InAppUpdateManager.cancelPendingInstall(context) },
                    title = { Text(stringResource(R.string.mainscaffoldkt_install_permission_required)) },
                    text = {
                        Text(stringResource(R.string.mainscaffoldkt_allow_pokemon_alerts_to_install_x, state.releaseTag))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onOpenUnknownSourcesSettings
                        ) {
                            Text(stringResource(R.string.mainscaffoldkt_open_settings))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { InAppUpdateManager.cancelPendingInstall(context) }) {
                            Text(stringResource(R.string.mainscaffoldkt_cancel))
                        }
                    }
                )
            }
            is UpdateState.Error -> Unit
            is UpdateState.Installing -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.mainscaffoldkt_installing)) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(stringResource(R.string.mainscaffoldkt_launching_installer))
                        }
                    },
                    confirmButton = {}
                )
            }
            else -> {}
        }
    }
}

// ── Permission Dialogs ───────────────────────────────────────────────────

@Composable
internal fun BackgroundLocationPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.background_location_title)) },
        text = {
            Text(text = stringResource(R.string.background_location_message))
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.background_location_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.not_now))
            }
        }
    )
}
