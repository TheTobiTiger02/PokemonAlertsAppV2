@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pokemonalertsv2.ui.components.RollingNumberText
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.BuildConfig

/**
 * Offline tile controls for the OpenStreetMap layer: what is stored, and two actions.
 *
 * Only shown for that layer: the Google maps do their own caching, which is not ours to
 * manage or to promise anything about.
 */
@Composable
internal fun OfflineTilesSection(centre: MapCameraSnapshot, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun refreshSize() {
        cacheBytes = OpenStreetMapTileCache.sizeBytes(context)
    }

    LaunchedEffect(Unit) { refreshSize() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = "Offline map · " + (status ?: cacheBytes?.let { formatCacheSize(it) + " saved" } ?: "…"),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Areas you have viewed work without a connection. Saving an area also " +
                    "fetches closer zoom levels.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val client = MapLibreInitializer.tileClient() ?: return@TextButton
                        busy = true
                        status = "saving…"
                        scope.launch {
                            val stored = OpenStreetMapTileCache.prefetch(
                                client = client,
                                tileUrlTemplate = BuildConfig.OSM_TILE_URL,
                                latitude = centre.latitude,
                                longitude = centre.longitude,
                                zoom = centre.zoom.toInt(),
                                // A few hundred tiles takes tens of seconds, so say where it
                                // is up to rather than leaving a bare "saving".
                                onProgress = { done, total -> status = "saving $done / $total…" }
                            )
                            refreshSize()
                            status = if (stored == 0) "could not save right now" else null
                            busy = false
                        }
                    }
                ) { Text("Save this area") }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            OpenStreetMapTileCache.clear(context, MapLibreInitializer.tileClient())
                            refreshSize()
                            status = null
                            busy = false
                        }
                    }
                ) { Text("Clear") }
            }
        }
    }
}

internal fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> (bytes / 1024).toString() + " KB"
    else -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
}

