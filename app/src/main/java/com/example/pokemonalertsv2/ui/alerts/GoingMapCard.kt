package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.hunt.HuntPath
import com.example.pokemonalertsv2.hunt.HuntPathCache
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.launch

/**
 * The "I'm going" journey on the alert's own screen: a switch, and when it is on a small map
 * with the trainer (an arrow pointing where the phone faces), the target and the walking path.
 *
 * The switch is the one the live notification's Show map / Hide map flips, so both agree. The
 * map is not pannable: inside a scrolling screen a drag means scroll. It keeps both points in
 * view on its own, and the button re-frames it.
 */
@Composable
internal fun GoingMapCard(alert: PokemonAlert, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { ArrivalTrackingRepository.getInstance(context) }
    val showMap by repository.showGoingMap.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertPill(
                text = "Arrival tracking active",
                icon = Icons.Filled.LocationOn,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            FilterChip(
                selected = showMap,
                onClick = { scope.launch { repository.setShowGoingMap(!showMap) } },
                label = { Text("Map") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
                modifier = Modifier.testTag("going_map_toggle")
            )
        }
        if (showMap) GoingMap(alert)
    }
}

@Composable
private fun GoingMap(alert: PokemonAlert) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val target = alert.mapCoordinatesOrNull() ?: return
    val controller = remember { OpenStreetMapController() }
    var pose by remember { mutableStateOf<MapUserPose?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var framedFrom by remember { mutableStateOf<android.location.Location?>(null) }

    // The same pose source as the map tab: fused location plus the rotation-vector compass,
    // only while this screen is started.
    DisposableEffect(lifecycleOwner, context) {
        val tracker = DefaultMapPoseTrackerFactory(context, { pose = it }, { })
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> tracker.start()
                Lifecycle.Event.ON_STOP -> tracker.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }

    // The service keeps this journey's path fresh while the map is on; draw it only when it
    // runs to this target, not a hunt's or an earlier journey's.
    val publishedPath by remember { HuntPathCache.getInstance().path }.collectAsStateWithLifecycle()
    val path = (publishedPath as? HuntPath.Resolved)?.takeIf { it.stopIds == listOf(alert.uniqueId) } ?: HuntPath.None

    val paddingPx = with(density) { 48.dp.toPx().toInt() }
    fun frame() {
        val location = pose?.location
        val points = listOfNotNull(
            location?.let { AlertMapCoordinates(it.latitude, it.longitude) },
            target
        )
        controller.fitAlerts(points, paddingPx)
        framedFrom = location
    }
    // Re-frame as the trainer walks, but not on every fix: only once they moved a little.
    LaunchedEffect(mapLoaded, pose?.location?.elapsedRealtimeNanos) {
        if (!mapLoaded) return@LaunchedEffect
        val location = pose?.location
        val last = framedFrom
        val moved = if (location == null || last == null) Float.MAX_VALUE else WalkingRouteUtils
            .straightLineDistanceMeters(last.latitude, last.longitude, location.latitude, location.longitude)
            ?: Float.MAX_VALUE
        if (last == null || moved >= GOING_MAP_REFRAME_METERS) frame()
    }

    val countdownClock = rememberCountdownClock()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .testTag("going_map")
    ) {
        OpenStreetMapView(
            modifier = Modifier.fillMaxSize(),
            alerts = listOf(alert),
            markerItems = remember(alert) { listOf(MapMarkerItem.Alert(alert, target.latitude, target.longitude)) },
            countdownTickMillis = 1_000L,
            minutePrecisionCountdown = false,
            userPose = pose,
            cameraSnapshot = remember(target) { MapCameraSnapshot(target.latitude, target.longitude, 16.0) },
            contentInsets = MapContentInsets(0, 0, 0, 0),
            showTimeLabels = true,
            countdownClock = countdownClock,
            goDexMatches = emptyMap(),
            controller = controller,
            onMapLoaded = { mapLoaded = true },
            onLoadError = { },
            onAlertClick = { },
            onCameraChanged = { },
            onUserGesture = { },
            huntPath = path,
            interactive = false,
            emphasizedAlertIds = remember(alert) { setOf(alert.uniqueId) }
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = { frame() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
        ) {
            Icon(painterResource(R.drawable.ic_fit_map), contentDescription = "Show me and the target")
        }
    }
}

private const val GOING_MAP_REFRAME_METERS = 15f
