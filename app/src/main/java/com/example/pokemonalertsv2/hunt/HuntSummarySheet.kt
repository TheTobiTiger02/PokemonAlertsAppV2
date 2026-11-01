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
import androidx.compose.foundation.layout.height
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
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import androidx.compose.foundation.layout.BoxWithConstraints

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
    /** Null for "Hunt finished"; the history passes the hunt's date. */
    title: String? = null,
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
                .keepScrollInSheet()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = title ?: "Hunt finished",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary.name + " · " + huntTimeRange(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(summary.catches.size.toString(), "Caught", Modifier.weight(1f))
                StatTile(formatHuntDistance(summary.distanceMeters), "Walked", Modifier.weight(1f))
                StatTile(formatHuntDuration(summary.activeMillis), "Hunted", Modifier.weight(1f))
            }
            val shinies = summary.catches.count { it.shiny }
            val pace = formatHuntPace(summary)
            val bestIv = summary.catches.mapNotNull { it.iv }.maxOrNull()
            if (pace != null || shinies > 0 || bestIv != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pace != null) StatTile(pace, "Pace", Modifier.weight(1f))
                    if (shinies > 0) StatTile(shinies.toString(), "Shiny", Modifier.weight(1f))
                    if (bestIv != null) StatTile("$bestIv%", "Best IV", Modifier.weight(1f))
                }
            }

            val track = remember(summary.track) { summary.trackPoints }
            val catchPoints = remember(summary.catches) { summary.catches.mapNotNull { it.point } }
            if (track.size >= 2 || catchPoints.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HuntPathMap(
                        track, catchPoints,
                        Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).testTag("hunt_summary_map")
                    )
                    Text(
                        text = if (track.size >= 2) "Teal: your walk · gold: where you caught" else "Gold: where you caught",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            com.example.pokemonalertsv2.health.WatchDataPanel(
                startMillis = summary.startedAtMillis,
                endMillis = summary.endedAtMillis,
                stored = summary.watch,
                onRead = { HuntRepository.getInstance(context).updateWatchStats(summary.startedAtMillis, it) }
            )

            if (summary.catches.isEmpty()) {
                Text(
                    text = "Nothing caught this time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        // A tile shares its row with up to two others, so on a narrow phone a value such as
        // "1 h 05 min" drops a type size rather than being cut off.
        BoxWithConstraints {
        val narrow = maxWidth < 96.dp
        Column(
            modifier = Modifier.padding(horizontal = if (narrow) 8.dp else 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = if (narrow) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }
    }
}

@Composable
private fun CatchRow(catch: HuntCatch, time: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CatchAvatar(catch, 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = catch.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (catch.shiny || catch.iv != null || catch.cp != null) CatchBadges(catch)
            }
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "14:05 – 15:20" on the hunt's day. */
internal fun huntTimeRange(summary: HuntSummary): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    return "${time.format(Date(summary.startedAtMillis))} – ${time.format(Date(summary.endedAtMillis))}"
}
