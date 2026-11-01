package com.example.pokemonalertsv2.watch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The watch's one screen: the phone's running hunt or catch route, its live numbers, and the
 * buttons that matter while walking (Got it / Skip, Pause, End). Opening it during a session
 * starts heart-rate and step tracking.
 */
class MainActivity : ComponentActivity() {
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        maybeStartWorkout()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchApp(onTrack = ::requestAndTrack, onStopTracking = ::stopTracking) }
        lifecycleScope.launch {
            SessionStore.refresh(applicationContext)
            if (intent?.getBooleanExtra(EXTRA_AUTOSTART, false) == true) requestAndTrack()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTOSTART, false)) requestAndTrack()
    }

    private fun neededPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 36) add("android.permission.health.READ_HEART_RATE") else add(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun requestAndTrack() {
        val missing = neededPermissions().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) maybeStartWorkout() else permissionRequest.launch(missing.toTypedArray())
    }

    private fun maybeStartWorkout() {
        if (!SessionStore.state.value.active || WorkoutService.running) return
        // The health service type needs one of the sensor permissions granted.
        val sensors = neededPermissions().filterNot { it == Manifest.permission.POST_NOTIFICATIONS }
        if (sensors.none { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) return
        ContextCompat.startForegroundService(this, Intent(this, WorkoutService::class.java))
    }

    private fun stopTracking() {
        if (WorkoutService.running) startService(Intent(this, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))
    }

    companion object {
        const val EXTRA_AUTOSTART = "autostart"
    }
}

@Composable
private fun WatchApp(onTrack: () -> Unit, onStopTracking: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by SessionStore.state.collectAsStateWithLifecycle()
    val heartRate by SessionStore.heartRate.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tracking by remember { mutableStateOf(WorkoutService.running) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
            tracking = WorkoutService.running
        }
    }
    // A session already running when the screen opens is tracked straight away.
    LaunchedEffect(state.active) { if (state.active) onTrack() }
    val listState = rememberScalingLazyListState()
    fun send(command: WearProtocol.Command) = scope.launch { PhoneLink.send(context, command) }

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!state.active) {
                    item {
                        Text(
                            "No hunt or catch route running.\nStart one on your phone.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body2,
                            modifier = Modifier.padding(top = 24.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                    return@ScalingLazyColumn
                }
                item {
                    Text(
                        if (state.isHunt) "HUNT" else "CATCH ROUTE",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary
                    )
                }
                item {
                    Text(state.name, style = MaterialTheme.typography.title3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
                item {
                    Text(
                        formatElapsed(state.activeMillisAt(now)) + if (state.paused) " · paused" else "",
                        style = MaterialTheme.typography.display3,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric("${state.caught}", if (state.isHunt) "caught" else "visited")
                        Metric(formatDistance(state.distanceMeters), "walked")
                        heartRate?.let { Metric("$it", "bpm", Color(0xFFFF6B6B)) }
                    }
                }
                state.target?.let { target ->
                    item {
                        Text(target, style = MaterialTheme.typography.caption1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colors.secondary)
                    }
                }
                state.remaining?.let { remaining ->
                    item { Text("$remaining stops left", style = MaterialTheme.typography.caption2) }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (state.isHunt) {
                            Button(onClick = { send(WearProtocol.Command.CAUGHT) }, colors = ButtonDefaults.primaryButtonColors()) {
                                Text("Got it", style = MaterialTheme.typography.caption1)
                            }
                        } else {
                            Button(onClick = { send(WearProtocol.Command.SKIP) }, colors = ButtonDefaults.secondaryButtonColors()) {
                                Text("Skip", style = MaterialTheme.typography.caption1)
                            }
                        }
                        Button(
                            onClick = { send(if (state.paused) WearProtocol.Command.RESUME else WearProtocol.Command.PAUSE) },
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Text(if (state.paused) "Go" else "Pause", style = MaterialTheme.typography.caption1)
                        }
                    }
                }
                item {
                    Chip(
                        onClick = { if (tracking) onStopTracking() else onTrack() },
                        label = { Text(if (tracking) "Stop heart rate" else "Track heart rate") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Chip(
                        onClick = { send(WearProtocol.Command.STOP) },
                        label = { Text(if (state.isHunt) "End hunt" else "End route") },
                        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF8B1E1E)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color = Color.Unspecified) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.title3, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.caption3)
    }
}

internal fun formatElapsed(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

internal fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.US, "%.1f km", meters / 1000)
