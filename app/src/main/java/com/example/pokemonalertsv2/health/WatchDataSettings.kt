package com.example.pokemonalertsv2.health

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.ui.settings.SwitchSetting
import kotlinx.coroutines.launch

/** Settings → "Watch & health data": connect Health Connect and choose whether summaries show it. */
@Composable
fun WatchDataSettings() {
    val context = LocalContext.current
    val reader = remember(context) { HealthDataReader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val show by remember(context) { HealthDataReader.showWatchStats(context) }.collectAsStateWithLifecycle(initialValue = true)
    var availability by remember { mutableStateOf(reader.availability()) }
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var check by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(HealthDataReader.permissionContract()) { check++ }
    // Coming back from Health Connect's own screens may have changed access.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { check++ }
    LaunchedEffect(check) {
        availability = reader.availability()
        connected = availability == HealthDataReader.Availability.AVAILABLE && reader.isConnected()
    }
    fun start(intent: android.content.Intent) = try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}

    Column(Modifier.testTag("watch_data_settings"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Heart rate, steps and calories from your Galaxy Watch, read from Health Connect for each hunt and catch " +
                "route. No app on the watch: in Samsung Health, open Settings → Health Connect and allow it to share " +
                "heart rate, steps and calories. Data appears once the watch has synced, usually within a few minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val status = when {
            availability == HealthDataReader.Availability.UNAVAILABLE -> "Health Connect is not available on this phone."
            availability == HealthDataReader.Availability.NEEDS_UPDATE -> "Health Connect needs an update."
            connected == true -> "Connected to Health Connect."
            connected == false -> "Not connected."
            else -> "Checking…"
        }
        Text(status, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (availability) {
                HealthDataReader.Availability.AVAILABLE ->
                    if (connected == true) {
                        OutlinedButton(onClick = { start(reader.manageIntent()) }) { Text("Manage access") }
                    } else {
                        FilledTonalButton(onClick = {
                            try { launcher.launch(HealthDataReader.PERMISSIONS) } catch (_: ActivityNotFoundException) {}
                        }) { Text("Connect") }
                    }
                HealthDataReader.Availability.NEEDS_UPDATE ->
                    FilledTonalButton(onClick = { start(reader.installIntent()) }) { Text("Update Health Connect") }
                HealthDataReader.Availability.UNAVAILABLE ->
                    FilledTonalButton(onClick = { start(reader.installIntent()) }) { Text("Get Health Connect") }
            }
        }
        SwitchSetting(
            title = "Show watch data in summaries",
            subtitle = "Heart rate chart, steps and calories under each hunt and catch route",
            checked = show,
            onCheckedChange = { value -> scope.launch { HealthDataReader.setShowWatchStats(context, value) } }
        )
    }
}
