package com.example.pokemonalertsv2.ui.alerts.mapchrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.hunt.CATCH_UNDO_WINDOW_MILLIS
import com.example.pokemonalertsv2.hunt.formatHuntDistance
import com.example.pokemonalertsv2.hunt.formatHuntDuration
import com.example.pokemonalertsv2.hunt.huntTargetDetail
import com.example.pokemonalertsv2.hunt.huntTargetTitle
import com.example.pokemonalertsv2.sync.live.LiveAction
import com.example.pokemonalertsv2.sync.live.LiveSessions
import com.example.pokemonalertsv2.ui.theme.Spacing
import kotlinx.coroutines.delay

/** What the hunt banner's menu and notice strip can ask for. */
internal class HuntChromeActions(
    val onOpenTarget: () -> Unit,
    val onAction: (LiveAction) -> Unit,
    val onEditTargets: () -> Unit,
    val onToggleRouteLine: () -> Unit,
    val onOpenTools: () -> Unit,
    val onTakeOver: () -> Unit
)

/** The current time, ticking once a second while shown: the active time and the undo offer count down. */
@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** The big line: how far, in range, locating, paused or waiting, the same words the floating window uses. */
@Composable
private fun headline(view: LiveSessions.HuntView): String = when {
    view.paused -> stringResource(R.string.tracking_hunt_paused)
    view.targetId == null -> stringResource(R.string.hunt_banner_waiting)
    view.inRange -> stringResource(R.string.hunt_banner_in_range)
    view.distanceMeters != null -> formatHuntDistance(view.distanceMeters.toDouble())
    else -> stringResource(R.string.hunt_banner_locating)
}

/**
 * The running hunt across the top of the map, in the route colour like a catch route's walking
 * banner: the stop number, how far, what, and what the hunt has done so far. Below it, a strip
 * for what needs a tap now (undo a catch, a raid that ended, the device running the hunt gone
 * quiet). Works the same for a hunt running here and one running on another device.
 */
@Composable
internal fun HuntBanner(
    view: LiveSessions.HuntView,
    ordinal: Int?,
    routeLineShown: Boolean,
    streamConnectedAt: Long?,
    actions: HuntChromeActions,
    modifier: Modifier = Modifier
) {
    val now = rememberNow()
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Surface(
            onClick = actions.onOpenTarget,
            enabled = view.target != null,
            modifier = Modifier.fillMaxWidth().testTag("hunt_banner"),
            shape = RoundedCornerShape(20.dp),
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shadowElevation = 6.dp
        ) {
            Row(
                Modifier.padding(start = Spacing.md, end = Spacing.xxs, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(shape = CircleShape, color = scheme.onPrimary, contentColor = scheme.primary, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (ordinal != null) {
                            Text(ordinal.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(headline(view), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        view.target?.let(::huntTargetTitle) ?: stringResource(R.string.hunt_banner_hunting, view.session.name),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val detail = view.target?.let { com.example.pokemonalertsv2.ui.alerts.mapchrome.guidanceRest(huntTargetDetail(it, null, now)) }.orEmpty()
                    val stats = androidx.compose.ui.res.pluralStringResource(
                        R.plurals.hunt_banner_stats,
                        view.catches.size,
                        view.catches.size,
                        formatHuntDistance(view.walkedMeters),
                        formatHuntDuration(view.activeMillis(now))
                    )
                    val mirror = view.mirroredFrom?.let { stringResource(R.string.live_on_device, it.deviceName.ifBlank { "?" }) }
                    Text(
                        listOfNotNull(detail.takeIf { it.isNotBlank() }, stats, mirror).joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onPrimary.copy(alpha = 0.85f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("hunt_menu")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.hunt_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (view.paused) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.hunt_menu_resume)) },
                                onClick = { menuOpen = false; actions.onAction(LiveAction.RESUME) })
                        } else {
                            DropdownMenuItem(text = { Text(stringResource(R.string.hunt_menu_pause)) },
                                onClick = { menuOpen = false; actions.onAction(LiveAction.PAUSE) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.hunt_menu_recalculate)) },
                            onClick = { menuOpen = false; actions.onAction(LiveAction.RECALCULATE) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.hunt_menu_edit)) },
                            onClick = { menuOpen = false; actions.onEditTargets() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hunt_menu_route_line)) },
                            trailingIcon = { Switch(checked = routeLineShown, onCheckedChange = null) },
                            onClick = { actions.onToggleRouteLine() }
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.hunt_menu_map_tools)) },
                            onClick = { menuOpen = false; actions.onOpenTools() })
                        if (view.isMirror) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.live_take_over)) },
                                onClick = { menuOpen = false; actions.onTakeOver() })
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hunt_menu_stop), color = scheme.error) },
                            onClick = { menuOpen = false; confirmStop = true }
                        )
                    }
                }
            }
        }
        HuntNotice(view, now, streamConnectedAt, actions)
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.hunt_stop_confirm_title)) },
            text = { Text(stringResource(R.string.hunt_stop_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; actions.onAction(LiveAction.STOP) }) {
                    Text(stringResource(R.string.hunt_menu_stop), color = scheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
        )
    }
}

