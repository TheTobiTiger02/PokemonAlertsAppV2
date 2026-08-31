package com.example.pokemonalertsv2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import com.example.pokemonalertsv2.ui.theme.Alphas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.ui.settings.SettingsScreen
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.navigation.DeepLinkTarget
import com.example.pokemonalertsv2.navigation.parseDeepLink
import com.example.pokemonalertsv2.ui.settings.SettingsDestination
import com.example.pokemonalertsv2.ui.settings.SettingsViewModel
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportCandidate
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

internal const val ALERTS_TAB_INDEX = 0
internal const val MAP_TAB_INDEX = 2

internal fun rootTabIndexOrNull(index: Int): Int? =
    index.takeIf { it in NAV_DESTINATIONS.indices }

class MainActivity : ComponentActivity() {

    private val alertsViewModel: PokemonAlertsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: AlertHistoryViewModel by viewModels()
    private val backgroundLocationPermissionNeeded = MutableStateFlow(false)
    private val requestedRootTab = MutableStateFlow<Int?>(null)
    private val requestedSettingsDestination = MutableStateFlow<SettingsDestination?>(null)
    private var lastExternalCsvUri: String? = null
    private var permissionStep = PermissionStep.IDLE

    /**
     * Permission callbacks fire outside composition, so their results are pushed here and
     * shown on the app's own SnackbarHost rather than as a Toast that floats over whatever
     * system dialog is still on screen.
     */
    private val transientMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private fun showMessage(text: String) {
        transientMessages.tryEmit(text)
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            if (fineLocationGranted || coarseLocationGranted) {
                showMessage("Location permission granted")
                requestBackgroundLocationStep()
            } else {
                showMessage("Location permission is needed for distance calculations and map features")
                finishPermissionFlow()
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            showMessage(
                if (isGranted) "Background location access granted"
                else "Background location access was not granted"
            )
            finishPermissionFlow()
        }

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                showMessage(getString(R.string.notification_permission_rationale))
            }
            requestForegroundLocationStep()
        }

    private val backgroundLocationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val granted = hasBackgroundLocationPermission()
            showMessage(
                if (granted) "Background location access granted"
                else "Background location access was not granted"
            )
            finishPermissionFlow()
        }

    private val unknownSourcesSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            lifecycleScope.launch {
                InAppUpdateManager.resumePendingInstall(this@MainActivity)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashView ->
            splashView.view.animate()
                .alpha(0f)
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(splashView::remove)
                .start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Re-prepare the URI after process recreation; preparation is read-only and the
        // candidate remains uncommitted until the user confirms it.
        lastExternalCsvUri = null
        handleNavigationIntent(intent)

        lifecycleScope.launch(Dispatchers.IO) {
            PokemonSpeciesRepository.getInstance(applicationContext).syncIfNeeded()
        }

        lifecycleScope.launch {
            InAppUpdateManager.restorePendingInstall(this@MainActivity)
        }

        setContent {
            val showBackgroundLocationDialog by backgroundLocationPermissionNeeded.collectAsStateWithLifecycle()
            val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            val requestedTab by requestedRootTab.collectAsStateWithLifecycle()
            val requestedSettings by requestedSettingsDestination.collectAsStateWithLifecycle()
            val pendingPokeGenieImport by settingsViewModel.pendingPokeGenieImport
                .collectAsStateWithLifecycle(initialValue = null)
            
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val onboardingArea by settingsViewModel.selectedArea.collectAsStateWithLifecycle()
            val onboardingDistance by settingsViewModel.maxDistance.collectAsStateWithLifecycle()
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

            // A pending CSV URI is restored by SettingsViewModel after process recreation.
            // Re-open the same destination so the user never has to hunt for the preview.
            LaunchedEffect(pendingPokeGenieImport) {
                if (pendingPokeGenieImport != null) {
                    requestedRootTab.value = NAV_SETTINGS_TAB_INDEX
                    requestedSettingsDestination.value = SettingsDestination.RAID_COUNTERS
                }
            }

            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                // Keep splash while determining initial screen
                if (showOnboarding == null) {
                    return@PokemonAlertsV2Theme
                }

                AnimatedContent(
                    targetState = showOnboarding == true,
                    transitionSpec = { appSharedAxisX(forward = !targetState) },
                    label = "onboarding_to_app"
                ) { onboardingVisible ->
                    if (onboardingVisible) {
                        com.example.pokemonalertsv2.ui.onboarding.OnboardingScreen(
                            initialArea = onboardingArea,
                            initialMaxDistance = onboardingDistance,
                            onAreaChanged = settingsViewModel::updateSelectedArea,
                            onMaxDistanceChanged = settingsViewModel::updateMaxDistance,
                            onPresetSelected = settingsViewModel::applyNotificationPreset,
                            onFinish = {
                                settingsViewModel.completeOnboarding()
                                showOnboarding = false
                            }
                        )
                    } else {
                        MainScaffold(
                            messages = transientMessages,
                            alertsViewModel = alertsViewModel,
                            historyViewModelProvider = { historyViewModel },
                            settingsViewModel = settingsViewModel,
                            requestedTab = requestedTab,
                            onRequestedTabConsumed = { requestedRootTab.value = null },
                            requestedSettingsDestination = requestedSettings,
                            onRequestedSettingsDestinationConsumed = {
                                requestedSettingsDestination.value = null
                            },
                            onManageLocationPermissions = ::restartLocationPermissionFlow,
                            onOpenUnknownSourcesSettings = {
                                unknownSourcesSettingsLauncher.launch(
                                    InAppUpdateManager.unknownSourcesSettingsIntent(this@MainActivity)
                                )
                            }
                        )
                    }
                }

                if (showBackgroundLocationDialog) {
                    BackgroundLocationPermissionDialog(
                        onDismiss = {
                            backgroundLocationPermissionNeeded.value = false
                            finishPermissionFlow()
                        },
                        onOpenSettings = {
                            backgroundLocationPermissionNeeded.value = false
                            backgroundLocationPermissionLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
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
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                if (handleDeepLink(uri.toString())) return
            }
        }
        handleExternalCsvIntent(intent)
    }

    internal fun handleExternalCsvIntent(intent: Intent): Boolean {
        val uri = externalCsvUri(intent) ?: return false
        if (!isSupportedCsvIntent(intent, uri)) return false
        val key = uri.toString()
        if (key == lastExternalCsvUri) return false
        lastExternalCsvUri = key
        runCatching {
            val read = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
            val write = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
            when {
                read && write -> contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                read -> contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                write -> contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        settingsViewModel.preparePokeGenieImport(uri)
        requestedRootTab.value = NAV_SETTINGS_TAB_INDEX
        requestedSettingsDestination.value = SettingsDestination.RAID_COUNTERS
        return true
    }

    internal fun pendingExternalCsvImportForTest(): PokeGenieImportCandidate? =
        settingsViewModel.pendingPokeGenieImport.value

    /**
     * Routes a `pokemonalerts://` link onto the same state the tab extras drive.
     * Returns true when the link was ours, so the CSV branch below is skipped.
     */
    private fun handleDeepLink(url: String): Boolean {
        when (val target = parseDeepLink(url) ?: return false) {
            is DeepLinkTarget.RootTab -> requestedRootTab.value = target.tabIndex
            is DeepLinkTarget.Settings -> {
                requestedRootTab.value = NAV_SETTINGS_TAB_INDEX
                requestedSettingsDestination.value = target.destination
            }
            is DeepLinkTarget.Alert -> {
                // The alert has to be resolved from the repository before the detail screen
                // can flatten it into extras; fall back to the feed when it is gone
                // (expired, or never seen on this device).
                lifecycleScope.launch {
                    val alert = withContext(Dispatchers.IO) {
                        runCatching {
                            PokemonAlertsRepository.create(applicationContext)
                                .getLocalAlerts()
                                .firstOrNull {
                                    it.uniqueId == target.alertId || it.id?.toString() == target.alertId
                                }
                        }.getOrNull()
                    }
                    if (alert != null) {
                        startActivity(
                            AlertDetailActivity.createIntent(
                                this@MainActivity,
                                alert,
                                returnToAlerts = true
                            )
                        )
                    } else {
                        requestedRootTab.value = ALERTS_TAB_INDEX
                        showMessage("That alert is no longer available")
                    }
                }
            }
        }
        return true
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
        private const val NAV_SETTINGS_TAB_INDEX = 3

        internal fun createAlertsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_INITIAL_TAB, ALERTS_TAB_INDEX)
            }

        internal fun createMapIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_INITIAL_TAB, MAP_TAB_INDEX)
            }

        internal fun requestedTab(intent: Intent?): Int? =
            intent?.getIntExtra(EXTRA_INITIAL_TAB, -1)
                ?.let(::rootTabIndexOrNull)
    }
}

