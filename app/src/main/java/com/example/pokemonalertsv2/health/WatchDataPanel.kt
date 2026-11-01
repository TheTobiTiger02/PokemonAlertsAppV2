package com.example.pokemonalertsv2.health

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.hunt.HeartRed
import com.example.pokemonalertsv2.hunt.WatchStatsSection
import kotlinx.coroutines.launch

/**
 * The watch part of a hunt or catch-route summary. Shows the stored stats, reads Health Connect
 * when they are missing or the watch may still have been syncing, and otherwise offers to
 * connect or to check again. Nothing is shown on phones without Health Connect, or when the
 * trainer turned watch data off in Settings.
 */
@Composable
fun WatchDataPanel(
    startMillis: Long,
    endMillis: Long,
    stored: WatchStats?,
    onRead: suspend (WatchStats) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reader = remember(context) { HealthDataReader(context.applicationContext) }
    val enabled by remember(context) { HealthDataReader.showWatchStats(context) }.collectAsStateWithLifecycle(initialValue = true)
    if (!enabled) return
    val availability = remember(reader) { reader.availability() }
    var stats by remember(stored) { mutableStateOf(stored) }
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var reading by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(HealthDataReader.permissionContract()) { granted ->
        connected = granted.any { it in HealthDataReader.PERMISSIONS }
        attempt++
    }

    LaunchedEffect(attempt) {
        if (availability != HealthDataReader.Availability.AVAILABLE) return@LaunchedEffect
        val isConnected = reader.isConnected()
        connected = isConnected
        val now = System.currentTimeMillis()
        // A manual refresh (attempt > 0) always reads; otherwise only when the data may be incomplete.
        if (isConnected && (attempt > 0 || needsHealthRead(endMillis, stats, now))) {
            reading = true
            reader.read(startMillis, endMillis, now)?.let { fresh ->
                stats = fresh
                onRead(fresh)
            }
            reading = false
        }
    }

    val current = stats
    if (current != null && current.hasData) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WatchStatsSection(current, Modifier.testTag("watch_stats"))
            if (connected == true && System.currentTimeMillis() - endMillis < HEALTH_READ_MAX_AGE_MILLIS) {
                TextButton(onClick = { attempt++ }, enabled = !reading) { Text(if (reading) "Checking…" else "Check watch data again") }
            }
        }
        return
    }
    if (availability == HealthDataReader.Availability.UNAVAILABLE) return

    Surface(modifier.fillMaxWidth().testTag("watch_connect"), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = HeartRed, modifier = Modifier.size(18.dp))
                Text("Watch data", style = MaterialTheme.typography.titleSmall)
            }
            when {
                availability == HealthDataReader.Availability.NEEDS_UPDATE -> {
                    Text("Update Health Connect to see heart rate, steps and calories from your watch.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = {
                        try { context.startActivity(reader.installIntent()) } catch (_: ActivityNotFoundException) {}
                    }) { Text("Update Health Connect") }
                }
                connected == false -> {
                    Text("See heart rate, steps and calories from your Galaxy Watch (or any watch that syncs to Health Connect) for this walk.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = {
                        try { launcher.launch(HealthDataReader.PERMISSIONS) } catch (_: ActivityNotFoundException) {}
                    }) { Text("Connect Health Connect") }
                }
                reading || connected == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Reading your watch data…", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text(
                        if (System.currentTimeMillis() - endMillis > HEALTH_READ_MAX_AGE_MILLIS) "Your watch recorded nothing for this walk."
                        else "Nothing from your watch yet. It can take a few minutes to sync via Samsung Health; open the Samsung Health app to speed it up.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { scope.launch { attempt++ } }) { Text("Check again") }
                }
            }
        }
    }
}
