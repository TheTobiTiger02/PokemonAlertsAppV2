@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerInfoWindowContent
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsMapRoute(viewModel: PokemonAlertsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
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
        onMarkerClick = { /* no-op, let info window show */ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsMapScreen(
    alerts: List<PokemonAlert>,
    onBack: () -> Unit,
    onMarkerClick: (PokemonAlert) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val defaultLatLng = remember { LatLng(0.0, 0.0) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 2f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) } // Start closed by default
    var selectedAlert by remember { mutableStateOf<PokemonAlert?>(null) } // For popup

    // Check if location permission is granted
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Real-time filtering for map markers
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val activeAlerts = remember(alerts, now) {
        alerts.filter {
            val end = TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
            end > now
        }
    }

    // Map UI Settings with smooth controls and location enabled only if permission granted
    val mapUiSettings = remember(hasLocationPermission) {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = hasLocationPermission, // Only show if permission granted
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    val mapProperties = remember(hasLocationPermission) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission, // Only enable if permission granted
            minZoomPreference = 3f,
            maxZoomPreference = 20f,
            mapStyleOptions = com.google.android.gms.maps.model.MapStyleOptions(
                """
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
                """.trimIndent()
            )
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.map_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                text = stringResource(id = R.string.alerts_toolbar_tagline),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back),
                                contentDescription = stringResource(id = R.string.back),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    ) { padding ->
        val topInset = padding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = padding,
                onMapLoaded = { 
                    mapLoaded = true 
                },
                properties = mapProperties,
                uiSettings = mapUiSettings
            ) {
                activeAlerts.forEach { alert ->
                    val position = LatLng(alert.latitude, alert.longitude)
                    
                    // Detect alert type for badge
                    val alertType = remember(alert) { detectAlertType(alert) }
                    
                    // Calculate if under 10 minutes (for triggering regeneration)
                    val timeRemainingMs = remember(now, alert.endTime) {
                        (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE) - now
                    }
                    val isExpiringSoon = timeRemainingMs < (10 * 60 * 1000)
                    
                    // Load custom marker icon asynchronously with type badge
                    // Regenerate when crossing the 10-minute threshold
                    var customIcon by remember(alert.imageUrl, alert.thumbnailUrl, alertType, isExpiringSoon) { 
                        mutableStateOf<BitmapDescriptor?>(null) 
                    }
                    
                    LaunchedEffect(alert.imageUrl, alert.thumbnailUrl, alertType, isExpiringSoon) {
                        val imageUrl = alert.thumbnailUrl ?: alert.imageUrl
                        val markerSizePx = with(density) { 64.dp.toPx().toInt() }
                        customIcon = imageUrl?.let { 
                            withContext(Dispatchers.IO) {
                                createBitmapDescriptorFromUrl(context, it, markerSizePx, alertType, alert.endTime)
                            }
                        }
                    }
                    
                    // Use custom icon marker with popup and type badge
                    MarkerInfoWindowContent(
                        state = MarkerState(position = position),
                        icon = customIcon,
                        title = if (alertType.displayName != null) "${alertType.displayName} ${alert.name}" else alert.name,
                        snippet = TimeUtils.formatDurationShort(
                            (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: 0L) - System.currentTimeMillis()
                        ),
                        onClick = {
                            // Show popup overlay instead of opening new screen
                            selectedAlert = alert
                            true
                        }
                    ) {
                        // Simple info window
                        Text(
                            text = alert.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
            
            // Animated insights overlay
            AnimatedVisibility(
                visible = showInsights && mapLoaded,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it }
                ),
                exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { -it }
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .padding(top = topInset + 12.dp)
            ) {
                MapInsightsOverlay(
                    alerts = activeAlerts,
                    onDismiss = { showInsights = false }
                )
            }
            
            // Alert detail popup overlay
            AnimatedVisibility(
                visible = selectedAlert != null,
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                    animationSpec = tween(200),
                    targetOffsetY = { it }
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
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
            
            // Floating action button to recenter
            AnimatedVisibility(
                visible = mapLoaded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FilledIconButton(
                    onClick = {
                        showInsights = !showInsights
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(8.dp, CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (showInsights) Icons.Filled.Close else Icons.Filled.Info,
                        contentDescription = if (showInsights) "Hide insights" else "Show insights"
                    )
                }
            }
        }
    }

    // Auto-fit camera to markers with smooth animation
    LaunchedEffect(mapLoaded, activeAlerts) {
        if (!mapLoaded) return@LaunchedEffect
        if (activeAlerts.isEmpty()) return@LaunchedEffect
        
        kotlinx.coroutines.delay(500) // Small delay for smoother initial load
        
        if (activeAlerts.size == 1) {
            val a = activeAlerts.first()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(a.latitude, a.longitude), 15f),
                durationMs = 800
            )
        } else {
            val builder = LatLngBounds.Builder()
            activeAlerts.forEach { a -> builder.include(LatLng(a.latitude, a.longitude)) }
            val bounds = builder.build()
            val paddingPx = kotlin.math.max(1, (80f * density.density).toInt())
            runCatching {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, paddingPx),
                    durationMs = 1000
                )
            }
        }
    }
}

