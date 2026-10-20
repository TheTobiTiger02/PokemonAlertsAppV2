@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.ui.components.PanelRowIcon
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import com.example.pokemonalertsv2.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus

/**
 * The map's alert sheet in the redesigned detail options: what it is first (sprite, title,
 * countdown, then IV · CP · distance · walk), then I'm going and Navigate, then what else is at
 * the same stop as small tiles, then one row to the full detail. GoDex, snooze,
 * picture-in-picture, share and dismiss live in the ⋮ menu.
 */
@Composable
internal fun MapAlertSheetContent(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult,
    distanceInfo: AlertDistanceInfo?,
    onDismiss: () -> Unit,
    isGoing: Boolean,
    onGoing: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissed: Boolean = false,
    onDismissAlert: () -> Unit = {},
    onRestoreAlert: () -> Unit = {},
    companions: List<PokemonAlert> = emptyList(),
    onOpenCompanion: (PokemonAlert) -> Unit = {},
    showCloseButton: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val countdownClock = rememberCountdownClock()
    val snoozeMinutes = rememberDefaultSnoozeMinutes()
    var showSnoozeDialog by rememberSaveable(alert.uniqueId) { mutableStateOf(false) }
    val expired = alert.isExpiredAt(countdownClock.value)

    Column(
        modifier = modifier
            .heightIn(max = 640.dp)
            .testTag("map_alert_sheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AlertSheetHeader(
            alert = alert,
            goDexStatus = goDexStatus,
            countdownClock = countdownClock,
            distanceInfo = distanceInfo,
            isGoing = isGoing,
            isDismissed = isDismissed
        ) {
            AlertOverflowMenu(
                alert = alert,
                goDexStatus = goDexStatus,
                onSnooze = if (expired) null else ({ showSnoozeDialog = true }),
                onPictureInPicture = { openAlertInPictureInPicture(context, alert) },
                onShare = onShare,
                onDismissAlert = onDismissAlert.takeUnless { isDismissed },
                onRestoreAlert = onRestoreAlert.takeIf { isDismissed },
                contentDescription = "More map alert actions"
            )
            if (showCloseButton) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
        }
        AlertPrimaryButtons(
            isGoing = isGoing,
            goingEnabled = alert.isEligibleArrivalDestination(),
            onGoing = onGoing,
            onNavigate = onOpenMaps
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .keepScrollInSheet()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (companions.isNotEmpty()) {
                Text(
                    text = "Also here",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("same_stop_companions")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    companions.forEach { companion -> CompanionTile(companion) { onOpenCompanion(companion) } }
                }
            }
            Surface(
                onClick = onOpenFullDetail,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_open_details")
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelRowIcon { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("Open details", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = mapSheetDetailsSummary(alert),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSnoozeDialog) {
        SnoozeDurationDialog(
            defaultMinutes = snoozeMinutes,
            onDismiss = { showSnoozeDialog = false },
            onConfirm = { minutes ->
                showSnoozeDialog = false
                scope.launch {
                    val scheduled = snoozeAlertFromUi(context, alert, minutes)
                    Toast.makeText(
                        context,
                        if (scheduled) "Snoozed for ${formatSnoozeDurationLabel(minutes)}" else "Alert ends before that snooze time",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

/** What the full detail adds, so the row says why to open it. */
internal fun mapSheetDetailsSummary(alert: PokemonAlert): String = when {
    !alert.pvpRankings.isNullOrEmpty() -> "PvP ranks, stats and location"
    alert.hasTypeContaining("raid") -> "Counters, moves and location"
    !alert.pokemonRewards.isNullOrEmpty() || !alert.gruntType.isNullOrBlank() -> "Possible rewards and location"
    alert.hasTypeContaining("quest") -> "Reward, task and location"
    else -> "Stats, moves and location"
}

/** One other alert at the same stop: its sprite and a short name. */
@Composable
private fun CompanionTile(companion: PokemonAlert, onClick: () -> Unit) {
    val label = questAlertPresentation(companion)?.reward ?: companion.pokemon ?: companion.cleanPokemonName
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .width(80.dp)
            .testTag("same_stop_${companion.uniqueId}")
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xxs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            AlertSprite(companion, size = 44.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = resolveAlertVisualStyle(companion).label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The map sheet's header: sprite, title, type and place, countdown, then
 * one line of numbers (with "Going" or "Dismissed" first when that applies), a quest's task,
 * and the GoDex status.
 */
@Composable
internal fun AlertSheetHeader(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult,
    countdownClock: State<Long>,
    distanceInfo: AlertDistanceInfo?,
    isGoing: Boolean,
    modifier: Modifier = Modifier,
    isDismissed: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.Top) {
            AlertSprite(alert, size = 56.dp)
            Spacer(Modifier.width(Spacing.md))
            AlertSummaryBlock(
                alert = alert,
                goDexStatus = goDexStatus,
                countdownClock = countdownClock,
                titleStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .testTag("map_alert_title_block")
            )
            actions()
        }
        val status = listOfNotNull(
            "Going".takeIf { isGoing },
            "Dismissed".takeIf { isDismissed }
        )
        val numbers = alertNumbersLine(alert, distanceInfo)
        val line = (status + listOf(numbers).filter { it.isNotEmpty() }).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(
                text = line,
                style = MaterialTheme.typography.labelLarge,
                color = if (status.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("alert_numbers_line")
            )
        }
        questAlertPresentation(alert)?.task?.let { task ->
            Text(task, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (goDexStatus.status != GoDexMatchStatus.NOT_CONFIGURED) GoDexStatusPill(goDexStatus)
    }
}
