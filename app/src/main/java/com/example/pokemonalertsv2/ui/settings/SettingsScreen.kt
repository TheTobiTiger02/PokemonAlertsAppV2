package com.example.pokemonalertsv2.ui.settings

import androidx.compose.material3.IconButton
import com.example.pokemonalertsv2.ui.alerts.shortLabel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.UpdateCheckSource
import com.example.pokemonalertsv2.util.UpdateState
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.text.style.TextOverflow

// Names are part of the pokemonalerts://settings/<name> deep-link contract; titles are free.
internal enum class SettingsDestination(val title: String) {
    OVERVIEW("Settings"),
    APPEARANCE_BEHAVIOR("Appearance"),
    MAP_TRACKING("Map & tracking"),
    ALERT_FILTERS("Filters"),
    GODEX("GoDex checklist"),
    GODEX_COLLECTION("GoDex collection"),
    RAID_COUNTERS("Raid counters"),
    NOTIFICATIONS("Notifications & permissions"),
    ABOUT_UPDATES("Backup & about")
}

internal fun goDexDisconnectMessage(pendingCount: Int): String =
    if (pendingCount > 0) {
        "This removes the cached checklist and discards $pendingCount unsent " +
            if (pendingCount == 1) {
                "change from this device. GoDex itself will not be changed."
            } else {
                "changes from this device. GoDex itself will not be changed."
            }
    } else {
        "This removes the cached checklist from this device. Your GoDex collection will not be changed."
    }


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    onManageLocationPermissions: () -> Unit,
    requestedDestination: SettingsDestination? = null,
    onRequestedDestinationConsumed: () -> Unit = {},
    /** Set when Settings opens from a gear: the overview then has a back arrow to where it came from. */
    onClose: (() -> Unit)? = null,
    /**
     * A page Settings was opened straight onto from outside, such as GoDex from the Tools tab.
     * Back from that page closes Settings rather than detouring through the overview.
     */
    closeFrom: SettingsDestination? = null
) {
    var destinationName by rememberSaveable { mutableStateOf(SettingsDestination.OVERVIEW.name) }
    val destination = SettingsDestination.entries.firstOrNull { it.name == destinationName }
        ?: SettingsDestination.OVERVIEW
    val navigateTo: (SettingsDestination) -> Unit = { destinationName = it.name }
    LaunchedEffect(requestedDestination) {
        requestedDestination?.let {
            navigateTo(it)
            onRequestedDestinationConsumed()
        }
    }
    val parentDestination = if (destination == SettingsDestination.GODEX_COLLECTION) {
        SettingsDestination.GODEX
    } else {
        SettingsDestination.OVERVIEW
    }

    val closesSettings = onClose != null && destination == closeFrom
    val goBack: () -> Unit = {
        if (closesSettings) onClose?.invoke() else navigateTo(parentDestination)
    }
    BackHandler(enabled = destination != SettingsDestination.OVERVIEW) { goBack() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val arrivalRepository = remember(context) { ArrivalTrackingRepository.getInstance(context) }
    val arrivalRadius by arrivalRepository.arrivalRadiusMeters.collectAsStateWithLifecycle()
    val arrivalScope = rememberCoroutineScope()
    var arrivalRadiusSlider by remember { mutableStateOf(arrivalRadius.toFloat()) }
    LaunchedEffect(arrivalRadius) {
        arrivalRadiusSlider = arrivalRadius.toFloat()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foregroundLocationGranted by remember { mutableStateOf(false) }
    var backgroundLocationGranted by remember { mutableStateOf(false) }
    var systemNotificationsGranted by remember { mutableStateOf(true) }
    fun refreshLocationPermissionStatus() {
        foregroundLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        backgroundLocationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        systemNotificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    DisposableEffect(lifecycleOwner) {
        refreshLocationPermissionStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshLocationPermissionStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val raidCounterSettings by viewModel.raidCounterSettings.collectAsStateWithLifecycle(
        initialValue = com.example.pokemonalertsv2.data.counters.RaidCounterSettings()
    )
    val pokeGenieImportStatus by viewModel.pokeGenieImportStatus.collectAsStateWithLifecycle(initialValue = null)
    val pendingPokeGenieImport by viewModel.pendingPokeGenieImport.collectAsStateWithLifecycle(initialValue = null)
    val pokeGenieImportUiState by viewModel.pokeGenieImportUiState.collectAsStateWithLifecycle()
    val previewCandidate = when (val importState = pokeGenieImportUiState) {
        is com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportUiState.Preview ->
            importState.candidate
        else -> pendingPokeGenieImport
    }
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val raidsNotifications by viewModel.raidsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val spawnsNotifications by viewModel.spawnsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val questsNotifications by viewModel.questsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val hundosNotifications by viewModel.hundosNotifications.collectAsStateWithLifecycle(initialValue = true)
    val pvpNotifications by viewModel.pvpNotifications.collectAsStateWithLifecycle(initialValue = true)
    val nundosNotifications by viewModel.nundosNotifications.collectAsStateWithLifecycle(initialValue = true)
    val kecleonNotifications by viewModel.kecleonNotifications.collectAsStateWithLifecycle(initialValue = true)
    val rocketNotifications by viewModel.rocketNotifications.collectAsStateWithLifecycle(initialValue = true)
    val notificationVibrate by viewModel.notificationVibrate.collectAsStateWithLifecycle(initialValue = true)
    val journeyOverlayEnabled by viewModel.journeyOverlayEnabled.collectAsStateWithLifecycle(initialValue = true)
    val lastPushReceived by viewModel.lastPushReceived.collectAsStateWithLifecycle(initialValue = 0L)
    val silenceUntil by viewModel.silenceUntil.collectAsStateWithLifecycle(initialValue = 0L)
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val quietHoursStart by viewModel.quietHoursStartMinute.collectAsStateWithLifecycle()
    val quietHoursEnd by viewModel.quietHoursEndMinute.collectAsStateWithLifecycle()
    val selectedArea by viewModel.selectedArea.collectAsStateWithLifecycle(initialValue = "All")
    val maxDistance by viewModel.maxDistance.collectAsStateWithLifecycle(initialValue = 0)
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = 0)
    val savedSortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
        initialValue = SortPreference.POSTED_TIME
    )
    val cardStyle by viewModel.cardStyle.collectAsStateWithLifecycle()
    
    // Excluded types for granular filtering
    val excludedHundoTypes by viewModel.excludedHundoTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedNundoTypes by viewModel.excludedNundoTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedPvpTypes by viewModel.excludedPvpTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedSpawnTypes by viewModel.excludedSpawnTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedRocketTypes by viewModel.excludedRocketTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedRaidTiers by viewModel.excludedRaidTiers.collectAsStateWithLifecycle(initialValue = emptySet())
    val goDexConfig by viewModel.goDexConfig.collectAsStateWithLifecycle()
    val goDexEntries by viewModel.goDexEntries.collectAsStateWithLifecycle()
    val goDexSyncUiState by viewModel.goDexSyncUiState.collectAsStateWithLifecycle()
    val goDexPendingEntryKeys by viewModel.goDexPendingEntryKeys.collectAsStateWithLifecycle()
    var goDexUrlInput by rememberSaveable { mutableStateOf("") }
    var showGoDexDisconnectConfirmation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(goDexConfig.url) {
        if (goDexConfig.url.isNotBlank() || goDexUrlInput.isBlank()) {
            goDexUrlInput = goDexConfig.url
        }
    }

    LaunchedEffect(destination, goDexConfig.isConnected) {
        if (
            goDexConfig.isConnected &&
            (destination == SettingsDestination.GODEX ||
                destination == SettingsDestination.GODEX_COLLECTION)
        ) {
            viewModel.refreshGoDexForPageEntry()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LinearModernBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = {
                        AnimatedContent(
                            targetState = destination.title,
                            transitionSpec = { appFadeThrough() },
                            label = "settings_title"
                        ) { title ->
                            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        AnimatedContent(
                            targetState = destination != SettingsDestination.OVERVIEW || onClose != null,
                            transitionSpec = { appFadeThrough() },
                            label = "settings_back"
                        ) { showBack ->
                            if (showBack) {
                                IconButton(onClick = {
                                    if (destination != SettingsDestination.OVERVIEW) goBack()
                                    else onClose?.invoke()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val movingDeeper = targetState != SettingsDestination.OVERVIEW &&
                        (initialState == SettingsDestination.OVERVIEW ||
                            targetState == SettingsDestination.GODEX_COLLECTION)
                    appSharedAxisX(forward = movingDeeper)
                },
                contentKey = { it.name },
                label = "settings_destination"
            ) { animatedDestination ->
            if (animatedDestination == SettingsDestination.GODEX_COLLECTION) {
                GoDexCollectionContent(
                    entries = goDexEntries,
                    pendingEntryKeys = goDexPendingEntryKeys,
                    canEdit = goDexConfig.hasWriteBackUrl,
                    sessionState = goDexSyncUiState.sessionState,
                    isSyncing = goDexSyncUiState.isSyncing,
                    syncError = goDexSyncUiState.errorMessage,
                    pendingCount = goDexSyncUiState.pendingCount,
                    onSetCaught = viewModel::setGoDexEntryCaught,
                    onSignIn = {
                        context.startActivity(
                            com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context)
                        )
                    },
                    modifier = Modifier.padding(padding)
                )
            } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val designedPage = animatedDestination == SettingsDestination.NOTIFICATIONS ||
                animatedDestination == SettingsDestination.GODEX
            if (designedPage) {
                if (animatedDestination == SettingsDestination.NOTIFICATIONS) {
                    NotificationsPageDesign(

                        viewModel = viewModel,
                        grants = PermissionGrants(
                            notifications = systemNotificationsGranted,
                            foregroundLocation = foregroundLocationGranted,
                            backgroundLocation = backgroundLocationGranted
                        ),
                        onManageNotifications = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        },
                        onManageLocation = onManageLocationPermissions,
                        onOpenFilterStudio = {
                            viewModel.requestFilterEditor(com.example.pokemonalertsv2.data.FilterSurface.NOTIFICATIONS)
                            navigateTo(SettingsDestination.ALERT_FILTERS)
                        }
                    )
                } else {
                    GoDexPageDesign(

                        viewModel = viewModel,
                        urlInput = goDexUrlInput,
                        onUrlInputChange = { goDexUrlInput = it },
                        onOpenCollection = { navigateTo(SettingsDestination.GODEX_COLLECTION) },
                        onDisconnect = { showGoDexDisconnectConfirmation = true }
                    )
                }
            } else
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (animatedDestination == SettingsDestination.OVERVIEW) {
                    SettingsOverview(
                        themeMode = themeMode,
                        sortPreference = savedSortPreference,
                        selectedArea = selectedArea,
                        maxDistance = maxDistance,
                        notificationsEnabled = notificationsEnabled,
                        foregroundLocationGranted = foregroundLocationGranted,
                        backgroundLocationGranted = backgroundLocationGranted,
                        systemNotificationsGranted = systemNotificationsGranted,
                        cardStyle = cardStyle,
                        goDexSummary = when {
                            !goDexConfig.isConnected -> "Not connected"
                            goDexSyncUiState.sessionState == GoDexSessionState.REAUTH_REQUIRED ->
                                "${goDexEntries.count { it.needed }} still needed · sign in again"
                            goDexConfig.hasWriteBackUrl ->
                                "${goDexEntries.count { it.needed }} still needed · two-way sync"
                            else -> "${goDexEntries.count { it.needed }} still needed · read-only"
                        },
                        goDexBadge = when {
                            !goDexConfig.isConnected -> "Connect"
                            goDexSyncUiState.sessionState == GoDexSessionState.REAUTH_REQUIRED -> "Sign in"
                            goDexSyncUiState.errorMessage != null -> "Sync issue"
                            else -> null
                        },
                        raidCountersSummary = buildString {
                            append("Level ${raidCounterSettings.options.attackerLevel}")
                            append(" · ")
                            append(
                                if (raidCounterSettings.pokeGenieCount > 0) {
                                    "${raidCounterSettings.pokeGenieCount} of yours imported"
                                } else {
                                    "Poke Genie not imported"
                                }
                            )
                        },
                        onDestinationSelected = navigateTo
                    )
                }

                if (animatedDestination == SettingsDestination.RAID_COUNTERS) {
                    RaidCountersSettingsContent(
                        settings = raidCounterSettings,
                        onOptionsChanged = viewModel::updateRaidCounterDefaults,
                        onPrepareCsv = viewModel::preparePokeGenieImport,
                        pendingImport = previewCandidate,
                        onConfirmImport = viewModel::commitPokeGenieImport,
                        onCancelImport = viewModel::cancelPokeGenieImport,
                        onClearPokeGenie = viewModel::clearPokeGenie,
                        importStatus = pokeGenieImportStatus
                    )
                }

                if (animatedDestination == SettingsDestination.APPEARANCE_BEHAVIOR) {
                SettingsSection(title = "Theme") {
                    SegmentedSetting(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = themeMode.coerceIn(0, 2),
                        onSelected = { viewModel.updateThemeMode(it) }
                    )
                }
                SettingsSection(title = "Alert list") {
                    Text(
                        text = "Row style",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SegmentedSetting(
                        options = com.example.pokemonalertsv2.data.AlertCardStyle.entries.map { it.label },
                        selectedIndex = cardStyle.ordinal,
                        onSelected = { viewModel.updateCardStyle(com.example.pokemonalertsv2.data.AlertCardStyle.entries[it]) }
                    )
                    Text(
                        text = "Compact fits about six alerts on screen. Large adds a map preview and buttons to every alert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text(
                        text = "Default sort",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sortOptions = listOf(
                        SortPreference.POSTED_TIME,
                        SortPreference.DISTANCE,
                        SortPreference.TIME_REMAINING,
                        SortPreference.NAME
                    )
                    SegmentedSetting(
                        // Four equal segments on a phone: "Ending soon" would not fit.
                        options = sortOptions.map { if (it == SortPreference.TIME_REMAINING) "Ending" else it.shortLabel },
                        selectedIndex = sortOptions.indexOf(savedSortPreference).coerceAtLeast(0),
                        onSelected = { viewModel.updateSortPreference(sortOptions[it]) }
                    )
                }
                }

                if (animatedDestination == SettingsDestination.ALERT_FILTERS) {
                FiltersHubContent(
                    viewModel = viewModel,
                    onOpenFullNotificationSettings = { navigateTo(SettingsDestination.NOTIFICATIONS) }
                )
                }

                if (animatedDestination == SettingsDestination.MAP_TRACKING) {
                    SettingsSection(title = "Map markers") {
                        com.example.pokemonalertsv2.ui.alerts.MapGroupingEditor()
                    }
                    SettingsSection(title = "Arrival tracking") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Other alert radius",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${ArrivalTrackingRepository.normalizeRadius(arrivalRadiusSlider.toInt())} m",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "How close counts as arrived for alerts without a stop or spawn. " +
                            "Gyms and PokéStops use 80 m; Pokémon spawns use 40 m (80 m with Spacial Rend).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Slider(
                        value = arrivalRadiusSlider,
                        onValueChange = { arrivalRadiusSlider = it },
                        onValueChangeFinished = {
                            val normalized = ArrivalTrackingRepository.normalizeRadius(
                                arrivalRadiusSlider.toInt()
                            )
                            arrivalRadiusSlider = normalized.toFloat()
                            arrivalScope.launch {
                                arrivalRepository.updateArrivalRadius(normalized)
                            }
                        },
                        valueRange = ArrivalTrackingRepository.MIN_RADIUS_METERS.toFloat()..
                            ArrivalTrackingRepository.MAX_RADIUS_METERS.toFloat()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("20 m", style = MaterialTheme.typography.labelSmall)
                        Text("40 m default", style = MaterialTheme.typography.labelSmall)
                        Text("200 m", style = MaterialTheme.typography.labelSmall)
                    }
                }
                    SettingsSection(title = "Watch & health data") {
                        com.example.pokemonalertsv2.health.WatchDataSettings()
                    }
                }


                

                
                if (animatedDestination == SettingsDestination.ABOUT_UPDATES) {
                SettingsSection(title = "Backup") {
                    SettingsBackupCard(snackbarHostState = snackbarHostState)
                }

                SettingsSection(title = "App information") {
                    val coroutineScope = rememberCoroutineScope()
                    val updateState by InAppUpdateManager.updateState.collectAsStateWithLifecycle(initialValue = UpdateState.Idle)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version ${com.example.pokemonalertsv2.BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        InAppUpdateManager.checkForUpdates(UpdateCheckSource.MANUAL)
                                    }
                                },
                                enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading
                            ) {
                                if (updateState is UpdateState.Checking) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Checking...")
                                } else {
                                    Text("Check for Updates")
                                }
                            }
                        }
                        Text(
                            text = "\u00A9 openrouteservice.org by HeiGIT | Map data \u00A9 OpenStreetMap contributors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            }
            }
            }
        }
    }

    if (showGoDexDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showGoDexDisconnectConfirmation = false },
            title = { Text("Disconnect GoDex?") },
            text = { Text(goDexDisconnectMessage(goDexSyncUiState.pendingCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGoDexDisconnectConfirmation = false
                        viewModel.disconnectGoDex()
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoDexDisconnectConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
