package com.example.pokemonalertsv2.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.notifications.QuietHours
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.data.godex.GoDexSyncUiState
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import java.util.Calendar
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.UpdateCheckSource
import com.example.pokemonalertsv2.util.UpdateState
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.NotificationCategoryState
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.alerts.AREA_FILTER_OPTIONS
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.data.backup.SettingsBackupRepository
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

internal enum class SettingsDestination(val title: String) {
    OVERVIEW("Settings"),
    APPEARANCE_BEHAVIOR("Appearance & behavior"),
    ALERT_FILTERS("Filters"),
    GODEX("GoDex checklist"),
    GODEX_COLLECTION("GoDex collection"),
    RAID_COUNTERS("Raid counters"),
    NOTIFICATIONS("Notifications"),
    ABOUT_UPDATES("About & updates")
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
    onRequestedDestinationConsumed: () -> Unit = {}
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

    BackHandler(enabled = destination != SettingsDestination.OVERVIEW) {
        navigateTo(parentDestination)
    }

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
                            Text(title, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        AnimatedContent(
                            targetState = destination != SettingsDestination.OVERVIEW,
                            transitionSpec = { appFadeThrough() },
                            label = "settings_back"
                        ) { showBack ->
                            if (showBack) {
                                FilledIconButton(
                                    onClick = { navigateTo(parentDestination) },
                                    shape = CircleShape
                                ) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                        goDexSummary = when {
                            !goDexConfig.isConnected -> "Not connected"
                            goDexSyncUiState.sessionState == GoDexSessionState.REAUTH_REQUIRED ->
                                "${goDexEntries.count { it.needed }} still needed - sign in again"
                            goDexConfig.hasWriteBackUrl ->
                                "${goDexEntries.count { it.needed }} still needed - two-way sync"
                            else -> "${goDexEntries.count { it.needed }} still needed - read-only"
                        },
                        goDexBadge = when {
                            !goDexConfig.isConnected -> "Connect"
                            goDexSyncUiState.sessionState == GoDexSessionState.REAUTH_REQUIRED -> "Sign in"
                            goDexSyncUiState.errorMessage != null -> "Sync issue"
                            else -> null
                        },
                        raidCountersSummary = buildString {
                            append("Level ${raidCounterSettings.options.attackerLevel}")
                            append(" - ")
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
                SettingsSection(title = "Display and sorting") {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(0 to "System", 1 to "Light", 2 to "Dark").forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateThemeMode(mode) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.updateThemeMode(mode) }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = "Default sort",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(
                        SortPreference.POSTED_TIME to "Newest",
                        SortPreference.TIME_REMAINING to "Time remaining",
                        SortPreference.DISTANCE to "Distance",
                        SortPreference.NAME to "Name"
                    ).forEach { (preference, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSortPreference(preference) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = savedSortPreference == preference,
                                onClick = { viewModel.updateSortPreference(preference) }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                }

                if (animatedDestination == SettingsDestination.ALERT_FILTERS) {
                FiltersHubContent(
                    viewModel = viewModel,
                    onOpenFullNotificationSettings = { navigateTo(SettingsDestination.NOTIFICATIONS) }
                )
                }

                if (animatedDestination == SettingsDestination.APPEARANCE_BEHAVIOR) {
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
                        text = "Gyms and PokéStops always use 80 m. Pokémon spawns use 40 m, " +
                            "or 80 m when Spacial Rend is enabled. This setting is only for " +
                            "other free-coordinate alerts. Arrival requires two precise fixes.",
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
                            ArrivalTrackingRepository.MAX_RADIUS_METERS.toFloat(),
                        steps = 35
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
                }

                if (animatedDestination == SettingsDestination.GODEX) {
                SettingsSection(title = "GoDex Hundo checklist") {
                    val totalCount = goDexEntries.size
                    val neededCount = goDexEntries.count { it.needed }
                    val isStale = goDexConfig.lastSuccessfulSyncMillis > 0L &&
                        System.currentTimeMillis() - goDexConfig.lastSuccessfulSyncMillis >=
                        GoDexRepository.STALE_WARNING_MILLIS

                     if (!goDexConfig.isConnected) {
                        val context = LocalContext.current
                        Text(
                            "Connect your GoDex Hundo collection to track needed targets, filter notifications, and sync catches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ElevatedButton(
                            onClick = {
                                context.startActivity(
                                    com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in to GoDex (Two-Way Sync)")
                        }
                        Text(
                            "Logging in allows you to mark Pokémon as caught directly inside the app to sync them back to your GoDex checklist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Or, connect with a Public URL (Read-Only):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = goDexUrlInput,
                            onValueChange = { goDexUrlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Public GoDex collection URL") },
                            placeholder = { Text("https://godex.site/public-collection/\u2026") },
                            singleLine = true,
                            enabled = !goDexSyncUiState.isSyncing
                        )
                        ElevatedButton(
                            onClick = { viewModel.connectGoDex(goDexUrlInput) },
                            enabled = goDexUrlInput.isNotBlank() && !goDexSyncUiState.isSyncing
                        ) {
                            Text(if (goDexSyncUiState.isSyncing) "Connecting\u2026" else "Connect")
                        }
                        goDexSyncUiState.errorMessage?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            goDexConfig.collectionTitle.ifBlank { "GoDex Hundo collection" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        GoDexSettingsProgressCard(
                            neededCount = neededCount,
                            totalCount = totalCount,
                            onOpenCollection = { navigateTo(SettingsDestination.GODEX_COLLECTION) }
                        )
                        GoDexSyncStatusCard(
                            config = goDexConfig,
                            syncState = goDexSyncUiState,
                            isStale = isStale
                        )
                        SwitchSetting(
                            title = "Only notify for needed GoDex Hundos",
                            subtitle = "Confirmed collected Hundos are suppressed. Unknown forms still notify.",
                            checked = goDexConfig.notificationFilterEnabled,
                            onCheckedChange = viewModel::updateGoDexNotificationFilter
                        )
                        if (goDexConfig.notificationFilterEnabled) {
                            Text(
                                "Your manual Hundo species selection is preserved and resumes when this filter is disabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { navigateTo(SettingsDestination.GODEX_COLLECTION) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (neededCount == 0) "View collection" else "Review needed")
                            }
                            OutlinedButton(
                                onClick = viewModel::syncGoDex,
                                enabled = !goDexSyncUiState.isSyncing
                            ) {
                                Text(if (goDexSyncUiState.isSyncing) "Syncing\u2026" else "Sync now")
                            }
                        }
                        val context = LocalContext.current
                        if (goDexConfig.hasSession) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (goDexConfig.hasWriteBackUrl) {
                                    val shortUrl = goDexConfig.writeBackUrl.substringAfterLast("/")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Two-way sync enabled for checklist: $shortUrl",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(onClick = {
                                            context.startActivity(
                                                com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context, startAtPicker = true)
                                            )
                                        }) {
                                            Text("Change checklist")
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(onClick = { viewModel.clearGoDexSession() }) {
                                            Text("Sign out")
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Signed in, but no checklist is selected.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ElevatedButton(
                                            onClick = {
                                                context.startActivity(
                                                    com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context, startAtPicker = true)
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Select checklist to sync")
                                        }
                                        TextButton(onClick = { viewModel.clearGoDexSession() }) {
                                            Text("Sign out")
                                        }
                                    }
                                }
                            }
                        } else {
                            val requiresReauthentication =
                                goDexSyncUiState.sessionState == GoDexSessionState.REAUTH_REQUIRED
                            if (requiresReauthentication) {
                                Text(
                                    "Your GoDex session expired. Sign in again to resume " +
                                        "${goDexSyncUiState.pendingCount} pending checklist " +
                                        if (goDexSyncUiState.pendingCount == 1) "change." else "changes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            ElevatedButton(
                                onClick = {
                                    context.startActivity(
                                        com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (requiresReauthentication) {
                                        "Sign in again to resume sync"
                                    } else {
                                        "Sign in to GoDex for two-way sync"
                                    }
                                )
                            }
                            Text(
                                "Sign in to mark Pokémon as caught directly from alerts, and have changes sync back to your GoDex checklist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showGoDexDisconnectConfirmation = true },
                            enabled = !goDexSyncUiState.isSyncing
                        ) {
                            Text("Disconnect GoDex")
                        }
                    }

                }
                }
                
                if (animatedDestination == SettingsDestination.NOTIFICATIONS) {
                SettingsSection(title = "Permission status") {
                    PermissionStatusRow(
                        title = "Notifications",
                        granted = systemNotificationsGranted,
                        description = "Required for instant alert notifications",
                        actionLabel = if (systemNotificationsGranted) "Manage" else "Enable",
                        onAction = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    )
                    HorizontalDivider()
                    PermissionStatusRow(
                        title = "Location while using the app",
                        granted = foregroundLocationGranted,
                        description = "Enables distance, nearby sorting, and map tracking",
                        actionLabel = if (foregroundLocationGranted) "Manage" else "Grant",
                        onAction = onManageLocationPermissions
                    )
                    HorizontalDivider()
                    PermissionStatusRow(
                        title = "Background location",
                        granted = backgroundLocationGranted,
                        description = "Keeps location-based features accurate when the app is closed",
                        actionLabel = if (backgroundLocationGranted) "Manage" else "Grant",
                        onAction = onManageLocationPermissions
                    )
                }
                SettingsSection(title = "Notification preferences") {
                    OutlinedButton(onClick = {
                        viewModel.requestFilterEditor(com.example.pokemonalertsv2.data.FilterSurface.NOTIFICATIONS)
                        navigateTo(SettingsDestination.ALERT_FILTERS)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Choose eligible alerts in Filter Studio") }
                    SwitchSetting(
                        title = "Enable Notifications",
                        subtitle = "Receive alerts for new Pokemon nearby",
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
                    )
                    
                    AnimatedVisibility(
                        visible = notificationsEnabled,
                        enter = appExpandIn(),
                        exit = appCollapseOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SwitchSetting(
                            title = "Vibration",
                            subtitle = "Vibrate when receiving notifications",
                            checked = notificationVibrate,
                            onCheckedChange = { viewModel.updateNotificationVibrate(it) }
                        )
                        
                        // A standing nightly window. Kept separate from the one-off
                        // "silence for N hours" below: they answer different questions and
                        // either can be on without the other.
                        QuietHoursCard(
                            enabled = quietHoursEnabled,
                            startMinute = quietHoursStart,
                            endMinute = quietHoursEnd,
                            onEnabledChange = { viewModel.updateQuietHoursEnabled(it) },
                            onWindowChange = { start, end -> viewModel.updateQuietHours(start, end) }
                        )

                        // Silence notifications section
                        SilenceNotificationsCard(
                            silenceUntil = silenceUntil,
                            onSilenceFor = { minutes -> viewModel.silenceNotificationsFor(minutes) },
                            onClearSilence = { viewModel.clearNotificationSilence() }
                        )
                        }
                    }
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
