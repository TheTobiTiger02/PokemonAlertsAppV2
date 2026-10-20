@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.ui.components.DesignCompareStrip
import com.example.pokemonalertsv2.ui.components.DesignScreen
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.counters.RaidCountersUiState
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.validAlertCoordinates

/** What every redesigned alert detail layout needs from the screen that hosts it. */
internal class AlertDetailUi(
    val alert: PokemonAlert,
    val goDexStatus: GoDexMatchResult,
    val isGoing: Boolean,
    val goingEnabled: Boolean,
    val distance: AlertDistanceInfo?,
    val countdownClock: State<Long>,
    val raidCountersState: RaidCountersUiState,
    val snackbarHostState: SnackbarHostState,
    val onBack: () -> Unit,
    val onGoing: () -> Unit,
    val onNavigate: () -> Unit,
    val onOpenCounters: () -> Unit,
    /** Null once the alert has ended. */
    val onSnooze: (() -> Unit)?,
    val onPictureInPicture: (() -> Unit)?,
    val onShare: () -> Unit,
    val onShowImage: () -> Unit
)

/**
 * The alert detail in design [option] 1 (One column), 2 (Tabs) or 3 (Map-first). Option 0 is the
 * shipped layout, which [AlertDetailScreen] still draws itself.
 */
@Composable
internal fun AlertDetailRedesign(option: Int, ui: AlertDetailUi) {
    when (option) {
        2 -> TabsAlertDetail(ui)
        3 -> MapFirstAlertDetail(ui)
        else -> OneColumnAlertDetail(ui)
    }
}

// ---- A · One column -----------------------------------------------------------------------

/**
 * Summary, the type's own section, then location, in one scroll. The picture is at most 28% of
 * the height and scrolls away with the rest, so a narrow phone keeps most of its screen for text.
 */
@Composable
private fun OneColumnAlertDetail(ui: AlertDetailUi) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("alert_detail_one_column")
    ) {
        val heroHeight = (maxHeight * 0.28f).coerceAtMost(ALERT_DETAIL_HERO_IMAGE_HEIGHT)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            ) {
                AlertImage(
                    alert = ui.alert,
                    rounded = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClickLabel = "Expand image", onClick = ui.onShowImage)
                )
                DetailTopActions(ui = ui, overlay = true, modifier = Modifier.statusBarsPadding())
            }
            DesignCompareStrip(DesignScreen.ALERT_DETAIL)
            SummaryWithNumbers(ui, Modifier.padding(top = Spacing.lg))
            AlertTypeSection(ui.alert, ui.raidCountersState, ui.onOpenCounters)
            PanelSectionLabel("Location")
            AlertLocationRows(ui.alert, ui.distance, ui.isGoing)
            MoreDetailsSection(ui.alert, ui.goDexStatus)
            Spacer(Modifier.height(DetailBottomBarHeight + navBottom + Spacing.lg))
        }
        DetailBottomBar(ui, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(
            hostState = ui.snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = DetailBottomBarHeight + navBottom)
        )
    }
}

// ---- B · Tabs -----------------------------------------------------------------------------

private enum class AlertDetailTab(val label: String) { OVERVIEW("Overview"), STATS("Stats"), LOCATION("Location") }

/** A fixed header (sprite, title, countdown) over Overview | Stats | Location. */
@Composable
private fun TabsAlertDetail(ui: AlertDetailUi) {
    var tab by rememberSaveable(ui.alert.uniqueId) { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("alert_detail_tabs")
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            DetailTopActions(ui = ui, overlay = false)
            DesignCompareStrip(DesignScreen.ALERT_DETAIL)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanelGutter, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlertSprite(ui.alert, size = 56.dp, modifier = Modifier.clickable(onClickLabel = "Expand image", onClick = ui.onShowImage))
                Spacer(Modifier.width(Spacing.md))
                AlertSummaryBlock(
                    alert = ui.alert,
                    goDexStatus = ui.goDexStatus,
                    countdownClock = ui.countdownClock,
                    titleStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).testTag("alert_summary")
                )
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                AlertDetailTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry.ordinal,
                        onClick = { tab = entry.ordinal },
                        text = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("alert_tab_${entry.name.lowercase()}")
                    )
                }
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = { appFadeThrough() },
                label = "alert_detail_tab",
                modifier = Modifier.weight(1f)
            ) { shown ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Spacing.lg)
                ) {
                    when (AlertDetailTab.entries[shown]) {
                        AlertDetailTab.OVERVIEW -> {
                            NumbersAndStatus(ui, Modifier.padding(top = Spacing.md))
                            AlertTypeSection(ui.alert, ui.raidCountersState, ui.onOpenCounters)
                            Spacer(Modifier.height(Spacing.sm))
                            MoreDetailsSection(ui.alert, ui.goDexStatus)
                        }
                        AlertDetailTab.STATS -> StatsTab(ui.alert)
                        AlertDetailTab.LOCATION -> {
                            Spacer(Modifier.height(Spacing.md))
                            AlertPicture(ui.alert, onClick = ui.onShowImage)
                            Spacer(Modifier.height(Spacing.sm))
                            AlertLocationRows(ui.alert, ui.distance, ui.isGoing)
                        }
                    }
                }
            }
            DetailBottomBar(ui, shadow = false)
        }
        SnackbarHost(
            hostState = ui.snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = DetailBottomBarHeight)
        )
    }
}

