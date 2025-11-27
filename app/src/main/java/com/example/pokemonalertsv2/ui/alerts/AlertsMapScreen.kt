@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import com.example.pokemonalertsv2.util.TimeUtils
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsMapRoute(viewModel: PokemonAlertsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Periodic refresh logic
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshAlerts()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }
    
    AlertsMapScreen(
        alerts = uiState.alerts,
        onBack = onBack,
        onRefresh = { viewModel.refreshAlerts() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsMapScreen(
    alerts: List<PokemonAlert>,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    // State
    val defaultLatLng = remember { LatLng(0.0, 0.0) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 2f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var selectedAlert by remember { mutableStateOf<PokemonAlert?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var mapType by rememberSaveable { mutableStateOf(MapType.NORMAL) }

    // Permissions
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Time for expiration checks
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(5000) // Check every 5s
        }
    }

    // Filtering Logic
    val filteredAlerts = remember(alerts, selectedFilter, now) {
        // 1. Filter by time (active only)
        val active = alerts.filter {
            val end = TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
            end > now
        }
        // 2. Filter by type
        when (selectedFilter) {
            AlertFilter.ALL -> active
            AlertFilter.RAIDS -> active.filter { it.type?.equals("Raid", ignoreCase = true) == true }
            AlertFilter.QUESTS -> active.filter { it.type?.equals("Quest", ignoreCase = true) == true }
            AlertFilter.SPAWNS -> active.filter { 
                val t = it.type
                t?.equals("Rare", ignoreCase = true) == true || t?.equals("Spawn", ignoreCase = true) == true 
            }
            AlertFilter.HUNDOS -> active.filter { it.type?.equals("Hundo", ignoreCase = true) == true }
            AlertFilter.PVP -> active.filter { it.type?.equals("PvP", ignoreCase = true) == true }
            AlertFilter.NUNDOS -> active.filter { it.type?.equals("Nundo", ignoreCase = true) == true }
            AlertFilter.KECLEON -> active.filter { it.type?.equals("Kecleon", ignoreCase = true) == true }
            AlertFilter.ROCKET -> active.filter { it.type?.equals("Rocket", ignoreCase = true) == true }
        }
    }
    
    // Determine available filters for UI
    val availableFilters = remember(alerts) {
        val set = mutableSetOf(AlertFilter.ALL)
        if (alerts.any { it.type?.equals("Raid", ignoreCase = true) == true }) set.add(AlertFilter.RAIDS)
        if (alerts.any { it.type?.equals("Quest", ignoreCase = true) == true }) set.add(AlertFilter.QUESTS)
        if (alerts.any { it.type?.equals("Rare", ignoreCase = true) == true || it.type?.equals("Spawn", ignoreCase = true) == true }) set.add(AlertFilter.SPAWNS)
        if (alerts.any { it.type?.equals("Hundo", ignoreCase = true) == true }) set.add(AlertFilter.HUNDOS)
        if (alerts.any { it.type?.equals("PvP", ignoreCase = true) == true }) set.add(AlertFilter.PVP)
        if (alerts.any { it.type?.equals("Nundo", ignoreCase = true) == true }) set.add(AlertFilter.NUNDOS)
        if (alerts.any { it.type?.equals("Kecleon", ignoreCase = true) == true }) set.add(AlertFilter.KECLEON)
        if (alerts.any { it.type?.equals("Rocket", ignoreCase = true) == true }) set.add(AlertFilter.ROCKET)
        set
    }

    // Map Configuration
    val mapUiSettings = remember(hasLocationPermission) {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false, // We'll implement a custom FAB
            compassEnabled = true,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = true,
            tiltGesturesEnabled = true
        )
    }

    val mapProperties = remember(hasLocationPermission, mapType) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapType = mapType,
            minZoomPreference = 3f,
            maxZoomPreference = 20f,
            // Apply dark style only if in Normal mode
            mapStyleOptions = if (mapType == MapType.NORMAL) MapStyleOptions(DarkMapStyle) else null
        )
    }

    // Root Container
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            // Add padding for top bar and bottom sheets
            contentPadding = PaddingValues(top = 100.dp, bottom = 100.dp),
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
                        context = context,
                        onClick = { selectedAlert = alert }
                    )
                }
            }
        }

        // 2. Top Floating UI (Back, Title, Refresh, Layers)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(top = 12.dp)
        ) {
            // Top Bar Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(8.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.map_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            IconButton(onClick = onRefresh) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh"
                                )
                            }
                            IconButton(onClick = {
                                mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_layers),
                                    contentDescription = "Layers",
                                    tint = if (mapType == MapType.HYBRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AlertFilter.values().filter { it in availableFilters }) { filter ->
                    ElevatedAssistChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        colors = AssistChipDefaults.elevatedAssistChipColors(
                            containerColor = if (selectedFilter == filter) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            labelColor = if (selectedFilter == filter) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        ),
                        border = null,
                        elevation = AssistChipDefaults.elevatedAssistChipElevation(elevation = 4.dp)
                    )
                }
            }
        }
        
        // 3. Bottom Controls (Recenter FAB)
        AnimatedVisibility(
            visible = mapLoaded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = if (selectedAlert != null) 320.dp else 0.dp), // Move up if popup shows
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                 FilledIconButton(
                    onClick = { 
                        scope.launch {
                             // Try to zoom to bounds of visible alerts, or simple user location?
                             // Let's just zoom to the alerts for now as user location might be null
                             if (filteredAlerts.isNotEmpty()) {
                                 if (filteredAlerts.size == 1) {
                                     val a = filteredAlerts.first()
                                     try {
                                         cameraPositionState.animate(
                                             CameraUpdateFactory.newLatLngZoom(LatLng(a.latitude, a.longitude), 14f),
                                             1000
                                         )
                                     } catch (_: Exception) {}
                                 } else {
                                     val builder = LatLngBounds.Builder()
                                     filteredAlerts.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                                     val bounds = builder.build()
                                     val results = FloatArray(1)
                                     android.location.Location.distanceBetween(
                                         bounds.northeast.latitude, bounds.northeast.longitude,
                                         bounds.southwest.latitude, bounds.southwest.longitude,
                                         results
                                     )
                                     try {
                                         if (results[0] < 2000) {
                                             cameraPositionState.animate(
                                                 CameraUpdateFactory.newLatLngZoom(bounds.center, 14f),
                                                 1000
                                             )
                                         } else {
                                             cameraPositionState.animate(
                                                 CameraUpdateFactory.newLatLngBounds(bounds, 300),
                                                 1000
                                             )
                                         }
                                     } catch (_: Exception) {}
                                 }
                             }
                        }
                    },
                    modifier = Modifier.size(56.dp).shadow(8.dp, CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.LocationOn, "Fit to Alerts")
                }
            }
        }

        // 4. Alert Detail Popup
        AnimatedVisibility(
            visible = selectedAlert != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedAlert?.let { alert ->
                AlertDetailPopup(
                    alert = alert,
                    onDismiss = { selectedAlert = null },
                    onOpenFullDetail = {
                        context.startActivity(AlertDetailActivity.createIntent(context, alert))
                    }
                )
            }
        }
    }
    
    // Auto-fit camera logic on first load or filter change (optional, but good UX)
    // Only run once on load
    LaunchedEffect(mapLoaded) {
        if (mapLoaded && filteredAlerts.isNotEmpty()) {
             kotlinx.coroutines.delay(500)
             if (filteredAlerts.size == 1) {
                 val a = filteredAlerts.first()
                 try {
                     cameraPositionState.animate(
                         CameraUpdateFactory.newLatLngZoom(LatLng(a.latitude, a.longitude), 14f),
                         1000
                     )
                 } catch (_: Exception) {}
             } else {
                 val builder = LatLngBounds.Builder()
                 filteredAlerts.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                 val bounds = builder.build()
                 val results = FloatArray(1)
                 android.location.Location.distanceBetween(
                     bounds.northeast.latitude, bounds.northeast.longitude,
                     bounds.southwest.latitude, bounds.southwest.longitude,
                     results
                 )
                 try {
                     if (results[0] < 2000) {
                         cameraPositionState.animate(
                             CameraUpdateFactory.newLatLngZoom(bounds.center, 14f),
                             1000
                         )
                     } else {
                         cameraPositionState.animate(
                             CameraUpdateFactory.newLatLngBounds(bounds, 300),
                             1000
                         )
                     }
                 } catch (_: Exception) {}
             }
        }
    }
}

