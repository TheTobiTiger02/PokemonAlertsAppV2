package com.example.pokemonalertsv2.ui.counters

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.ui.alerts.rememberCountdownClock
import com.example.pokemonalertsv2.ui.theme.MetricTextStyle
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TimeUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// The raid counters screen. Every setting lives in exactly one place, "import your Pokémon" is
// said once, and the battle setup is edited through the shared CounterSetupPanel.

/** The settings the chip row edits in place; everything else is in its Setup sheet. */
internal enum class QuickSetting { WEATHER, LEVEL, SORT }

/**
 * Boss with a time pill, the source tabs, then one chip row (Setup · Weather · Level · Sort ·
 * Owned) whose chips open their editor right under the row.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RaidCountersChipRow(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<QuickSetting?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val copyTeam = rememberCopyTeam(state, snackbarHostState)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CountersTopBar(state, onBack) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("counters_list"),
                contentPadding = countersListPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "boss") {
                    BossSummary(state) { state.raidEndTimeMillis?.let { RaidTimePill(it) } }
                }
                item(key = "source") { SourceSelector(state, actions, showHint = false) }
                if (!state.hasPersonalSource) item(key = "source_hint") { PersonalSourceHint() }
                item(key = "quick") {
                    QuickChipRow(
                        state = state,
                        actions = actions,
                        editing = editing,
                        onEdit = { editing = if (editing == it) null else it },
                        onClose = { editing = null },
                        onOpenSetup = { setupOpen = true }
                    )
                }
                if (state.hasNotices(includeWeather = false)) {
                    item(key = "notices") { ScreenNotices(state, actions, includeWeather = false) }
                }
                countersBody(state, actions, copyTeam)
            }
        }
    }

    if (setupOpen) {
        CounterSetupSheet(
            state = state,
            actions = actions,
            title = "More setup",
            excluded = setOf(SetupItem.WEATHER, SetupItem.LEVEL, SetupItem.SORT, SetupItem.OWNED),
            onDismiss = { setupOpen = false }
        )
    }
}

@Composable
private fun QuickChipRow(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    editing: QuickSetting?,
    onEdit: (QuickSetting) -> Unit,
    onClose: () -> Unit,
    onOpenSetup: () -> Unit
) {
    val options = state.options
    val defaults = remember { RaidCounterOptions() }
    // What the Setup sheet holds that differs from the defaults, so the chip can say so.
    val hiddenChanges = listOf(
        state.selectedBossMoveset != null,
        options.friendship != defaults.friendship,
        options.attackStrategy != defaults.attackStrategy,
        options.partyPower != defaults.partyPower,
        options.includeMegas != defaults.includeMegas,
        options.includeShadow != defaults.includeShadow,
        options.includeLegendary != defaults.includeLegendary
    ).count { it }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickChip(
                label = if (hiddenChanges > 0) "Setup · $hiddenChanges" else "Setup",
                icon = R.drawable.ic_tune,
                active = hiddenChanges > 0,
                onClick = onOpenSetup,
                tag = "quick_chip_setup"
            )
            QuickChip(
                label = (if (options.weather == PokebattlerWeather.NONE) "No weather" else options.weather.tileLabel) +
                    if (state.weatherUnconfirmed) " · unconfirmed" else "",
                icon = options.weather.icon,
                active = options.weather != defaults.weather,
                open = editing == QuickSetting.WEATHER,
                onClick = { onEdit(QuickSetting.WEATHER) },
                tag = "quick_chip_weather"
            )
            QuickChip(
                // My Pokémon ranks each copy at its own level, so this one does not apply there.
                label = if (state.showingPersonal) "Your levels" else "Level ${options.attackerLevel}",
                active = !state.showingPersonal && options.attackerLevel != defaults.attackerLevel,
                open = editing == QuickSetting.LEVEL,
                enabled = !state.showingPersonal,
                onClick = { onEdit(QuickSetting.LEVEL) },
                tag = "quick_chip_level"
            )
            QuickChip(
                label = options.sort.label,
                active = options.sort != defaults.sort,
                open = editing == QuickSetting.SORT,
                onClick = { onEdit(QuickSetting.SORT) },
                tag = "quick_chip_sort"
            )
            if (state.pokeGenieCount > 0 && !state.showingPersonal) {
                QuickChip(
                    label = "Owned only",
                    active = state.ownedOnly,
                    onClick = { actions.onOwnedOnlyChanged(!state.ownedOnly) },
                    tag = "quick_chip_owned"
                )
            }
        }
        AnimatedContent(targetState = editing, label = "quick_editor") { shown ->
            if (shown != null) QuickEditor(shown, state, actions, onClose)
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    icon: Int? = null,
    open: Boolean? = null,
    enabled: Boolean = true
) {
    FilterChip(
        selected = active || open == true,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = icon?.let {
            { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp)) }
        },
        trailingIcon = open?.let { isOpen ->
            { ExpandChevron(expanded = isOpen, modifier = Modifier.size(18.dp)) }
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.padding(vertical = Spacing.xxs).testTag(tag)
    )
}

/** The open chip's editor, under the row. Picking a value applies it and folds the editor. */
@Composable
private fun QuickEditor(
    setting: QuickSetting,
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onClose: () -> Unit
) {
    val options = state.options
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xxs, bottom = Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (setting) {
                            QuickSetting.WEATHER -> "Weather"
                            QuickSetting.LEVEL -> "Attacker level"
                            QuickSetting.SORT -> "Rank by"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (setting == QuickSetting.WEATHER) {
                        weatherSourceNote(state)?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            val tiles = Modifier.padding(end = Spacing.md)
            when (setting) {
                QuickSetting.WEATHER -> WeatherTiles(
                    selected = options.weather,
                    onSelect = { actions.onOptionsChanged(options.copy(weather = it)); onClose() },
                    modifier = tiles
                )
                QuickSetting.LEVEL -> LevelTiles(
                    selected = options.attackerLevel,
                    onSelect = { actions.onOptionsChanged(options.copy(attackerLevel = it)); onClose() },
                    modifier = tiles
                )
                QuickSetting.SORT -> ChoiceTiles(
                    entries = PokebattlerSort.entries,
                    isSelected = { it == options.sort },
                    onSelect = { actions.onOptionsChanged(options.copy(sort = it)); onClose() },
                    label = { it.label },
                    minTileWidth = 96.dp,
                    tag = { "sort_tile_${it.name.lowercase()}" },
                    modifier = tiles
                )
            }
        }
    }
}

