package com.example.pokemonalertsv2.ui.alerts.mapchrome

import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.hunt.HuntMapFocus
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.MAP_TOP_CHROME_HEIGHT
import com.example.pokemonalertsv2.ui.alerts.SyncStatus

/** The running hunt, as the map's controls describe it. */
internal data class MapChromeHunt(
    val name: String,
    val target: PokemonAlert?,
    val distanceMeters: Float?,
    val focus: HuntMapFocus
)

/** What the locate button is doing, which decides its colour and its spoken label. */
internal enum class MapLocateState { IDLE, REQUESTED, FOLLOWING, FOLLOWING_DEGRADED }

/** Everything the controls around the map read, built by the map screen from state it owns. */
internal data class MapChromeState(
    val visibleCount: Int,
    val totalCount: Int,
    val categoryCounts: Map<AlertCategory, Int>,
    val mutedCategories: Set<AlertCategory>,
    val syncStatus: SyncStatus,
    /** Filter rules the chip rail cannot show; alert types are excluded on purpose. */
    val advancedFilterRuleCount: Int,
    val hunt: MapChromeHunt?,
    val locate: MapLocateState,
    val mapLoaded: Boolean,
    /** An alert's detail sheet is up; on phones the floating controls get out of its way. */
    val alertSelected: Boolean,
    val useSidePanel: Boolean,
    val showBackButton: Boolean
)

/** Everything the controls can ask the map screen to do. The screen hosts every sheet. */
internal class MapChromeActions(
    val onCategoriesChange: (Set<AlertCategory>) -> Unit,
    val onBack: () -> Unit,
    val onOpenSettings: (() -> Unit)?,
    val onOpenFilters: () -> Unit,
    val onOpenLayers: () -> Unit,
    val onOpenTools: () -> Unit,
    /** Frames every visible alert, or during a hunt flips between its target and route. */
    val onFit: () -> Unit,
    val onLocate: () -> Unit,
    val onRefresh: () -> Unit
)

internal val TopBarHeight = 52.dp

/** The top bar, the gap under it and the chip rail; the camera and map logo stay clear of it. */
internal val MapChromeTopInset = TopBarHeight + 8.dp + MAP_TOP_CHROME_HEIGHT

/** The Tools pill and the camera buttons along the bottom edge. */
internal val MapChromeBottomInset = 96.dp
