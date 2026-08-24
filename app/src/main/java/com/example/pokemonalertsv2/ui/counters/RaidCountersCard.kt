package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.DecoratedCounter
import com.example.pokemonalertsv2.data.counters.PokebattlerDodge
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import java.util.Locale

private const val COLLAPSED_COUNT = 6

/**
 * Best counters for a raid boss, from Pokebattler.
 *
 * Lives in its own file rather than in `SharedComponents.kt`, which is already past 3000
 * lines, but deliberately mirrors the card styling of its neighbours there.
 */
@Composable
fun RaidCountersCard(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return

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

            if (state.pokeGenieCount > 0) {
                SourceSelector(state, actions)
                Spacer(modifier = Modifier.height(8.dp))
            }

            OptionRow(state, actions)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.isLoading && !state.hasCounters -> LoadingRow()
                state.unresolvedBossName != null -> UnresolvedState(state, actions)
                state.errorMessage != null && !state.hasCounters -> ErrorState(state, actions)
                !state.hasCounters -> EmptyState(state)
                else -> CountersList(state, actions)
            }
        }
    }
}

@Composable
private fun Header(state: RaidCountersUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "🛡️", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Best counters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        if (state.isLoading && state.hasCounters) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
        }
    }
    if (state.isStale) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Offline — showing cached counters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SourceSelector(state: RaidCountersUiState, actions: RaidCountersActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.source == CounterSourceId.ALL_POKEMON,
            onClick = { actions.onSourceChanged(CounterSourceId.ALL_POKEMON) },
            label = { Text(CounterSourceId.ALL_POKEMON.label) }
        )
        FilterChip(
            selected = state.source == CounterSourceId.POKE_GENIE,
            onClick = { actions.onSourceChanged(CounterSourceId.POKE_GENIE) },
            label = { Text(CounterSourceId.POKE_GENIE.label) }
        )
        if (state.source == CounterSourceId.POKE_GENIE) {
            FilterChip(
                selected = state.ownedOnly,
                onClick = { actions.onOwnedOnlyChanged(!state.ownedOnly) },
                label = { Text("Only mine") }
            )
        }
    }
}

@Composable
private fun OptionRow(state: RaidCountersUiState, actions: RaidCountersActions) {
    val options = state.options
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OptionDropdown(
            label = "Level ${options.attackerLevel}",
            entries = RaidCounterOptions.ATTACKER_LEVELS.map { it to "Level $it" },
            onSelect = { actions.onOptionsChanged(options.copy(attackerLevel = it)) }
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
            label = options.dodge.label,
            entries = PokebattlerDodge.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(dodge = it)) }
        )
        OptionDropdown(
            label = options.sort.label,
            entries = PokebattlerSort.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(sort = it)) }
        )
    }
}

@Composable
private fun <T> OptionDropdown(
    label: String,
    entries: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(label) }
        )
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

@Composable
private fun CountersList(state: RaidCountersUiState, actions: RaidCountersActions) {
    val all = state.visibleCounters
    if (all.isEmpty()) {
        Text(
            text = "You don't own any of the top counters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { actions.onOwnedOnlyChanged(false) }) { Text("Show all") }
        return
    }

    val shown = if (state.expanded) all else all.take(COLLAPSED_COUNT)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { CounterRow(it, state.options.sort) }
    }

    if (state.source == CounterSourceId.POKE_GENIE) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (state.ownedMatchCount == 0) {
                "None of these matched your ${state.pokeGenieCount} imported Pokémon."
            } else {
                // Be explicit that ownership annotates, it does not re-rank.
                "Ranked at level ${state.options.attackerLevel}. " +
                    "Highlighted are ${state.ownedMatchCount} you already own."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (all.size > COLLAPSED_COUNT) {
        TextButton(onClick = actions.onToggleExpanded) {
            Text(if (state.expanded) "Show fewer" else "Show all ${all.size}")
        }
    }
}

@Composable
private fun CounterRow(entry: DecoratedCounter, sort: PokebattlerSort) {
    val counter = entry.counter
    val accent = if (entry.isOwned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (entry.isOwned) 40.dp else 28.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = counter.rank.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = counter.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = headlineMetric(entry, sort),
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
                        mine.level?.let { append("L").append(formatLevel(it)).append(" · ") }
                        if (mine.atkIv != null && mine.defIv != null && mine.staIv != null) {
                            append("${mine.atkIv}/${mine.defIv}/${mine.staIv}")
                        } else {
                            append("unappraised")
                        }
                        mine.cp?.let { append(" · CP ").append(it) }
                        if (entry.movesetDiffers) append(" · different moveset")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Loading counters…",
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
    val message = if (state.source == CounterSourceId.POKE_GENIE && state.pokeGenieCount == 0) {
        "Import your Poké Genie CSV in Settings to see which counters you already own."
    } else {
        "No counters available for this raid."
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun headlineMetric(entry: DecoratedCounter, sort: PokebattlerSort): String {
    val counter = entry.counter
    return when (sort) {
        PokebattlerSort.ESTIMATOR, PokebattlerSort.TIME ->
            counter.estimator?.let { String.format(Locale.US, "%.1f trainers", it) }.orEmpty()
        PokebattlerSort.TDO ->
            counter.tdo?.let { String.format(Locale.US, "%.0f dmg", it) }.orEmpty()
        PokebattlerSort.POWER ->
            counter.power?.let { String.format(Locale.US, "%.2f pwr", it) }.orEmpty()
        PokebattlerSort.OVERALL ->
            counter.overallRating?.let { String.format(Locale.US, "%.0f%%", it * 100) }.orEmpty()
    }
}

/** Levels are half-steps, so 40.0 reads as "40" and 31.5 as "31.5". */
private fun formatLevel(level: Double): String =
    if (level % 1.0 == 0.0) level.toInt().toString() else String.format(Locale.US, "%.1f", level)
