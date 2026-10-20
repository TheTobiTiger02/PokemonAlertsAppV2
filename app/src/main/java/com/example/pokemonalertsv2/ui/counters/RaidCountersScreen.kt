package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
fun RaidCountersScreen(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    RaidCountersChipRow(state, actions, onBack, modifier)
}

/** The list's padding: the screen gutter, and room for the navigation bar under the last row. */
@Composable
internal fun countersListPadding(top: androidx.compose.ui.unit.Dp = 8.dp): PaddingValues = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = top,
    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
)

/** The counters themselves for the current phase, then where they came from. */
internal fun LazyListScope.countersBody(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    copyTeam: (CopyTeamFormat) -> Unit
) {
    val metric = state.options.sort.toCounterMetric()
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

/**
 * Copies the suggested team as a Pokémon GO search, and offers the species-only query when the
 * exact one may no longer match.
 */
@Composable
internal fun rememberCopyTeam(
    state: RaidCountersUiState,
    snackbarHostState: SnackbarHostState
): (CopyTeamFormat) -> Unit {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    return { format ->
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
}

/** Boss identity at the top of the list: artwork, moveset, types and the fixed tier facts. */
@Composable
internal fun BossSummary(
    state: RaidCountersUiState,
    spriteSize: androidx.compose.ui.unit.Dp = 56.dp,
    trailing: @Composable () -> Unit = {}
) {
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
            size = spriteSize,
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
        trailing()
    }
}

/**
 * Degraded-setup and refresh notices, hidden together when there is nothing to say. Designs that
 * show the weather's source next to the weather itself pass [includeWeather] false.
 */
@Composable
internal fun ScreenNotices(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    includeWeather: Boolean = true
) {
    val showRefresh = state.hasCounters &&
        (state.isLoading || state.isStale || state.rateLimited)
    val showWeather = includeWeather && state.weatherUnconfirmed
    if (state.degradedOptions.isEmpty() && !showWeather && !showRefresh) return
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
        // Ranked with this weather regardless — the last known reading beats assuming none —
        // but say so, because the wrong boost reorders the counters.
        if (showWeather) {
            Text(
                text = "Using ${state.options.weather.label} — this weather is not confirmed yet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showRefresh) InlineRefreshStatus(state, actions)
    }
}

// ── List bodies ──────────────────────────────────────────────────────────────

internal fun LazyListScope.generalItems(state: RaidCountersUiState, metric: CounterMetric) {
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

internal fun LazyListScope.personalItems(
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
                    "${progress.serverCandidates} counters for this boss · level 40+ only · " +
                    when (state.personalMovesMode) {
                        // The mode lives in the setup sheet, so a roster silently scored on
                        // ideal movesets is indistinguishable from a ranking bug. Name it.
                        PersonalMovesMode.CURRENT -> "scored on your current moves"
                        PersonalMovesMode.BEST_POTENTIAL -> "scored on best potential moves"
                    },
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