@Composable
private fun MapMarker(
    alert: PokemonAlert,
    now: Long,
    density: androidx.compose.ui.unit.Density,
    context: android.content.Context,
    onClick: () -> Unit
) {
    val position = remember(alert.latitude, alert.longitude) { 
        LatLng(alert.latitude, alert.longitude) 
    }
    val alertType = remember(alert.uniqueId) { detectAlertType(alert) }
    val timeRemainingMs = remember(now, alert.endTime) {
        (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE) - now
    }
    val isExpiringSoon = timeRemainingMs < (10 * 60 * 1000)
    
    // Cache the image URL to avoid reloading
    val imageUrl = remember(alert.uniqueId) { alert.thumbnailUrl ?: alert.imageUrl }
    val markerSizePx = remember(density) { with(density) { 64.dp.toPx().toInt() } }
    
    var customIcon by remember(alert.uniqueId) { mutableStateOf<BitmapDescriptor?>(null) }
    
    // Only reload icon when the alert itself changes, not on every time tick
    LaunchedEffect(alert.uniqueId, alertType) {
        if (imageUrl != null) {
            customIcon = withContext(Dispatchers.IO) {
                createBitmapDescriptorFromUrl(context, imageUrl, markerSizePx, alertType, alert.endTime)
            }
        }
    }
    
    MarkerInfoWindowContent(
        state = remember(position) { MarkerState(position = position) },
        icon = customIcon,
        title = alert.name,
        visible = true,
        onClick = { 
            onClick()
            true // Consume click
        }
    ) {
         // Empty info window, we use the custom popup
    }
}

