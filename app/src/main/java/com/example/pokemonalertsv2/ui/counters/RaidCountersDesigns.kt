package com.example.pokemonalertsv2.ui.counters

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import com.example.pokemonalertsv2.ui.alerts.rememberCountdownClock
import com.example.pokemonalertsv2.ui.components.DesignCompareStrip
import com.example.pokemonalertsv2.ui.components.DesignScreen
import com.example.pokemonalertsv2.ui.theme.MetricTextStyle
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TimeUtils

// Three redesigns of the raid counters screen, compared on a device through DesignScreen.RAID_COUNTERS.
// Each keeps every setting in exactly one place, says "import your Pokémon" once, and edits the
// battle setup through the shared CounterSetupPanel.

// ── A · Chip row ─────────────────────────────────────────────────────────────

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
            DesignCompareStrip(DesignScreen.RAID_COUNTERS)
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

/**
 * A large boss header that scrolls away into the top bar, sticky source tabs, removable chips
 * for every setting off its default, and the whole setup in one sheet opened from the top bar.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
internal fun RaidCountersCollapsing(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val copyTeam = rememberCopyTeam(state, snackbarHostState)
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CountersTopBar(
                state = state,
                onBack = onBack,
                title = {
                    Crossfade(targetState = collapsed, label = "collapsed_title") { small ->
                        if (small) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.bossDisplayName ?: "Best counters",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                state.raidEndTimeMillis?.let {
                                    Spacer(Modifier.size(Spacing.sm))
                                    RaidTimePill(it)
                                }
                            }
                        } else {
                            Text(
                                "Best counters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                extraActions = {
                    TextButton(
                        onClick = { setupOpen = true },
                        modifier = Modifier.testTag("open_setup")
                    ) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.xs))
                        Text("Setup")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DesignCompareStrip(DesignScreen.RAID_COUNTERS)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("counters_list"),
                contentPadding = countersListPadding(top = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "hero") { CollapsingBossHeader(state, listState) }
                stickyHeader(key = "source") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                            SourceSelector(state, actions, showHint = false)
                        }
                    }
                }
                if (!state.hasPersonalSource) item(key = "source_hint") { PersonalSourceHint() }
                if (activeRules(state).isNotEmpty()) {
                    item(key = "rules") { ActiveRulesRow(state, actions) }
                }
                if (state.hasNotices(includeWeather = true)) {
                    item(key = "notices") { ScreenNotices(state, actions) }
                }
                countersBody(state, actions, copyTeam)
            }
        }
    }

    if (setupOpen) {
        CounterSetupSheet(
            state = state,
            actions = actions,
            title = "Setup",
            excluded = emptySet(),
            onDismiss = { setupOpen = false }
        )
    }
}

/** Fades and drifts up as it scrolls under the top bar, which then takes over the boss's name. */
@Composable
private fun CollapsingBossHeader(state: RaidCountersUiState, listState: LazyListState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs)
            .graphicsLayer {
                val offset = if (listState.firstVisibleItemIndex == 0) {
                    listState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    size.height
                }
                val progress = if (size.height > 0f) (offset / size.height).coerceIn(0f, 1f) else 0f
                alpha = 1f - progress
                translationY = offset * 0.4f
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            CounterSprite(
                urls = listOfNotNull(state.bossThumbnailUrl) + state.spriteUrls[state.bossPokemonId].orEmpty(),
                size = 80.dp,
                type = state.pokemonTypes[state.bossPokemonId]?.firstOrNull()
            ) {
                Text(text = "🛡️", style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = state.bossDisplayName ?: state.unresolvedBossName ?: "Raid boss",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                state.bossPokemonId?.let { id -> TypeBadges(types = state.pokemonTypes[id].orEmpty()) }
                BossFacts(state)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = "Moveset · ${bossMovesetLabel(state)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            state.raidEndTimeMillis?.let { RaidTimePill(it) }
        }
    }
}

/** A setting that differs from its default, and how to put it back. */
private class ActiveRule(val id: String, val label: String, val clear: () -> Unit)

private fun activeRules(state: RaidCountersUiState, actions: RaidCountersActions = RaidCountersActions.Noop): List<ActiveRule> {
    val options = state.options
    val defaults = RaidCounterOptions()
    val set: (RaidCounterOptions) -> Unit = actions.onOptionsChanged
    return buildList {
        state.selectedBossMoveset?.let { add(ActiveRule("moveset", it.displayName) { actions.onBossMovesetChanged(null) }) }
        if (options.weather != defaults.weather) {
            val from = if (state.weatherFromAlert) " · from raid" else ""
            add(ActiveRule("weather", options.weather.tileLabel + from) { set(options.copy(weather = defaults.weather)) })
        }
        if (!state.showingPersonal && options.attackerLevel != defaults.attackerLevel) {
            add(ActiveRule("level", "Level ${options.attackerLevel}") { set(options.copy(attackerLevel = defaults.attackerLevel)) })
        }
        if (options.friendship != defaults.friendship) {
            add(ActiveRule("friendship", options.friendship.label) { set(options.copy(friendship = defaults.friendship)) })
        }
        if (options.attackStrategy != defaults.attackStrategy) {
            add(ActiveRule("strategy", options.attackStrategy.label) { set(options.copy(attackStrategy = defaults.attackStrategy)) })
        }
        if (options.sort != defaults.sort) {
            add(ActiveRule("sort", "By ${options.sort.label.lowercase()}") { set(options.copy(sort = defaults.sort)) })
        }
        if (options.partyPower) add(ActiveRule("party", "Party Power") { set(options.copy(partyPower = false)) })
        if (!options.includeMegas) add(ActiveRule("megas", "No megas") { set(options.copy(includeMegas = true)) })
        if (!options.includeShadow) add(ActiveRule("shadows", "No shadows") { set(options.copy(includeShadow = true)) })
        if (!options.includeLegendary) add(ActiveRule("legendaries", "No legendaries") { set(options.copy(includeLegendary = true)) })
        if (!state.showingPersonal && state.ownedOnly) add(ActiveRule("owned", "Owned only") { actions.onOwnedOnlyChanged(false) })
        if (state.showingPersonal) {
            state.activeMegaId?.let { id ->
                val name = state.megaOptions.firstOrNull { it.pokemonId == id }?.displayName ?: "Active mega"
                add(ActiveRule("mega", name) { actions.onActiveMegaChanged(null) })
            }
        }
    }
}