@Composable
internal fun MapAlertSidePanel(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult,
    distanceInfo: AlertDistanceInfo?,
    onDismiss: () -> Unit,
    isGoing: Boolean,
    onGoing: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier,
    isDismissed: Boolean = false,
    onDismissAlert: () -> Unit = {},
    onRestoreAlert: () -> Unit = {},
    companions: List<PokemonAlert> = emptyList(),
    onOpenCompanion: (PokemonAlert) -> Unit = {}
) {
    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 12.dp, end = 16.dp)
            .width(360.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 0.dp
    ) {
        MapAlertDetailContent(
            alert = alert,
            goDexStatus = goDexStatus,
            distanceInfo = distanceInfo,
            onDismiss = onDismiss,
            isGoing = isGoing,
            onGoing = onGoing,
            onOpenMaps = onOpenMaps,
            onShare = onShare,
            onOpenFullDetail = onOpenFullDetail,
            isDismissed = isDismissed,
            onDismissAlert = onDismissAlert,
            onRestoreAlert = onRestoreAlert,
            companions = companions,
            onOpenCompanion = onOpenCompanion,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
internal fun MapAlertDetailContent(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult = GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED),
    distanceInfo: AlertDistanceInfo?,
    onDismiss: () -> Unit,
    isGoing: Boolean,
    onGoing: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissed: Boolean = false,
    onDismissAlert: () -> Unit = {},
    onRestoreAlert: () -> Unit = {},
    companions: List<PokemonAlert> = emptyList(),
    onOpenCompanion: (PokemonAlert) -> Unit = {},
    goDexAction: @Composable () -> Unit = {
        GoDexCaughtAction(alert = alert, matchResult = goDexStatus)
    },
    /** Only the redesigned sheet honours this: a bottom sheet closes by dragging instead. */
    showCloseButton: Boolean = true
) {
    MapAlertSheetContent(
        alert = alert,
        goDexStatus = goDexStatus,
        distanceInfo = distanceInfo,
        onDismiss = onDismiss,
        isGoing = isGoing,
        onGoing = onGoing,
        onOpenMaps = onOpenMaps,
        onShare = onShare,
        onOpenFullDetail = onOpenFullDetail,
        modifier = modifier,
        isDismissed = isDismissed,
        onDismissAlert = onDismissAlert,
        onRestoreAlert = onRestoreAlert,
        companions = companions,
        onOpenCompanion = onOpenCompanion,
        showCloseButton = showCloseButton
    )

}

@Composable
internal fun MapAlertCountdown(
    endTime: String,
    categoryAccent: Color,
    countdownClock: State<Long>
) {
    val endMillis = remember(endTime) { TimeUtils.parseEndTimeToMillis(endTime) }
    val remaining = endMillis?.minus(countdownClock.value) ?: 0L
    val isExpired = remaining <= 0L
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isExpired) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            categoryAccent.copy(alpha = 0.18f)
        }
    ) {
        RollingNumberText(
            text = if (isExpired) "Expired" else "Ends in ${TimeUtils.formatDurationShort(remaining)}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = if (isExpired) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                categoryAccent
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
internal fun MapCountdownText(endTime: String, countdownClock: State<Long>) {
    Text(mapCountdownLabel(endTime, countdownClock.value))
}

internal data class AlertMapCoordinates(
    val latitude: Double,
    val longitude: Double
)

internal enum class MapDisplaySource {
    GOOGLE,
    OPENSTREETMAP
}

internal data class MapViewportTarget(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float
)

internal fun validMapCoordinates(
    latitude: Double?,
    longitude: Double?
): AlertMapCoordinates? {
    if (latitude == null || longitude == null) return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    if (latitude == 0.0 && longitude == 0.0) return null
    return AlertMapCoordinates(latitude, longitude)
}

internal fun PokemonAlert.mapCoordinatesOrNull(): AlertMapCoordinates? =
    validMapCoordinates(latitude, longitude)

internal fun resolveFitAllCoordinates(alerts: List<PokemonAlert>): List<AlertMapCoordinates> =
    alerts.mapNotNull(PokemonAlert::mapCoordinatesOrNull)

internal fun resolveMapAlertDistanceInfo(
    userLocation: android.location.Location?,
    alert: PokemonAlert,
    routeInfo: WalkingRouteInfo? = null
): AlertDistanceInfo? = resolveMapAlertDistanceInfo(
    userLatitude = userLocation?.latitude,
    userLongitude = userLocation?.longitude,
    alert = alert,
    routeInfo = routeInfo
)

internal fun resolveMapAlertDistanceInfo(
    userLatitude: Double?,
    userLongitude: Double?,
    alert: PokemonAlert,
    routeInfo: WalkingRouteInfo? = null,
    distanceBetween: (AlertMapCoordinates, AlertMapCoordinates) -> Float? = { origin, destination ->
        val results = FloatArray(1)
        runCatching {
            android.location.Location.distanceBetween(
                origin.latitude,
                origin.longitude,
                destination.latitude,
                destination.longitude,
                results
            )
            results[0]
        }.getOrNull()
    }
): AlertDistanceInfo? {
    val origin = validMapCoordinates(userLatitude, userLongitude) ?: return null
    val destination = alert.mapCoordinatesOrNull() ?: return null
    val straightLineDistance = distanceBetween(origin, destination)
        ?.takeUnless { it.isNaN() || it.isInfinite() || it < 0f }
        ?: return null
    val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(straightLineDistance, routeInfo)
    return AlertDistanceInfo(
        distanceMeters = displayInfo.effectiveDistanceMeters,
        distanceText = displayInfo.distanceText,
        walkingText = displayInfo.walkingText,
        straightLineDistanceMeters = displayInfo.straightLineDistanceMeters,
        routedWalkingDistanceMeters = displayInfo.routedDistanceMeters,
        walkingDurationSeconds = displayInfo.walkingDurationSeconds,
        source = displayInfo.source
    )
}

internal fun resolveInitialMapViewport(
    userLatitude: Double?,
    userLongitude: Double?,
    alerts: List<PokemonAlert>
): MapViewportTarget {
    validMapCoordinates(userLatitude, userLongitude)?.let { location ->
        return MapViewportTarget(location.latitude, location.longitude, USER_LOCATION_ZOOM)
    }

    alerts.firstNotNullOfOrNull(PokemonAlert::mapCoordinatesOrNull)?.let { alertLocation ->
        return MapViewportTarget(alertLocation.latitude, alertLocation.longitude, ALERT_LOCATION_ZOOM)
    }

    return MapViewportTarget(ALSBACH_LATITUDE, ALSBACH_LONGITUDE, ALSBACH_ZOOM)
}
