package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import com.example.pokemonalertsv2.ui.components.SpringSegmentedRow
import androidx.compose.ui.graphics.Color
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
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.draw.clip
import com.example.pokemonalertsv2.ui.components.rememberShimmerBrush
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedContent
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import androidx.compose.foundation.rememberScrollState
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.ui.theme.MetricTextStyle

@Composable
internal fun InlineRefreshStatus(state: RaidCountersUiState, actions: RaidCountersActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val statusLabel = when {
            state.rateLimited -> "Pokébattler is busy"
            state.isLoading -> "Refreshing counters…"
            else -> "Showing cached counters"
        }
        // The status flips between three strings in the same spot. Crossing them over
        // reads as one line changing its mind; a hard swap reads as a glitch.
        AnimatedContent(
            targetState = statusLabel,
            transitionSpec = { appFadeThrough() },
            label = "counters-refresh-status",
            modifier = Modifier.weight(1f)
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.rateLimited || state.isStale) {
            TextButton(onClick = actions.onRetry) { Text("Retry") }
        }
    }
}

/**
 * Boss CP, tier HP and shiny availability.
 *
 * All three were already in state and none was ever rendered. Tier HP is a local
 * game-master read, so this line works offline.
 */
@Composable
internal fun BossFacts(state: RaidCountersUiState) {
    val facts = buildList {
        state.bossCp?.takeIf { it > 0 }?.let { add("CP $it") }
        state.bossHp?.takeIf { it > 0 }?.let { add("$it HP") }
        if (state.bossShiny) add("Shiny possible")
    }
    if (facts.isEmpty()) return
    Text(
        text = facts.joinToString(" · "),
        style = MetricTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun SourceSelector(state: RaidCountersUiState, actions: RaidCountersActions) {
    SpringSegmentedRow(
        selectedIndex = if (state.showingPersonal) 1 else 0,
        segmentCount = 2
    ) {
        SegmentedChoice(
            label = "Pokébattler",
            selected = state.source == CounterSourceId.ALL_POKEMON,
            modifier = Modifier.weight(1f),
            transparent = true,
            onClick = { actions.onSourceChanged(CounterSourceId.ALL_POKEMON) }
        )
        SegmentedChoice(
            label = "My Pokémon",
            selected = state.showingPersonal,
            enabled = state.pokeGenieCount > 0 || !state.pokebattlerUserId.isNullOrBlank(),
            modifier = Modifier.weight(1f),
            transparent = true,
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
        SpringSegmentedRow(
            selectedIndex = if (state.source == CounterSourceId.POKEBATTLER_POKEBOX) 1 else 0,
            segmentCount = 2,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            SegmentedChoice(
                label = "PokéGenie CSV",
                selected = state.source == CounterSourceId.POKE_GENIE,
                modifier = Modifier.weight(1f),
                transparent = true,
                onClick = { actions.onSourceChanged(CounterSourceId.POKE_GENIE) }
            )
            SegmentedChoice(
                // The account label is usually an email address, which wraps the chip onto
                // a second line and doubles the height of the selector. The local part
                // identifies the account well enough.
                label = state.pokebattlerAccountName
                    ?.substringBefore('@')
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Pokébox · $it" }
                    ?: "My Pokébox",
                selected = state.source == CounterSourceId.POKEBATTLER_POKEBOX,
                modifier = Modifier.weight(1f),
                transparent = true,
                onClick = { actions.onSourceChanged(CounterSourceId.POKEBATTLER_POKEBOX) }
            )
        }
    }
}

@Composable
internal fun SegmentedChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Set when the choice sits inside a SpringSegmentedRow, which paints the track and
    // the travelling selection pill itself.
    transparent: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        color = when {
            transparent -> Color.Transparent
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        },
        contentColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

// ── Quick controls ───────────────────────────────────────────────────────────

/**
 * The three settings that actually get changed, inline.
 *
 * Weather, level and sort were three taps deep (summary -> sheet -> dropdown) while the
 * switches nobody touches sat at the same depth. The sheet stays for the rest. Each chip
 * shows its current value and reads as selected when it is off the default, so an active
 * filter is visible without opening anything.
 */
@Composable
internal fun QuickControls(state: RaidCountersUiState, actions: RaidCountersActions) {
    val options = state.options
    val defaults = remember { RaidCounterOptions() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OptionDropdown(
            label = when {
                state.weatherFromAlert && state.weatherUnconfirmed ->
                    "${options.weather.label} · unconfirmed"
                state.weatherFromAlert -> "${options.weather.label} · from raid"
                else -> options.weather.label
            },
            entries = PokebattlerWeather.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(weather = it)) },
            selected = options.weather != defaults.weather
        )
        OptionDropdown(
            label = "L${options.attackerLevel}",
            entries = RaidCounterOptions.ATTACKER_LEVELS.map { it to "Level $it" },
            onSelect = { actions.onOptionsChanged(options.copy(attackerLevel = it)) },
            // Personal mode reads the level off the user's own roster.
            enabled = !state.showingPersonal,
            selected = !state.showingPersonal && options.attackerLevel != defaults.attackerLevel
        )
        OptionDropdown(
            label = options.sort.label,
            entries = PokebattlerSort.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(sort = it)) },
            selected = options.sort != defaults.sort
        )
        if (state.pokeGenieCount > 0 && !state.showingPersonal) {
            CounterChip(
                label = "Owned only",
                selected = state.ownedOnly,
                onClick = { actions.onOwnedOnlyChanged(!state.ownedOnly) }
            )
        }
    }
}

