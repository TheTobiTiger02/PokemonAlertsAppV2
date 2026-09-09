@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
internal fun rememberMapClusteringPreferences(): MapClusteringPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { MapClusteringPreferences(context.alertPreferencesDataStore) }
}

/**
 * How densely the map draws, as a section of the map panel rather than a sheet on top of it.
 *
 * It used to open its own modal sheet from a bare text button in the middle of the panel, which
 * meant a second scroller stacked over the first and a control that did not look like any of the
 * settings around it.
 *
 * Density leads because it is the setting anyone actually wants; the grouping mechanics stay,
 * below a divider, for when the presets are not quite right.
 */
/**
 * The same section, owning its own open/closed state.
 *
 * Settings shows it on its own rather than as one row of the map panel's list, so there is no
 * shared expansion state for it to join.
 */
@Composable
internal fun MapClusteringSettingsSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    MapClusteringSection(expanded = expanded, onToggle = { expanded = !expanded })
}

@Composable
internal fun MapClusteringSection(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val preferences = rememberMapClusteringPreferences()
    val settings by preferences.settings.collectAsStateWithLifecycle(MapClusteringSettings())
    val scope = rememberCoroutineScope()
    val config = settings.config

    MapPanelSection(
        title = "Density & grouping",
        summary = settings.label,
        active = settings.preset != MapClusteringPreset.LESS.name,
        expanded = expanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = "How quickly groups separate as you zoom in. Every alert stays reachable " +
                    "either way — a group opens into its members.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.testTag("map_clustering_settings"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                MapClusteringPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.preset == preset.name,
                        onClick = { scope.launch { preferences.select(preset.name) } },
                        label = { Text(preset.label) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                FilterChip(
                    selected = settings.preset == "CUSTOM",
                    onClick = { scope.launch { preferences.select("CUSTOM") } },
                    label = { Text("Custom") },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            HorizontalDivider()

            Text(
                text = "Advanced",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            fun change(value: MapClusteringConfig) {
                scope.launch { preferences.customize(value) }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                listOf(
                    MapGrouping.DISTANCE to "Distance",
                    MapGrouping.GRID to "Grid",
                    MapGrouping.COINCIDENT to "Same location"
                ).forEach { (method, title) ->
                    FilterChip(
                        selected = config.grouping == method ||
                            (config.grouping == MapGrouping.CURRENT && method == MapGrouping.DISTANCE),
                        onClick = { change(config.copy(grouping = method)) },
                        label = { Text(title) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            if (config.grouping != MapGrouping.COINCIDENT) {
                ClusterSlider("Grouping distance", config.distanceDp, 8..80, "dp") {
                    change(config.copy(distanceDp = it))
                }
                ClusterSlider("Separate pins at zoom", config.zoomCutoff, 8..18, "") {
                    change(config.copy(zoomCutoff = it))
                }
            }
            ClusterSlider("Overview marker limit", config.overviewLimit, 25..600, "", 25) {
                change(config.copy(overviewLimit = it))
            }
            ClusterSlider("Close zoom marker limit", config.closeLimit, 50..1200, "", 50) {
                change(config.copy(closeLimit = it))
            }
            Text(
                text = "Markers drawn on screen before extra grouping kicks in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TextButton(onClick = { scope.launch { preferences.reset() } }) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun ClusterSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    var pending by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text(
            text = "$label: ${pending.toInt()} $unit".trim(),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = pending,
            onValueChange = { pending = it },
            onValueChangeFinished = {
                onChange((kotlin.math.round(pending / step) * step).toInt().coerceIn(range))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            modifier = Modifier.testTag("clustering_$label")
        )
    }
}
