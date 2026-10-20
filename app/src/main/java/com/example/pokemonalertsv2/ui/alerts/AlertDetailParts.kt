package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.ui.components.RollingNumberText
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