/** One removable chip per setting off its default; tapping one puts that setting back. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActiveRulesRow(state: RaidCountersUiState, actions: RaidCountersActions) {
    val rules = activeRules(state, actions)
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("counters_active_rules"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        rules.forEach { rule ->
            InputChip(
                selected = true,
                onClick = rule.clear,
                label = { Text(rule.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove ${rule.label}",
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("rule_${rule.id}")
            )
        }
    }
}

// ── C · Tabs ─────────────────────────────────────────────────────────────────

private enum class CountersTab(val label: String) { COUNTERS("Counters"), TEAM("My team"), SETUP("Setup") }

/** Counters | My team | Setup under a compact boss row; the setup edits in its own tab. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RaidCountersTabs(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable {
        mutableIntStateOf(if (state.showingPersonal) CountersTab.TEAM.ordinal else CountersTab.COUNTERS.ordinal)
    }
    // A source change from elsewhere (the arrival notification asks for the team) moves the tab.
    LaunchedEffect(state.showingPersonal) {
        if (tab != CountersTab.SETUP.ordinal) {
            tab = if (state.showingPersonal) CountersTab.TEAM.ordinal else CountersTab.COUNTERS.ordinal
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val copyTeam = rememberCopyTeam(state, snackbarHostState)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CountersTopBar(state, onBack) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DesignCompareStrip(DesignScreen.RAID_COUNTERS)
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = Spacing.sm)) {
                BossSummary(state, spriteSize = 48.dp) { state.raidEndTimeMillis?.let { RaidTimePill(it) } }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                CountersTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry.ordinal,
                        onClick = {
                            tab = entry.ordinal
                            when (entry) {
                                CountersTab.COUNTERS -> if (state.showingPersonal) {
                                    actions.onSourceChanged(CounterSourceId.ALL_POKEMON)
                                }
                                CountersTab.TEAM -> if (!state.showingPersonal && state.hasPersonalSource) {
                                    actions.onSourceChanged(
                                        if (state.pokeGenieCount > 0) CounterSourceId.POKE_GENIE
                                        else CounterSourceId.POKEBATTLER_POKEBOX
                                    )
                                }
                                CountersTab.SETUP -> Unit
                            }
                        },
                        text = { Text(entry.label, maxLines = 1) },
                        modifier = Modifier.testTag("counters_tab_${entry.name.lowercase()}")
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (CountersTab.entries[tab]) {
                    CountersTab.COUNTERS, CountersTab.TEAM -> {
                        val team = tab == CountersTab.TEAM.ordinal
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("counters_list"),
                            contentPadding = countersListPadding(top = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (team && !state.hasPersonalSource) {
                                item(key = "source_hint") { PersonalSourceHint() }
                            } else {
                                if (team && state.pokeGenieCount > 0 && !state.pokebattlerUserId.isNullOrBlank()) {
                                    item(key = "source") { SourceSelector(state, actions, showHint = false, showTopLevel = false) }
                                }
                                if (state.hasNotices(includeWeather = true)) {
                                    item(key = "notices") { ScreenNotices(state, actions) }
                                }
                                countersBody(state, actions, copyTeam)
                            }
                        }
                    }

                    CountersTab.SETUP -> CounterSetupPanel(
                        options = state.options,
                        onOptionsChanged = actions.onOptionsChanged,
                        live = LiveSetup(state, actions),
                        modifier = Modifier.fillMaxSize().testTag("counters_setup")
                    ) {
                        TextButton(
                            onClick = actions.onSaveAsDefault,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.lg)
                        ) { Text("Save as default") }
                    }
                }
            }
        }
    }
}

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

internal fun bossMovesetLabel(state: RaidCountersUiState): String {
    // Pokebattler uses the literal id RANDOM when it averages over movesets.
    val moves = listOfNotNull(state.bossMove1, state.bossMove2)
        .filterNot { it.equals("RANDOM", ignoreCase = true) }
        .mapNotNull { prettifyMoveName(it) }
    return moves.takeIf { it.isNotEmpty() }?.joinToString(" / ")
        ?: state.selectedBossMoveset?.displayName
        ?: "Average"
}

