package com.example.pokemonalertsv2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.AlertsMapRoute
import com.example.pokemonalertsv2.ui.alerts.PokemonAlertsRoute
import com.example.pokemonalertsv2.ui.alerts.PokemonAlertsViewModel
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel
import com.example.pokemonalertsv2.ui.settings.SettingsScreen
import com.example.pokemonalertsv2.ui.settings.SettingsViewModel
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository
import androidx.compose.runtime.LaunchedEffect
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.UpdateState


/**
 * Bottom navigation destinations.
 * Onboarding is handled separately and is not part of the nav bar.
 */
private data class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

internal enum class NavigationLayoutMode { BOTTOM_BAR, RAIL }

internal fun navigationLayoutModeForWidth(width: Dp): NavigationLayoutMode =
    if (width >= 600.dp) NavigationLayoutMode.RAIL else NavigationLayoutMode.BOTTOM_BAR

private val NAV_DESTINATIONS = listOf(
    NavDestination("Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    NavDestination("History", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    NavDestination("Map", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    NavDestination("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {

    private val alertsViewModel: PokemonAlertsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: AlertHistoryViewModel by viewModels()
    private val backgroundLocationPermissionNeeded = MutableStateFlow(false)
    private val requestedRootTab = MutableStateFlow<Int?>(null)
    private var permissionStep = PermissionStep.IDLE

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            if (fineLocationGranted || coarseLocationGranted) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
                requestBackgroundLocationStep()
            } else {
                Toast.makeText(
                    this,
                    "Location permission is needed for distance calculations and map features",
                    Toast.LENGTH_LONG
                ).show()
                finishPermissionFlow()
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Background location access granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Background location access was not granted", Toast.LENGTH_SHORT).show()
            }
            finishPermissionFlow()
        }

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_rationale),
                    Toast.LENGTH_LONG
                ).show()
            }
            requestForegroundLocationStep()
        }

    private val backgroundLocationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val granted = hasBackgroundLocationPermission()
            Toast.makeText(
                this,
                if (granted) "Background location access granted" else "Background location access was not granted",
                Toast.LENGTH_SHORT
            ).show()
            finishPermissionFlow()
        }

    private val unknownSourcesSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            lifecycleScope.launch {
                InAppUpdateManager.resumePendingInstall(this@MainActivity)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNavigationIntent(intent)

        lifecycleScope.launch {
            PokemonSpeciesRepository.getInstance(applicationContext).syncIfNeeded()
        }

        lifecycleScope.launch {
            InAppUpdateManager.restorePendingInstall(this@MainActivity)
        }

        setContent {
            val showBackgroundLocationDialog by backgroundLocationPermissionNeeded.collectAsStateWithLifecycle()
            val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            val requestedTab by requestedRootTab.collectAsStateWithLifecycle()
            
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())

            // Track whether we should show onboarding or the main app
            var showOnboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }
            if (showOnboarding == null && onboardingCompleted != null) {
                showOnboarding = onboardingCompleted != true
            }

            LaunchedEffect(showOnboarding) {
                if (showOnboarding == false) {
                    startPermissionFlow()
                }
            }

            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                // Keep splash while determining initial screen
                if (showOnboarding == null) {
                    return@PokemonAlertsV2Theme
                }

                if (showOnboarding == true) {
                    com.example.pokemonalertsv2.ui.onboarding.OnboardingScreen(
                        onFinish = {
                            settingsViewModel.completeOnboarding()
                            showOnboarding = false
                        }
                    )
                } else {
                    MainScaffold(
                        alertsViewModel = alertsViewModel,
                        historyViewModelProvider = { historyViewModel },
                        settingsViewModel = settingsViewModel,
                        requestedTab = requestedTab,
                        onRequestedTabConsumed = { requestedRootTab.value = null },
                        onManageLocationPermissions = ::restartLocationPermissionFlow,
                        onOpenUnknownSourcesSettings = {
                            unknownSourcesSettingsLauncher.launch(
                                InAppUpdateManager.unknownSourcesSettingsIntent(this@MainActivity)
                            )
                        }
                    )
                }

                if (showBackgroundLocationDialog) {
                    BackgroundLocationPermissionDialog(
                        onDismiss = {
                            backgroundLocationPermissionNeeded.value = false
                            finishPermissionFlow()
                        },
                        onOpenSettings = {
                            backgroundLocationPermissionNeeded.value = false
                            val launchResult = launchAppLocationPermissionSettings(
                                context = this@MainActivity,
                                launch = backgroundLocationSettingsLauncher::launch
                            )
                            if (launchResult == LocationSettingsLaunchResult.FAILED) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Unable to open Location settings. Open Settings > Apps > Pokemon Alerts > Permissions.",
                                    Toast.LENGTH_LONG
                                ).show()
                                finishPermissionFlow()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    internal fun handleNavigationIntent(intent: Intent) {
        requestedTab(intent)?.let { requestedRootTab.value = it }
    }

    private fun startPermissionFlow() {
        if (permissionStep != PermissionStep.IDLE) return
        requestNotificationPermissionStep()
    }

    private fun requestNotificationPermissionStep() {
        permissionStep = PermissionStep.NOTIFICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestForegroundLocationStep()
    }

    private fun requestForegroundLocationStep() {
        permissionStep = PermissionStep.FOREGROUND_LOCATION
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted && !coarseLocationGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        requestBackgroundLocationStep()
    }

    private fun requestBackgroundLocationStep() {
        permissionStep = PermissionStep.BACKGROUND_LOCATION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocationPermission()) {
            finishPermissionFlow()
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            backgroundLocationPermissionNeeded.value = true
        }
    }

    private fun hasBackgroundLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun finishPermissionFlow() {
        permissionStep = PermissionStep.COMPLETE
    }

    private fun restartLocationPermissionFlow() {
        if (permissionStep.isRequestActive()) return
        requestForegroundLocationStep()
    }

    companion object {
        private const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        private const val ALERTS_TAB_INDEX = 0

        internal fun createAlertsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_INITIAL_TAB, ALERTS_TAB_INDEX)
            }

        internal fun requestedTab(intent: Intent?): Int? =
            intent?.getIntExtra(EXTRA_INITIAL_TAB, -1)
                ?.takeIf { it in NAV_DESTINATIONS.indices }
    }
}

