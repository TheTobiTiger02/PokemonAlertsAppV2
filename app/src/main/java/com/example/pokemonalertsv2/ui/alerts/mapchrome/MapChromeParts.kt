package com.example.pokemonalertsv2.ui.alerts.mapchrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.hunt.HuntMapFocus
import com.example.pokemonalertsv2.hunt.huntTargetDetail
import com.example.pokemonalertsv2.hunt.huntTargetTitle
import com.example.pokemonalertsv2.ui.alerts.SyncStatus
import com.example.pokemonalertsv2.ui.theme.DangerRed
import com.example.pokemonalertsv2.ui.theme.SuccessGreen
import com.example.pokemonalertsv2.ui.theme.WarningAmber

/** The primary map action: find yourself, then follow. Colour says which of the two is on. */
@Composable
internal fun MapLocateButton(state: MapLocateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val following = state == MapLocateState.FOLLOWING || state == MapLocateState.FOLLOWING_DEGRADED
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.testTag("map_locate"),
        containerColor = when {
            following -> scheme.primary
            state == MapLocateState.REQUESTED -> scheme.secondaryContainer
            else -> scheme.primaryContainer
        },
        contentColor = when {
            following -> scheme.onPrimary
            state == MapLocateState.REQUESTED -> scheme.onSecondaryContainer
            else -> scheme.onPrimaryContainer
        },
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_my_location),
            contentDescription = stringResource(locateDescription(state))
        )
    }
}

private fun locateDescription(state: MapLocateState): Int = when (state) {
    MapLocateState.FOLLOWING_DEGRADED -> R.string.map_location_tracking_degraded
    MapLocateState.FOLLOWING -> R.string.map_location_following
    MapLocateState.REQUESTED -> R.string.map_location_tracking_recenter
    MapLocateState.IDLE -> R.string.map_location_start_tracking
}

/** The small framing button above [MapLocateButton]; what it frames is the caller's to say. */
@Composable
internal fun MapFitButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
    ) {
        Icon(painter = painterResource(R.drawable.ic_fit_map), contentDescription = contentDescription)
    }
}

/** The small blue count over a control that hides active filter rules. */
@Composable
internal fun MapCountBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

/** An icon with a [MapCountBadge] tucked onto its corner when [count] is positive. */
@Composable
internal fun BadgedIcon(icon: Painter, contentDescription: String?, count: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Icon(painter = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        if (count > 0) {
            MapCountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp))
        }
    }
}

/** Green when live, amber when showing a cached copy, red when nothing loaded, blue while loading. */
@Composable
internal fun MapSyncDot(status: SyncStatus, modifier: Modifier = Modifier) {
    val color: Color = when (status) {
        is SyncStatus.Live -> SuccessGreen
        is SyncStatus.Cached -> WarningAmber
        is SyncStatus.Failed -> DangerRed
        SyncStatus.Loading, SyncStatus.Refreshing -> MaterialTheme.colorScheme.primary
    }
    Box(modifier = modifier.size(10.dp).background(color, CircleShape))
}

internal val SyncStatus.isProblem: Boolean
    get() = this is SyncStatus.Cached || this is SyncStatus.Failed

/** One line under the count: whether the alerts on screen are current. */
internal fun SyncStatus.chromeSummary(): String = when (this) {
    SyncStatus.Loading -> "Loading alerts…"
    SyncStatus.Refreshing -> "Refreshing…"
    is SyncStatus.Live -> "Up to date"
    is SyncStatus.Cached -> "Offline · tap to retry"
    is SyncStatus.Failed -> "Couldn't load · tap to retry"
}

internal fun MapChromeState.countLabel(): String =
    if (visibleCount == totalCount) "$visibleCount on map" else "$visibleCount of $totalCount on map"

/** Frames every visible alert, or, during a hunt, flips between its target and its route. */
@Composable
internal fun fitDescription(hunt: MapChromeHunt?): String = when {
    hunt == null -> stringResource(R.string.map_show_all_alerts)
    hunt.focus == HuntMapFocus.TARGET -> "Show Hunt route"
    else -> "Focus Hunt target"
}

internal fun MapChromeHunt.title(): String = target?.let(::huntTargetTitle) ?: "Hunting $name"

internal fun MapChromeHunt.detail(): String =
    target?.let { huntTargetDetail(it, distanceMeters) } ?: "Waiting for a match"
