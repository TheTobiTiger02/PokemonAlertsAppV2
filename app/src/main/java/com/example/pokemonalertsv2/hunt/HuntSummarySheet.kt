package com.example.pokemonalertsv2.hunt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.raidwatch.RaidWatchStore
import kotlinx.coroutines.launch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

/**
 * How the hunt that just ended went: what was caught, how far it took, and how long.
 *
 * Shown by the app for as long as the summary is waiting to be seen -- straight after
 * stopping in the app, or on the next open after stopping from the floating map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuntSummarySheet(
    summary: HuntSummary,
    doneLabel: String = "Done",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Offered only while that raid's info is actually still up: it ends on its own when the
    // raid does, or from its own notification.
    val watchedRaid by remember(context) { RaidWatchStore(context).watched }
        .collectAsStateWithLifecycle(initialValue = null)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("hunt_summary")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Hunt finished",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(summary.catches.size.toString(), "Caught", Modifier.weight(1f))
                StatTile(formatHuntDistance(summary.distanceMeters), "Walked", Modifier.weight(1f))
                StatTile(formatHuntDuration(summary.activeMillis), "Hunted", Modifier.weight(1f))
            }

            if (summary.catches.isEmpty()) {
                Text(
                    text = "Nothing caught this time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Caught", style = MaterialTheme.typography.titleSmall)
                    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
                    summary.catches.forEach { catch ->
                        CatchRow(catch, timeFormat.format(Date(catch.atMillis)))
                    }
                }
            }

            val raid = summary.raidWatchStillShowing
            if (raid != null && watchedRaid != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            RaidWatchController.stop(context)
                            HuntRepository.getInstance(context).forgetSummaryRaid()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dismiss $raid raid info")
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(doneLabel)
            }
        }
    }
}

@Composable
internal fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CatchRow(catch: HuntCatch, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (catch.imageUrl != null) {
                AsyncImage(
                    model = catch.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Text(catch.name.take(1), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            text = catch.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