private val SUPPORTED_EXTERNAL_CSV_MIME_TYPES = setOf(
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "application/vnd.ms-excel"
)

/** Returns the one externally supplied CSV URI for supported open/share actions. */
internal fun externalCsvUri(intent: Intent): Uri? = when (intent.action) {
    Intent.ACTION_VIEW -> intent.data
    Intent.ACTION_SEND -> intent.sharedStreamUri() ?: intent.clipData
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.uri
    else -> null
}

@Suppress("DEPRECATION")
private fun Intent.sharedStreamUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
} else {
    getParcelableExtra(Intent.EXTRA_STREAM)
}

internal fun isSupportedCsvIntent(intent: Intent, uri: Uri): Boolean {
    val mime = intent.type?.lowercase()?.substringBefore(';')
    val mimeSupported = mime in SUPPORTED_EXTERNAL_CSV_MIME_TYPES
    val extensionSupported = uri.lastPathSegment
        ?.substringBefore('?')
        ?.endsWith(".csv", ignoreCase = true) == true
    return mimeSupported || extensionSupported
}

// ── Main Scaffold with Bottom Navigation ─────────────────────────────────

/**
 * The icon and label are shared between [NavigationBarItem] and [NavigationRailItem]:
 * two copies of the same selected/unselected treatment is exactly the kind of thing that
 * drifts once one of the branches is edited.
 */
