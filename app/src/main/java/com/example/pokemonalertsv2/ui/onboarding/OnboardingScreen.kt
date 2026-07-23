package com.example.pokemonalertsv2.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.ui.components.LinearModernBackground

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

    LinearModernBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("Step ${step + 1} of 4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                when (step) {
                    0 -> SetupIntro()
                    1 -> AreaSetup(area, distance, { area = it }, { distance = it })
                    2 -> PresetSetup(NotificationPreset.valueOf(presetName)) { presetName = it.name }
                    else -> PermissionSetup()
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                    }
                    Button(
                        onClick = {
                            if (step < 3) step++ else {
                                onAreaChanged(area)
                                onMaxDistanceChanged(distance)
                                onPresetSelected(NotificationPreset.valueOf(presetName))
                                onFinish()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (step == 3) "Enable & finish" else "Continue") }
                }
                if (step == 3) {
                    Text(
                        "Android will ask for notification and location access next. You can decline and change these later in Settings.",
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
private fun AreaSetup(area: String, distance: Int, onArea: (String) -> Unit, onDistance: (Int) -> Unit) {
    SetupHeader(Icons.Filled.LocationOn, "Choose your alert area", "These choices can be changed at any time in Alert filters.")
    Text("Area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Alsbach", "Darmstadt").forEach { value ->
            FilterChip(selected = area == value, onClick = { onArea(value) }, label = { Text(value) })
        }
    }
    Text(
        if (distance == 0) "Distance: Unlimited" else "Distance: $distance km",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Slider(value = distance.toFloat(), onValueChange = { onDistance(kotlin.math.round(it).toInt()) }, valueRange = 0f..50f)
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
