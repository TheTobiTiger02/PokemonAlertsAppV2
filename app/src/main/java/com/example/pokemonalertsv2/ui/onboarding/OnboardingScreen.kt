package com.example.pokemonalertsv2.ui.onboarding

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.CheckCircle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.ALERT_DISTANCE_STEPS_METERS
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.data.distanceStepIndex
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import com.example.pokemonalertsv2.ui.alerts.AREA_FILTER_OPTIONS
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.motion.appRiseIn
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.ui.motion.appSinkOut

@Composable
fun OnboardingScreen(
    initialArea: String,
    initialMaxDistance: Int,
    onAreaChanged: (String) -> Unit,
    onMaxDistanceChanged: (Int) -> Unit,
    onPresetSelected: (NotificationPreset) -> Unit,
    onFinish: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var area by rememberSaveable(initialArea) { mutableStateOf(initialArea) }
    var distance by rememberSaveable(initialMaxDistance) { mutableIntStateOf(initialMaxDistance) }
    var presetName by rememberSaveable { mutableStateOf(NotificationPreset.EVERYTHING.name) }

    BackHandler(enabled = step > 0) { step-- }
    val lastStep = STEP_PERMISSIONS

    LinearModernBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "QUICK SETUP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${step + 1} / ${lastStep + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { (step + 1) / (lastStep + 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { appSharedAxisX(forward = targetState > initialState) },
                    label = "onboarding_step"
                ) { currentStep ->
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        when (currentStep) {
                            0 -> SetupIntro()
                            1 -> TourCard(
                                icon = Icons.Filled.Notifications,
                                title = "Alerts: live, history and insights",
                                points = listOf(
                                    "Live lists every active alert, newest first. Tap one for details and directions.",
                                    "Long press an alert for I’m going, snooze, share or dismiss. Swipe left to dismiss.",
                                    "History and Insights show what spawned before, and when a species tends to appear."
                                )
                            )
                            2 -> TourCard(
                                icon = Icons.Filled.LocationOn,
                                title = "The map",
                                points = listOf(
                                    "The chips along the top show or hide each alert type.",
                                    "The filter button holds filters, map style and overlays; the gear opens Settings.",
                                    "The tools button starts a hunt, plans a catch route or opens the floating map."
                                )
                            )
                            3 -> TourCard(
                                icon = Icons.Filled.Settings,
                                title = "Filters, per place",
                                points = listOf(
                                    "The feed, the map, notifications and each widget keep their own filter.",
                                    "The filter button on the feed or the map edits that one in place.",
                                    "Save a filter as a profile to reuse it somewhere else."
                                )
                            )
                            STEP_AREA -> AreaSetup(area, distance, { area = it }, { distance = it })
                            STEP_PRESET -> PresetSetup(NotificationPreset.valueOf(presetName)) { presetName = it.name }
                            else -> PermissionSetup()
                        }
                    }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(
                        visible = step > 0,
                        enter = appRiseIn(),
                        exit = appSinkOut(),
                        modifier = Modifier.weight(1f)
                    ) {
                        // The tour is optional; everything after it is setup.
                        if (step in 1 until STEP_AREA) {
                            OutlinedButton(onClick = { step = STEP_AREA }, modifier = Modifier.fillMaxWidth()) { Text("Skip tour") }
                        } else {
                            OutlinedButton(onClick = { step-- }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                        }
                    }
                    Button(
                        onClick = {
                            if (step < lastStep) step++ else {
                                onAreaChanged(area)
                                onMaxDistanceChanged(distance)
                                onPresetSelected(NotificationPreset.valueOf(presetName))
                                onFinish()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = step == lastStep,
                            transitionSpec = { appFadeThrough() },
                            label = "onboarding_primary_action"
                        ) { finishing ->
                            Text(if (finishing) "Finish" else "Continue")
                        }
                    }
                }
                AnimatedVisibility(
                    visible = step == lastStep,
                    enter = appRiseIn(),
                    exit = appSinkOut()
                ) {
                    Text(
                        "You can change any of these later in Settings › Notifications & permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupIntro() = SetupHeader(
    Icons.Filled.Star,
    "Catch the alerts that matter",
    "Pokémon Alerts shows live nearby activity, remaining time, distance, and navigation. Background updates keep notifications and widgets useful when the app is closed."
)

@Composable
private fun AreaSetup(area: String, distance: Int, onArea: (String) -> Unit, onDistance: (Int) -> Unit) {
    SetupHeader(Icons.Filled.LocationOn, "Choose your alert area", "These choices can be changed at any time in Filters.")
    Text("Area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AREA_FILTER_OPTIONS.forEach { value ->
            FilterChip(selected = area == value, onClick = { onArea(value) }, label = { Text(value) })
        }
    }
    Text(
        "Distance: ${distanceLabel(distance)}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    com.example.pokemonalertsv2.ui.components.DistanceLimitPicker(meters = distance, onChange = onDistance)
}

@Composable
private fun PresetSetup(selected: NotificationPreset, onSelected: (NotificationPreset) -> Unit) {
    SetupHeader(Icons.Filled.Notifications, "Choose notification intensity", "Presets only set alert categories. Fine-grained species and raid filters remain available in Settings.")
    listOf(
        NotificationPreset.EVERYTHING to "Every supported alert category",
        NotificationPreset.HIGH_VALUE to "Spawns, Hundos, PvP, Nundos, and Kecleon",
        NotificationPreset.QUIET_ESSENTIALS to "Only Hundos, Nundos, and Kecleon"
    ).forEach { (preset, description) ->
        Surface(
            onClick = { onSelected(preset) },
            shape = MaterialTheme.shapes.large,
            color = if (selected == preset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(preset.label, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val STEP_AREA = 4
private const val STEP_PRESET = 5
private const val STEP_PERMISSIONS = 6

@Composable
private fun TourCard(icon: ImageVector, title: String, points: List<String>) {
    SetupHeader(icon, title, "")
    points.forEach { point ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(point, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Every permission the app wants, asked here and only here. The app used to fire the
 * notification prompt and a background-location dialog back to back on every launch until
 * they were granted; now a missing permission shows as one card in Settings instead.
 */
@Composable
private fun PermissionSetup() {
    val context = LocalContext.current
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun notificationsGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        granted(Manifest.permission.POST_NOTIFICATIONS)
    fun locationGranted() = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun backgroundGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    var notifications by remember { mutableStateOf(notificationsGranted()) }
    var location by remember { mutableStateOf(locationGranted()) }
    var background by remember { mutableStateOf(backgroundGranted()) }
    fun refresh() {
        notifications = notificationsGranted(); location = locationGranted(); background = backgroundGranted()
    }
    // Background location is granted on a system settings page, so re-check on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    SetupHeader(
        Icons.Filled.Notifications,
        "Allow access",
        "Each one is optional. The app works without them, with fewer features."
    )
    PermissionRow(
        title = "Notifications",
        description = "Get new alerts even when the app is closed.",
        granted = notifications,
        onAllow = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )
    PermissionRow(
        title = "Location",
        description = "Show distances and walking times, and find you on the map.",
        granted = location,
        onAllow = {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PermissionRow(
            title = "Location all the time",
            description = if (location) "Keeps distances right in notifications and widgets. Choose \u201CAllow all the time\u201D."
            else "Allow location first.",
            granted = background,
            enabled = location,
            onAllow = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        )
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, enabled: Boolean = true, onAllow: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Allowed", tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onAllow, enabled = enabled) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun SetupHeader(icon: ImageVector, title: String, description: String) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (description.isNotEmpty()) {
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(4.dp))
}
