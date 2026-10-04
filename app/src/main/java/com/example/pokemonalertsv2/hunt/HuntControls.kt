package com.example.pokemonalertsv2.hunt

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService
import kotlinx.coroutines.launch

/**
 * Start, edit or stop a hunt from the map's control panel.
 *
 * The target is chosen in [HuntTargetSheet], which the map hosts so the Tools tab and the
 * floating map's "Edit targets" can open the very same sheet: [onOpenHuntSetup] opens it,
 * to start a hunt or, while one runs, to change what it looks for.
 */
@Composable
fun HuntControls(
    onOpenHuntSetup: () -> Unit,
    userLocation: android.location.Location? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val huntRepository = remember(context) { HuntRepository.getInstance(context) }
    val session by huntRepository.activeHunt.collectAsStateWithLifecycle()
    val destination by remember(context) {
        ArrivalTrackingRepository.getInstance(context).destinationFlow
    }.collectAsStateWithLifecycle(initialValue = null)

    val batterySaverEnabled by huntRepository.batterySaverEnabled.collectAsStateWithLifecycle(initialValue = false)
    var batterySaverProblem by remember { mutableStateOf<String?>(null) }
    val panelOpenedAt = remember { System.currentTimeMillis() }
    val lastCaught by remember(context) {
        AlertPreferences(context.alertPreferencesDataStore).lastCaughtAlert
    }.collectAsStateWithLifecycle(initialValue = null)

    Column(modifier = modifier.fillMaxWidth()) {
        val active = session
        if (active == null) {
            FilledTonalButton(
                onClick = onOpenHuntSetup,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Start a hunt", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Hunting ${active.name}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Which match, not just which hunt. "Hunting Dragon grunts" on its
                    // own left no way to tell from in here which of them you were
                    // walking to.
                    val target = destination?.alert
                    Text(
                        text = target?.let { "→ ${huntTargetTitle(it)}" } ?: "Waiting for a match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    target?.let {
                        Text(
                            text = huntTargetDetail(it, huntTargetDistanceMeters(userLocation, it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Changing what you hunt is the everyday action, stopping the rare one.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenHuntSetup,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Edit targets", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                // One path for every stop button -- this one, the
                                // window's, and the notification's -- so they cannot
                                // end up ending different amounts of the hunt.
                                ArrivalTrackingService.stopEverything(context, fromApp = true)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Stop hunt", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Battery Saver", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = batterySaverEnabled,
                onCheckedChange = { enabled ->
                    batterySaverProblem = if (enabled) HuntBatterySaver.unavailableReason(context) else null
                    if (batterySaverProblem == null) scope.launch { huntRepository.setBatterySaverEnabled(enabled) }
                },
                modifier = Modifier.semantics { contentDescription = "Hunt Battery Saver" }
            )
        }
        Text(
            "Face down during a hunt blacks out the screen. Long press it to turn off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        batterySaverProblem?.let { problem ->
            Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (!android.provider.Settings.canDrawOverlays(context)) {
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")))
                }) { Text("Allow display over other apps") }
            }
        }

        // The tick on the floating window sits between the two step arrows, on a
        // map being read while walking, and it retires the alert for good. This is
        // the way back from a mis-tap -- one of four, all going through the same
        // undoLastCatch so they cannot undo different amounts of it.
        //
        // Liveness is read once, when the panel opens: it is a panel you are on
        // your way out of, not a readout that has to count down in place.
        lastCaught?.takeIf { isUndoOfferLive(it, panelOpenedAt) }?.let { caught ->
            TextButton(
                onClick = { scope.launch { undoLastCatch(context) } },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    undoOfferLabel(caught),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
