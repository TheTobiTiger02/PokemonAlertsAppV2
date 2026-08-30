package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.counters.CounterMetric
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PokemonGoSearch
import com.example.pokemonalertsv2.data.counters.bestOf
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import com.example.pokemonalertsv2.data.counters.toCounterMetric
import com.example.pokemonalertsv2.ui.alerts.rememberCountdownClock
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.launch

/**
 * The raid counters, as their own screen.
 *
 * Previously this was a card inside the alert detail scroller, which was the wrong shape for
 * it: a header, a source selector, a setup summary, a chip row, six rows and a footer is a
 * screen's worth of UI, and it pushed everything else on the detail page below the fold. The
 * detail page now carries a one-line [RaidCountersTeaser] that opens this.
 *
 * The list is the screen's root [LazyColumn], which also retires the "show all in a bottom
 * sheet" workaround: rows are lazy and recycled for free, so there is no longer a short list
 * and a long list to keep in sync.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RaidCountersScreen(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var setupSheetOpen by rememberSaveable { mutableStateOf(false) }
    var megaSheetOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // One spinner for the whole screen, in the bar. Rows must not each
                    // sprout their own progress affordance during a refresh.
                    if (state.isLoading || state.personalLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(16.dp)
                        )
                    }
                    ShareCountersButton(state)
                }
            )
        }
    ) { padding ->
        val metric = state.options.sort.toCounterMetric()
        val copyTeam: (CopyTeamFormat) -> Unit = { format ->
            val query = when (format) {
                CopyTeamFormat.EXACT -> PokemonGoSearch.teamQuery(state.team, state.dexNumbers)
                CopyTeamFormat.SPECIES -> PokemonGoSearch.speciesQuery(state.team, state.dexNumbers)
            }
            if (query.isNotBlank()) {
                clipboard.setText(AnnotatedString(query))
                scope.launch {
                    // Android 13+ shows its own copy toast, so this exists mainly to carry
                    // the fallback: an exact query silently matches nothing if the roster
                    // has moved on since the import.
                    val result = snackbarHostState.showSnackbar(
                        message = "Copied ${state.team.sumOf { it.count }} Pokémon",
                        actionLabel = if (format == CopyTeamFormat.EXACT) "Species only" else null,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        clipboard.setText(
                            AnnotatedString(
                                PokemonGoSearch.speciesQuery(state.team, state.dexNumbers)
                            )
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.raidEndTimeMillis?.let { endMillis ->
                item(key = "raid_time") { RaidTimeRemaining(endMillis) }
            }
            item(key = "boss") { BossSummary(state) }
            item(key = "source") { SourceSelector(state, actions) }
            if (state.personalTeamRequested &&
                state.source == CounterSourceId.ALL_POKEMON &&
                state.pokeGenieCount == 0 &&
                state.pokebattlerUserId.isNullOrBlank()
            ) {
                item(key = "personal_setup") { PersonalTeamSetupGuidance() }
            }
            item(key = "setup") { BattleSetupSummary(state, onOpen = { setupSheetOpen = true }) }
            if (state.showingPersonal) {
                item(key = "mega") { ActiveMegaRow(state, onOpen = { megaSheetOpen = true }) }
            }
            item(key = "quick") { QuickControls(state, actions) }
            // An item that renders nothing still costs a `spacedBy` gap, which is what
            // left a band of dead space above the first section.
            if (state.hasScreenNotices) {
                item(key = "notices") { ScreenNotices(state, actions) }
            }

            when (state.phase) {
                CountersPhase.UNRESOLVED -> item(key = "unresolved") { UnresolvedState(state, actions) }
                CountersPhase.LOADING -> item(key = "loading") { CounterSkeletonList("Loading counters…") }
                CountersPhase.ERROR -> item(key = "error") { ErrorState(state, actions) }
                CountersPhase.EMPTY -> item(key = "empty") { EmptyState(state) }
                CountersPhase.PERSONAL -> personalItems(state, actions, metric, copyTeam)
                CountersPhase.GENERAL -> generalItems(state, metric)
            }

            item(key = "footer") { CountersFooter(state) }
        }
    }

    if (megaSheetOpen) {
        ModalBottomSheet(onDismissRequest = { megaSheetOpen = false }) {
            ActiveMegaSheet(
                state = state,
                onSelect = {
                    actions.onActiveMegaChanged(it)
                    megaSheetOpen = false
                }
            )
        }
    }

    if (setupSheetOpen) {
        ModalBottomSheet(onDismissRequest = { setupSheetOpen = false }) {
            BattleSetupSheet(
                state = state,
                actions = actions,
                onDismiss = { setupSheetOpen = false }
            )
        }
    }
}

@Composable
private fun PersonalTeamSetupGuidance() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Set up your recommended team",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "Import a Poké Genie CSV or link Pokébattler in Settings → Raid counters. " +
                    "Copy for GO is enabled only for a real personal team.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun RaidTimeRemaining(endMillis: Long) {
    val now by rememberCountdownClock()
    val remaining = (endMillis - now).coerceAtLeast(0L)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Time remaining",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = if (remaining > 0L) {
                    TimeUtils.formatDurationShort(remaining)
                } else {
                    "Raid ended"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** Boss identity at the top of the list: artwork, moveset, types and the fixed tier facts. */
