package com.example.pokemonalertsv2.ui.alerts

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository
import com.example.pokemonalertsv2.data.PvpRanking
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.PokemonSpriteUrls
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.speciesArt
import com.example.pokemonalertsv2.data.speciesArtwork
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelRowIcon
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.RollingNumberText
import com.example.pokemonalertsv2.ui.counters.RaidCountersUiState
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

// The building blocks of the redesigned alert detail (options A-C) and the map's alert sheet.
// Every section is a flat list in the panel vocabulary: blue labels, rows led by a round icon
// badge, one container colour, and the alert type only ever as a small dot.

/** The one place an alert's category colour shows: a small dot before its type. */
@Composable
internal fun AlertTypeDot(alert: PokemonAlert, size: Dp = 8.dp) {
    val accent = remember(alert) { Color(resolveAlertVisualStyle(alert).category.accentArgb) }
    Box(
        modifier = Modifier
            .size(size)
            .background(accent, CircleShape)
    )
}

/** "Raid · Town Hall Fountain": the category always, then the place when there is one. */
internal fun alertTypeAndPlace(alert: PokemonAlert): String = listOfNotNull(
    resolveAlertVisualStyle(alert).label,
    (alert.venueName ?: alert.locationDisplay)?.trim()?.takeIf { it.isNotEmpty() }
).joinToString(" · ")

/**
 * The numbers worth scanning, in one line: IV, CP and level, a raid's perfect-catch CP, and on
 * the map sheet the distance and walk. Anything missing drops out.
 */
internal fun alertNumbersLine(alert: PokemonAlert, distanceInfo: AlertDistanceInfo? = null): String = buildList {
    if (alert.isShiny == true) add("Shiny")
    val iv = if (alert.isWeatherChange && alert.newIv != null) alert.newIv else alert.formattedIv
    iv?.let { add("IV $it") }
    alert.displayCp?.let { add("CP $it") }
    if (!alert.isWeatherChange || alert.isSpeciesReplacement) {
        alert.level?.let { level ->
            add("L" + if (level == level.toLong().toDouble()) level.toLong().toString() else level.toString())
        }
    }
    if (alert.hasTypeContaining("raid")) {
        val hundo = listOfNotNull(
            alert.hundoCP?.level20?.takeIf { it > 0 }?.let { "$it" },
            alert.hundoCP?.level25?.takeIf { it > 0 }?.let { "$it boosted" }
        )
        if (hundo.isNotEmpty()) add("100% CP " + hundo.joinToString(" / "))
    }
    if (alert.isWeatherBoosted == true) add("Boosted")
    distanceInfo?.distanceText?.takeIf { it.isNotBlank() }?.let(::add)
    distanceInfo?.walkingText?.takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" · ")

/** "Ends in 12m 05s · until 14:32", rolling per digit; red once it has ended. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlertCountdownLine(
    alert: PokemonAlert,
    countdownClock: State<Long>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium
) {
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    val now = countdownClock.value
    val remaining = endMillis?.minus(now)
    // The end time wraps under the countdown when a narrow phone has no room beside it.
    FlowRow(
        modifier = modifier.testTag("alert_countdown"),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // The timer icon stays with the countdown when the line wraps.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(
                painter = painterResource(R.drawable.ic_timer),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (remaining != null && remaining <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            when {
                endMillis == null -> Text(
                    text = alert.endTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                remaining != null && remaining <= 0 -> Text(
                    text = "Ended ${TimeUtils.formatTimeAgo(endMillis)}",
                    style = style,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                else -> RollingNumberText(
                    text = "Ends in ${TimeUtils.formatDurationShort(remaining ?: 0L)}",
                    style = style.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (endMillis != null && remaining != null && remaining > 0) {
            Text(
                text = TimeUtils.formatAlertEndTime(endMillis, now) +
                    if (alert.expiryVerified == false) " (estimated)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Title, "type · place" and the countdown: what every redesigned layout opens with, and the map
 * sheet too, so the two read the same.
 */