@Composable
private fun NavDestinationIcon(destination: NavDestination, selected: Boolean) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = { appFadeThrough() },
        label = "${destination.label}_nav_icon"
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.label
        )
    }
}

@Composable
private fun NavDestinationLabel(destination: NavDestination, selected: Boolean) {
    Text(
        text = destination.label,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun MainScaffold(
    messages: SharedFlow<String>,
    alertsViewModel: PokemonAlertsViewModel,
    historyViewModelProvider: () -> AlertHistoryViewModel,
    settingsViewModel: SettingsViewModel,
    requestedTab: Int?,
    onRequestedTabConsumed: () -> Unit,
    requestedSettingsDestination: SettingsDestination?,
    onRequestedSettingsDestinationConsumed: () -> Unit,
    onManageLocationPermissions: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val updateState by InAppUpdateManager.updateState.collectAsStateWithLifecycle(initialValue = UpdateState.Idle)

    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            selectedTab = it
            onRequestedTabConsumed()
        }
    }

    BackHandler(enabled = selectedTab == MAP_TAB_INDEX) {
        selectedTab = ALERTS_TAB_INDEX
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
                        NAV_DESTINATIONS.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { NavDestinationIcon(destination, selectedTab == index) },
                                label = { NavDestinationLabel(destination, selectedTab == index) },
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
            Row(modifier = Modifier.padding(paddingValues)) {
                if (layoutMode == NavigationLayoutMode.RAIL) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = Alphas.Elevated),
                        modifier = Modifier.border(
                            1.dp,
                            Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outline, Color.Transparent)),
                            RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                    ) {
                        NAV_DESTINATIONS.forEachIndexed { index, destination ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { NavDestinationIcon(destination, selectedTab == index) },
                                label = { NavDestinationLabel(destination, selectedTab == index) },
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
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { appSharedAxisX(forward = targetState > initialState) },
                    contentKey = { it },
                    label = "root_destination"
                ) { destinationIndex ->
                saveableStateHolder.SaveableStateProvider(destinationIndex) {
                    when (destinationIndex) {
                        0 -> {
                            PokemonAlertsRoute(
                                viewModel = alertsViewModel,
                                snackbarHostState = snackbarHostState,
                                onStartManualRaid = {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            com.example.pokemonalertsv2.ui.counters.ManualRaidActivity::class.java
                                        )
                                    )
                                }
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
                                onBack = { selectedTab = ALERTS_TAB_INDEX },
                                showBackButton = false,
                                onEnterPictureInPicture = if (
                                    context.packageManager.hasSystemFeature(
                                        PackageManager.FEATURE_PICTURE_IN_PICTURE
                                    )
                                ) {
                                    { zoom ->
                                        context.startActivity(
                                            com.example.pokemonalertsv2.ui.alerts.AlertsMapActivity
                                                .createPictureInPictureIntent(context, zoom)
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                        3 -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onManageLocationPermissions = onManageLocationPermissions,
                                requestedDestination = requestedSettingsDestination,
                                onRequestedDestinationConsumed = onRequestedSettingsDestinationConsumed
                            )
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
                            Text("Cancel")
                        }
                    }
                )
            }
            is UpdateState.Downloading -> {
                AlertDialog(
                    // Not dismissable by tapping outside, but Cancel below is always
                    // reachable so a stalled download cannot trap the user.
                    onDismissRequest = {},
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
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                downloadJob?.cancel()
                                downloadJob = null
                                InAppUpdateManager.cancelPendingInstall(context)
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
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
