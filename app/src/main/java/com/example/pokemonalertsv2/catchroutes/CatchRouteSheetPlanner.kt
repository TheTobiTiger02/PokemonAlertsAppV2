package com.example.pokemonalertsv2.catchroutes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Draws the planner's map; the hint about map taps goes at the top when something covers the bottom. */
internal typealias PlannerMap = @Composable (modifier: Modifier, hintAtTop: Boolean) -> Unit

/**
 * The planner: the map fills the screen under a sheet. Collapsed, the sheet shows what is
 * set and Generate; pulled up, the rows, which open their pages inside the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetPlanner(model: CatchRoutesViewModel, state: PlannerState, actions: PlannerActions, planning: Boolean,
    topBar: @Composable () -> Unit, map: PlannerMap) {
    val scaffold = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(PlanPage.ROOT) }
    var peekPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The drag handle is 48dp tall; the peek is the handle plus the measured summary.
    val peek = if (peekPx == 0) 180.dp else with(density) { peekPx.toDp() } + 48.dp + navBar
    // Below the top bar, so the sheet never covers the way back.
    val maxSheet = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    val expanded = scaffold.bottomSheetState.currentValue == SheetValue.Expanded
    LaunchedEffect(page) { if (page != PlanPage.ROOT) scaffold.bottomSheetState.expand() }
    BottomSheetScaffold(
        scaffoldState = scaffold,
        topBar = topBar,
        sheetPeekHeight = peek,
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(max = maxSheet).navigationBarsPadding().testTag("catch_route_sheet")) {
                if (planning) PlanPanel(model, state, actions, page, { page = it }, scrollModifier = Modifier.keepScrollInSheet()) {
                    Column(Modifier.reportHeight { peekPx = it }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { scope.launch { if (expanded) scaffold.bottomSheetState.partialExpand() else scaffold.bottomSheetState.expand() } }
                                .padding(horizontal = PanelGutter).testTag("plan_sheet_summary"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Plan a route", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val s = state.settings
                                Text(listOf(startSummary(model, s), finishSummary(s), whenSummary(model, s)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                contentDescription = if (expanded) "Show less" else "Show all settings")
                        }
                        GenerateStatus(model, state.now)
                        GenerateButton(model, state.now, Modifier.fillMaxWidth().panelGutter().padding(top = Spacing.sm, bottom = Spacing.sm))
                    }
                } else ResultPanel(model, state, actions, listModifier = Modifier.keepScrollInSheet(), firstItem = Modifier.reportHeight { peekPx = it })
            }
        }
    ) { padding ->
        map(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), true)
    }
}