// ── Footer ───────────────────────────────────────────────────────────────────

/**
 * Where the numbers came from and when.
 *
 * `fetchedAtMillis` and `pokebattlerWebUrl` were both computed on every load — including on
 * failure — and never shown. The link out matters most exactly when the card cannot help:
 * a boss it could not resolve, or a setup Pokebattler has no ranking for.
 */
@Composable
internal fun CountersFooter(state: RaidCountersUiState) {
    val context = LocalContext.current
    val fetched = state.fetchedAtMillis.takeIf { it > 0L }
    if (fetched == null && state.pokebattlerWebUrl == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildString {
                append("Pokébattler")
                fetched?.let { append(" · ").append(TimeUtils.formatTimeAgo(it)) }
                if (state.isStale) append(" · cached")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        state.pokebattlerWebUrl?.let { url ->
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            ) { Text("Open on Pokébattler") }
        }
    }
}

/**
 * Exports the visible ranking as an image.
 *
 * Hidden until there is something to export, so the icon never sits there doing nothing.
 * The work is a bitmap render plus sprite loads, hence the coroutine and the disabled state
 * while it runs — a double tap would otherwise queue two chooser intents.
 */
@Composable
internal fun ShareCountersButton(state: RaidCountersUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val content = remember(state.counters, state.personal, state.options, state.ownedOnly) {
        CounterTeamShareCard.buildContent(state)
    } ?: return

    IconButton(
        onClick = {
            if (sharing) return@IconButton
            sharing = true
            scope.launch {
                CounterTeamShareCard.share(context, content)
                sharing = false
            }
        },
        enabled = !sharing
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share these counters",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Detail-screen teaser ─────────────────────────────────────────────────────

/**
 * The one-line entry point on the alert detail screen.
 *
 * Everything the counters feature shows now lives on [RaidCountersScreen]; the detail page
 * keeps only enough to say the feature is there and worth a tap — the boss's top few
 * counters. A failure is reported here rather than swallowed, because a row that silently
 * shows nothing is indistinguishable from a boss with no counters.
 */
@Composable
fun RaidCountersTeaser(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return

    val names = remember(state.counters, state.personal, state.showingPersonal) {
        if (state.showingPersonal) {
            state.personal?.ranked.orEmpty().map { it.displayName }
        } else {
            state.counters.map { it.counter.displayName }
        }
    }
    val failed = state.errorMessage != null && names.isEmpty()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !failed,
                role = Role.Button,
                onClickLabel = "Open best counters",
                onClick = onOpen
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CounterSprite(
                urls = listOfNotNull(state.bossThumbnailUrl) +
                    state.spriteUrls[state.bossPokemonId].orEmpty(),
                size = 36.dp,
                type = state.pokemonTypes[state.bossPokemonId]?.firstOrNull()
            ) {
                Text(text = "🛡️", style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Best counters",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                when {
                    failed -> Text(
                        text = state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    names.isEmpty() && state.isLoading -> TeaserSkeletonLine()

                    names.isEmpty() -> Text(
                        text = "No counters available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> Text(
                        text = buildString {
                            append(names.take(TEASER_NAMES).joinToString(" · "))
                            val rest = names.size - TEASER_NAMES
                            if (rest > 0) append(" +").append(rest)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (failed) {
                TextButton(onClick = actions.onRetry) { Text("Retry") }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Two names read as a pair; three starts to look like the list it is replacing. */
private const val TEASER_NAMES = 2

@Composable
private fun TeaserSkeletonLine() {
    val brush = rememberShimmerBrush()
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth(0.55f)
            .height(11.dp)
            .clip(MaterialTheme.shapes.small)
            .background(brush)
    )
}