@Composable
private fun AlertDetailPopup(
    alert: PokemonAlert,
    onDismiss: () -> Unit,
    onOpenFullDetail: () -> Unit
) {
    val context = LocalContext.current
    
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)) // Dim background slightly
            .clickable(onClick = onDismiss)
    ) {
        ElevatedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(enabled = false) {}, // Prevent clicks passing through card
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f) // Glassy look
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                         Text(
                            text = alert.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Time Remaining Pill
                        val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
                        val remaining = endMillis?.let { it - currentTime } ?: 0L
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (remaining < 0) 
                                MaterialTheme.colorScheme.errorContainer 
                            else 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = if (remaining > 0) 
                                    "Ends in ${TimeUtils.formatDurationShort(remaining)}" 
                                else 
                                    "Expired",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (remaining < 0)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    // Close
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(18.dp))
                    }
                }
                
                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                     androidx.compose.material3.Button(
                        onClick = {
                            openMapForAlert(context, alert)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                         Icon(
                             painter = painterResource(id = R.drawable.ic_map),
                             contentDescription = null,
                             modifier = Modifier.size(18.dp).padding(end = 8.dp)
                         )
                         Text("Directions")
                    }
                    
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onOpenFullDetail,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                         Text("Details")
                    }
                }
            }
        }
    }
}


// Helper to detect alert type from name and description
private fun detectAlertType(alert: PokemonAlert): AlertType {
    val text = "${alert.name} ${alert.description} ${alert.type ?: ""}".lowercase()
    return when {
        text.contains("hundo") || text.contains("100%") || text.contains("100 iv") -> AlertType.HUNDO
        text.contains("pvp") || text.contains("rank") -> AlertType.PVP
        text.contains("nundo") || text.contains("0%") || text.contains("0 iv") -> AlertType.NUNDO
        else -> AlertType.NORMAL
    }
}

private enum class AlertType(val displayName: String?, val badgeColor: Int?) {
    HUNDO("💯", 0xFFFF4444.toInt()),     // Red
    PVP("⚔️", 0xFF2196F3.toInt()),        // Blue  
    NUNDO("0️⃣", 0xFFFF9800.toInt()),     // Orange
    NORMAL(null, null)                    // No badge
}

