package com.example.pokemonalertsv2.health

import com.example.pokemonalertsv2.R
import androidx.compose.ui.res.stringResource
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
    val unavailable = {
        android.widget.Toast.makeText(context, "Health Connect could not be opened on this phone", android.widget.Toast.LENGTH_LONG).show()
    }
    fun start(intent: android.content.Intent) = try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { unavailable() }

    Column(Modifier.testTag("watch_data_settings"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Heart rate, steps and calories from your Galaxy Watch, read from Health Connect for each hunt and catch " +
                "route. No app on the watch: in Samsung Health, open Settings → Health Connect and allow it to share " +
                "heart rate, steps and calories. Data appears once the watch has synced, usually within a few minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val status = when {
            availability == HealthDataReader.Availability.UNAVAILABLE -> stringResource(R.string.health_health_connect_is_not_available_on)
            availability == HealthDataReader.Availability.NEEDS_UPDATE -> stringResource(R.string.health_health_connect_needs_an_update)
            connected == true -> stringResource(R.string.health_connected_to_health_connect)
            connected == false -> stringResource(R.string.health_not_connected)
            else -> stringResource(R.string.health_checking)
        }
        Text(status, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (availability) {
                HealthDataReader.Availability.AVAILABLE ->
                    if (connected == true) {
                        OutlinedButton(onClick = { start(reader.manageIntent()) }) { Text(stringResource(R.string.health_manage_access)) }
                    } else {
                        FilledTonalButton(onClick = {
                            try { launcher.launch(HealthDataReader.PERMISSIONS) } catch (_: ActivityNotFoundException) { unavailable() }
                        }) { Text(stringResource(R.string.health_connect)) }
                    }
                HealthDataReader.Availability.NEEDS_UPDATE ->
                    FilledTonalButton(onClick = { start(reader.installIntent()) }) { Text(stringResource(R.string.health_update_health_connect)) }
                HealthDataReader.Availability.UNAVAILABLE ->
                    FilledTonalButton(onClick = { start(reader.installIntent()) }) { Text(stringResource(R.string.health_get_health_connect)) }
            }
        }
        SwitchSetting(
            title = stringResource(R.string.health_show_watch_data_in_summaries),
            subtitle = stringResource(R.string.health_heart_rate_chart_steps_and_calories),
            checked = show,
            onCheckedChange = { value -> scope.launch { HealthDataReader.setShowWatchStats(context, value) } }
        )
    }
}
