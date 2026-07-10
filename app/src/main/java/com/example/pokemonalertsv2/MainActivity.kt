@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository

/**
 * Bottom navigation destinations.
 * Onboarding is handled separately and is not part of the nav bar.
 */
internal enum class AppDestination(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
){
    ALERTS(R.string.navigation_alerts, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    MAP(R.string.navigation_map, Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    SETTINGS(R.string.navigation_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

internal enum class AlertsSection(@StringRes val labelRes: Int) {
    LIVE(R.string.alerts_section_live),
    HISTORY(R.string.alerts_section_history)
}

internal enum class NavigationLayoutMode { BOTTOM_BAR, RAIL }

internal fun navigationLayoutModeForWidth(width: Dp): NavigationLayoutMode =
    if (width >= 600.dp) NavigationLayoutMode.RAIL else NavigationLayoutMode.BOTTOM_BAR

class MainActivity : ComponentActivity() {

    private val alertsViewModel: PokemonAlertsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: AlertHistoryViewModel by viewModels()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            PokemonSpeciesRepository.getInstance(applicationContext).syncIfNeeded()
        }

        setContent {
            val showBackgroundLocationDialog by backgroundLocationPermissionNeeded.collectAsStateWithLifecycle()
            val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            
            // Track whether we should show onboarding or the main app
            var showOnboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }
            if (showOnboarding == null && onboardingCompleted != null) {
                showOnboarding = onboardingCompleted != true
            }

            PokemonAlertsV2Theme {
                // Keep splash while determining initial screen
                if (showOnboarding == null) {
                    return@PokemonAlertsV2Theme
                }

                if (showOnboarding == true) {
                    com.example.pokemonalertsv2.ui.onboarding.OnboardingScreen(
                        onFinish = {
                            settingsViewModel.completeOnboarding()
                            showOnboarding = false
                            // Prompt only after the onboarding explanation and the user's
                            // explicit CTA; the existing denial/settings recovery remains.
                            requestNotificationPermissionIfNeeded()
                            requestLocationPermissionIfNeeded()
                        }
                    )
                } else {
                    MainScaffold(
                        alertsViewModel = alertsViewModel,
                        historyViewModelProvider = { historyViewModel },
                        settingsViewModel = settingsViewModel
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
    historyViewModelProvider: () -> AlertHistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.ALERTS) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveableStateHolder = rememberSaveableStateHolder()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val navigationLayout = navigationLayoutModeForWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            if (navigationLayout == NavigationLayoutMode.RAIL) {
                AppNavigationRail(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { selectedDestination = it }
                )
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (navigationLayout == NavigationLayoutMode.BOTTOM_BAR) {
                        AppNavigationBar(
                            selectedDestination = selectedDestination,
                            onDestinationSelected = { selectedDestination = it }
                        )
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    saveableStateHolder.SaveableStateProvider(selectedDestination.name) {
                        when (selectedDestination) {
                            AppDestination.ALERTS -> AlertsDestination(
                                alertsViewModel = alertsViewModel,
                                historyViewModelProvider = historyViewModelProvider,
                                snackbarHostState = snackbarHostState
                            )

                            AppDestination.MAP -> AlertsMapRoute(
                                viewModel = alertsViewModel,
                                onBack = { selectedDestination = AppDestination.ALERTS }
                            )

                            AppDestination.SETTINGS -> SettingsScreen(
                                viewModel = settingsViewModel,
                                onBackClick = { selectedDestination = AppDestination.ALERTS }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit
) {
    NavigationBar {
        AppDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = if (destination == selectedDestination) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit
) {
    NavigationRail {
        Spacer(modifier = Modifier.width(1.dp))
        AppDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationRailItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = if (destination == selectedDestination) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun AlertsDestination(
    alertsViewModel: PokemonAlertsViewModel,
    historyViewModelProvider: () -> AlertHistoryViewModel,
    snackbarHostState: SnackbarHostState
) {
    var selectedSection by rememberSaveable { mutableStateOf(AlertsSection.LIVE) }
    val historyViewModel = historyViewModelProvider()
    val historyUiState by historyViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.alerts_destination_title))
                            Text(
                                text = if (selectedSection == AlertsSection.LIVE) {
                                    stringResource(R.string.alerts_destination_live_subtitle)
                                } else {
                                    stringResource(R.string.alerts_destination_history_subtitle)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedSection == AlertsSection.LIVE) {
                                    alertsViewModel.refreshAlerts()
                                } else {
                                    historyViewModel.refreshHistoryAndStats()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.refresh_alerts)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                TabRow(selectedTabIndex = selectedSection.ordinal) {
                    AlertsSection.entries.forEach { section ->
                        Tab(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            text = { Text(stringResource(section.labelRes)) },
                            icon = {
                                Icon(
                                    imageVector = if (section == AlertsSection.LIVE) {
                                        Icons.Outlined.Notifications
                                    } else {
                                        Icons.Outlined.DateRange
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedSection) {
                AlertsSection.LIVE -> PokemonAlertsRoute(
                    viewModel = alertsViewModel,
                    snackbarHostState = snackbarHostState,
                    showTopBar = false
                )

                AlertsSection.HISTORY -> com.example.pokemonalertsv2.ui.alerts.AlertHistoryRoute(
                    uiState = historyUiState,
                    snackbarHostState = snackbarHostState,
                    onRefresh = historyViewModel::refreshHistoryAndStats,
                    onLoadMore = historyViewModel::loadMore,
                    onDateChanged = historyViewModel::setDateFilter,
                    onTypeChanged = historyViewModel::setTypeFilter,
                    onSearchChanged = historyViewModel::setSearchQuery,
                    consumeError = historyViewModel::consumeError,
                    showTopBar = false
                )
            }
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
