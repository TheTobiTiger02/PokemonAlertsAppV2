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
import com.example.pokemonalertsv2.data.counters.CounterMetrics
import com.example.pokemonalertsv2.data.counters.CounterMoveset
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
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import com.example.pokemonalertsv2.ui.theme.MetricTextStyle
import com.example.pokemonalertsv2.ui.theme.typeColor
import com.example.pokemonalertsv2.ui.theme.typeLabel
import com.example.pokemonalertsv2.ui.components.rememberShimmerBrush
import com.example.pokemonalertsv2.ui.motion.AppMotion
import com.example.pokemonalertsv2.data.counters.toCounterMetric

internal const val COLLAPSED_COUNT = 6

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
internal fun CounterSprite(
    urls: List<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    /** Primary type, used only to tint the disc behind the artwork. */
    type: String? = null,
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
        color = typeColor(type)?.copy(alpha = 0.22f)
            ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

/**
 * Podium tints for the first three rows.
 *
 * Fixed hues rather than theme colours: gold/silver/bronze only read as a podium if they stay
 * metallic in both themes, and they are deliberately the only non-palette colours in the card.
 */
private val MedalGold = Color(0xFFD9A62E)
private val MedalSilver = Color(0xFF9AA5B1)
private val MedalBronze = Color(0xFFB07348)

@Composable
internal fun RankBadge(rank: Int, highlighted: Boolean = false) {
    val medal = when (rank) {
        1 -> MedalGold
        2 -> MedalSilver
        3 -> MedalBronze
        else -> null
    }
    val container = when {
        medal != null -> medal
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val content = when {
        medal != null -> Color.White
        highlighted -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
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
internal fun UnresolvedState(state: RaidCountersUiState, actions: RaidCountersActions) {
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
internal fun ErrorState(state: RaidCountersUiState, actions: RaidCountersActions) {
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
internal fun EmptyState(state: RaidCountersUiState) {
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

/** Levels are half steps, so 40.0 reads as "40" and 31.5 as "31.5". */
internal fun formatLevel(level: Double): String =
    if (level % 1.0 == 0.0) level.toInt().toString() else "%.1f".format(Locale.US, level)

// ── Metric bar ───────────────────────────────────────────────────────────────

/** Below this the bar reads as absent rather than as a very weak counter. */
private const val MIN_BAR_FRACTION = 0.05f

/**
 * How strong one row is relative to the best row, as a hairline bar.
 *
 * [fraction] comes from `CounterMetric.strengthRatio`, so 1f is always the winner whichever
 * direction the metric runs. The width animates so that changing the sort visibly re-scales
 * the list instead of snapping.
 */
@Composable
internal fun MetricBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(AppMotion.Standard, easing = LinearOutSlowInEasing),
        label = "metric_bar"
    )
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    // Weak rows fade toward the neutral colour so the eye lands on the top of the list.
    val fill = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        MaterialTheme.colorScheme.primary,
        animated
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceAtLeast(MIN_BAR_FRACTION))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fill)
        )
    }
}

/** Rotating affordance so a row reads as expandable; nothing signalled that before. */
@Composable
internal fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        // Direct manipulation: the user taps the row, so physics fits better than a
        // fixed duration.
        animationSpec = AppMotion.springQuick(),
        label = "chevron"
    )
    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = rotation }
    )
}

// ── Loading skeleton ─────────────────────────────────────────────────────────

/**
 * Placeholder rows shaped like the real ones.
 *
 * Replaces a bare indeterminate bar: the list arriving in place of its own outline reads as
 * much faster than the same wait behind a spinner. [label] keeps the personal-mode progress
 * ("Ranking your Pokemon... (1/2)") visible underneath.
 */
