@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.TimeUtils
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerInfoWindowContent
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AlertsMapRoute(viewModel: PokemonAlertsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh while the map is visible without duplicating the main feed cadence.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshAlertsInBackground()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    AlertsMapScreen(
        alerts = uiState.alerts,
        onBack = onBack,
        onRefresh = viewModel::refreshAlerts
    )
}

@Composable
fun AlertsMapScreen(
    alerts: List<PokemonAlert>,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    val defaultLatLng = remember { LatLng(0.0, 0.0) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 2f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var selectedAlert by remember { mutableStateOf<PokemonAlert?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var mapType by rememberSaveable { mutableStateOf(MapType.NORMAL) }
    var showTimeLabels by rememberSaveable { mutableStateOf(false) }
    var initialCameraPositioned by rememberSaveable { mutableStateOf(false) }

    fun hasLocationPermissionNow(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var hasLocationPermission by remember { mutableStateOf(hasLocationPermissionNow()) }

    fun centerOnUserLocation() {
        scope.launch {
            val location = runCatching {
                CachedLocationProvider.get(
                    context = context,
                    timeoutMs = 5_000,
                    highAccuracy = true
                )
            }.getOrNull()

            if (location == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.map_current_location_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    16f
                ),
                1_000
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasLocationPermissionNow()

        if (hasLocationPermission) {
            centerOnUserLocation()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.map_location_permission_needed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // A live timer is only needed while a time label or the selected-alert panel is visible.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showTimeLabels, selectedAlert?.uniqueId) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(if (showTimeLabels || selectedAlert != null) 1_000 else 30_000)
        }
    }

    val filteredAlerts = remember(alerts, selectedFilter, now) {
        val activeAlerts = alerts.filter {
            (TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE) > now
        }
        when (selectedFilter) {
            AlertFilter.ALL -> activeAlerts
            AlertFilter.RAIDS -> activeAlerts.filter { it.hasType("Raid") }
            AlertFilter.QUESTS -> activeAlerts.filter { it.hasType("Quest") }
            AlertFilter.RARES -> activeAlerts.filter { it.hasType("Rare") || it.hasType("Spawn") }
            AlertFilter.HUNDOS -> activeAlerts.filter { it.hasType("Hundo") }
            AlertFilter.PVP -> activeAlerts.filter { it.hasType("PvP") }
            AlertFilter.NUNDOS -> activeAlerts.filter { it.hasType("Nundo") }
            AlertFilter.KECLEON -> activeAlerts.filter { it.hasType("Kecleon") }
            AlertFilter.ROCKET -> activeAlerts.filter { it.hasType("Rocket") }
            AlertFilter.WEATHER_CHANGE -> activeAlerts.filter { it.hasType("WeatherChange") }
        }
    }

    val availableFilters = remember(alerts) {
        buildSet {
            add(AlertFilter.ALL)
            if (alerts.any { it.hasType("Raid") }) add(AlertFilter.RAIDS)
            if (alerts.any { it.hasType("Quest") }) add(AlertFilter.QUESTS)
            if (alerts.any { it.hasType("Rare") || it.hasType("Spawn") }) add(AlertFilter.RARES)
            if (alerts.any { it.hasType("Hundo") }) add(AlertFilter.HUNDOS)
            if (alerts.any { it.hasType("PvP") }) add(AlertFilter.PVP)
            if (alerts.any { it.hasType("Nundo") }) add(AlertFilter.NUNDOS)
            if (alerts.any { it.hasType("Kecleon") }) add(AlertFilter.KECLEON)
            if (alerts.any { it.hasType("Rocket") }) add(AlertFilter.ROCKET)
            if (alerts.any { it.hasType("WeatherChange") }) add(AlertFilter.WEATHER_CHANGE)
        }
    }

    LaunchedEffect(availableFilters) {
        if (selectedFilter !in availableFilters) selectedFilter = AlertFilter.ALL
    }

    val mapUiSettings = remember(hasLocationPermission) {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = true,
            tiltGesturesEnabled = true
        )
    }

    val mapProperties = remember(hasLocationPermission, mapType, darkTheme) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapType = mapType,
            minZoomPreference = 3f,
            maxZoomPreference = 20f,
            mapStyleOptions = if (mapType == MapType.NORMAL) {
                MapStyleOptions(if (darkTheme) DarkMapStyle else LightMapStyle)
            } else {
                null
            }
        )
    }

    LaunchedEffect(mapLoaded, filteredAlerts) {
        if (mapLoaded && !initialCameraPositioned) {
            buildAlertsCameraUpdate(filteredAlerts)?.let { update ->
                runCatching { cameraPositionState.move(update) }
                    .onSuccess { initialCameraPositioned = true }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useSidePanel = maxWidth >= 840.dp
        val controlsEndPadding = if (useSidePanel) 392.dp else 16.dp
        val mapContentPadding = PaddingValues(
            top = 120.dp,
            end = if (useSidePanel) 392.dp else 16.dp,
            bottom = if (useSidePanel) 24.dp else 96.dp
        )

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = mapContentPadding,
            onMapLoaded = { mapLoaded = true },
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            filteredAlerts.forEach { alert ->
                key(alert.uniqueId) {
                    MapMarker(
                        alert = alert,
                        now = now,
                        density = density,
                        showTimeLabel = showTimeLabels,
                        onClick = { selectedAlert = alert }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(top = 8.dp, start = 16.dp, end = controlsEndPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapTopAppBar(
                visibleAlertCount = filteredAlerts.size,
                showTimeLabels = showTimeLabels,
                hybridMap = mapType == MapType.HYBRID,
                onBack = onBack,
                onToggleTimeLabels = { showTimeLabels = !showTimeLabels },
                onRefresh = onRefresh,
                onToggleMapType = {
                    mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                }
            )

            MapFilterRow(
                filters = AlertFilter.values().filter { it in availableFilters },
                selectedFilter = selectedFilter,
                visibleAlertCount = filteredAlerts.size,
                onFilterSelected = { selectedFilter = it }
            )
        }

        if (mapLoaded && (useSidePanel || selectedAlert == null)) {
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermissionNow()) {
                        hasLocationPermission = true
                        centerOnUserLocation()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (useSidePanel) 392.dp else 16.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_my_location),
                    contentDescription = "Center on current location"
                )
            }
        }

        selectedAlert?.let { alert ->
            if (useSidePanel) {
                MapAlertSidePanel(
                    alert = alert,
                    onDismiss = { selectedAlert = null },
                    onOpenMaps = { openMapForAlert(context, alert) },
                    onShare = { scope.launch { AlertShareCard.share(context, alert) } },
                    onOpenFullDetail = {
                        context.startActivity(AlertDetailActivity.createIntent(context, alert))
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            } else {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { selectedAlert = null },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    MapAlertDetailContent(
                        alert = alert,
                        onDismiss = { selectedAlert = null },
                        onOpenMaps = { openMapForAlert(context, alert) },
                        onShare = { scope.launch { AlertShareCard.share(context, alert) } },
                        onOpenFullDetail = {
                            context.startActivity(AlertDetailActivity.createIntent(context, alert))
                        },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapTopAppBar(
    visibleAlertCount: Int,
    showTimeLabels: Boolean,
    hybridMap: Boolean,
    onBack: () -> Unit,
    onToggleTimeLabels: () -> Unit,
    onRefresh: () -> Unit,
    onToggleMapType: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.map_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$visibleAlertCount visible",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            actions = {
                IconButton(onClick = onToggleTimeLabels) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_timer),
                        contentDescription = "Toggle countdown labels",
                        tint = if (showTimeLabels) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh_alerts)
                    )
                }
                IconButton(onClick = onToggleMapType) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_layers),
                        contentDescription = if (hybridMap) "Switch to standard map" else "Switch to satellite map",
                        tint = if (hybridMap) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun MapFilterRow(
    filters: List<AlertFilter>,
    selectedFilter: AlertFilter,
    visibleAlertCount: Int,
    onFilterSelected: (AlertFilter) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filters, key = { it.name }) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        item(key = "map-alert-count") {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = "$visibleAlertCount alerts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MapAlertSidePanel(
    alert: PokemonAlert,
    onDismiss: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(top = 12.dp, end = 16.dp)
            .width(360.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        MapAlertDetailContent(
            alert = alert,
            onDismiss = onDismiss,
            onOpenMaps = onOpenMaps,
            onShare = onShare,
            onOpenFullDetail = onOpenFullDetail,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun MapAlertDetailContent(
    alert: PokemonAlert,
    onDismiss: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit,
    onOpenFullDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTime by remember(alert.uniqueId) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alert.uniqueId) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }

    val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
    val remaining = endMillis?.let { it - currentTime } ?: 0L
    val visualStyle = resolveAlertVisualStyle(alert)
    val isExpired = remaining <= 0L

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formatAlertTitle(alert),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${visualStyle.shortCode} · ${visualStyle.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        alert.locationDisplay?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isExpired) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Text(
                text = if (isExpired) {
                    "Expired"
                } else {
                    "Ends in ${TimeUtils.formatDurationShort(remaining)}"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (isExpired) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onOpenMaps,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Directions")
            }
            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Share")
            }
        }

        FilledTonalButton(
            onClick = onOpenFullDetail,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Open full detail")
        }
    }
}

private fun buildAlertsCameraUpdate(alerts: List<PokemonAlert>): CameraUpdate? {
    val positions = alerts.mapNotNull { alert ->
        val latitude = alert.latitude
        val longitude = alert.longitude
        if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
    }

    return when (positions.size) {
        0 -> null
        1 -> CameraUpdateFactory.newLatLngZoom(positions.first(), 14f)
        else -> {
            val bounds = LatLngBounds.Builder().also { builder ->
                positions.forEach(builder::include)
            }.build()
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                bounds.northeast.latitude,
                bounds.northeast.longitude,
                bounds.southwest.latitude,
                bounds.southwest.longitude,
                results
            )
            if (results[0] < 2_000) {
                CameraUpdateFactory.newLatLngZoom(bounds.center, 14f)
            } else {
                CameraUpdateFactory.newLatLngBounds(bounds, 300)
            }
        }
    }
}

@Composable
private fun MapMarker(
    alert: PokemonAlert,
    now: Long,
    density: androidx.compose.ui.unit.Density,
    showTimeLabel: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val position = remember(alert.latitude, alert.longitude) {
        LatLng(alert.latitude ?: 0.0, alert.longitude ?: 0.0)
    }
    val visualStyle = resolveAlertVisualStyle(alert)
    val speciesName = alert.pokemon?.takeIf { it.isNotBlank() } ?: alert.cleanPokemonName
    val speciesImageUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: alert.imageUrl?.takeIf { it.isNotBlank() }
    val timeRemainingMs = remember(now, alert.endTime) {
        (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE) - now
    }
    val timeLabel = remember(timeRemainingMs) {
        if (timeRemainingMs <= 0L) "Expired" else TimeUtils.formatDurationShort(timeRemainingMs)
    }
    val markerSizePx = remember(density) { with(density) { 68.dp.toPx().toInt() } }

    val colors = MaterialTheme.colorScheme
    val palette = remember(
        colors.primary,
        colors.onPrimary,
        colors.surface,
        colors.onSurface,
        colors.outline,
        colors.error,
        colors.onError
    ) {
        MapMarkerPalette(
            primary = colors.primary.toArgb(),
            onPrimary = colors.onPrimary.toArgb(),
            surface = colors.surface.toArgb(),
            onSurface = colors.onSurface.toArgb(),
            outline = colors.outline.toArgb(),
            error = colors.error.toArgb(),
            onError = colors.onError.toArgb()
        )
    }
    var markerIcon by remember(alert.uniqueId) { mutableStateOf<MapMarkerIcon?>(null) }

    LaunchedEffect(
        alert.uniqueId,
        visualStyle.shortCode,
        speciesName,
        speciesImageUrl,
        showTimeLabel,
        timeLabel,
        markerSizePx,
        palette
    ) {
        markerIcon = withContext(Dispatchers.IO) {
            createMapMarkerIcon(
                context = context,
                sizePx = markerSizePx,
                categoryCode = visualStyle.shortCode,
                speciesName = speciesName,
                speciesImageUrl = speciesImageUrl,
                endTime = alert.endTime,
                showTimeLabel = showTimeLabel,
                timeLabel = if (showTimeLabel) timeLabel else null,
                palette = palette
            )
        }
    }

    MarkerInfoWindowContent(
        state = remember(position) { MarkerState(position = position) },
        icon = markerIcon?.descriptor,
        anchor = markerIcon?.anchor ?: Offset(0.5f, 1f),
        title = formatAlertTitle(alert),
        visible = true,
        onClick = {
            onClick()
            true
        }
    ) {
        // The map uses the Material bottom sheet/side panel instead of an info window.
    }
}

private data class MapMarkerPalette(
    val primary: Int,
    val onPrimary: Int,
    val surface: Int,
    val onSurface: Int,
    val outline: Int,
    val error: Int,
    val onError: Int
)

private data class MapMarkerIcon(
    val descriptor: BitmapDescriptor,
    val anchor: Offset
)

private val markerIconCache = LruCache<String, MapMarkerIcon>(256)

private suspend fun createMapMarkerIcon(
    context: android.content.Context,
    sizePx: Int,
    categoryCode: String,
    speciesName: String,
    speciesImageUrl: String?,
    endTime: String?,
    showTimeLabel: Boolean,
    timeLabel: String?,
    palette: MapMarkerPalette
): MapMarkerIcon? {
    try {
        val timeRemainingMs = endTime?.let(TimeUtils::parseEndTimeToMillis)?.let {
            it - System.currentTimeMillis()
        } ?: Long.MAX_VALUE
        val isUrgent = timeRemainingMs < 10 * 60 * 1_000
        val cacheKey = listOf(
            "material3-marker",
            sizePx,
            categoryCode,
            speciesName,
            speciesImageUrl.orEmpty(),
            showTimeLabel,
            timeLabel.orEmpty(),
            isUrgent,
            palette
        ).joinToString("|")
        markerIconCache.get(cacheKey)?.let { return it }

        val speciesDrawable = speciesImageUrl?.let { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(sizePx, sizePx)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            (PokemonAlertsApplication.imageLoader(context).execute(request) as? SuccessResult)?.drawable
        }

        val padding = (sizePx * 0.14f).toInt()
        val pinRadius = sizePx * 0.33f
        val tailHeight = sizePx * 0.20f
        val labelGap = (sizePx * 0.07f).toInt()
        val categoryHeight = (sizePx * 0.25f).toInt().coerceAtLeast(16)
        val timeHeight = if (showTimeLabel && timeLabel != null) {
            (sizePx * 0.26f).toInt().coerceAtLeast(16)
        } else {
            0
        }
        val totalWidth = sizePx + padding * 2
        val totalHeight = (
            padding + pinRadius * 2 + tailHeight + labelGap + categoryHeight +
                (if (timeHeight > 0) labelGap + timeHeight else 0) + padding
            ).toInt()
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = totalWidth / 2f
        val centerY = padding + pinRadius
        val pinTipY = centerY + pinRadius + tailHeight
        val tailHalfWidth = pinRadius * 0.46f
        val tailPath = android.graphics.Path().apply {
            moveTo(centerX - tailHalfWidth, centerY + pinRadius * 0.56f)
            lineTo(centerX + tailHalfWidth, centerY + pinRadius * 0.56f)
            lineTo(centerX, pinTipY)
            close()
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            alpha = 48
        }
        canvas.save()
        canvas.translate(0f, sizePx * 0.035f)
        canvas.drawPath(tailPath, shadowPaint)
        canvas.drawCircle(centerX, centerY, pinRadius, shadowPaint)
        canvas.restore()

        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
        }
        canvas.drawPath(tailPath, primaryPaint)
        canvas.drawCircle(centerX, centerY, pinRadius, primaryPaint)

        val keylinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surface
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.035f
        }
        canvas.drawCircle(centerX, centerY, pinRadius - sizePx * 0.018f, keylinePaint)

        val innerRadius = pinRadius * 0.71f
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface }
        canvas.drawCircle(centerX, centerY, innerRadius, innerPaint)

        if (speciesDrawable != null) {
            val spritePath = android.graphics.Path().apply {
                addCircle(centerX, centerY, innerRadius, android.graphics.Path.Direction.CW)
            }
            val checkpoint = canvas.save()
            canvas.clipPath(spritePath)
            val intrinsicWidth = speciesDrawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
            val intrinsicHeight = speciesDrawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
            val availableSize = innerRadius * 2.45f
            val scale = minOf(
                availableSize / intrinsicWidth.toFloat(),
                availableSize / intrinsicHeight.toFloat()
            )
            val drawWidth = intrinsicWidth * scale
            val drawHeight = intrinsicHeight * scale
            val left = (centerX - drawWidth / 2f).toInt()
            val top = (centerY - drawHeight / 2f).toInt()
            speciesDrawable.setBounds(left, top, (left + drawWidth).toInt(), (top + drawHeight).toInt())
            speciesDrawable.draw(canvas)
            canvas.restoreToCount(checkpoint)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.onSurface
                textSize = innerRadius * 0.68f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val fallback = speciesName
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
                .ifBlank { categoryCode }
            val textY = centerY - (fallbackPaint.descent() + fallbackPaint.ascent()) / 2f
            canvas.drawText(fallback, centerX, textY, fallbackPaint)
        }

        if (isUrgent) {
            val dotRadius = sizePx * 0.075f
            val dotX = centerX + pinRadius * 0.70f
            val dotY = centerY - pinRadius * 0.66f
            canvas.drawCircle(dotX, dotY, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.error })
            canvas.drawCircle(
                dotX,
                dotY,
                dotRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.surface
                    style = Paint.Style.STROKE
                    strokeWidth = sizePx * 0.022f
                }
            )
        }

        val categoryTop = pinTipY + labelGap
        canvas.drawMarkerLabel(
            centerX = centerX,
            top = categoryTop,
            height = categoryHeight.toFloat(),
            text = categoryCode,
            background = palette.surface,
            foreground = palette.primary,
            outline = palette.primary,
            maxWidth = totalWidth - padding * 2f
        )

        if (timeHeight > 0 && timeLabel != null) {
            canvas.drawMarkerLabel(
                centerX = centerX,
                top = categoryTop + categoryHeight + labelGap,
                height = timeHeight.toFloat(),
                text = timeLabel,
                background = if (isUrgent) palette.error else palette.surface,
                foreground = if (isUrgent) palette.onError else palette.onSurface,
                outline = if (isUrgent) palette.error else palette.outline,
                maxWidth = totalWidth - padding * 2f
            )
        }

        val icon = MapMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchor = Offset(0.5f, (pinTipY / totalHeight).coerceIn(0f, 1f))
        )
        markerIconCache.put(cacheKey, icon)
        return icon
    } catch (exception: kotlinx.coroutines.CancellationException) {
        throw exception
    } catch (_: Throwable) {
        return null
    }
}

