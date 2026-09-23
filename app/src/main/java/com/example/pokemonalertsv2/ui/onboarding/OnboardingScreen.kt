package com.example.pokemonalertsv2.ui.onboarding

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
                        "${step + 1} / 3",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { (step + 1) / 3f },
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { appSharedAxisX(forward = targetState > initialState) },
                    label = "onboarding_step"
                ) { currentStep ->
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        when (currentStep) {
                            0 -> AreaSetup(area, distance, { area = it }, { distance = it })
                            1 -> PresetSetup(NotificationPreset.valueOf(presetName)) { presetName = it.name }
                            else -> {
                                SetupHeader(Icons.Filled.Notifications, "Your alert layout", "Choose how you like to browse. Change this anytime above the feed or in Settings.")
                                com.example.pokemonalertsv2.ui.components.FeedLayoutPicker(preview = true)
                            }
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
                        OutlinedButton(onClick = { step-- }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                    }
                    Button(
                        onClick = {
                            if (step < 2) step++ else {
                                onAreaChanged(area)
                                onMaxDistanceChanged(distance)
                                onPresetSelected(NotificationPreset.valueOf(presetName))
                                onFinish()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = step == 2,
                            transitionSpec = { appFadeThrough() },
                            label = "onboarding_primary_action"
                        ) { finishing ->
                            Text(if (finishing) "Start exploring" else "Continue")
                        }
                    }
                }
                AnimatedVisibility(
                    visible = step == 2,
                    enter = appRiseIn(),
                    exit = appSinkOut()
                ) {
                    Text(
                        "Location and notification access are optional. Enable them when you need them, or later in Settings.",
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
    Icons.Filled.Warning,
    "Catch the alerts that matter",
    "Pokémon Alerts shows live nearby activity, remaining time, distance, and navigation. Background updates keep notifications and widgets useful when the app is closed."
)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun AreaSetup(area: String, distance: Int, onArea: (String) -> Unit, onDistance: (Int) -> Unit) {
    SetupHeader(Icons.Filled.LocationOn, "Choose your alert area", "These choices can be changed at any time in Filters.")
    Text("Area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AREA_FILTER_OPTIONS.forEach { value ->
            FilterChip(selected = area == value, onClick = { onArea(value) }, label = { Text(value) })
        }
    }
    com.example.pokemonalertsv2.ui.components.DistanceLimitControl(distance, onDistance)
}

@Composable
private fun PresetSetup(selected: NotificationPreset, onSelected: (NotificationPreset) -> Unit) {
    SetupHeader(Icons.Filled.Notifications, "Choose notification intensity", "Presets only set alert categories. Fine-grained species and raid filters remain available in Settings.")
    Text("Hundo means perfect IVs (15/15/15). Nundo means zero IVs (0/0/0).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun PermissionSetup() = SetupHeader(
    Icons.Filled.Notifications,
    "Stay informed",
    "Notifications deliver new alerts. Location calculates distance and powers map tracking. Background location keeps location-based features accurate when the app is not open."
)

@Composable
private fun SetupHeader(icon: ImageVector, title: String, description: String) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}