private suspend fun createBitmapDescriptorFromUrl(
    context: android.content.Context, 
    url: String, 
    sizePx: Int,
    alertType: AlertType = AlertType.NORMAL,
    endTime: String? = null  // Add endTime parameter
): BitmapDescriptor? = withContext(Dispatchers.IO) {
    try {
        // Calculate if alert has less than 10 minutes remaining
        val timeRemainingMs = endTime?.let { TimeUtils.parseEndTimeToMillis(it) }?.let { 
            it - System.currentTimeMillis() 
        } ?: Long.MAX_VALUE
        val isExpiringSoon = timeRemainingMs < (10 * 60 * 1000) // 10 minutes in ms
        val globalAlpha = if (isExpiringSoon) 150 else 255
        
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(sizePx, sizePx)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) {
            val drawable = result.drawable
            
            // Create bitmap with smaller padding for tighter glow effect
            val padding = (sizePx * 0.15f).toInt()
            val totalSize = sizePx + (padding * 2)
            val bmp = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            
            val centerX = totalSize / 2f
            val centerY = totalSize / 2f
            val imageRadius = sizePx / 2f

            // 1. Draw Shadow/Glow
            alertType.badgeColor?.let { color ->
                val paint = Paint().apply {
                    this.color = color
                    alpha = 180
                    style = Paint.Style.FILL
                    isAntiAlias = true
                    maskFilter = android.graphics.BlurMaskFilter(
                        padding.toFloat(),
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                canvas.drawCircle(centerX, centerY, imageRadius, paint)
            }
            
            // 2. Circular Crop for Image
            val imagePaint = Paint().apply {
                isAntiAlias = true
                alpha = globalAlpha
            }
            
            // Draw circle mask
            val output = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
            val outputCanvas = Canvas(output)
            outputCanvas.drawCircle(centerX, centerY, imageRadius, imagePaint)
            
            // Set transfer mode to SRC_IN (keep only image inside circle)
            imagePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            
            // Draw image into the circle
            drawable.setBounds(padding, padding, padding + sizePx, padding + sizePx)
            drawable.draw(outputCanvas)
            
            // Draw output onto main canvas
            canvas.drawBitmap(output, 0f, 0f, Paint().apply { isAntiAlias = true })
            
            // 3. Border Ring
            val borderPaint = Paint().apply {
                color = AndroidColor.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }
            canvas.drawCircle(centerX, centerY, imageRadius, borderPaint)
            
            BitmapDescriptorFactory.fromBitmap(bmp)
        } else null
    } catch (_: Throwable) { null }
}

// Hardcoded Dark Map Style JSON
private const val DarkMapStyle = """
[
  {
    "elementType": "geometry",
    "stylers": [{"color": "#212121"}]
  },
  {
    "elementType": "labels.icon",
    "stylers": [{"visibility": "off"}]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#757575"}]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [{"color": "#212121"}]
  },
  {
    "featureType": "administrative",
    "elementType": "geometry",
    "stylers": [{"color": "#757575"}]
  },
  {
    "featureType": "administrative.country",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#9e9e9e"}]
  },
  {
    "featureType": "administrative.locality",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#bdbdbd"}]
  },
  {
    "featureType": "poi",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#757575"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "geometry",
    "stylers": [{"color": "#181818"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#616161"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "labels.text.stroke",
    "stylers": [{"color": "#1b1b1b"}]
  },
  {
    "featureType": "road",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#2c2c2c"}]
  },
  {
    "featureType": "road",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#8a8a8a"}]
  },
  {
    "featureType": "road.arterial",
    "elementType": "geometry",
    "stylers": [{"color": "#373737"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [{"color": "#3c3c3c"}]
  },
  {
    "featureType": "road.highway.controlled_access",
    "elementType": "geometry",
    "stylers": [{"color": "#4e4e4e"}]
  },
  {
    "featureType": "road.local",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#616161"}]
  },
  {
    "featureType": "transit",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#757575"}]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [{"color": "#000000"}]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#3d3d3d"}]
  }
]
"""