private fun Canvas.drawMarkerLabel(
    centerX: Float,
    top: Float,
    height: Float,
    text: String,
    background: Int,
    foreground: Int,
    outline: Int,
    maxWidth: Float
) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground
        textSize = height * 0.57f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val horizontalPadding = height * 0.55f
    val width = (textPaint.measureText(text) + horizontalPadding * 2)
        .coerceAtLeast(height * 1.75f)
        .coerceAtMost(maxWidth)
    val rect = android.graphics.RectF(
        centerX - width / 2f,
        top,
        centerX + width / 2f,
        top + height
    )
    val cornerRadius = height / 2f
    drawRoundRect(
        rect,
        cornerRadius,
        cornerRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
    )
    drawRoundRect(
        rect,
        cornerRadius,
        cornerRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outline
            style = Paint.Style.STROKE
            strokeWidth = height * 0.07f
        }
    )
    val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    drawText(text, centerX, textY, textPaint)
}

private const val LightMapStyle = """
[
  {"elementType":"geometry","stylers":[{"color":"#F7F9FB"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#424754"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#F7F9FB"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#EFF2F4"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#E3E9E4"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#FFFFFF"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#DCE6FF"}]},
  {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#E7EAED"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#DCE8FA"}]}
]
"""

private const val DarkMapStyle = """
[
  {"elementType":"geometry","stylers":[{"color":"#10131F"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#C2C6D6"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#10131F"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#5F6473"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#171B28"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#14291F"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#1B2433"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#263247"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#344B73"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0D1B2D"}]}
]
"""