@Composable
private fun StatsTab(alert: PokemonAlert) {
    val hasStats = alert.formattedIv != null || alert.cp != null || alert.level != null || alert.hundoCP != null
    if (hasStats) {
        PanelSectionLabel("Stats")
        AlertStatsRows(alert)
    }
    val weather = currentWeatherDisplay(alert)
    if (weather != null || alert.isWeatherBoosted == true) {
        PanelSectionLabel("Weather")
        weather?.let {
            DetailInfoRow(
                title = it.label,
                value = if (it.confirmed) "Current weather" else UNCONFIRMED_WEATHER_NOTE.replaceFirstChar { c -> c.uppercase() }
            ) { PanelIcon(R.drawable.ic_cloud) }
        }
        if (alert.isWeatherBoosted == true) {
            DetailInfoRow(title = "Weather boosted", value = "Higher level and CP") {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
    val moves = alert.moves
    if (moves != null && !alert.hasTypeContaining("raid")) {
        PanelSectionLabel("Moves")
        MoveRows(fast = moves.fast, charged = moves.charged)
    }
    if (!hasStats && weather == null && moves == null) {
        Text(
            text = "No stats for this alert.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PanelGutter, vertical = Spacing.xl)
        )
    }
}

// ---- C · Map-first ------------------------------------------------------------------------

/**
 * A large map of the spot with the details in a sheet over it, the same sheet the map tab opens:
 * header, numbers, the two buttons, then the rest as the sheet is pulled up.
 */
@Composable
private fun MapFirstAlertDetail(ui: AlertDetailUi) {
    val sheetState = rememberBottomSheetScaffoldState()
    // The map shows the spot itself even when the alert carries a picture; the picture is one
    // tap away.
    val mapAlert = remember(ui.alert) {
        if (validAlertCoordinates(ui.alert) != null) ui.alert.copy(imageUrl = null) else ui.alert
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("alert_detail_map_first")
    ) {
        val peek = (maxHeight * 0.45f).coerceAtLeast(280.dp)
        // Reaches under the sheet's rounded top, so no background shows in its corners.
        val mapHeight = maxHeight - peek + 28.dp
        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = peek,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.background,
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = Spacing.lg)
                ) {
                    DesignCompareStrip(DesignScreen.ALERT_DETAIL)
                    AlertSheetHeader(
                        alert = ui.alert,
                        goDexStatus = ui.goDexStatus,
                        countdownClock = ui.countdownClock,
                        distanceInfo = ui.distance,
                        isGoing = ui.isGoing,
                        modifier = Modifier.padding(horizontal = PanelGutter, vertical = Spacing.sm)
                    )
                    AlertPrimaryButtons(
                        isGoing = ui.isGoing,
                        goingEnabled = ui.goingEnabled,
                        onGoing = ui.onGoing,
                        onNavigate = ui.onNavigate,
                        modifier = Modifier.padding(horizontal = PanelGutter, vertical = Spacing.sm)
                    )
                    if (ui.alert.isInvalidated) {
                        Box(Modifier.padding(horizontal = PanelGutter, vertical = Spacing.sm)) { InvalidationBanner(ui.alert) }
                    }
                    AlertTypeSection(ui.alert, ui.raidCountersState, ui.onOpenCounters)
                    PanelSectionLabel("Location")
                    AlertLocationRows(ui.alert, null, ui.isGoing)
                    MoreDetailsSection(ui.alert, ui.goDexStatus)
                }
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                AlertImage(
                    alert = mapAlert,
                    rounded = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                        .clickable(onClickLabel = "Expand image", onClick = ui.onShowImage)
                )
                DetailTopActions(ui = ui, overlay = true, modifier = Modifier.statusBarsPadding())
            }
        }
        SnackbarHost(
            hostState = ui.snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
        )
    }
}

