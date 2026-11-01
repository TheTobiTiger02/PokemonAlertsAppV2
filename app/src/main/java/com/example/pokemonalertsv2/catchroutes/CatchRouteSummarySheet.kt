package com.example.pokemonalertsv2.catchroutes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pokemonalertsv2.hunt.StatTile
import java.text.DateFormat
import java.util.Date
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column

/**
 * How a guided route went: the numbers, and a map of what was walked over what was planned.
 * [onWalkAgain] plans the same walk again from now; null hides it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatchRouteSummarySheet(summary: CatchRouteSummary, onDismiss: () -> Unit, onWalkAgain: (() -> Unit)?) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Scrolls on short screens so Done stays reachable under the map.
        Column(Modifier.keepScrollInSheet().verticalScroll(rememberScrollState())) {
            CatchRouteSummaryContent(summary, Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onWalkAgain != null) OutlinedButton(onClick = onWalkAgain, modifier = Modifier.weight(1f).testTag("catch_summary_again")) { Text("Walk again") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("catch_summary_done")) { Text("Done") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun CatchRouteSummaryContent(summary: CatchRouteSummary, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(summary.name, style = MaterialTheme.typography.headlineSmall)
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        Text("${format.format(Date(summary.startedAtMillis))} · ${(summary.activeMillis / 60_000)} min walking" +
            (if (summary.replans > 0) " · replanned ${summary.replans}×" else ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(summary.caught.toString(), "Caught", Modifier.weight(1f).testTag("catch_summary_caught"))
            StatTile(formatCatchKm(summary.walkedMeters), "Walked", Modifier.weight(1f))
            StatTile(formatCatchSpeed(summary.averageKmh), "Avg speed", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(summary.planned.toString(), "Planned", Modifier.weight(1f))
            StatTile(summary.missed.toString(), "Not reached", Modifier.weight(1f))
            StatTile(summary.skipped.toString(), "Skipped", Modifier.weight(1f))
        }
        if (summary.track.size >= 2 || summary.plannedPath.size >= 2) {
            AndroidView(
                factory = { ctx ->
                    CatchRouteMapView(ctx).also { map ->
                        map.update(summary.settings, summaryItinerary(summary), null)
                        map.setTrack(summary.track)
                        map.onReady = { map.fitArea(summary.track + summary.plannedPath) }
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).testTag("catch_summary_map"),
            )
            Text("Teal: where you walked · blue: the last planned route",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        com.example.pokemonalertsv2.health.WatchDataPanel(
            startMillis = summary.startedAtMillis,
            endMillis = summary.endedAtMillis,
            stored = summary.watch,
            onRead = { CatchRouteStore.get(context).updateHistory(summary.copy(watch = it)) }
        )
    }
}

/** The planned line of a past route, as an itinerary the map can draw; no stops, since none are live any more. */
internal fun summaryItinerary(summary: CatchRouteSummary): CatchItinerary? {
    if (summary.plannedPath.size < 2) return null
    var meters = 0.0
    val path = summary.plannedPath.mapIndexed { i, p ->
        if (i > 0) meters += catchDistance(summary.plannedPath[i - 1], p)
        CatchPathPosition(p, meters)
    }
    return CatchItinerary(summary.settings, path, emptyList(), emptyList())
}
