package com.example.pokemonalertsv2.ui.alerts

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.AlertCardStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.TravelTime

/**
 * One alert as a dense row: sprite, title, category and place, one line of numbers, and the
 * countdown. Six or seven fit on a phone where the map-preview card fitted one and a bit.
 *
 * Tap opens the detail page, the map button navigates, and a long press (or the countdown
 * column) offers everything else the large card had as buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactAlertCard(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo,
    goDexStatus: GoDexMatchResult = NoGoDexMatch,
    onOpenMaps: () -> Unit,
    onShowDetails: () -> Unit,
    onSecondaryAction: (AlertSecondaryAction) -> Unit,
    cardContext: AlertCardContext = AlertCardContext.LIVE,
    snoozeEnabled: Boolean = cardContext == AlertCardContext.LIVE,
    isGoing: Boolean = false,
    huntTarget: Boolean = false,
    onGoingClick: (() -> Unit)? = null,
    countdownClock: State<Long> = rememberCountdownClock(),
    modifier: Modifier = Modifier
) {
    val visualStyle = remember(alert) { resolveAlertVisualStyle(alert) }
    val title = remember(alert, goDexStatus.status) { formatAlertTitle(alert, goDexStatus.status) }
    val subtitle = remember(alert) { formatAlertSubtitle(alert) }
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    val accent = Color(visualStyle.category.accentArgb)
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF171A20) else Color.White
    val isExpired = endMillis?.let { it <= countdownClock.value } ?: false
    val policy = alertActionPolicy(
        context = cardContext,
        isExpired = isExpired,
        snoozeEnabled = snoozeEnabled,
        hasGoingAction = onGoingClick != null
    )
    val lateWarning = TravelTime.expiresBeforeArrival(
        walkingDurationSeconds = distanceInfo.walkingDurationSeconds,
        remainingMillis = endMillis?.minus(countdownClock.value)
    )
    val details = remember(alert, distanceInfo) { compactDetailLine(alert, distanceInfo) }
    val questTask = remember(alert) { questAlertPresentation(alert)?.task }
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (huntTarget || isGoing) 2.dp else 1.dp,
            color = if (huntTarget || isGoing) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .combinedClickable(
                        onClick = onShowDetails,
                        onLongClick = { menuOpen = true },
                        onLongClickLabel = "More actions"
                    )
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactAlertSprite(alert = alert, accent = accent)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val statusPrefix = when {
                        isGoing -> "Going"
                        huntTarget -> "Hunt target"
                        distanceInfo.isInRange -> "In range"
                        lateWarning -> "Ends before you arrive"
                        else -> null
                    }
                    val line = listOfNotNull(statusPrefix, details.takeIf { it.isNotEmpty() })
                        .joinToString(" · ")
                    if (line.isNotEmpty()) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                lateWarning && statusPrefix == "Ends before you arrive" -> MaterialTheme.colorScheme.error
                                statusPrefix != null -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    questTask?.let { task ->
                        Text(
                            text = task,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        // In history every row has ended; a red EXPIRED on each one is noise.
                        if (isExpired && cardContext == AlertCardContext.HISTORY) {
                            Text(
                                text = endMillis?.let { "Ended " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "Ended",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            AlertCountdownBadge(
                                endTime = alert.endTime,
                                categoryAccent = accent,
                                categoryOnAccent = onAccent,
                                countdownClock = countdownClock
                            )
                        }
                    }
                    if (policy.showNavigate) {
                        IconButton(
                            onClick = onOpenMaps,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_map),
                                contentDescription = "Navigate to ${alert.cleanPokemonName}"
                            )
                        }
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (policy.showGoing && onGoingClick != null) {
                    DropdownMenuItem(
                        text = { Text(if (isGoing) "Stop going" else "I’m going") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onGoingClick()
                        }
                    )
                    HorizontalDivider()
                }
                policy.overflowActions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.menuLabel) },
                        onClick = {
                            menuOpen = false
                            onSecondaryAction(action)
                        }
                    )
                }
            }
        }
    }
}

internal val AlertSecondaryAction.menuLabel: String
    get() = when (this) {
        AlertSecondaryAction.SNOOZE -> "Snooze"
        AlertSecondaryAction.PICTURE_IN_PICTURE -> "Open in picture-in-picture"
        AlertSecondaryAction.SHARE -> "Share"
        AlertSecondaryAction.DISMISS -> "Dismiss"
        AlertSecondaryAction.RESTORE -> "Restore"
    }

/**
 * The numbers worth reading at a glance: IV and CP, then how far away it is. Anything missing
 * drops out. A quest's task gets its own line, and weather stays on the detail page.
 */
internal fun compactDetailLine(alert: PokemonAlert, distanceInfo: AlertDistanceInfo): String = buildList {
    val iv = if (alert.isWeatherChange && alert.newIv != null) alert.newIv else alert.formattedIv
    iv?.let { add("IV $it") }
    alert.displayCp?.let { add("CP $it") }
    distanceInfo.distanceText?.takeIf { it.isNotBlank() }?.let(::add)
    distanceInfo.walkingText?.takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" · ")

@Composable
private fun CompactAlertSprite(alert: PokemonAlert, accent: Color) {
    val context = LocalContext.current
    val url = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.14f)
        ) {}
        if (url != null) {
            AsyncImage(
                model = remember(url) {
                    ImageRequest.Builder(context).data(url).crossfade(false).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(42.dp),
                placeholder = painterResource(R.drawable.ic_placeholder),
                error = painterResource(R.drawable.ic_placeholder)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_placeholder),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/** The row style the user picked in Appearance; compact until the preference loads. */
@Composable
internal fun rememberAlertCardStyle(): AlertCardStyle {
    val context = LocalContext.current
    val style by remember(context) {
        AlertPreferences(context.alertPreferencesDataStore).cardStyle
    }.collectAsStateWithLifecycle(initialValue = AlertCardStyle.COMPACT)
    return style
}

/** A feed or history row in the chosen [style]. Parameters mirror [AlertCard]. */
@Composable
internal fun AlertListItem(
    style: AlertCardStyle,
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo,
    goDexStatus: GoDexMatchResult = NoGoDexMatch,
    onOpenMaps: () -> Unit,
    onShowDetails: () -> Unit,
    onSecondaryAction: (AlertSecondaryAction) -> Unit,
    cardContext: AlertCardContext = AlertCardContext.LIVE,
    isGoing: Boolean = false,
    huntTarget: Boolean = false,
    onGoingClick: (() -> Unit)? = null,
    countdownClock: State<Long> = rememberCountdownClock(),
    modifier: Modifier = Modifier
) {
    when (style) {
        AlertCardStyle.COMPACT -> CompactAlertCard(
            alert = alert, distanceInfo = distanceInfo, goDexStatus = goDexStatus,
            onOpenMaps = onOpenMaps, onShowDetails = onShowDetails, onSecondaryAction = onSecondaryAction,
            cardContext = cardContext, isGoing = isGoing, huntTarget = huntTarget,
            onGoingClick = onGoingClick, countdownClock = countdownClock, modifier = modifier
        )
        AlertCardStyle.LARGE -> AlertCard(
            alert = alert, distanceInfo = distanceInfo, goDexStatus = goDexStatus,
            onOpenMaps = onOpenMaps, onShowDetails = onShowDetails, onSecondaryAction = onSecondaryAction,
            cardContext = cardContext, isGoing = isGoing, huntTarget = huntTarget,
            onGoingClick = onGoingClick, countdownClock = countdownClock, modifier = modifier
        )
    }
}
