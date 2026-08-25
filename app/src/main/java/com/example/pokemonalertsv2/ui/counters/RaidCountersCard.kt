package com.example.pokemonalertsv2.ui.counters

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.DecoratedCounter
import com.example.pokemonalertsv2.data.counters.PersonalCounter
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PokebattlerDodge
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounter
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import java.util.Locale

private const val COLLAPSED_COUNT = 6

/**
 * Best counters for a raid boss.
 *
 * Two modes. "All Pokemon" is Pokebattler's ranking of generic level-N attackers, with the
 * ones the user owns highlighted. "My Pokemon" is ranked locally from the Poke Genie
 * import, using each Pokemon actual level, IVs and moveset, and suggests a team of six.
 *
 * Kept out of `SharedComponents.kt`, which is already past 3000 lines, but deliberately
 * mirrors the card styling of its neighbours there.
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

            if (state.pokeGenieCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                SourceSelector(state, actions)
            }

            Spacer(modifier = Modifier.height(10.dp))
            OptionRow(state, actions)
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
}

@Composable
private fun Header(state: RaidCountersUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CounterSprite(
            urls = state.spriteUrls[state.bossPokemonId].orEmpty(),
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
    if (state.isStale) {
        Spacer(modifier = Modifier.height(6.dp))
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
        CounterChip(
            label = "All Pokémon",
            selected = state.source == CounterSourceId.ALL_POKEMON,
            onClick = { actions.onSourceChanged(CounterSourceId.ALL_POKEMON) }
        )
        CounterChip(
            label = "My Pokémon",
            selected = state.source == CounterSourceId.POKE_GENIE,
            onClick = { actions.onSourceChanged(CounterSourceId.POKE_GENIE) }
        )
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
            label = "L${options.attackerLevel}",
            entries = RaidCounterOptions.ATTACKER_LEVELS.map { it to "Level $it" },
            onSelect = { actions.onOptionsChanged(options.copy(attackerLevel = it)) },
            // The level only drives the Pokebattler list; personal results use real levels.
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
            label = options.dodge.label,
            entries = PokebattlerDodge.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(dodge = it)) }
        )
        OptionDropdown(
            label = options.sort.label,
            entries = PokebattlerSort.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(sort = it)) },
            enabled = !state.showingPersonal
        )
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
        state.personalError != null -> Column {
            Text(
                text = state.personalError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = actions.onRetry) { Text("Retry") }
        }

        personal == null || state.personalLoading -> LoadingRow("Ranking your Pokémon…")

        personal.ranked.isEmpty() -> Text(
            text = "None of your ${state.pokeGenieCount} imported Pokémon could be matched.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TeamSection(personal, state.spriteUrls)
            PersonalList(state, personal, actions)
        }
    }
}

@Composable
private fun TeamSection(personal: PersonalRanking, spriteUrls: Map<String, List<String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Suggested team")

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val percent = (personal.bossFraction * 100).toInt()
                Text(
                    text = if (personal.canSolo) {
                        "These six can take it down on their own."
                    } else {
                        "These six deal about $percent% of its HP."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
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

        personal.team.forEach { slot ->
            TeamRow(slot.counter, slot.count, spriteUrls)
        }
    }
}

@Composable
private fun TeamRow(
    counter: PersonalCounter,
    count: Int,
    spriteUrls: Map<String, List<String>>
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
            text = "%.0f DPS".format(Locale.US, counter.dps),
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
            PersonalRow(index + 1, counter, state.spriteUrls)
        }
        if (personal.ranked.size > COLLAPSED_COUNT) {
            TextButton(onClick = actions.onToggleExpanded) {
                Text(if (state.expanded) "Show fewer" else "Show more")
            }
        }
        if (personal.ranked.any { it.movesetAssumed }) {
            Text(
                text = "Where Poké Genie has no moveset recorded, the best legal one is assumed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PersonalRow(
    rank: Int,
    counter: PersonalCounter,
    spriteUrls: Map<String, List<String>>
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                // Ranking balances damage against staying alive, so a slightly lower DPS
                // can still rank higher. Showing total damage makes that legible.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%.0f DPS".format(Locale.US, counter.dps),
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
                text = counter.moveLine() + if (counter.movesetAssumed) " (assumed)" else "",
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
        }
    }
}

// ── Pokebattler mode ─────────────────────────────────────────────────────────

@Composable
private fun GeneralList(state: RaidCountersUiState, actions: RaidCountersActions) {
    val all = state.counters
    val shown = if (state.expanded) all else all.take(COLLAPSED_COUNT)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { GeneralRow(it, state.spriteUrls) }
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
    val counter = entry.counter
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    text = headlineMetric(counter),
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
    if (owned.atkIv != null && owned.defIv != null && owned.staIv != null) {
        if (isNotEmpty()) append(" · ")
        append("${owned.atkIv}/${owned.defIv}/${owned.staIv}")
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
    counter.estimator?.let { "%.1f trainers".format(Locale.US, it) }.orEmpty()

/** Levels are half steps, so 40.0 reads as "40" and 31.5 as "31.5". */
private fun formatLevel(level: Double): String =
    if (level % 1.0 == 0.0) level.toInt().toString() else "%.1f".format(Locale.US, level)
