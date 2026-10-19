@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val CUSTOM_PRESET = "CUSTOM"

@Composable
internal fun rememberMapClusteringPreferences(): MapClusteringPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { MapClusteringPreferences(context.alertPreferencesDataStore) }
}

/** The saved grouping settings, for a summary of them. */
@Composable
internal fun rememberMapClusteringSettings(): MapClusteringSettings {
    val settings by rememberMapClusteringPreferences().settings.collectAsStateWithLifecycle(MapClusteringSettings())
    return settings
}

/**
 * How the map groups markers: one preset from a radio list, each with a line saying what it does.
 * Custom opens the grouping method and the sliders; the other presets need neither.
 *
 * The map's Layers sheet and Settings › Map & tracking both show this, so the two cannot drift.
 */
@Composable
internal fun MapGroupingEditor(modifier: Modifier = Modifier) {
    val preferences = rememberMapClusteringPreferences()
    val settings by preferences.settings.collectAsStateWithLifecycle(MapClusteringSettings())
    val scope = rememberCoroutineScope()
    val config = settings.config
    val custom = settings.preset == CUSTOM_PRESET

    fun change(value: MapClusteringConfig) {
        scope.launch { preferences.customize(value) }
    }

    Column(modifier = modifier.testTag("map_clustering_settings")) {
        Text(
            text = "Groups keep a busy map readable. Every alert stays reachable: a group opens " +
                "into its members.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        Column(modifier = Modifier.selectableGroup()) {
            MapClusteringPreset.entries.forEach { preset ->
                GroupingOption(
                    title = preset.label,
                    description = preset.description,
                    selected = settings.preset == preset.name,
                    onClick = { scope.launch { preferences.select(preset.name) } }
                )
            }
            GroupingOption(
                title = "Custom",
                description = "Tune it yourself.",
                selected = custom,
                onClick = { scope.launch { preferences.select(CUSTOM_PRESET) } }
            )
        }

        AnimatedVisibility(visible = custom) {
            Column(
                modifier = Modifier.padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                HorizontalDivider()
                Text("Fine-tune", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Group by", style = MaterialTheme.typography.labelLarge)
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
                TextButton(onClick = { scope.launch { preferences.reset() } }) { Text("Reset to default") }
            }
        }
    }
}

@Composable
private fun GroupingOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            modifier = Modifier.fillMaxWidth().testTag("clustering_$label")
        )
    }
}
