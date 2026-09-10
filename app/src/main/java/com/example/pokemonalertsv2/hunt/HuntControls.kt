package com.example.pokemonalertsv2.hunt

import android.os.Build
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
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService
import com.example.pokemonalertsv2.tracking.JourneyOverlay
import com.example.pokemonalertsv2.tracking.resolveJourneyReadoutSurface
import com.example.pokemonalertsv2.tracking.shouldOpenHuntPictureInPicture
import kotlinx.coroutines.launch

/**
 * Start or stop a hunt from the map's control panel.
 *
 * The target is chosen in place — see [HuntTargetSheet]. There is no step where
 * the trainer has to have prepared a saved profile first.
 */
@Composable
fun HuntControls(
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    questRewardThumbnails: Map<String, String>,
    categoryCounts: Map<AlertCategory, Int>,
    onHuntStarted: () -> Unit,
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

    var pickerOpen by remember { mutableStateOf(false) }
    val lastCaught by remember(context) {
        AlertPreferences(context.alertPreferencesDataStore).lastCaughtAlert
    }.collectAsStateWithLifecycle(initialValue = null)
    val overlayAllowed by remember(context) {
        AlertPreferences(context.alertPreferencesDataStore).journeyOverlayEnabled
    }.collectAsStateWithLifecycle(initialValue = false)
    val readoutSurface = resolveJourneyReadoutSurface(
        sdkInt = Build.VERSION.SDK_INT,
        canDrawOverlays = JourneyOverlay.canDraw(context),
        overlayAllowed = overlayAllowed
    )

    Column(modifier = modifier.fillMaxWidth()) {
        val active = session
        if (active == null) {
            FilledTonalButton(
                onClick = { pickerOpen = true },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                TextButton(
                    onClick = {
                        scope.launch {
                            // One path for every stop button -- this one, the
                            // window's, and the notification's -- so they cannot
                            // end up ending different amounts of the hunt.
                            ArrivalTrackingService.stopEverything(context)
                        }
                    }
                ) {
                    Text("Stop hunt")
                }
            }
        }

        // The tick on the floating window sits between the two step arrows, on a
        // map being read while walking, and it retires the alert for good. This is
        // the way back from a mis-tap.
        lastCaught?.let { caught ->
            TextButton(
                onClick = {
                    scope.launch {
                        val preferences = AlertPreferences(context.alertPreferencesDataStore)
                        preferences.removeDismissedAlert(caught.id)
                        // Cleared either way: the offer is for the last catch, and
                        // taking it back is the end of that offer.
                        preferences.forgetCaughtAlert()
                    }
                },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Undo catching ${caught.displayName}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (pickerOpen) {
        HuntTargetSheet(
            catalog = catalog,
            artwork = artwork,
            questRewardThumbnails = questRewardThumbnails,
            categoryCounts = categoryCounts,
            onDismiss = { pickerOpen = false },
            onStart = { name, definition ->
                scope.launch {
                    // The hunt is written first, then the old journey is cleared.
                    // The other order leaves a moment with neither a destination nor
                    // a hunt, and a service running for the old journey reads that as
                    // "nothing to do" and stops itself mid-start.
                    huntRepository.start(name = name, definition = definition)
                    // A new hunt supersedes whatever you were walking to. Without
                    // this the old journey simply carries on under the new hunt's
                    // name, which is how a raid hunt ended up pointing at a spawn.
                    ArrivalTrackingRepository.getInstance(context).stopTracking()
                    pickerOpen = false
                    // Start the service even with nothing to walk to yet: it is
                    // what waits for the first match to arrive.
                    ArrivalTrackingService.startHunt(context)
                    // Only where the floating map is the readout. On a device with the
                    // status bar chip, opening it would hide the chip for the whole hunt.
                    if (shouldOpenHuntPictureInPicture(readoutSurface)) onHuntStarted()
                }
            }
        )
    }
}