/** One line under the banner, only while there is something to act on. */
@Composable
private fun HuntNotice(view: LiveSessions.HuntView, now: Long, streamConnectedAt: Long?, actions: HuntChromeActions) {
    val undo = view.undo?.takeIf { now - it.caughtAtMillis in 0..CATCH_UNDO_WINDOW_MILLIS }
    val offline = view.ownerOffline(now, streamConnectedAt)
    val (text, action, onAction) = when {
        offline -> Triple(
            stringResource(R.string.live_owner_offline, view.mirroredFrom?.deviceName.orEmpty().ifBlank { "?" }),
            stringResource(R.string.live_take_over),
            actions.onTakeOver
        )
        undo != null -> Triple(
            stringResource(R.string.hunt_banner_undo, undo.displayName) +
                " · ${((CATCH_UNDO_WINDOW_MILLIS - (now - undo.caughtAtMillis)) / 1000).coerceAtLeast(0)} s",
            stringResource(R.string.hunt_banner_undo_action),
            { actions.onAction(LiveAction.UNDO) }
        )
        view.endedNote != null -> Triple(view.endedNote, null, {})
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.xxs).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (action != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * The hunt's three everyday buttons along the bottom: previous target, Got it, next target, the
 * floating window's dock at thumb size. A button waiting on the device running the hunt shows a
 * spinner instead of acting twice.
 */
@Composable
internal fun HuntDock(
    view: LiveSessions.HuntView,
    pending: LiveAction?,
    onAction: (LiveAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val hasTargets = view.route.isNotEmpty() || view.others.isNotEmpty() || view.targetId != null
    Surface(
        modifier = modifier.fillMaxWidth().testTag("hunt_dock"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            FilledTonalIconButton(
                onClick = { onAction(LiveAction.PREVIOUS) },
                enabled = hasTargets && pending == null,
                modifier = Modifier.size(52.dp).testTag("hunt_previous")
            ) {
                Icon(painterResource(R.drawable.ic_pip_prev), contentDescription = stringResource(R.string.hunt_dock_previous))
            }
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAction(LiveAction.GOT_IT)
                },
                enabled = view.targetId != null && pending == null,
                modifier = Modifier.weight(1f).height(52.dp).testTag("hunt_got_it"),
                colors = ButtonDefaults.buttonColors()
            ) {
                if (pending != null) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else if (view.targetId == null) {
                    Text(stringResource(R.string.hunt_dock_looking), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.hunt_dock_got_it), fontWeight = FontWeight.SemiBold)
                }
            }
            FilledTonalIconButton(
                onClick = { onAction(LiveAction.NEXT) },
                enabled = hasTargets && pending == null,
                modifier = Modifier.size(52.dp).testTag("hunt_next")
            ) {
                Icon(painterResource(R.drawable.ic_pip_next), contentDescription = stringResource(R.string.hunt_dock_next))
            }
        }
    }
}

/** A session running on another device that this one has no screen for: a catch route, a journey, Raid Watch. */
@Composable
internal fun MirroredSessionCard(
    title: String,
    detail: String?,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.xxs, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions.forEach { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
    }
}
