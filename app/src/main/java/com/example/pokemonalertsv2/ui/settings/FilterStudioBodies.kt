@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.ui.alerts.mappanel.FiltersState
import com.example.pokemonalertsv2.ui.alerts.mappanel.FiltersTabs
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * What every Filter Studio body edits and every way it can act. Changes go to the draft; the
 * host's Apply bar writes them out together.
 */
internal class StudioBodyState(
    val draft: FilterDefinition,
    val onDraftChange: (FilterDefinition) -> Unit,
    val catalog: FilterCatalog,
    val artwork: Map<String, String>,
    val rewardThumbnails: Map<String, String>,
    val matchCount: Int,
    val totalCount: Int,
    val areas: List<String>,
    val profileSummary: String,
    val openProfiles: () -> Unit,
    val saveProfile: () -> Unit,
    val openQuestRules: () -> Unit,
    val openDistanceOverrides: () -> Unit
) {
    fun filters(nearbyExtras: (@Composable () -> Unit)? = null) = FiltersState(
        definition = draft,
        catalog = catalog,
        artwork = artwork,
        rewardThumbnails = rewardThumbnails,
        categoryCounts = emptyMap(),
        visibleCount = matchCount,
        totalCount = totalCount,
        onDefinitionChange = onDraftChange,
        openQuests = openQuestRules,
        openDistanceOverrides = openDistanceOverrides,
        nearbyExtras = nearbyExtras
    )
}

/** Profile to load or link, and saving the draft as one; the same two rows in every body. */
@Composable
private fun ProfileRows(state: StudioBodyState) {
    PanelNavRow(
        title = "Profile",
        summary = state.profileSummary,
        onClick = state.openProfiles,
        modifier = Modifier.testTag("studio_profiles"),
        icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp)) }
    )
    PanelNavRow(
        title = "Save as profile",
        summary = "Keep these rules to reuse or link",
        onClick = state.saveProfile,
        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) }
    )
}

/** The map's Filters tabs, editing the draft; areas and profiles join the Nearby tab. */
@Composable
internal fun StudioTabsBody(state: StudioBodyState) {
    val filters = state.filters(nearbyExtras = {
        PanelSectionLabel("Areas")
        AreaChipsEditor(state.draft, state.areas, state.onDraftChange, Modifier.panelGutter())
        PanelSectionLabel("Profile")
        ProfileRows(state)
    })
    Box(Modifier.fillMaxSize().padding(top = Spacing.sm).testTag("studio_tabs")) {
        FiltersTabs(filters, header = {})
    }
}