@Composable
internal fun AlertSummaryBlock(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult,
    countdownClock: State<Long>,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    titleMaxLines: Int = 2
) {
    val title = remember(alert, goDexStatus.status) { formatAlertTitle(alert, goDexStatus.status) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            AlertTypeDot(alert)
            Text(
                text = alertTypeAndPlace(alert),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AlertCountdownLine(alert = alert, countdownClock = countdownClock, modifier = Modifier.padding(top = Spacing.xxs))
    }
}

/** A round sprite, as the compact feed row draws it but without the category tint. */
@Composable
internal fun AlertSprite(alert: PokemonAlert, size: Dp, modifier: Modifier = Modifier) {
    val url = alert.thumbnailUrl?.takeIf { it.isNotBlank() } ?: alert.imageUrl?.takeIf { it.isNotBlank() }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                placeholder = painterResource(R.drawable.ic_placeholder),
                error = painterResource(R.drawable.ic_placeholder),
                fallback = painterResource(R.drawable.ic_placeholder),
                modifier = Modifier.size(size * 0.8f)
            )
        }
    }
}

/** I'm going and Navigate: the two things done next, the same on every surface. */
@Composable
internal fun AlertPrimaryButtons(
    isGoing: Boolean,
    goingEnabled: Boolean,
    onGoing: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        FilledTonalButton(
            onClick = onGoing,
            enabled = goingEnabled || isGoing,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = Spacing.md),
            colors = if (isGoing) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else ButtonDefaults.filledTonalButtonColors()
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(if (isGoing) "Stop" else "I’m going", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onNavigate,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = Spacing.md)
        ) {
            Icon(painterResource(R.drawable.ic_map), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Navigate", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The ⋮ menu that holds everything besides the two main buttons: GoDex, snooze, picture-in-picture,
 * share, and on the map dismiss or restore. A null action is left out.
 */
@Composable
internal fun AlertOverflowMenu(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult,
    onSnooze: (() -> Unit)?,
    onPictureInPicture: (() -> Unit)?,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissAlert: (() -> Unit)? = null,
    onRestoreAlert: (() -> Unit)? = null,
    overlay: Boolean = false,
    contentDescription: String = "More alert actions"
) {
    // Composed outside the menu, so its dialogs outlive the menu closing on tap.
    val goDex = GoDexCaughtControl(alert, goDexStatus)
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (overlay) {
            FilledIconButton(
                onClick = { expanded = true },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) { Icon(Icons.Filled.MoreVert, contentDescription = contentDescription) }
        } else {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = contentDescription)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fun pick(action: () -> Unit) {
                expanded = false
                action()
            }
            if (goDex != null) {
                DropdownMenuItem(
                    text = { Text(goDex.label) },
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    enabled = !goDex.isPending,
                    onClick = { pick(goDex.onClick) }
                )
                HorizontalDivider()
            }
            onSnooze?.let { action ->
                DropdownMenuItem(
                    text = { Text(AlertSecondaryAction.SNOOZE.menuLabel) },
                    leadingIcon = { AlertSecondaryActionIcon(AlertSecondaryAction.SNOOZE) },
                    onClick = { pick(action) }
                )
            }
            onPictureInPicture?.let { action ->
                DropdownMenuItem(
                    text = { Text(AlertSecondaryAction.PICTURE_IN_PICTURE.menuLabel) },
                    leadingIcon = { AlertSecondaryActionIcon(AlertSecondaryAction.PICTURE_IN_PICTURE) },
                    onClick = { pick(action) }
                )
            }
            DropdownMenuItem(
                text = { Text(AlertSecondaryAction.SHARE.menuLabel) },
                leadingIcon = { AlertSecondaryActionIcon(AlertSecondaryAction.SHARE) },
                onClick = { pick(onShare) }
            )
            onDismissAlert?.let { action ->
                DropdownMenuItem(
                    text = { Text(AlertSecondaryAction.DISMISS.menuLabel) },
                    leadingIcon = { AlertSecondaryActionIcon(AlertSecondaryAction.DISMISS) },
                    onClick = { pick(action) }
                )
            }
            onRestoreAlert?.let { action ->
                DropdownMenuItem(
                    text = { Text(AlertSecondaryAction.RESTORE.menuLabel) },
                    leadingIcon = { AlertSecondaryActionIcon(AlertSecondaryAction.RESTORE) },
                    onClick = { pick(action) }
                )
            }
        }
    }
}

