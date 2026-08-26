package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.CounterMetric
import com.example.pokemonalertsv2.data.counters.DecoratedCounter
import com.example.pokemonalertsv2.data.counters.PersonalCounter
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounter
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidBossMoveset
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import java.util.Locale

private const val COLLAPSED_COUNT = 6

/**
 * Best counters for a raid boss.
 *
 * Two modes. "Pokébattler" is its generic ranking of level-N attackers, with the ones the
 * user owns highlighted. "My Pokémon" joins a CSV or Pokébox roster to Pokébattler's own
 * server metrics; the former uses one exact-level request per distinct imported level.
 *
 * Kept out of `SharedComponents.kt`, which is already past 3000 lines, but deliberately
 * mirrors the card styling of its neighbours there.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RaidCountersCard(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return

    var setupSheetOpen by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Header(state)

            Spacer(modifier = Modifier.height(12.dp))
            SourceSelector(state, actions)

            Spacer(modifier = Modifier.height(10.dp))
            BattleSetupSummary(state, onOpen = { setupSheetOpen = true })
            // Pokébattler serves precomputed rankings only, and for a boss outside the
            // current raid rotation just one combination exists. Rather than show nothing,
            // the repository retries at that baseline — so say which settings were dropped
            // instead of quietly changing the numbers under the user.
            if (state.degradedOptions.isNotEmpty()) {
                Text(
                    text = "Pokébattler has no ranking for this boss at " +
                        state.degradedOptions.joinToString(" · ") +
                        ". Showing its level 40, no-friendship results.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (state.hasCounters && (state.isLoading || state.isStale || state.rateLimited)) {
                InlineRefreshStatus(state, actions)
            }
            Spacer(modifier = Modifier.height(14.dp))

            when {
                state.unresolvedBossName != null -> UnresolvedState(state, actions)
                state.isLoading && !state.hasCounters -> LoadingRow("Loading counters…")
                state.errorMessage != null && !state.hasCounters -> ErrorState(state, actions)
                state.showingPersonal -> PersonalContent(state, actions)
                state.hasCounters -> GeneralList(state, actions)
                else -> EmptyState(state)
            }
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
private fun InlineRefreshStatus(state: RaidCountersUiState, actions: RaidCountersActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = when {
                state.rateLimited -> "Pokébattler is busy"
                state.isLoading -> "Refreshing counters…"
                else -> "Showing cached counters"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (state.rateLimited || state.isStale) {
            TextButton(onClick = actions.onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun Header(state: RaidCountersUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CounterSprite(
            // The feed thumbnail is form-exact, so prefer it and keep the rebuilt URLs as
            // the fallback for alerts that arrive without one.
            urls = listOfNotNull(state.bossThumbnailUrl) +
                state.spriteUrls[state.bossPokemonId].orEmpty(),
            size = 40.dp
        ) {
            Text(text = "🛡️", style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Best counters",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val subtitle = buildString {
                state.bossDisplayName?.let { append(it) }
                // Pokebattler uses the literal id RANDOM when it averages over movesets.
                val moves = listOfNotNull(state.bossMove1, state.bossMove2)
                    .filterNot { it.equals("RANDOM", ignoreCase = true) }
                    .mapNotNull { prettifyMoveName(it) }
                if (moves.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(moves.joinToString(" / "))
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (state.isLoading || state.personalLoading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SourceSelector(state: RaidCountersUiState, actions: RaidCountersActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SegmentedChoice(
            label = "Pokébattler",
            selected = state.source == CounterSourceId.ALL_POKEMON,
            modifier = Modifier.weight(1f),
            onClick = { actions.onSourceChanged(CounterSourceId.ALL_POKEMON) }
        )
        SegmentedChoice(
            label = "My Pokémon",
            selected = state.showingPersonal,
            enabled = state.pokeGenieCount > 0 || !state.pokebattlerUserId.isNullOrBlank(),
            modifier = Modifier.weight(1f),
            onClick = {
                actions.onSourceChanged(
                    if (state.pokeGenieCount > 0) CounterSourceId.POKE_GENIE
                    else CounterSourceId.POKEBATTLER_POKEBOX
                )
            }
        )
    }
    if (state.showingPersonal &&
        state.pokeGenieCount > 0 && !state.pokebattlerUserId.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedChoice(
                label = "PokéGenie CSV",
                selected = state.source == CounterSourceId.POKE_GENIE,
                modifier = Modifier.weight(1f),
                onClick = { actions.onSourceChanged(CounterSourceId.POKE_GENIE) }
            )
            SegmentedChoice(
                label = state.pokebattlerAccountName?.let { "Pokébox · $it" } ?: "My Pokébox",
                selected = state.source == CounterSourceId.POKEBATTLER_POKEBOX,
                modifier = Modifier.weight(1f),
                onClick = { actions.onSourceChanged(CounterSourceId.POKEBATTLER_POKEBOX) }
            )
        }
    }
}

@Composable
private fun SegmentedChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        },
        contentColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun BattleSetupSummary(state: RaidCountersUiState, onOpen: () -> Unit) {
    val options = state.options
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "Open battle setup",
                onClick = onOpen
            )
            .semantics {
                contentDescription = "Battle setup: level ${options.attackerLevel}, " +
                    "${options.weather.label}, ${options.friendship.label}, ${options.sort.label}"
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Battle setup", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = buildString {
                        append("L${options.attackerLevel} · ${options.weather.label} · ")
                        append(options.friendship.label)
                        append(" · ${options.sort.label}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpen) { Text("Tune") }
        }
    }
}

@Composable
private fun BattleSetupSheet(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onDismiss: () -> Unit
) {
    val options = state.options
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Battle setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            "These controls match the Pokébattler raid defaults. Results stay visible while a new query loads.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OptionDropdown(
            label = state.selectedBossMoveset?.displayName ?: "Average moveset",
            entries = buildList {
                add(null to "Average moveset")
                state.bossMovesets.filterNot { it.isRandom }.forEach { add(it to it.displayName) }
            }.distinctBy { it.first?.cacheToken() ?: "AVERAGE" },
            onSelect = actions.onBossMovesetChanged
        )
        OptionDropdown(
            label = "Level ${options.attackerLevel}",
            entries = RaidCounterOptions.ATTACKER_LEVELS.map { it to "Level $it" },
            onSelect = { actions.onOptionsChanged(options.copy(attackerLevel = it)) },
            enabled = !state.showingPersonal
        )
        OptionDropdown(
            label = options.weather.label,
            entries = PokebattlerWeather.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(weather = it)) }
        )
        OptionDropdown(
            label = options.friendship.label,
            entries = PokebattlerFriendship.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(friendship = it)) }
        )
        OptionDropdown(
            label = options.attackStrategy.label,
            entries = com.example.pokemonalertsv2.data.counters.PokebattlerAttackStrategy.entries
                .map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(attackStrategy = it)) }
        )
        OptionDropdown(
            label = options.sort.label,
            entries = PokebattlerSort.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(sort = it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        SheetSwitchRow("Include megas", options.includeMegas) {
            actions.onOptionsChanged(options.copy(includeMegas = it))
        }
        SheetSwitchRow("Include shadows", options.includeShadow) {
            actions.onOptionsChanged(options.copy(includeShadow = it))
        }
        SheetSwitchRow("Include legendaries", options.includeLegendary) {
            actions.onOptionsChanged(options.copy(includeLegendary = it))
        }
        SheetSwitchRow("Owned only", state.ownedOnly, actions.onOwnedOnlyChanged)

        if (state.showingPersonal) {
            Text("My Pokémon moves", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PersonalMovesMode.entries.forEach { mode ->
                    SegmentedChoice(
                        label = mode.label,
                        selected = state.personalMovesMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { actions.onPersonalMovesModeChanged(mode) }
                    )
                }
            }
            Text(
                if (state.personalMovesMode == PersonalMovesMode.CURRENT) {
                    "Only imports with a recorded fast move and at least one charged move are ranked."
                } else {
                    "Known moves are kept; only missing components use the best legal move."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = actions.onSaveAsDefault) { Text("Save as default") }
        TextButton(onClick = onDismiss) { Text("Done") }
    }
}

@Composable
private fun SheetSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CounterChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun <T> OptionDropdown(
    label: String,
    entries: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CounterChip(label = label, selected = false, enabled = enabled) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

// ── Personal mode ────────────────────────────────────────────────────────────

@Composable
private fun PersonalContent(state: RaidCountersUiState, actions: RaidCountersActions) {
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
        personal == null && state.personalLoading -> LoadingRow(
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

        personal.ranked.isEmpty() -> Column {
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

        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            // Say what actually happened. Pokébattler returns only its top 30 counters for
            // the boss, so this is a match count against those 30 — not "your best N out of
            // 2405", which is what the old wording implied.
            state.personalProgress?.takeIf { it.serverCandidates > 0 }?.let { progress ->
                Text(
                    "Matched ${personal.ranked.size} of your Pokémon to Pokébattler's top " +
                        "${progress.serverCandidates} counters for this boss · " +
                        "level 40+ only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Only levels/40 is precomputed for an off-rotation boss, so the level-50
            // bucket gets scored against the level-40 response. Understating is fine;
            // hiding it is not.
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
            TeamSection(personal, state.spriteUrls, state.options.sort.toCounterMetric())
            PersonalList(state, personal, actions)
        }
    }
}

@Composable
private fun TeamSection(
    personal: PersonalRanking,
    spriteUrls: Map<String, List<String>>,
    metric: CounterMetric
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Suggested team")

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (personal.serverBacked) {
                        "Pokébattler's top six for the selected setup"
                    } else if (personal.canSolo) {
                        "These six can take it down on their own."
                    } else {
                        "These six deal about ${(personal.bossFraction * 100).toInt()}% of its HP."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                val teamDetails = if (personal.serverBacked) {
                    listOf("Individual rows and metrics are copied from Pokébattler")
                } else buildList {
                    if (personal.teamDeaths > 0.0) {
                        add("${"%.1f".format(Locale.US, personal.teamDeaths)} faints")
                    }
                    personal.teamTimeToWinSeconds?.let {
                        add("${"%.0f".format(Locale.US, it)}s to win")
                    }
                }
                if (teamDetails.isNotEmpty()) {
                    Text(
                        text = teamDetails.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!personal.serverBacked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { personal.bossFraction.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        TeamSlots(personal, spriteUrls)

        personal.team.forEach { slot ->
            TeamRow(slot.counter, slot.count, spriteUrls, metric)
        }
    }
}

@Composable
private fun TeamRow(
    counter: PersonalCounter,
    count: Int,
    spriteUrls: Map<String, List<String>>,
    metric: CounterMetric
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CounterSprite(spriteUrls[counter.pokemonId].orEmpty(), size = 34.dp)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Text(
                text = "×$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = counter.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = counter.moveLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = counter.statLine(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = counter.metrics.headline(metric),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PersonalList(
    state: RaidCountersUiState,
    personal: PersonalRanking,
    actions: RaidCountersActions
) {
    val shown = if (state.expanded) personal.ranked.take(30) else personal.ranked.take(COLLAPSED_COUNT)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Your best counters")
        shown.forEachIndexed { index, counter ->
            PersonalRow(index + 1, counter, state.spriteUrls, state.options.sort.toCounterMetric())
        }
        if (personal.ranked.size > COLLAPSED_COUNT) {
            TextButton(onClick = actions.onToggleExpanded) {
                Text(if (state.expanded) "Show fewer" else "Show more")
            }
        }
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
                    "$excluded imported Pokémon excluded from Current moves (missing moves or unmatched).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PersonalRow(
    rank: Int,
    counter: PersonalCounter,
    spriteUrls: Map<String, List<String>>,
    metric: CounterMetric
) {
    var expanded by remember(counter.owned.displayName, counter.pokemonId, rank) {
        mutableStateOf(false)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .semantics {
                role = Role.Button
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RankBadge(rank)
        CounterSprite(spriteUrls[counter.pokemonId].orEmpty(), size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = counter.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = counter.metrics.headline(metric),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.0f dmg".format(Locale.US, counter.tdo),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = counter.moveLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = counter.statLine(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (counter.movesetAssumed) {
                Text(
                    "Assumed move(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (expanded) {
                Text(
                    text = listOfNotNull(
                        counter.metrics.headline(CounterMetric.OVERALL),
                        counter.metrics.headline(CounterMetric.ESTIMATOR),
                        counter.metrics.headline(CounterMetric.TIME),
                        counter.metrics.headline(CounterMetric.POWER),
                        counter.metrics.headline(CounterMetric.TDO),
                        counter.metrics.deaths?.let { "${"%.1f".format(Locale.US, it)} faints" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Pokebattler mode ─────────────────────────────────────────────────────────

@Composable
private fun GeneralList(state: RaidCountersUiState, actions: RaidCountersActions) {
    val all = if (state.ownedOnly) state.counters.filter { it.isOwned } else state.counters
    if (all.isEmpty()) {
        Text(
            if (state.ownedOnly) {
                "None of your imported Pokémon appears in this ranking. Turn off Owned only to see all counters."
            } else {
                "No counters available."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val shown = if (state.expanded) all else all.take(COLLAPSED_COUNT)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { GeneralRow(it, state.spriteUrls, state.options.sort.toCounterMetric()) }
        if (all.size > COLLAPSED_COUNT) {
            TextButton(onClick = actions.onToggleExpanded) {
                Text(if (state.expanded) "Show fewer" else "Show all ${all.size}")
            }
        }
        if (state.pokeGenieCount > 0 && state.ownedMatchCount > 0) {
            Text(
                text = "Highlighted are ${state.ownedMatchCount} you already own.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GeneralRow(entry: DecoratedCounter, spriteUrls: Map<String, List<String>>) {
    GeneralRow(entry, spriteUrls, CounterMetric.ESTIMATOR)
}

@Composable
private fun GeneralRow(
    entry: DecoratedCounter,
    spriteUrls: Map<String, List<String>>,
    metric: CounterMetric
) {
    val counter = entry.counter
    var expanded by remember(entry.counter.pokemonId, entry.counter.rank) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .semantics {
                role = Role.Button
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RankBadge(counter.rank, highlighted = entry.isOwned)
        CounterSprite(spriteUrls[counter.pokemonId].orEmpty(), size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = counter.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = headlineMetric(counter, metric),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val moves = listOfNotNull(counter.fastMove, counter.chargedMove)
            if (moves.isNotEmpty()) {
                Text(
                    text = moves.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            entry.owned?.let { mine ->
                Text(
                    text = buildString {
                        append("Yours: ")
                        mine.level?.let { append("L").append(formatLevel(it)) }
                        if (mine.atkIv != null && mine.defIv != null && mine.staIv != null) {
                            append(" · ${mine.atkIv}/${mine.defIv}/${mine.staIv}")
                        }
                        mine.cp?.let { append(" · CP ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                val metrics = counter.metrics()
                val details = buildList {
                    counter.level?.let { add("Level $it") }
                    if (counter.atkIv != null && counter.defIv != null && counter.staIv != null) {
                        add("IV ${counter.atkIv}/${counter.defIv}/${counter.staIv}")
                    }
                    counter.cp?.let { add("CP $it") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = listOfNotNull(
                        metrics.headline(CounterMetric.OVERALL),
                        metrics.headline(CounterMetric.ESTIMATOR),
                        metrics.headline(CounterMetric.TIME),
                        metrics.headline(CounterMetric.POWER),
                        metrics.headline(CounterMetric.TDO),
                        metrics.deaths?.let { "${"%.1f".format(Locale.US, it)} faints" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TeamSlots(personal: PersonalRanking, spriteUrls: Map<String, List<String>>) {
    val slots = personal.team.flatMap { slot -> List(slot.count) { slot.counter } }.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until 6).chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { index ->
                    val counter = slots.getOrNull(index)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Team slot ${index + 1}: " +
                                    (counter?.displayName ?: "Empty")
                            },
                        shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        ) {
                        if (counter == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(66.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Empty", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CounterSprite(
                                    spriteUrls[counter.pokemonId].orEmpty(),
                                    size = 36.dp,
                                    fallback = { Text("•") }
                                )
                                Text(
                                    counter.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared pieces ────────────────────────────────────────────────────────────

/**
 * Circular Pokemon artwork with a graceful cascade.
 *
 * [urls] is best-first: a mega or shadow variant, then the plain dex sprite. Coverage of the
 * variants is uneven, so a load failure advances to the next URL and finally to [fallback].
 * Mirrors GoDexEntryArtwork, and deliberately passes no imageLoader: the Application is an
 * ImageLoaderFactory and already supplies the shared, disk-cached one.
 */
