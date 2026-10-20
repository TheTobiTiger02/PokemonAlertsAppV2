@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.ui.alerts.mappanel.DistanceEditor
import com.example.pokemonalertsv2.ui.alerts.mappanel.FiltersState
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.settings.AreaChipsEditor
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * The Live list's own filters, quick like History's: alert types, areas and distance edit the
 * feed in place, and the list behind updates as you tap. Species, raid tiers, quests and profiles
 * stay in Filter Studio, one row away.
 */
@Composable
internal fun LiveQuickFiltersSheet(
    definition: FilterDefinition,
    catalog: FilterCatalog,
    categoryCounts: Map<AlertCategory, Int>,
    visibleCount: Int,
    totalCount: Int,
    showDismissed: Boolean,
    onShowDismissedChange: (Boolean) -> Unit,
    onDefinitionChange: (FilterDefinition) -> Unit,
    onOpenFilterStudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = FiltersState(
        definition = definition,
        catalog = catalog,
        artwork = emptyMap(),
        rewardThumbnails = emptyMap(),
        categoryCounts = categoryCounts,
        visibleCount = visibleCount,
        totalCount = totalCount,
        onDefinitionChange = onDefinitionChange,
        openQuests = onOpenFilterStudio,
        openDistanceOverrides = onOpenFilterStudio
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("live_quick_filters")
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .keepScrollInSheet()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg)
        ) {
            PanelHeader(
                title = "Filter alerts",
                subtitle = if (visibleCount == 1) "1 alert shown" else "$visibleCount alerts shown",
                actions = {
                    // Nothing to clear while everything shows, and an always-on Clear invites accidents.
                    if (definition != FilterDefinition() || showDismissed) {
                        TextButton(
                            onClick = {
                                onDefinitionChange(FilterDefinition())
                                onShowDismissedChange(false)
                            },
                            modifier = Modifier.testTag("live_filters_clear")
                        ) { Text("Clear all") }
                    }
                }
            )
            PanelSectionLabel("Alert types")
            Box(Modifier.panelGutter()) {
                AlertTypesSection(
                    definition = definition,
                    categoryCounts = categoryCounts,
                    onDefinitionChange = onDefinitionChange,
                    setAllLabel = "Tap to show or hide"
                )
            }
            if (catalog.areas.isNotEmpty()) {
                PanelSectionLabel("Area")
                AreaChipsEditor(definition, catalog.areas, onDefinitionChange, Modifier.panelGutter())
            }
            PanelSectionLabel("Distance")
            DistanceEditor(state)
            PanelSectionLabel("More")
            PanelSwitchRow(
                title = "Show dismissed alerts",
                checked = showDismissed,
                onCheckedChange = onShowDismissedChange,
                modifier = Modifier.testTag("live_filters_dismissed"),
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(0.dp)) }
            )
            PanelNavRow(
                title = "More filters",
                summary = "Species, raid tiers, quests and profiles in Filter Studio",
                onClick = onOpenFilterStudio,
                modifier = Modifier.testTag("live_filters_studio"),
                icon = { PanelIcon(R.drawable.ic_tune) }
            )
        }
    }
}