/** The snooze length the user chose in Settings; 10 minutes until it has loaded. */
@Composable
internal fun rememberDefaultSnoozeMinutes(): Int {
    val context = LocalContext.current
    val minutes by produceState(initialValue = 10, context) {
        value = withContext(Dispatchers.IO) {
            AlertPreferences(context.alertPreferencesDataStore).snoozeDuration.first()
        }
    }
    return minutes
}

/** Straight-line distance and walk from the last known fix, or null without one. */
@Composable
internal fun rememberAlertDistance(alert: PokemonAlert): AlertDistanceInfo? {
    val context = LocalContext.current
    val info by produceState<AlertDistanceInfo?>(initialValue = null, alert.uniqueId) {
        value = withContext(Dispatchers.IO) {
            resolveMapAlertDistanceInfo(getLastKnownLocation(context), alert)
        }
    }
    return info
}

/** Species sprite per normalized name, for rewards that name a Pokémon but carry no image. */
@Composable
internal fun rememberSpeciesArtworkMap(): Map<String, String> {
    val context = LocalContext.current
    val flow = remember(context) {
        PokemonSpeciesRepository.getInstance(context).searchSpecies("")
            .map(::speciesArtwork)
            .flowOn(Dispatchers.Default)
    }
    val artwork by flow.collectAsStateWithLifecycle(initialValue = emptyMap())
    return artwork
}

/** A non-clickable row in the panel vocabulary: badge, label, value. */
@Composable
internal fun DetailInfoRow(
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = PanelGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelRowIcon(content = icon)
        Spacer(Modifier.width(Spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!value.isNullOrBlank()) {
                Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
            }
        }
        trailing?.invoke()
    }
}

// ---- Type sections ------------------------------------------------------------------------

/**
 * The one section an alert's type earns: PvP ranks as a table, Rocket rewards as sprite tiles,
 * a raid's counters and moves, a quest's reward and task, or a weather change. Draws nothing
 * for a plain spawn.
 */
