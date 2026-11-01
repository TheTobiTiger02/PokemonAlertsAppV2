@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.data.normalizeFilterToken
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

private val DISTANCE_CHOICES = listOf(0, 50, 100, 300, 500, 1_000, 2_000, 5_000)

/**
 * The Live list's quick filters, the same small sheet as History's: one row of chips each for
 * alert type, area and distance, edited in place. Species, raid tiers, quests and profiles stay
 * in Filter Studio, behind "More filters".
 */
@Composable
internal fun LiveQuickFiltersSheet(
    definition: FilterDefinition,
    catalog: FilterCatalog,
    categoryCounts: Map<AlertCategory, Int>,
    visibleCount: Int,
    showDismissed: Boolean,
    onShowDismissedChange: (Boolean) -> Unit,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenFilterStudio: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("live_quick_filters")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrolls only when a narrow phone or large font makes it taller than the screen.
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Filter alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Text("Alert type", style = MaterialTheme.typography.titleSmall)
            val allTypes = remember(Unit) { FilterAlertType.entries.map { it.name } }
            ChipRow(FilterAlertType.entries, key = { it.name }) { type ->
                val shown = definition.alertTypes.contains(type.name)
                val count = categoryCounts[type.mapCategory()] ?: 0
                QuickChip(
                    label = if (count > 0) "${type.label} · $count" else type.label,
                    selected = shown,
                    accent = type.mapAccent(),
                    modifier = Modifier.semantics { contentDescription = "${type.label} filter" }
                ) { onDefinitionChange(definition.copy(alertTypes = definition.alertTypes.toggled(type.name, allTypes))) }
            }

            if (catalog.areas.isNotEmpty()) {
                Text("Area", style = MaterialTheme.typography.titleSmall)
                ChipRow(listOf<String?>(null) + catalog.areas, key = { it ?: "*all" }) { area ->
                    val selected = if (area == null) definition.areas.mode == FilterSelectionMode.ALL
                    else definition.areas.mode == FilterSelectionMode.ONLY && definition.areas.contains(area)
                    QuickChip(label = area ?: "All", selected = selected) {
                        onDefinitionChange(definition.copy(areas = if (area == null) FilterSelection.All else toggleArea(definition.areas, area)))
                    }
                }
            }

            Text("Distance", style = MaterialTheme.typography.titleSmall)
            ChipRow(DISTANCE_CHOICES, key = { it }) { meters ->
                QuickChip(label = if (meters == 0) "Any" else distanceLabel(meters), selected = definition.maxDistanceMeters == meters) {
                    onDefinitionChange(definition.copy(maxDistanceMeters = meters))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show dismissed alerts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = showDismissed, onCheckedChange = onShowDismissedChange, modifier = Modifier.testTag("live_filters_dismissed"))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenFilterStudio, modifier = Modifier.testTag("live_filters_studio")) { Text("More filters") }
                if (definition != FilterDefinition() || showDismissed) {
                    TextButton(
                        onClick = {
                            onDefinitionChange(FilterDefinition())
                            onShowDismissedChange(false)
                        },
                        modifier = Modifier.testTag("live_filters_clear")
                    ) { Text("Clear all filters") }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(if (visibleCount == 1) "Show 1 alert" else "Show $visibleCount alerts")
            }
        }
    }
}

private fun toggleArea(areas: FilterSelection, area: String): FilterSelection {
    val set = if (areas.mode == FilterSelectionMode.ONLY) areas.normalizedValues.toMutableSet() else mutableSetOf()
    val key = normalizeFilterToken(area)
    if (!set.add(key)) set.remove(key)
    return if (set.isEmpty()) FilterSelection.All else FilterSelection.only(set)
}

@Composable
private fun <T> ChipRow(items: List<T>, key: (T) -> Any, chip: @Composable (T) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Keep horizontal drags in the row, as on History.
            .pointerInput(Unit) { detectHorizontalDragGestures { _, _ -> } }
    ) {
        items(items, key = key) { chip(it) }
    }
}

/** The History sheet's chip: tinted in the type's colour (or blue) when selected. */
@Composable
private fun QuickChip(
    label: String,
    selected: Boolean,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = AssistChipDefaults.elevatedAssistChipColors(
            containerColor = if (selected) accent.copy(alpha = 0.24f).compositeOver(MaterialTheme.colorScheme.surface)
            else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            labelColor = if (selected) accent else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline)
    )
}
