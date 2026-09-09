package com.example.pokemonalertsv2.hunt

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val huntRepository = remember(context) { HuntRepository.getInstance(context) }
    val session by huntRepository.activeHunt.collectAsStateWithLifecycle()

    var pickerOpen by remember { mutableStateOf(false) }

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
                Text(
                    text = "Hunting ${active.name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            huntRepository.stop()
                            // The journey belongs to the hunt: leaving it running
                            // would keep a pill up for a trip nobody is taking.
                            ArrivalTrackingRepository.getInstance(context).stopTracking()
                        }
                    }
                ) {
                    Text("Stop hunt")
                }
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
                    huntRepository.start(name = name, definition = definition)
                    pickerOpen = false
                    onHuntStarted()
                }
            }
        )
    }
}