@Composable
private fun BossSummary(state: RaidCountersUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CounterSprite(
            // The feed thumbnail is form-exact, so prefer it and keep the rebuilt URLs as
            // the fallback for alerts that arrive without one.
            urls = listOfNotNull(state.bossThumbnailUrl) +
                state.spriteUrls[state.bossPokemonId].orEmpty(),
            size = 56.dp,
            type = state.pokemonTypes[state.bossPokemonId]?.firstOrNull()
        ) {
            Text(text = "🛡️", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            state.bossPokemonId?.let { id ->
                TypeBadges(types = state.pokemonTypes[id].orEmpty())
            }
            // Pokebattler uses the literal id RANDOM when it averages over movesets.
            val moves = listOfNotNull(state.bossMove1, state.bossMove2)
                .filterNot { it.equals("RANDOM", ignoreCase = true) }
                .mapNotNull { prettifyMoveName(it) }
            val movesetLabel = moves.takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")
                ?: state.selectedBossMoveset?.displayName
                ?: "Average moveset"
            Text(
                text = "Boss moveset · $movesetLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BossFacts(state)
        }
    }
}

/** Degraded-setup and refresh notices, hidden together when there is nothing to say. */
@Composable
private fun ScreenNotices(state: RaidCountersUiState, actions: RaidCountersActions) {
    val showRefresh = state.hasCounters &&
        (state.isLoading || state.isStale || state.rateLimited)
    if (state.degradedOptions.isEmpty() && !showRefresh) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Pokébattler serves precomputed rankings only, and for a boss outside the current
        // raid rotation just one combination exists. Rather than show nothing, the repository
        // retries at that baseline — so say which settings were dropped instead of quietly
        // changing the numbers under the user.
        if (state.degradedOptions.isNotEmpty()) {
            Text(
                text = "Pokébattler has no ranking for this boss at " +
                    state.degradedOptions.joinToString(" · ") +
                    ". Showing its level 40, no-friendship results.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showRefresh) InlineRefreshStatus(state, actions)
    }
}

// ── List bodies ──────────────────────────────────────────────────────────────

private fun LazyListScope.generalItems(state: RaidCountersUiState, metric: CounterMetric) {
    val all = if (state.ownedOnly) state.counters.filter { it.isOwned } else state.counters
    if (all.isEmpty()) {
        item(key = "general_empty") {
            Text(
                text = if (state.ownedOnly) {
                    "None of your imported Pokémon appears in this ranking. " +
                        "Turn off Owned only to see all counters."
                } else {
                    "No counters available."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    // Once per list, not once per row: every bar is a ratio to the same winning value.
    val best = metric.bestOf(all.map { it.counter.metrics().valueFor(metric) })
    items(
        items = all,
        key = { entry -> "counter-${entry.counter.pokemonId}-${entry.counter.rank}" }
    ) { entry ->
        GeneralRow(
            entry = entry,
            spriteUrls = state.spriteUrls,
            types = state.pokemonTypes,
            moveTypes = state.moveTypes,
            metric = metric,
            best = best
        )
    }
    if (state.pokeGenieCount > 0 && state.ownedMatchCount > 0) {
        item(key = "owned_note") {
            Text(
                text = "Highlighted are ${state.ownedMatchCount} you already own.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun LazyListScope.personalItems(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    metric: CounterMetric,
    onCopyTeam: (CopyTeamFormat) -> Unit
) {
    val personal = state.personal
    if (personal == null || personal.ranked.isEmpty()) {
        item(key = "personal_placeholder") { PersonalPlaceholder(state, actions) }
        return
    }

    if (state.hasPersonalNotices) {
        item(key = "personal_notices") { PersonalNotices(state, personal) }
    }
    item(key = "team") {
        TeamSection(
            personal = personal,
            team = state.team,
            activeMegaId = state.activeMegaId,
            spriteUrls = state.spriteUrls,
            metric = metric,
            onCopyTeam = onCopyTeam
        )
    }
    item(key = "personal_label") { SectionLabel("Your best counters") }

    val best = metric.bestOf(personal.ranked.map { it.metrics.valueFor(metric) })
    itemsIndexed(
        items = personal.ranked,
        key = { index, counter -> "personal-${counter.pokemonId}-$index" }
    ) { index, counter ->
        PersonalRow(
            rank = index + 1,
            counter = counter,
            spriteUrls = state.spriteUrls,
            types = state.pokemonTypes,
            moveTypes = state.moveTypes,
            metric = metric,
            best = best
        )
    }
    if (personal.ranked.any { it.movesetAssumed } ||
        (state.personalMovesMode == PersonalMovesMode.CURRENT &&
            state.pokeGenieCount > personal.ranked.size)
    ) {
        item(key = "personal_notes") { PersonalListNotes(state, personal) }
    }
}

/** The empty, failed and not-yet-ranked personal states, which are all a single block. */
@Composable
private fun PersonalPlaceholder(state: RaidCountersUiState, actions: RaidCountersActions) {
    val personal = state.personal
    when {
        state.personalError != null && personal == null -> Column {
            Text(
                text = state.personalError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = actions.onRetry) { Text("Retry") }
                // A failing Pokébox should not strand the user on it when their imported
                // roster is right there and works.
                if (state.source == CounterSourceId.POKEBATTLER_POKEBOX && state.pokeGenieCount > 0) {
                    TextButton(
                        onClick = { actions.onSourceChanged(CounterSourceId.POKE_GENIE) }
                    ) { Text("Use Poké Genie CSV") }
                }
            }
        }

        // Loading is its own state, distinct from "nothing to show". Keying this on
        // `personal == null` alone made every stalled or aborted job look like progress.
        personal == null && state.personalLoading -> CounterSkeletonList(
            state.personalProgress
                ?.takeIf { it.totalLevels > 0 }
                ?.let { "Ranking your Pokémon… (${it.completedLevels}/${it.totalLevels})" }
                ?: "Ranking your Pokémon…"
        )

        personal == null -> Column {
            Text(
                text = "No ranking yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = actions.onRetry) { Text("Retry") }
        }

        else -> Column {
            Text(
                text = if (state.source == CounterSourceId.POKEBATTLER_POKEBOX) {
                    "Your Pokébattler Pokébox is empty, or the account has no Pokémon saved."
                } else {
                    "None of your ${state.pokeGenieCount} imported Pokémon is among Pokébattler's " +
                        "top counters for this boss at level 40 or above."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = actions.onRetry) { Text("Retry") }
        }
    }
}

/** What the ranking covers and what it could not — never silent about a partial result. */
@Composable
private fun PersonalNotices(state: RaidCountersUiState, personal: PersonalRanking) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.personalLoading) {
            Text(
                state.personalProgress
                    ?.takeIf { it.totalLevels > 0 }
                    ?.let { "Updating… Pokébattler levels ${it.completedLevels}/${it.totalLevels}" }
                    ?: "Updating your ranking…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Say what actually happened. Pokébattler returns only its top 30 counters for the
        // boss, so this is a match count against those 30 — not "your best N out of 2405",
        // which is what the old wording implied.
        state.personalProgress?.takeIf { it.serverCandidates > 0 }?.let { progress ->
            Text(
                "Matched ${personal.ranked.size} of your Pokémon to Pokébattler's top " +
                    "${progress.serverCandidates} counters for this boss · level 40+ only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Only levels/40 is precomputed for an off-rotation boss, so the level-50 bucket
        // gets scored against the level-40 response. Understating is fine; hiding it is not.
        state.personalProgress?.substitutedLevels?.takeIf { it.isNotEmpty() }?.let { levels ->
            Text(
                "Pokébattler has no level " +
                    levels.joinToString(", ") { formatLevel(it) } +
                    " ranking for this boss; those Pokémon are scored at level 40.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.personalError?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Trailing caveats about assumed and excluded imports. */
@Composable
private fun PersonalListNotes(state: RaidCountersUiState, personal: PersonalRanking) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (personal.ranked.any { it.movesetAssumed }) {
            Text(
                text = if (state.personalMovesMode == PersonalMovesMode.CURRENT) {
                    "Current moves excludes imports without a recorded fast and charged move."
                } else {
                    "Assumed moves are shown when Poké Genie did not record that move component."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.personalMovesMode == PersonalMovesMode.CURRENT) {
            val excluded = (state.pokeGenieCount - personal.ranked.size).coerceAtLeast(0)
            if (excluded > 0) {
                Text(
                    "$excluded imported Pokémon excluded from Current moves " +
                        "(missing moves or unmatched).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