@Composable
internal fun CounterSkeletonList(label: String, rows: Int = COLLAPSED_COUNT) {
    val brush = rememberShimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(rows) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            // Ragged widths so the block does not read as a table.
                            .fillMaxWidth(0.34f + (index % 3) * 0.12f)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f - (index % 2) * 0.1f)
                            .height(9.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(brush)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(11.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Expanded detail ──────────────────────────────────────────────────────────

/**
 * Every metric for one counter, as a two-column label/value grid.
 *
 * Replaces a single joined sentence of six figures, which was unreadable at `labelSmall`.
 * Values use [MetricTextStyle] so the columns line up between rows.
 */
@Composable
internal fun MetricGrid(
    metrics: CounterMetrics,
    leading: List<Pair<String, String>> = emptyList()
) {
    val cells = buildList {
        addAll(leading)
        CounterMetric.entries.forEach { metric ->
            metrics.headline(metric).takeIf { it.isNotBlank() }?.let { add(metric.label to it) }
        }
        metrics.deaths?.let { add("Faints" to "%.1f".format(Locale.US, it)) }
    }
    if (cells.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        cells.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = value,
                            style = MetricTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Keep an odd final cell in the left column instead of stretching it.
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A small tinted note attached to a row, such as "TM to Shadow Ball". */
@Composable
internal fun RowNote(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * The card each counter row sits in.
 *
 * [owned] paints a left accent stripe with `drawBehind` rather than a sibling `Box`, which
 * would need `IntrinsicSize.Min` on the row just to get a height to fill.
 */
@Composable
internal fun CounterRowShell(
    owned: Boolean,
    onClick: () -> Unit,
    expanded: Boolean,
    content: @Composable () -> Unit
) {
    val stripe = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(if (expanded) 4.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
    ) {
        // Inside the Surface, so the card's own shape clips it. Painting this on the
        // Surface's modifier instead put it outside that clip and left square corners
        // hanging past the rounded edge.
        Box(
            modifier = Modifier
                .drawBehind {
                    if (!owned) return@drawBehind
                    drawRoundRect(
                        color = stripe,
                        size = Size(STRIPE_WIDTH_PX, size.height),
                        cornerRadius = CornerRadius(STRIPE_WIDTH_PX / 2f)
                    )
                }
                .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
        ) {
            content()
        }
    }
}

private const val STRIPE_WIDTH_PX = 8f

/**
 * Which of the mutually exclusive bodies the card is showing.
 *
 * Extracted from the `when` so that [AnimatedContent] has a target that changes exactly when
 * the body should cross-fade, and not on every metric or option tweak within one phase.
 */
internal enum class CountersPhase { UNRESOLVED, LOADING, ERROR, PERSONAL, GENERAL, EMPTY }

internal val RaidCountersUiState.phase: CountersPhase
    get() = when {
        unresolvedBossName != null -> CountersPhase.UNRESOLVED
        isLoading && !hasCounters -> CountersPhase.LOADING
        errorMessage != null && !hasCounters -> CountersPhase.ERROR
        showingPersonal -> CountersPhase.PERSONAL
        hasCounters -> CountersPhase.GENERAL
        else -> CountersPhase.EMPTY
    }

// ── Type chips ───────────────────────────────────────────────────────────────

/**
 * A move or a type as a tinted pill.
 *
 * The fill is the type hue at low alpha and the text is the hue at full strength, which
 * keeps the pill legible in both themes without a second palette. An unknown type falls
 * back to the theme's own muted surface, so a missing game-master row degrades to the plain
 * text this replaced.
 */
@Composable
internal fun TypeChip(
    label: String,
    type: String?,
    modifier: Modifier = Modifier
) {
    val hue = typeColor(type)
    Surface(
        shape = CircleShape,
        color = hue?.copy(alpha = 0.16f)
            ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = hue ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/** The fast and charged move as type-tinted chips, replacing "Move A · Move B". */
@Composable
internal fun MoveChips(
    moves: List<String>,
    moveTypes: Map<String, String>,
    modifier: Modifier = Modifier
) {
    if (moves.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        moves.forEach { move ->
            TypeChip(
                label = move,
                type = moveTypes[move.uppercase(Locale.ROOT)],
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

/** The boss's own types, for the card header. */
@Composable
internal fun TypeBadges(types: List<String>, modifier: Modifier = Modifier) {
    if (types.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        types.forEach { type ->
            typeLabel(type)?.let { TypeChip(label = it, type = type) }
        }
    }
}

/**
 * What the other legal movesets would cost, against the one being ranked.
 *
 * No extra request: `byMove` was always in the response and always discarded. [chosen] is
 * the headline estimator, so each row reads as a percentage penalty against it — which is
 * what "is it worth a TM" actually asks.
 */
@Composable
internal fun MovesetComparison(
    alternatives: List<CounterMoveset>,
    chosen: Double?,
    moveTypes: Map<String, String>
) {
    if (alternatives.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionLabel("Other movesets")
        alternatives.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MoveChips(
                    moves = listOf(option.fastMove, option.chargedMove),
                    moveTypes = moveTypes,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = penaltyLabel(option.estimator, chosen),
                    style = MetricTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * "+12%" — how much worse this moveset is than the headline one.
 *
 * Falls back to the raw estimator when there is nothing to compare against, rather than
 * printing a percentage of an unknown.
 */
private fun penaltyLabel(estimator: Double?, chosen: Double?): String {
    if (estimator == null) return ""
    if (chosen == null || chosen <= 0.0) return "%.2f".format(Locale.US, estimator)
    val delta = (estimator - chosen) / chosen * 100.0
    return if (delta < 0.5) "same" else "+%.0f%%".format(Locale.US, delta)
}
