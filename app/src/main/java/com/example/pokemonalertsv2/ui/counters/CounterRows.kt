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
import com.example.pokemonalertsv2.data.counters.PersonalTeamSlot
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounter
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidBossMoveset
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import com.example.pokemonalertsv2.data.counters.toCounterMetric
import com.example.pokemonalertsv2.data.counters.bestOf
import com.example.pokemonalertsv2.data.counters.strengthRatio
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.theme.MetricTextStyle

// -- Personal mode -----------------------------------------------------------

@Composable
internal fun TeamSection(
    personal: PersonalRanking,
    team: List<PersonalTeamSlot>,
    activeMegaId: String?,
    spriteUrls: Map<String, List<String>>,
    metric: CounterMetric,
    onCopyTeam: (CopyTeamFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Suggested team")
            Spacer(modifier = Modifier.weight(1f))
            CopyTeamButton(enabled = team.isNotEmpty(), onCopy = onCopyTeam)
        }

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
                // Say why no mega is here, rather than leaving the trainer to wonder
                // whether the ranking simply rates them badly.
                if (activeMegaId == null) {
                    Text(
                        text = "No mega active, so none is suggested. " +
                            "Set yours in Battle setup.",
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

        TeamSlots(team, spriteUrls)

        team.forEach { slot ->
            TeamRow(slot.counter, slot.count, spriteUrls, metric)
        }
    }
}

@Composable
internal fun TeamRow(
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
internal fun PersonalRow(
    rank: Int,
    counter: PersonalCounter,
    spriteUrls: Map<String, List<String>>,
    types: Map<String, List<String>>,
    moveTypes: Map<String, String>,
    metric: CounterMetric,
    best: Double?
) {
    var expanded by remember(counter.owned.displayName, counter.pokemonId, rank) {
        mutableStateOf(false)
    }
    val fraction = metric.strengthRatio(counter.metrics.valueFor(metric), best)
    CounterRowShell(
        owned = true,
        expanded = expanded,
        onClick = { expanded = !expanded }
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RankBadge(rank)
            CounterSprite(
                urls = spriteUrls[counter.pokemonId].orEmpty(),
                size = spriteSize(rank),
                type = types[counter.pokemonId]?.firstOrNull()
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                RowHeadline(
                    name = counter.displayName,
                    value = counter.metrics.headline(metric),
                    expanded = expanded
                )
                MetricBar(fraction)
                MoveChips(counter.moveList(), moveTypes)
                Text(
                    text = counter.statLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (counter.movesetAssumed) {
                    RowNote(
                        text = "Assumed move(s)",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (counter.movesetUnlisted) {
                    RowNote(
                        text = "Pokébattler doesn't rank your moveset — scored at its floor",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = appExpandIn(),
                    exit = appCollapseOut()
                ) {
                    MetricGrid(counter.metrics)
                }
            }
        }
    }
}

// -- Pokebattler mode --------------------------------------------------------

@Composable
internal fun GeneralRow(
    entry: DecoratedCounter,
    spriteUrls: Map<String, List<String>>,
    types: Map<String, List<String>>,
    moveTypes: Map<String, String>,
    metric: CounterMetric,
    best: Double?
) {
    val counter = entry.counter
    var expanded by remember(entry.counter.pokemonId, entry.counter.rank) { mutableStateOf(false) }
    val fraction = metric.strengthRatio(counter.metrics().valueFor(metric), best)
    CounterRowShell(
        owned = entry.isOwned,
        expanded = expanded,
        onClick = { expanded = !expanded }
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RankBadge(counter.rank, highlighted = entry.isOwned)
            CounterSprite(
                urls = spriteUrls[counter.pokemonId].orEmpty(),
                size = spriteSize(counter.rank),
                type = types[counter.pokemonId]?.firstOrNull()
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                RowHeadline(
                    name = counter.displayName,
                    value = headlineMetric(counter, metric),
                    expanded = expanded
                )
                MetricBar(fraction)
                MoveChips(listOfNotNull(counter.fastMove, counter.chargedMove), moveTypes)
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
                // The app has always known when the owned copy has the wrong moves; say so.
                entry.missingMoves.takeIf { it.isNotEmpty() }?.let { missing ->
                    RowNote(
                        text = "TM to ${missing.joinToString(" + ")}",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = appExpandIn(),
                    exit = appCollapseOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricGrid(
                        metrics = counter.metrics(),
                        leading = buildList {
                            counter.level?.let { add("Level" to it.toString()) }
                            if (counter.atkIv != null && counter.defIv != null && counter.staIv != null) {
                                add("IV" to "${counter.atkIv}/${counter.defIv}/${counter.staIv}")
                            }
                            counter.cp?.let { add("CP" to it.toString()) }
                        }
                    )
                    MovesetComparison(counter.alternatives, counter.estimator, moveTypes)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TeamSlots(team: List<PersonalTeamSlot>, spriteUrls: Map<String, List<String>>) {
    val slots = team.flatMap { slot -> List(slot.count) { slot.counter } }.take(6)
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

internal fun PersonalCounter.moveList(): List<String> =
    listOfNotNull(prettifyMoveName(fastMove.moveId), prettifyMoveName(chargedMove.moveId))

internal fun PersonalCounter.moveLine(): String =
    listOfNotNull(prettifyMoveName(fastMove.moveId), prettifyMoveName(chargedMove.moveId))
        .joinToString(" · ")

internal fun PersonalCounter.statLine(): String = buildString {
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
 * The headline number for one counter under the currently selected sort.
 *
 * The estimator reads as how many trainers it takes, so lower is better; see
 * [CounterMetric] for the rest.
 */
internal fun headlineMetric(counter: RaidCounter, metric: CounterMetric): String =
    counter.metrics().headline(metric)

/** The podium gets a larger sprite; it is the only size difference in the list. */
internal fun spriteSize(rank: Int): Dp = if (rank <= 3) 40.dp else 32.dp

/** Name on the left, the sorted-by value and the expand affordance on the right. */
@Composable
internal fun RowHeadline(name: String, value: String, expanded: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MetricTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        ExpandChevron(expanded)
    }
}

/** Which flavour of Pokémon GO query the trainer asked for. */
internal enum class CopyTeamFormat { EXACT, SPECIES }

/**
 * Copies the team as a Pokémon GO search string.
 *
 * The app's first clipboard affordance. A plain tap gives the precise query — species,
 * shadow, moves and CP — because that is what selects exactly these six and nothing else.
 */
@Composable
private fun CopyTeamButton(enabled: Boolean, onCopy: (CopyTeamFormat) -> Unit) {
    TextButton(onClick = { onCopy(CopyTeamFormat.EXACT) }, enabled = enabled) {
        Text("Copy for GO", style = MaterialTheme.typography.labelMedium)
    }
}