// ── B · Collapsing header ────────────────────────────────────────────────────

// ── Shared by the designs ────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CountersTopBar(
    state: RaidCountersUiState,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    title: @Composable () -> Unit = {
        Column {
            Text(
                text = "Best counters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            state.bossDisplayName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    },
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = title,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // One spinner for the whole screen, in the bar.
            if (state.isLoading || state.personalLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 12.dp).size(16.dp)
                )
            }
            ShareCountersButton(state)
            extraActions()
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CounterSetupSheet(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    title: String,
    excluded: Set<SetupItem>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        CounterSetupPanel(
            options = state.options,
            onOptionsChanged = actions.onOptionsChanged,
            live = LiveSetup(state, actions),
            title = title,
            excluded = excluded,
            modifier = Modifier.testTag("counters_setup")
        ) {
            SetupSheetFooter(onSaveAsDefault = actions.onSaveAsDefault, onDone = onDismiss)
        }
    }
}

/** Time left on the raid, small enough to sit beside the boss. */
@Composable
internal fun RaidTimePill(endMillis: Long) {
    val now by rememberCountdownClock()
    val remaining = (endMillis - now).coerceAtLeast(0L)
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Icon(
                painterResource(R.drawable.ic_timer),
                contentDescription = "Time remaining",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (remaining > 0L) TimeUtils.formatDurationShort(remaining) else "Ended",
                style = MetricTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/** The one place a design says how to get a roster to rank. */
@Composable
internal fun PersonalSourceHint() {
    Text(
        text = "To rank your own Pokémon, import a Poké Genie CSV or sign in to Pokébattler in " +
            "Settings › Raid counters.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("personal_source_hint")
    )
}

/** Whether [ScreenNotices] would draw anything, so an empty item never costs a gap. */
internal fun RaidCountersUiState.hasNotices(includeWeather: Boolean): Boolean =
    degradedOptions.isNotEmpty() ||
        (includeWeather && weatherUnconfirmed) ||
        (hasCounters && (isLoading || isStale || rateLimited))

