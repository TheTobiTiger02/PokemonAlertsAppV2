package com.example.pokemonalertsv2.tracking

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.launchAppLocationPermissionSettings
import kotlinx.coroutines.launch

data class ArrivalTrackingUiController(
    val activeDestination: TrackedDestination?,
    val onToggle: (PokemonAlert) -> Unit
) {
    fun isTracking(alert: PokemonAlert): Boolean =
        activeDestination?.uniqueId == alert.uniqueId
}

private enum class ArrivalRequirement {
    PRECISE_LOCATION,
    NOTIFICATIONS,
    LOCATION_SERVICES
}

@Composable
fun rememberArrivalTrackingUiController(): ArrivalTrackingUiController {
    val context = LocalContext.current
    val repository = remember(context) { ArrivalTrackingRepository.getInstance(context) }
    val activeDestination by repository.activeDestination.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pendingAlert by remember { mutableStateOf<PokemonAlert?>(null) }
    var replacementAlert by remember { mutableStateOf<PokemonAlert?>(null) }
    var requirement by remember { mutableStateOf<ArrivalRequirement?>(null) }

    fun start(alert: PokemonAlert) {
        scope.launch {
            runCatching {
                val destination = repository.startTracking(alert)
                ArrivalTrackingService.start(context)
                destination
            }.onSuccess {
                Toast.makeText(
                    context,
                    "Arrival alert set for ${it.radiusMeters} m",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(context, "Could not start arrival tracking", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun continueStart(alert: PokemonAlert) {
        when {
            !hasNotificationPermission(context) -> {
                pendingAlert = alert
                requirement = ArrivalRequirement.NOTIFICATIONS
            }
            !hasFineLocationPermission(context) -> {
                pendingAlert = alert
                requirement = ArrivalRequirement.PRECISE_LOCATION
            }
            !areLocationServicesEnabled(context) -> {
                pendingAlert = alert
                requirement = ArrivalRequirement.LOCATION_SERVICES
            }
            else -> {
                pendingAlert = null
                start(alert)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingAlert?.let(::continueStart)
    }

    fun requestStart(alert: PokemonAlert) {
        val permissions = buildList {
            if (!hasFineLocationPermission(context)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission(context)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            pendingAlert = alert
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            continueStart(alert)
        }
    }

    replacementAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = { replacementAlert = null },
            title = { Text("Switch destination?") },
            text = {
                Text(
                    "Stop tracking ${activeDestination?.alert?.pokemon ?: activeDestination?.alert?.name ?: "the current alert"} " +
                        "and start going to ${alert.pokemon ?: alert.name}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        replacementAlert = null
                        requestStart(alert)
                    }
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { replacementAlert = null }) { Text("Cancel") }
            }
        )
    }

    requirement?.let { missing ->
        val (title, message, actionLabel) = when (missing) {
            ArrivalRequirement.PRECISE_LOCATION -> Triple(
                "Precise location required",
                "Arrival tracking needs Android Precise location to reliably detect your selected radius.",
                "Open permissions"
            )
            ArrivalRequirement.NOTIFICATIONS -> Triple(
                "Notifications required",
                "Enable notifications so the app can show the active journey and tell you when you arrive.",
                "Open settings"
            )
            ArrivalRequirement.LOCATION_SERVICES -> Triple(
                "Turn on location",
                "Location services must be enabled before arrival tracking can start.",
                "Location settings"
            )
        }
        AlertDialog(
            onDismissRequest = {
                requirement = null
                pendingAlert = null
            },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        requirement = null
                        when (missing) {
                            ArrivalRequirement.PRECISE_LOCATION ->
                                launchAppLocationPermissionSettings(context) { intent ->
                                    context.startActivity(intent)
                                }
                            ArrivalRequirement.NOTIFICATIONS ->
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                )
                            ArrivalRequirement.LOCATION_SERVICES ->
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    }
                ) {
                    Text(actionLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        requirement = null
                        pendingAlert = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    return ArrivalTrackingUiController(
        activeDestination = activeDestination,
        onToggle = { alert ->
            when {
                activeDestination?.uniqueId == alert.uniqueId -> {
                    scope.launch {
                        repository.stopTracking()
                        Toast.makeText(context, "Arrival tracking stopped", Toast.LENGTH_SHORT).show()
                    }
                }
                !alert.isEligibleArrivalDestination() -> {
                    Toast.makeText(
                        context,
                        "This alert is expired or has no valid destination",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                activeDestination != null -> replacementAlert = alert
                else -> requestStart(alert)
            }
        }
    )
}

internal fun hasFineLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

internal fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

internal fun areLocationServicesEnabled(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
