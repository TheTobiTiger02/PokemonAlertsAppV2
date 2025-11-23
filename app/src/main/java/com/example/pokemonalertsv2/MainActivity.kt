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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.alerts.PokemonAlertsRoute
import com.example.pokemonalertsv2.ui.alerts.PokemonAlertsViewModel
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.work.AlertAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.pokemonalertsv2.ui.settings.SettingsScreen
import com.example.pokemonalertsv2.ui.settings.SettingsViewModel
import com.example.pokemonalertsv2.ui.history.AlertHistoryRoute
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel

private enum class Screen { Onboarding, Alerts, Settings, History }

class MainActivity : ComponentActivity() {

    private val alertsViewModel: PokemonAlertsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: AlertHistoryViewModel by viewModels()
    private val exactAlarmPermissionNeeded = MutableStateFlow(false)

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
        requestNotificationPermissionIfNeeded()
        exactAlarmPermissionNeeded.value = AlertAlarmScheduler.shouldPromptForPermission(this)
        setContent {
            val showExactAlarmDialog by exactAlarmPermissionNeeded.collectAsStateWithLifecycle()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            
            val darkTheme = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> isSystemInDarkTheme()
            }

            var currentScreen by rememberSaveable { mutableStateOf<Screen?>(null) }

            // Decide initial screen once onboarding state is loaded
            if (currentScreen == null && onboardingCompleted != null) {
                currentScreen = if (onboardingCompleted == true) Screen.Alerts else Screen.Onboarding
            }

            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                // Show nothing or splash while determining initial screen
                if (currentScreen == null) {
                    // Keep splash screen active or show empty box
                    return@PokemonAlertsV2Theme
                }

                when (currentScreen!!) {
                    Screen.Onboarding -> {
                        com.example.pokemonalertsv2.ui.onboarding.OnboardingScreen(
                            onFinish = {
                                settingsViewModel.completeOnboarding()
                                currentScreen = Screen.Alerts
                            }
                        )
                    }
                    Screen.Alerts -> {
                        PokemonAlertsRoute(
                            viewModel = alertsViewModel,
                            onSettingsClick = { currentScreen = Screen.Settings },
                            onHistoryClick = { currentScreen = Screen.History }
                        )
                    }
                    Screen.Settings -> {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBackClick = { currentScreen = Screen.Alerts }
                        )
                    }
                    Screen.History -> {
                        AlertHistoryRoute(
                            viewModel = historyViewModel,
                            onBackClick = { currentScreen = Screen.Alerts }
                        )
                    }
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
}

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