@Composable
private fun MapInsightsOverlay(
    alerts: List<PokemonAlert>, 
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val endingSoonCount = remember(alerts) {
        alerts.count {
            val millis = TimeUtils.parseEndTimeToMillis(it.endTime) ?: return@count false
            val remaining = millis - System.currentTimeMillis()
            remaining in 1..(20 * 60 * 1000)
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
            ),
            modifier = Modifier.shadow(12.dp, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.alerts_hero_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InsightMetric(label = stringResource(id = R.string.alerts_hero_active_label), value = alerts.size)
                    InsightMetric(label = stringResource(id = R.string.alerts_hero_ending_label), value = endingSoonCount)
                }
            }
        }
    }
}

@Composable
private fun InsightMetric(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.shadow(4.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(), 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label, 
                style = MaterialTheme.typography.labelMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlertDetailPopup(
    alert: PokemonAlert,
    onDismiss: () -> Unit,
    onOpenFullDetail: () -> Unit
) {
    val context = LocalContext.current
    
    // Live timer update
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000) // Update every second
        }
    }
    
    // Scrim background to dim the map
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
    )
    
    // Bottom sheet style card
    ElevatedCard(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 280.dp) // More map visible
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp) // Reduced padding
        ) {
            // Handle bar at top
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Alert image - smaller
            val imageUrl = alert.thumbnailUrl ?: alert.imageUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp) // Reduced from 200dp
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Alert name with type badge (only for special types)
            val alertType = detectAlertType(alert)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.name,
                    style = MaterialTheme.typography.titleLarge, // Reduced from headlineSmall
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
                
                // Only show badge for Hundo, PVP, and Nundo
                alertType.badgeColor?.let { color ->
                    alertType.displayName?.let { name ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(color)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Time remaining with live updates
            val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
            if (endMillis != null && endMillis > currentTime) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "⏱️ ${TimeUtils.formatDurationShort(endMillis - currentTime)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Description - more compact
            if (alert.description.isNotBlank()) {
                Text(
                    text = alert.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2 // Reduced from 4
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Action buttons - more compact
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Google Maps Directions button
                androidx.compose.material3.Button(
                    onClick = {
                        onDismiss()
                        openMapForAlert(context, alert)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                    Text("Get Directions", style = MaterialTheme.typography.labelLarge)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Close button
                    androidx.compose.material3.OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text("Close", style = MaterialTheme.typography.labelLarge)
                    }
                    
                    // Full details button
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenFullDetail()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Full Details")
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
        val globalAlpha = if (isExpiringSoon) 128 else 255 // 50% transparent if expiring soon
        
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
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            
            // Create bitmap with smaller padding for tighter glow effect
            val padding = (sizePx * 0.10f).toInt()
            val totalSize = sizePx + (padding * 2)
            val bmp = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            
            // Draw glowing backlight for special types (smaller, tighter glow)
            alertType.badgeColor?.let { color ->
                val centerX = totalSize / 2f
                val centerY = totalSize / 2f
                val baseRadius = sizePx / 2f
                
                // Outer glow (most diffuse) - reduced radius
                val outerGlowPaint = Paint().apply {
                    this.color = color
                    alpha = (45 * globalAlpha / 255) // Apply transparency
                    style = Paint.Style.FILL
                    isAntiAlias = true
                    maskFilter = android.graphics.BlurMaskFilter(
                        padding.toFloat() * 0.5f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                canvas.drawCircle(centerX, centerY, baseRadius + padding * 0.35f, outerGlowPaint)
                
                // Mid glow - reduced radius
                val midGlowPaint = Paint().apply {
                    this.color = color
                    alpha = (80 * globalAlpha / 255) // Apply transparency
                    style = Paint.Style.FILL
                    isAntiAlias = true
                    maskFilter = android.graphics.BlurMaskFilter(
                        padding.toFloat() * 0.3f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                canvas.drawCircle(centerX, centerY, baseRadius + padding * 0.2f, midGlowPaint)
                
                // Inner glow (brightest) - reduced radius
                val innerGlowPaint = Paint().apply {
                    this.color = color
                    alpha = (110 * globalAlpha / 255) // Apply transparency
                    style = Paint.Style.FILL
                    isAntiAlias = true
                    maskFilter = android.graphics.BlurMaskFilter(
                        padding.toFloat() * 0.15f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                canvas.drawCircle(centerX, centerY, baseRadius + padding * 0.1f, innerGlowPaint)
            }
            
            // Draw the Pokemon image centered on the padded canvas with transparency if expiring
            val imagePaint = Paint().apply {
                alpha = globalAlpha
                isAntiAlias = true
            }
            drawable.setBounds(padding, padding, padding + sizePx, padding + sizePx)
            canvas.saveLayer(null, imagePaint)
            drawable.draw(canvas)
            canvas.restore()
            
            BitmapDescriptorFactory.fromBitmap(bmp)
        } else null
    } catch (_: Throwable) { null }
}