@Composable
internal fun AlertTypeSection(
    alert: PokemonAlert,
    raidCountersState: RaidCountersUiState,
    onOpenCounters: () -> Unit
) {
    if (alert.isWeatherChange && (weatherTransitionLabel(alert) != null || alert.affectedAlerts.isNotEmpty())) {
        PanelSectionLabel("Weather change")
        DetailInfoRow(
            title = weatherTransitionLabel(alert) ?: "Weather changed",
            value = alert.affectedAlerts.takeIf { it.isNotEmpty() }?.let {
                if (it.size == 1) "1 active alert was replaced" else "${it.size} active alerts were replaced"
            }
        ) { PanelIcon(R.drawable.ic_cloud) }
        alert.affectedAlerts.forEach { affected ->
            DetailInfoRow(
                title = affected.name?.takeIf { it.isNotBlank() } ?: affectedAlertSummary(affected),
                value = listOfNotNull(
                    affected.cp?.let { "CP $it" },
                    affected.area?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifEmpty { null }
            ) { PanelIcon(R.drawable.ic_refresh) }
        }
    }
    alert.pvpRankings?.takeIf { it.isNotEmpty() }?.let { rankings ->
        PanelSectionLabel("PvP ranks")
        PvpLeagueTable(rankings = rankings)
    }
    if (alert.gruntType?.isNotBlank() == true || alert.pokemonRewards?.isNotEmpty() == true) {
        PanelSectionLabel("Team GO Rocket")
        alert.gruntType?.takeIf { it.isNotBlank() }?.let { grunt ->
            DetailInfoRow(title = grunt, value = "Trainer") { PanelIcon(R.drawable.ic_species) }
        }
        alert.pokemonRewards?.takeIf { it.isNotEmpty() }?.let { RocketRewardTiles(rewards = it) }
    }
    val isRaid = alert.hasTypeContaining("raid")
    if (isRaid && (raidCountersState.visible || alert.moves != null)) {
        PanelSectionLabel("Raid")
        RaidCountersRow(state = raidCountersState, onOpen = onOpenCounters)
        alert.moves?.let { moves -> MoveRows(fast = moves.fast, charged = moves.charged) }
    }
    questAlertPresentation(alert)?.takeIf { it.task != null || it.reward != null }?.let { quest ->
        PanelSectionLabel("Quest")
        QuestRewardTile(alert = alert, reward = quest.reward, task = quest.task, requiresAr = quest.requiresAr)
    }
}

/** League · Pokémon · #rank · CP · %, one line per league, columns lined up. */
@Composable
internal fun PvpLeagueTable(rankings: List<PvpRanking>, modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelMedium
    val valueStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Columns as wide as their widest possible value at the current font scale, so the table
    // lines up on a narrow phone at 1.3 too and only the Pokémon name gives way.
    fun width(sample: String, style: TextStyle): Dp =
        with(density) { measurer.measure(sample, style.copy(fontWeight = FontWeight.Bold)).size.width.toDp() } + 2.dp
    val rows = remember(rankings) {
        rankings.map { ranking ->
            listOf(
                shortLeagueName(ranking.league),
                ranking.pokemon ?: "—",
                ranking.rank?.let { "#$it" } ?: "—",
                ranking.cp?.toString() ?: "—",
                ranking.percentage?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: "—"
            )
        }
    }
    // Each column as wide as its widest value here, so only the Pokémon name gives way.
    val widths = remember(rows, valueStyle, labelStyle, density) {
        listOf("League", "Pokémon", "Rank", "CP", "Stat").mapIndexed { index, header ->
            maxOf(width(header, labelStyle), rows.maxOf { width(it[index], valueStyle) })
        }
    }
    val leagueWidth = widths[0]
    val rankWidth = widths[2]
    val cpWidth = widths[3]
    val pctWidth = widths[4]
    // The feed often sends ranks without a stat product; a column of dashes says nothing.
    val showPct = rankings.any { it.percentage != null }

    @Composable
    fun Line(league: String, pokemon: String, rank: String, cp: String, pct: String, header: Boolean, best: Boolean = false) {
        val style = if (header) labelStyle else valueStyle
        val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (header) 24.dp else 40.dp)
                .padding(horizontal = PanelGutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(league, style = style, color = color, fontWeight = if (header) null else FontWeight.SemiBold,
                maxLines = 1, softWrap = false, modifier = Modifier.width(leagueWidth))
            Text(pokemon, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text(rank, style = style, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                fontWeight = if (best) FontWeight.Bold else null,
                color = if (best) MaterialTheme.colorScheme.primary else color,
                modifier = Modifier.width(rankWidth))
            Text(cp, style = style, color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                modifier = Modifier.width(cpWidth))
            if (showPct) {
                Text(pct, style = style, color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                    modifier = Modifier.width(pctWidth))
            }
        }
    }

    Column(modifier = modifier.testTag("pvp_league_table")) {
        Line("League", "Pokémon", "Rank", "CP", "Stat", header = true)
        rankings.forEachIndexed { i, ranking ->
            val (league, pokemon, rank, cp, pct) = rows[i]
            Line(league, pokemon, rank, cp, pct, header = false, best = ranking.rank != null && ranking.rank <= 10)
        }
    }
}

internal fun shortLeagueName(league: String?): String {
    val name = league?.trim().orEmpty()
    return listOf("Little", "Great", "Ultra", "Master").firstOrNull { name.contains(it, ignoreCase = true) }
        ?: name.removeSuffix(" League").ifEmpty { "League" }
}

/** Possible Rocket rewards as sprite tiles, three across, each with its chance. */
@Composable
internal fun RocketRewardTiles(rewards: List<PokemonReward>, modifier: Modifier = Modifier) {
    val artwork = rememberSpeciesArtworkMap()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PanelGutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        rewards.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { reward ->
                    val name = reward.pokemon?.takeIf { it.isNotBlank() } ?: "Unknown"
                    // The feed's own icon set when the reward is a plain species; PokeAPI's
                    // artwork for a regional or other form, which that set files by form id.
                    val sprite = remember(name, artwork) {
                        val art = speciesArt(name, artwork)
                        if (art.form == null) PokemonSpriteUrls.candidates(art.dex, null).lastOrNull() ?: art.url else art.url
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rocket_reward_tile"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xxs),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = sprite,
                                contentDescription = null,
                                placeholder = painterResource(R.drawable.ic_widget_pokeball),
                                error = painterResource(R.drawable.ic_widget_pokeball),
                                fallback = painterResource(R.drawable.ic_widget_pokeball),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = reward.percentage?.let { "$it%" } ?: "—",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The raid's counters as one row that opens the counters screen. */
@Composable
internal fun RaidCountersRow(state: RaidCountersUiState, onOpen: () -> Unit) {
    if (!state.visible) return
    val names = remember(state.counters, state.personal, state.showingPersonal) {
        if (state.showingPersonal) state.personal?.ranked.orEmpty().map { it.displayName }
        else state.counters.map { it.counter.displayName }
    }
    val summary = when {
        names.isNotEmpty() -> names.take(3).joinToString(", ")
        state.isLoading -> "Loading…"
        state.errorMessage != null -> state.errorMessage
        else -> "No counters available"
    }
    PanelNavRow(
        title = "Best counters",
        summary = summary,
        onClick = onOpen,
        modifier = Modifier.testTag("raid_counters_row"),
        icon = { PanelIcon(R.drawable.ic_group) }
    )
}

@Composable
internal fun MoveRows(fast: String?, charged: String?) {
    fast?.let { DetailInfoRow(title = it, value = "Fast move") { PanelIcon(R.drawable.ic_star_border) } }
    charged?.let { DetailInfoRow(title = it, value = "Charged move") { Icon(Icons.Filled.Star, null, Modifier.size(20.dp)) } }
}

/** The quest's reward as a tile with its icon, and the task under it. */
@Composable
internal fun QuestRewardTile(alert: PokemonAlert, reward: String?, task: String?, requiresAr: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PanelGutter, vertical = Spacing.sm)
            .testTag("quest_reward_tile"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = alert.thumbnailUrl?.takeIf { it.isNotBlank() },
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.ic_reward_item),
                    error = painterResource(R.drawable.ic_reward_item),
                    fallback = painterResource(R.drawable.ic_reward_item),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = reward ?: "Unknown reward",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            task?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (requiresAr) {
                Text(text = "AR scan required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---- Stats, location, more details --------------------------------------------------------

/** IV, CP, level and the ATK/DEF/STA bars, flat, for a layout that has room for them. */
@Composable
internal fun AlertStatsRows(alert: PokemonAlert) {
    if (alert.formattedIv == null && alert.cp == null && alert.level == null && alert.hundoCP == null) return
    Box(Modifier.padding(horizontal = PanelGutter, vertical = Spacing.sm)) {
        StatsCardContent(alert = alert, showTitle = false)
    }
}

/** Where it is and how far: the place, the distance, and arrival tracking while it runs. */
@Composable
internal fun AlertLocationRows(alert: PokemonAlert, distanceInfo: AlertDistanceInfo?, isGoing: Boolean) {
    val venue = alert.venueName
    val address = alert.pokemonLocation?.takeIf { it.isNotBlank() }
    val title = venue?.let { v -> alert.venueTypeLabel?.let { "$it: $v" } ?: v } ?: address ?: alert.locationDisplay
    if (title != null) {
        DetailInfoRow(
            title = title,
            value = address?.takeIf { venue != null && it != venue }
        ) { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) }
    }
    val distance = listOfNotNull(
        distanceInfo?.distanceText,
        distanceInfo?.walkingText
    ).joinToString(" · ")
    if (distance.isNotEmpty()) {
        DetailInfoRow(title = distance, value = "From your last known location") { PanelIcon(R.drawable.ic_route) }
    }
    AnimatedVisibility(visible = isGoing, enter = appExpandIn(), exit = appCollapseOut()) {
        DetailInfoRow(
            title = "Arrival tracking active",
            value = "You will be told when you get there",
            valueColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) { PanelIcon(R.drawable.ic_my_location) }
    }
}

/**
 * Facts needed now and then, behind one row: Dex number, form, posted time, gender, weather,
 * moves outside raids, GoDex evolution and form-change targets, and the coordinates to copy.
 */
@Composable
internal fun MoreDetailsSection(alert: PokemonAlert, goDexStatus: GoDexMatchResult) {
    var expanded by rememberSaveable(alert.uniqueId) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "more_details_chevron")
    val facts = remember(alert, goDexStatus) {
        buildList {
            alert.pokedexId?.let { add("Pokédex" to "#${it.toString().padStart(4, '0')}") }
            alert.pokemonForm?.takeIf { it.isNotBlank() }?.let { add("Form" to it) }
            TimeUtils.formatPostedTime(alert.createdAt)?.let { add("Posted" to it) }
            alert.gender?.takeIf { it.isNotBlank() }?.let { g -> add("Gender" to g.replaceFirstChar { it.uppercase() }) }
            currentWeatherDisplay(alert)?.let { add("Weather" to if (it.confirmed) it.label else "${it.label} ($UNCONFIRMED_WEATHER_NOTE)") }
            if (!alert.hasTypeContaining("raid")) alert.moves?.formatted()?.let { add("Moves" to it) }
            goDexStatus.evolutionTargets.forEach { add("Evolution needed" to it.displayName) }
            goDexStatus.formChangeTargets.forEach { add("Form change needed" to it.displayName) }
            if (alert.isSpeciesReplacement) add("Replaced" to listOfNotNull(alert.oldSpecies, alert.oldIv?.let { "IV $it" }, alert.oldCp?.let { "CP $it" }).joinToString(" · "))
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide details" else "Show details") { expanded = !expanded }
                .padding(horizontal = PanelGutter, vertical = Spacing.sm)
                .testTag("alert_more_details"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelRowIcon { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text("More details", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = facts.joinToString(", ") { it.first.lowercase(Locale.getDefault()) }
                        .ifEmpty { "Coordinates" }
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(visible = expanded, enter = appExpandIn(), exit = appCollapseOut()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PanelGutter + 56.dp, end = PanelGutter, bottom = Spacing.sm)
                    .testTag("alert_more_details_content"),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                facts.forEach { (label, value) -> MoreDetailsFact(label, value) }
                CopyCoordinatesFact(alert)
            }
        }
    }
}

@Composable
private fun MoreDetailsFact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CopyCoordinatesFact(alert: PokemonAlert) {
    val lat = alert.latitude ?: return
    val lon = alert.longitude ?: return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val text = String.format(Locale.ROOT, "%.6f, %.6f", lat, lon)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Coordinates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, "Coordinates copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Copy")
        }
    }
}

/** The alert's own picture, framed, for a layout that keeps it in a section. */
@Composable
internal fun AlertPicture(alert: PokemonAlert, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    AlertImage(
        alert = alert,
        rounded = false,
        modifier = modifier
            .padding(horizontal = PanelGutter)
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = "Expand image", onClick = onClick)
    )
}
