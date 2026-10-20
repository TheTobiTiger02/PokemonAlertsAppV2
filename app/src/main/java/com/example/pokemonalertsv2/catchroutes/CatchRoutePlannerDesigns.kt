package com.example.pokemonalertsv2.catchroutes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelPages
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Draws the planner's map; the hint about map taps goes at the top when something covers the bottom. */
internal typealias PlannerMap = @Composable (modifier: Modifier, hintAtTop: Boolean) -> Unit

/**
 * A · Rows: the map on top, the settings as one row each below it, every row opening its own
 * page in place, and Generate fixed at the bottom.
 */
@Composable
internal fun RowsPlanner(model: CatchRoutesViewModel, state: PlannerState, actions: PlannerActions, planning: Boolean, map: PlannerMap) {
    var page by rememberSaveable { mutableStateOf(PlanPage.ROOT) }
    Column(Modifier.fillMaxSize()) {
        map(Modifier.fillMaxWidth().weight(if (planning) 0.7f else 1.25f), false)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (planning) PlanPanel(model, state, actions, page, { page = it })
            else ResultPanel(model, state, actions)
        }
        if (planning) GenerateBar(model, state.now)
    }
}

/**
 * B · Map + sheet: the map fills the screen under a sheet. Collapsed, the sheet shows what is
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

/** The Steps design's steps, in order. */
internal enum class PlanStep(val title: String) { WHERE("Where"), WHEN("When"), WHAT("What"), GENERATE("Check and generate") }

/**
 * C · Steps: Where, When and What one at a time with Back and Next, then a last step listing
 * everything set, from which any step can be reopened, and Generate.
 */
@Composable
internal fun StepsPlanner(model: CatchRoutesViewModel, state: PlannerState, actions: PlannerActions, planning: Boolean, map: PlannerMap) {
    var step by rememberSaveable { mutableStateOf(PlanStep.WHERE) }
    var sub by rememberSaveable { mutableStateOf(PlanPage.ROOT) }
    val s = state.settings
    Column(Modifier.fillMaxSize()) {
        map(Modifier.fillMaxWidth().weight(if (planning) 0.7f else 1.25f), false)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (!planning) ResultPanel(model, state, actions)
            else PanelPages(page = sub, root = PlanPage.ROOT, onBack = { sub = PlanPage.ROOT }) { shown ->
                Column(Modifier.fillMaxSize()) {
                    if (shown != PlanPage.ROOT) {
                        PanelHeader(shown.title, onBack = { sub = PlanPage.ROOT })
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg)) {
                            PlanEditor(shown, model, state, actions) { sub = PlanPage.ROOT }
                        }
                    } else {
                        StepHeader(step)
                        key(step) {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg)
                                .testTag("plan_step_${step.name.lowercase()}")) {
                                when (step) {
                                    PlanStep.WHERE -> {
                                        StartEditor(model, s, actions)
                                        if (!s.fixed) {
                                            PanelSectionLabel("Finish")
                                            FinishEditor(model, s, actions)
                                            PanelSectionLabel("Area")
                                            AreaEditor(model, s, actions)
                                        }
                                    }
                                    PlanStep.WHEN -> {
                                        WhenEditor(model, s, actions)
                                        PanelSectionLabel("Not sure?")
                                        PanelNavRow(PlanPage.SUGGEST.title, if (s.fixed) "Best start time" else "Best start spot or time",
                                            { sub = PlanPage.SUGGEST }, Modifier.testTag("plan_row_suggest")) { PanelIcon(R.drawable.ic_insights) }
                                    }
                                    PlanStep.WHAT -> {
                                        IncludesEditor(model, s)
                                        PanelSectionLabel("More")
                                        PanelNavRow("Advanced", advancedSummary(s), { sub = PlanPage.ADVANCED },
                                            Modifier.testTag("plan_row_advanced")) { PanelIcon(R.drawable.ic_tune) }
                                    }
                                    PlanStep.GENERATE -> {
                                        if (state.plan != null) TextButton(onClick = { actions.setEditing(false) },
                                            modifier = Modifier.padding(start = Spacing.sm)) { Text("‹ Back to route") }
                                        PlanRows(model, s, state.saved.size) { page ->
                                            when (page) {
                                                PlanPage.START, PlanPage.FINISH, PlanPage.AREA -> step = PlanStep.WHERE
                                                PlanPage.WHEN -> step = PlanStep.WHEN
                                                PlanPage.INCLUDES -> step = PlanStep.WHAT
                                                else -> sub = page
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        StepBar(model, state.now, step) { step = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(step: PlanStep) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PanelGutter, vertical = Spacing.sm)) {
        Text("Step ${step.ordinal + 1} of ${PlanStep.entries.size}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            PlanStep.entries.forEach {
                Box(Modifier.weight(1f).height(4.dp).background(
                    if (it.ordinal <= step.ordinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun StepBar(model: CatchRoutesViewModel, now: Long, step: PlanStep, onStep: (PlanStep) -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.padding(bottom = Spacing.sm)) {
            if (step == PlanStep.GENERATE) GenerateStatus(model, now)
            Row(Modifier.fillMaxWidth().panelGutter().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { onStep(PlanStep.entries[step.ordinal - 1]) }, enabled = step != PlanStep.WHERE,
                    modifier = Modifier.height(52.dp).testTag("step_back"), contentPadding = PaddingValues(horizontal = Spacing.lg)) { Text("Back", maxLines = 1) }
                if (step == PlanStep.GENERATE) GenerateButton(model, now, Modifier.weight(1f))
                else {
                    val next = PlanStep.entries[step.ordinal + 1]
                    Button(onClick = { onStep(next) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(52.dp).testTag("step_next")) {
                        Text(if (next == PlanStep.GENERATE) "Next · Check" else "Next · ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