@Composable
private fun CounterSprite(
    urls: List<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {}
) {
    var attempt by remember(urls) { mutableStateOf(0) }
    if (urls.isEmpty() || attempt >= urls.size) {
        fallback()
        return
    }
    val context = LocalContext.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.size(size)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(urls[attempt])
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            error = painterResource(id = R.drawable.ic_placeholder),
            onError = { attempt++ },
            modifier = Modifier.padding(3.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun RankBadge(rank: Int, highlighted: Boolean = false) {
    val container = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val content = if (highlighted) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(shape = CircleShape, color = container) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LoadingRow(label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnresolvedState(state: RaidCountersUiState, actions: RaidCountersActions) {
    Column {
        Text(
            text = "Couldn't match ${state.unresolvedBossName} to a Pokebattler raid boss.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = actions.onRetry) { Text("Retry") }
    }
}

@Composable
private fun ErrorState(state: RaidCountersUiState, actions: RaidCountersActions) {
    Column {
        Text(
            text = state.errorMessage.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = actions.onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyState(state: RaidCountersUiState) {
    Text(
        text = if (state.pokeGenieCount == 0) {
            "No counters available for this raid."
        } else {
            "No counters available."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun PersonalCounter.moveLine(): String =
    listOfNotNull(prettifyMoveName(fastMove.moveId), prettifyMoveName(chargedMove.moveId))
        .joinToString(" · ")

private fun PersonalCounter.statLine(): String = buildString {
    owned.level?.let { append("L").append(formatLevel(it)) }
    evaluatedLevel?.takeIf { owned.level == null || it != owned.level }?.let {
        if (isNotEmpty()) append(" · ")
        append("PB L").append(formatLevel(it))
    }
    if (owned.atkIv != null && owned.defIv != null && owned.staIv != null) {
        if (isNotEmpty()) append(" · ")
        append("${owned.atkIv}/${owned.defIv}/${owned.staIv}")
    }
    if (rankingIgnoresIv) {
        if (isNotEmpty()) append(" · ")
        append("IVs ignored by PB")
    }
    owned.cp?.let {
        if (isNotEmpty()) append(" · ")
        append("CP ").append(it)
    }
}

/**
 * The Pokebattler estimator, read as how many trainers it takes. Lower is better.
 *
 * Shown regardless of the chosen sort: the old overallRating * 100 was a cost metric dressed
 * up as a quality score, and rendered as a meaningless "36%".
 */
internal fun headlineMetric(counter: RaidCounter): String =
    headlineMetric(counter, CounterMetric.ESTIMATOR)

internal fun headlineMetric(counter: RaidCounter, metric: CounterMetric): String =
    counter.metrics().headline(metric)

private fun PokebattlerSort.toCounterMetric(): CounterMetric = when (this) {
    PokebattlerSort.OVERALL -> CounterMetric.OVERALL
    PokebattlerSort.ESTIMATOR -> CounterMetric.ESTIMATOR
    PokebattlerSort.TIME -> CounterMetric.TIME
    PokebattlerSort.POWER -> CounterMetric.POWER
    PokebattlerSort.TDO -> CounterMetric.TDO
}

/** Levels are half steps, so 40.0 reads as "40" and 31.5 as "31.5". */
private fun formatLevel(level: Double): String =
    if (level % 1.0 == 0.0) level.toInt().toString() else "%.1f".format(Locale.US, level)