// ── Main Scaffold with Bottom Navigation ─────────────────────────────────

@Composable
private fun MainScaffold(
    alertsViewModel: PokemonAlertsViewModel,
    historyViewModelProvider: () -> AlertHistoryViewModel,
    settingsViewModel: SettingsViewModel,
    requestedTab: Int?,
    onRequestedTabConsumed: () -> Unit,
    onManageLocationPermissions: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current
    val updateState by InAppUpdateManager.updateState.collectAsStateWithLifecycle(initialValue = UpdateState.Idle)

    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            selectedTab = it
            onRequestedTabConsumed()
        }
    }

    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateState.UpToDate -> {
                Toast.makeText(context, "App is up to date", Toast.LENGTH_SHORT).show()
                InAppUpdateManager.resetState()
            }
            is UpdateState.Error -> {
                val errorMsg = (updateState as UpdateState.Error).message
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                InAppUpdateManager.resetState()
            }
            else -> {}
        }
    }

    val colors = LocalLinearModernColors.current

    LinearModernBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = colors.foreground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = colors.bgElevated.copy(alpha = 0.8f),
                    modifier = Modifier.border(1.dp, Brush.verticalGradient(listOf(colors.borderDefault, Color.Transparent)), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    tonalElevation = 0.dp
                ) {
                    NAV_DESTINATIONS.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index)
                                        destination.selectedIcon
                                    else
                                        destination.unselectedIcon,
                                    contentDescription = destination.label
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    fontWeight = if (selectedTab == index)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = colors.accent,
                                indicatorColor = colors.accent,
                                unselectedIconColor = colors.foregroundMuted.copy(alpha = 0.8f),
                                unselectedTextColor = colors.foregroundMuted.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                saveableStateHolder.SaveableStateProvider(selectedTab) {
                    when (selectedTab) {
                        0 -> {
                            PokemonAlertsRoute(
                                viewModel = alertsViewModel,
                                snackbarHostState = snackbarHostState
                            )
                        }
                        1 -> {
                            val historyViewModel = historyViewModelProvider()
                            val historyUiState by historyViewModel.uiState.collectAsStateWithLifecycle()
                            com.example.pokemonalertsv2.ui.alerts.AlertHistoryRoute(
                                uiState = historyUiState,
                                snackbarHostState = snackbarHostState,
                                onRefresh = historyViewModel::refreshHistoryAndStats,
                                onLoadMore = historyViewModel::loadMore,
                                onDateChanged = historyViewModel::setDateFilter,
                                onTypeChanged = historyViewModel::setTypeFilter,
                                onSearchChanged = historyViewModel::setSearchQuery,
                                consumeError = historyViewModel::consumeError
                            )
                        }
                        2 -> {
                            AlertsMapRoute(
                                viewModel = alertsViewModel,
                                onBack = { selectedTab = 0 }
                            )
                        }
                        3 -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onManageLocationPermissions = onManageLocationPermissions
                            )
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
                            Text("A new version of PokemonAlerts (${state.release.tagName}) is available. Would you like to download and install it?")
                            if (!state.release.body.isNullOrBlank()) {
                                Text(
                                    text = "Release Notes:\n${state.release.body}",
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
                                scope.launch {
                                    InAppUpdateManager.downloadAndInstall(context, state.release)
                                }
                            }
                        ) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { InAppUpdateManager.resetState() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            is UpdateState.Downloading -> {
                AlertDialog(
                    onDismissRequest = {}, // Force non-dismissable during download
                    title = { Text("Downloading Update") },
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
                    confirmButton = {}
                )
            }
            is UpdateState.AwaitingInstallPermission -> {
                AlertDialog(
                    onDismissRequest = { InAppUpdateManager.cancelPendingInstall(context) },
                    title = { Text("Install Permission Required") },
                    text = {
                        Text("Allow Pokemon Alerts to install ${state.releaseTag}, then return here. Installation will continue automatically.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onOpenUnknownSourcesSettings
                        ) {
                            Text("Open Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { InAppUpdateManager.cancelPendingInstall(context) }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            is UpdateState.Error -> Unit
            is UpdateState.Installing -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Installing...") },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Launching installer...")
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
private fun BackgroundLocationPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Background Location Access") },
        text = { 
            Text(text = "To get accurate distances and enable location-based features even when the app is in the background, please grant 'Allow all the time' location permission in settings.") 
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = "Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Not Now")
            }
        }
    )
}
