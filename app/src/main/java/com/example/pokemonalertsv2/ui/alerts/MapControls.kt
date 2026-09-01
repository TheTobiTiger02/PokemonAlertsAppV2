@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.LruCache
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.tracking.rememberArrivalTrackingUiController
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatcher
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pokemonalertsv2.ui.theme.LocalAppDarkTheme
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.components.RollingNumberText
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.motion.appFadeIn
import com.example.pokemonalertsv2.ui.motion.appFadeOut
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerInfoWindowContent
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.OutlinedButton
import com.example.pokemonalertsv2.BuildConfig

@Composable
internal fun MapTopAppBar(
    visibleAlertCount: Int,
    showBackButton: Boolean,
    refreshing: Boolean,
    activeLayerCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEnterPictureInPicture: (() -> Unit)? = null,
    onOpenLayers: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(
                modifier = Modifier.weight(1f).padding(start = if (showBackButton) 2.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    AnimatedContent(
                        targetState = visibleAlertCount,
                        transitionSpec = { appFadeThrough() },
                        label = "map_alert_count"
                    ) { count ->
                        Text(
                            text = "$count",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                AnimatedRefreshIcon(
                    refreshing = refreshing,
                    contentDescription = stringResource(R.string.refresh_alerts)
                )
            }
            if (onEnterPictureInPicture != null) {
                IconButton(
                    onClick = onEnterPictureInPicture,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pip),
                        contentDescription = "Open map in picture-in-picture",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Box {
                IconButton(onClick = onOpenLayers, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_layers),
                        contentDescription = "Map layers",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                AnimatedContent(
                    targetState = activeLayerCount,
                    transitionSpec = { appFadeThrough() },
                    modifier = Modifier.align(Alignment.TopEnd),
                    label = "map_layer_count"
                ) { count ->
                    if (count > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Offline tile controls for the OpenStreetMap layer.
 *
 * Only shown for that layer: the Google maps do their own caching, which is not ours to
 * manage or to promise anything about.
 */
@Composable
internal fun OfflineTilesSection(centre: MapCameraSnapshot) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun refreshSize() {
        cacheBytes = OpenStreetMapTileCache.sizeBytes(context)
    }

    LaunchedEffect(Unit) { refreshSize() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Offline map", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "Tiles you have already looked at stay available without a connection. " +
                "Downloading an area also fetches the two zoom levels below it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = status ?: cacheBytes?.let { "Stored: " + formatCacheSize(it) } ?: "Stored: ...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val client = MapLibreInitializer.tileClient() ?: return@OutlinedButton
                    busy = true
                    status = "Downloading..."
                    scope.launch {
                        val stored = OpenStreetMapTileCache.prefetch(
                            client = client,
                            tileUrlTemplate = BuildConfig.OSM_TILE_URL,
                            latitude = centre.latitude,
                            longitude = centre.longitude,
                            zoom = centre.zoom.toInt(),
                            // A few hundred tiles takes tens of seconds, so say where it
                            // is up to rather than leaving a bare "Downloading...".
                            onProgress = { done, total ->
                                status = "Downloading " + done + " / " + total + "..."
                            }
                        )
                        refreshSize()
                        status = if (stored == 0) {
                            "Could not download tiles right now."
                        } else {
                            "Downloaded " + stored + " tiles."
                        }
                        busy = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Download this area") }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        OpenStreetMapTileCache.clear(context, MapLibreInitializer.tileClient())
                        refreshSize()
                        status = null
                        busy = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Clear") }
        }
    }
}

internal fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> (bytes / 1024).toString() + " KB"
    else -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
}

@Composable
internal fun MapLayersSheet(
    mapStyle: MapStylePreference,
    showTimeLabels: Boolean,
    showSpawnRadius: Boolean,
    spacialRendEnabled: Boolean,
    showDismissed: Boolean,
    mapCentre: MapCameraSnapshot,
    onDismiss: () -> Unit,
    onMapStyleChanged: (MapStylePreference) -> Unit,
    onToggleTimeLabels: () -> Unit,
    onToggleSpawnRadius: () -> Unit,
    onToggleSpacialRend: () -> Unit,
    onToggleDismissed: () -> Unit,
    autoEnterPictureInPicture: Boolean = false,
    onToggleAutoEnterPictureInPicture: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Map layers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Provider and style", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MapStylePreference.entries, key = { it.name }) { style ->
                    FilterChip(
                        selected = mapStyle == style,
                        onClick = { onMapStyleChanged(style) },
                        label = {
                            Text(
                                when (style) {
                                    MapStylePreference.GOOGLE_STANDARD -> "Google standard"
                                    MapStylePreference.GOOGLE_SATELLITE -> "Google satellite"
                                    MapStylePreference.OPENSTREETMAP -> "OpenStreetMap"
                                }
                            )
                        }
                    )
                }
            }
            if (mapStyle == MapStylePreference.OPENSTREETMAP) {
                OfflineTilesSection(centre = mapCentre)
            }
            FilterChip(
                selected = showTimeLabels,
                onClick = onToggleTimeLabels,
                label = { Text("Countdown labels") },
                leadingIcon = if (showTimeLabels) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
            FilterChip(
                selected = showSpawnRadius,
                onClick = onToggleSpawnRadius,
                label = { Text("Spawn radius") },
                leadingIcon = if (showSpawnRadius) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
            FilterChip(
                selected = spacialRendEnabled,
                enabled = showSpawnRadius,
                onClick = onToggleSpacialRend,
                label = { Text("Spacial Rend") },
                leadingIcon = if (spacialRendEnabled) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
            if (onToggleAutoEnterPictureInPicture != null) {
                FilterChip(
                    selected = autoEnterPictureInPicture,
                    onClick = onToggleAutoEnterPictureInPicture,
                    label = { Text(stringResource(R.string.map_pip_auto_enter)) },
                    leadingIcon = if (autoEnterPictureInPicture) {
                        { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                    } else null,
                    modifier = Modifier.testTag("map_auto_pip")
                )
            }
            Text("Alerts", style = MaterialTheme.typography.labelLarge)
            FilterChip(
                selected = showDismissed,
                onClick = onToggleDismissed,
                label = { Text("Show dismissed alerts") },
                leadingIcon = if (showDismissed) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null,
                modifier = Modifier.testTag("map_show_dismissed")
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun MapFilterRow(
    filters: Collection<AlertFilter>,
    selectedFilter: AlertFilter,
    @Suppress("UNUSED_PARAMETER") visibleAlertCount: Int,
    onFilterSelected: (AlertFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("map_filter_rail"),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters.toList(), key = { it.name }) { filter ->
            val selected = selectedFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label, maxLines = 1) },
                shape = RoundedCornerShape(50),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
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
    onRestoreAlert: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .padding(WindowInsets.statusBars.asPaddingValues())
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
    goDexAction: @Composable () -> Unit = {
        GoDexCaughtAction(alert = alert, matchResult = goDexStatus)
    }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSnoozeDialog by rememberSaveable(alert.uniqueId) { mutableStateOf(false) }
    val countdownClock = rememberCountdownClock()
    val visualStyle = resolveAlertVisualStyle(alert)
    val categoryAccent = Color(visualStyle.category.accentArgb)
    val formattedTitle = remember(alert, goDexStatus.status) {
        formatAlertTitle(alert, goDexStatus.status)
    }
    val questPresentation = remember(alert) { questAlertPresentation(alert) }

    Column(
        modifier = modifier.heightIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AlertImage(
                alert = alert,
                modifier = Modifier.size(88.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("map_alert_title_block"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (shouldShowAlertCategoryLabel(formattedTitle, visualStyle.label)) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = categoryAccent.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = visualStyle.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = categoryAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    GoDexStatusPill(goDexStatus)
                    if (isDismissed) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Dismissed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onGoing,
                enabled = isGoing || alert.isEligibleArrivalDestination(),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(if (isGoing) "Stop" else "I’m going")
            }
            Button(
                onClick = onOpenMaps,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Directions")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            goDexAction()
            FilledTonalButton(
                onClick = onOpenFullDetail,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("map_open_details")
            ) {
                Text("Open details", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AlertSecondaryActionsMenu(
                actions = listOf(
                    AlertSecondaryAction.SNOOZE,
                    AlertSecondaryAction.PICTURE_IN_PICTURE,
                    AlertSecondaryAction.SHARE,
                    if (isDismissed) AlertSecondaryAction.RESTORE else AlertSecondaryAction.DISMISS
                ),
                onAction = { action ->
                    when (action) {
                        AlertSecondaryAction.SNOOZE -> showSnoozeDialog = true
                        AlertSecondaryAction.PICTURE_IN_PICTURE -> {
                            openAlertInPictureInPicture(context, alert)
                        }
                        AlertSecondaryAction.SHARE -> onShare()
                        AlertSecondaryAction.DISMISS -> onDismissAlert()
                        AlertSecondaryAction.RESTORE -> onRestoreAlert()
                    }
                },
                contentDescription = "More map alert actions"
            )
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        val venue = alert.venueName
        val venueType = alert.venueTypeLabel
        val address = alert.pokemonLocation

        if (venue != null) {
            Text(
                text = if (venueType != null) "$venueType: $venue" else venue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!address.isNullOrBlank() && address != venue) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else alert.locationDisplay?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }


        questPresentation?.takeIf { it.task != null || it.reward != null }?.let { quest ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = categoryAccent.copy(alpha = 0.12f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quest.task?.let { task ->
                        Text(
                            text = "Task: $task",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    quest.reward?.let { reward ->
                        Text(
                            text = "Reward: $reward",
                            style = MaterialTheme.typography.bodyMedium,
                            color = categoryAccent
                        )
                    }
                    if (quest.requiresAr) {
                        Text(
                            text = "AR scan required",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            val distanceText = distanceInfo?.distanceText
            val walkingText = distanceInfo?.walkingText
            Text(
                text = when {
                    distanceText != null && walkingText != null -> {
                        "$distanceText · ${stringResource(R.string.map_estimated_walk, walkingText)}"
                    }
                    distanceText != null -> distanceText
                    else -> stringResource(R.string.map_distance_unavailable)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }

        if (isGoing) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Arrival tracking active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        alert.displayCp?.let { cp ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = categoryAccent.copy(alpha = 0.18f)
            ) {
                Text(
                    text = "CP $cp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryAccent,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        MapAlertCountdown(
            endTime = alert.endTime,
            categoryAccent = categoryAccent,
            countdownClock = countdownClock
        )

        }
    }

    if (showSnoozeDialog) {
        SnoozeDurationDialog(
            defaultMinutes = 10,
            onDismiss = { showSnoozeDialog = false },
            onConfirm = { minutes ->
                showSnoozeDialog = false
                scope.launch { snoozeAlertFromUi(context, alert, minutes) }
            }
        )
    }
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
