package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.*
import kotlinx.coroutines.launch

@Composable
internal fun rememberMapClusteringPreferences(): MapClusteringPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { MapClusteringPreferences(context.alertPreferencesDataStore) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MapClusteringSettingsButton() {
    val preferences = rememberMapClusteringPreferences()
    val settings by preferences.settings.collectAsStateWithLifecycle(MapClusteringSettings())
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("map_clustering_settings")) {
        Text("Clustering · ${settings.label}")
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Clustering", style = MaterialTheme.typography.headlineSmall)
            Text("Choose how quickly groups separate as you zoom. All alerts remain accessible.",
                style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapClusteringPreset.entries.forEach { preset ->
                    FilterChip(selected = settings.preset == preset.name,
                        onClick = { scope.launch { preferences.select(preset.name) } }, label = { Text(preset.label) })
                }
                FilterChip(selected = settings.preset == "CUSTOM",
                    onClick = { scope.launch { preferences.select("CUSTOM") } }, label = { Text("Custom") })
            }
            HorizontalDivider()
            Text("Advanced", style = MaterialTheme.typography.titleMedium)
            val config = settings.config
            fun change(value: MapClusteringConfig) { scope.launch { preferences.customize(value) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MapGrouping.DISTANCE to "Distance", MapGrouping.GRID to "Grid",
                    MapGrouping.COINCIDENT to "Same location").forEach { (method, title) ->
                    FilterChip(selected = config.grouping == method ||
                        (config.grouping == MapGrouping.CURRENT && method == MapGrouping.DISTANCE),
                        onClick = { change(config.copy(grouping = method)) },
                        label = { Text(title) })
                }
            }
            if (config.grouping != MapGrouping.COINCIDENT) {
                ClusterSlider("Grouping distance", config.distanceDp, 8..80, "dp") { change(config.copy(distanceDp = it)) }
                ClusterSlider("Separate pins at zoom", config.zoomCutoff, 8..18, "") { change(config.copy(zoomCutoff = it)) }
            }
            ClusterSlider("Overview marker limit", config.overviewLimit, 25..600, "", 25) { change(config.copy(overviewLimit = it)) }
            ClusterSlider("Close zoom marker limit", config.closeLimit, 50..1200, "", 50) { change(config.copy(closeLimit = it)) }
            Text("Markers visible on screen before extra grouping kicks in. A phone fits about 150 pins side by side, so higher limits mean overlapping pins and slower dense views.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { scope.launch { preferences.reset() } }) { Text("Reset") }
                Button(onClick = { open = false }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun ClusterSlider(label: String, value: Int, range: IntRange, unit: String, step: Int = 1,
    onChange: (Int) -> Unit) {
    var pending by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Text("$label: ${pending.toInt()} $unit", style = MaterialTheme.typography.bodyMedium)
    Slider(value = pending, onValueChange = { pending = it },
        onValueChangeFinished = { onChange((kotlin.math.round(pending / step) * step).toInt().coerceIn(range)) },
        valueRange = range.first.toFloat()..range.last.toFloat(), steps = (range.last - range.first) / step - 1,
        modifier = Modifier.testTag("clustering_$label"))
}