// ---- Shared by the layouts ----------------------------------------------------------------

/**
 * The map sheet's header, also used by Map-first: sprite, title, type and place, countdown, then
 * one line of numbers (with "Going" or "Dismissed" first when that applies), a quest's task,
 * and the GoDex status.
 */
@Composable
internal fun AlertSheetHeader(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult,
    countdownClock: State<Long>,
    distanceInfo: AlertDistanceInfo?,
    isGoing: Boolean,
    modifier: Modifier = Modifier,
    isDismissed: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.Top) {
            AlertSprite(alert, size = 56.dp)
            Spacer(Modifier.width(Spacing.md))
            AlertSummaryBlock(
                alert = alert,
                goDexStatus = goDexStatus,
                countdownClock = countdownClock,
                titleStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .testTag("map_alert_title_block")
            )
            actions()
        }
        val status = listOfNotNull(
            "Going".takeIf { isGoing },
            "Dismissed".takeIf { isDismissed }
        )
        val numbers = alertNumbersLine(alert, distanceInfo)
        val line = (status + listOf(numbers).filter { it.isNotEmpty() }).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(
                text = line,
                style = MaterialTheme.typography.labelLarge,
                color = if (status.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("alert_numbers_line")
            )
        }
        questAlertPresentation(alert)?.task?.let { task ->
            Text(task, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (goDexStatus.status != GoDexMatchStatus.NOT_CONFIGURED) GoDexStatusPill(goDexStatus)
    }
}

/** Summary block, numbers line and status, gutter-padded, for the one-column layout. */
@Composable
private fun SummaryWithNumbers(ui: AlertDetailUi, modifier: Modifier = Modifier) {
    AlertSummaryBlock(
        alert = ui.alert,
        goDexStatus = ui.goDexStatus,
        countdownClock = ui.countdownClock,
        modifier = modifier
            .padding(horizontal = PanelGutter)
            .testTag("alert_summary")
    )
    NumbersAndStatus(ui, Modifier.padding(top = Spacing.sm))
}

@Composable
private fun NumbersAndStatus(ui: AlertDetailUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = PanelGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val numbers = alertNumbersLine(ui.alert, ui.distance)
        if (numbers.isNotEmpty()) {
            Text(
                text = numbers,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("alert_numbers_line")
            )
        }
        if (ui.goDexStatus.status != GoDexMatchStatus.NOT_CONFIGURED) GoDexStatusPill(ui.goDexStatus)
        if (ui.alert.isInvalidated) InvalidationBanner(alert = ui.alert)
    }
}

/** Back on the left, the ⋮ menu on the right; over a picture both sit on opaque circles. */
@Composable
private fun DetailTopActions(ui: AlertDetailUi, overlay: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (overlay) Spacing.md else Spacing.xxs, vertical = if (overlay) Spacing.sm else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (overlay) {
            FilledIconButton(
                onClick = ui.onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
        } else {
            IconButton(onClick = ui.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
        Spacer(Modifier.weight(1f))
        AlertOverflowMenu(
            alert = ui.alert,
            goDexStatus = ui.goDexStatus,
            onSnooze = ui.onSnooze,
            onPictureInPicture = ui.onPictureInPicture,
            onShare = ui.onShare,
            overlay = overlay
        )
    }
}

private val DetailBottomBarHeight = 64.dp

/** The two main buttons, pinned, 64dp tall plus the navigation bar. */
@Composable
private fun DetailBottomBar(ui: AlertDetailUi, modifier: Modifier = Modifier, shadow: Boolean = true) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (shadow) 8.dp else 0.dp
    ) {
        AlertPrimaryButtons(
            isGoing = ui.isGoing,
            goingEnabled = ui.goingEnabled,
            onGoing = ui.onGoing,
            onNavigate = ui.onNavigate,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = PanelGutter, vertical = Spacing.sm)
        )
    }
}
