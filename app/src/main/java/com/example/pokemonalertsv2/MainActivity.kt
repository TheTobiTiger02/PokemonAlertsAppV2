package com.example.pokemonalertsv2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.work.AlertAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository

/**
 * Bottom navigation destinations.
 * Onboarding is handled separately and is not part of the nav bar.
 */
private data class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

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
    private val exactAlarmPermissionNeeded = MutableStateFlow(false)
    private val backgroundLocationPermissionNeeded = MutableStateFlow(false)

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            if (fineLocationGranted || coarseLocationGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundLocationPermission()
                } else {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Location permission is needed for distance calculations and map features",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Background location access granted", Toast.LENGTH_SHORT).show()
            } else {
                backgroundLocationPermissionNeeded.value = true
            }
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
        }

    private val exactAlarmPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val shouldPrompt = AlertAlarmScheduler.shouldPromptForPermission(this)
            if (!shouldPrompt) {
                AlertAlarmScheduler.prime(this)
                Toast.makeText(
                    this,
                    getString(R.string.exact_alarm_permission_granted),
                    Toast.LENGTH_SHORT
                ).show()
            }
            exactAlarmPermissionNeeded.value = shouldPrompt
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            PokemonSpeciesRepository.getInstance(applicationContext).syncIfNeeded()
        }

        requestNotificationPermissionIfNeeded()
        requestLocationPermissionIfNeeded()
        exactAlarmPermissionNeeded.value = AlertAlarmScheduler.shouldPromptForPermission(this)
        setContent {
            val showExactAlarmDialog by exactAlarmPermissionNeeded.collectAsStateWithLifecycle()
            val showBackgroundLocationDialog by backgroundLocationPermissionNeeded.collectAsStateWithLifecycle()
            val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            
            val darkTheme = true

            // Track whether we should show onboarding or the main app
            var showOnboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }
            if (showOnboarding == null && onboardingCompleted != null) {
                showOnboarding = onboardingCompleted != true
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
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }

                if (showExactAlarmDialog) {
                    ExactAlarmPermissionDialog(
                        onDismiss = { exactAlarmPermissionNeeded.value = false },
                        onOpenSettings = {
                            exactAlarmPermissionNeeded.value = false
                            val intent = AlertAlarmScheduler.createSettingsIntent(this@MainActivity)
                                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                            exactAlarmPermissionLauncher.launch(intent)
                        }
                    )
                }

                if (showBackgroundLocationDialog) {
                    BackgroundLocationPermissionDialog(
                        onDismiss = { backgroundLocationPermissionNeeded.value = false },
                        onOpenSettings = {
                            backgroundLocationPermissionNeeded.value = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val shouldPrompt = AlertAlarmScheduler.shouldPromptForPermission(this)
        exactAlarmPermissionNeeded.value = shouldPrompt
        if (!shouldPrompt) {
            AlertAlarmScheduler.prime(this)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestLocationPermissionIfNeeded() {
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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundLocationGranted) {
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }
}

// ── Main Scaffold with Bottom Navigation ─────────────────────────────────

@Composable
private fun MainScaffold(
    alertsViewModel: PokemonAlertsViewModel,
    historyViewModel: AlertHistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                    tonalElevation = 6.dp
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
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> {
                        PokemonAlertsRoute(
                            viewModel = alertsViewModel,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    1 -> {
                        val historyUiState by historyViewModel.uiState.collectAsStateWithLifecycle()
                        com.example.pokemonalertsv2.ui.alerts.AlertHistoryRoute(
                            uiState = historyUiState,
                            snackbarHostState = snackbarHostState,
                            onRefresh = historyViewModel::refreshHistory,
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
                            onBackClick = { selectedTab = 0 }
                        )
                    }
                }
            }
        }
    }
}

// ── Permission Dialogs ───────────────────────────────────────────────────

@Composable
private fun ExactAlarmPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.exact_alarm_permission_title)) },
        text = { Text(text = stringResource(R.string.exact_alarm_permission_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.exact_alarm_permission_positive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.exact_alarm_permission_negative))
            }
        }
    )
}

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
