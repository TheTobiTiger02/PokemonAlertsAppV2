@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
// removed dp import by computing px from density directly
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerInfoWindowContent
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.android.gms.maps.model.MapStyleOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        onMarkerClick = { /* No-op for map route, handled internally */ }
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
    val scope = rememberCoroutineScope()
    val defaultLatLng = remember { LatLng(0.0, 0.0) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 2f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var selectedAlert by remember { mutableStateOf<PokemonAlert?>(null) }
    var filterType by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val mapProperties = remember {
        MapProperties(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_pokemon),
            isMyLocationEnabled = true // Re-enabled for modern feel
        )
    }
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false // We will use our own FAB
        )
    }

    val availableTypes = remember(alerts) {
        alerts.mapNotNull { it.type }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredAlerts = remember(alerts, filterType) {
        if (filterType == null) alerts else alerts.filter { it.type == filterType }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.map_title),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ) 
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
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                onMapLoaded = { mapLoaded = true }
            ) {
                filteredAlerts.forEach { alert ->
                    val position = LatLng(alert.latitude, alert.longitude)
                    var icon by remember(alert.imageUrl, alert.thumbnailUrl) { mutableStateOf<BitmapDescriptor?>(null) }
                    val imageUrl = alert.thumbnailUrl ?: alert.imageUrl
                    val markerSizePx = kotlin.math.max(1, (56f * density.density).toInt())
                    LaunchedEffect(imageUrl, markerSizePx) {
                        icon = imageUrl?.let { createBitmapDescriptorFromUrl(context, it, markerSizePx) }
                    }

                    // Use standard Marker instead of Window for cleaner interaction with BottomSheet
                    Marker(
                        state = MarkerState(position = position),
                        icon = icon,
                        title = alert.name,
                        onClick = {
                            selectedAlert = alert
                            true // Consumed
                        }
                    )
                }
            }

            // Filter Chips Row
            if (availableTypes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .align(androidx.compose.ui.Alignment.TopStart),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filterType == null,
                            onClick = { filterType = null },
                            label = { Text("All") }
                        )
                    }
                    items(availableTypes) { type ->
                        FilterChip(
                            selected = filterType == type,
                            onClick = { filterType = if (filterType == type) null else type },
                            label = { Text(type) }
                        )
                    }
                }
            }

            // My Location FAB
            FloatingActionButton(
                onClick = {
                    val loc = getLastKnownLocation(context)
                    if (loc != null) {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude), 15f
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map), // Should be my_location icon but ic_map is available
                    contentDescription = "My Location"
                )
            }
        }

        if (selectedAlert != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedAlert = null },
                sheetState = sheetState
            ) {
                AlertDetailContent(
                    alert = selectedAlert!!,
                    onOpenMaps = {
                         val mapsIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, selectedAlert!!.googleMapsUri)
                         try {
                             context.startActivity(mapsIntent)
                         } catch (e: Exception) {
                             // ignore
                         }
                    }
                )
                Spacer(modifier = Modifier.height(32.dp)) // Bottom padding
            }
        }
    }

    // Auto-fit camera logic
    LaunchedEffect(mapLoaded, alerts) {
        if (!mapLoaded) return@LaunchedEffect
        if (alerts.isEmpty()) return@LaunchedEffect
        // Only animate once on load, not every refresh, unless we want to track "initialLoad"
        // We can check if camera is at 0,0
        if (cameraPositionState.position.target.latitude == 0.0 && cameraPositionState.position.target.longitude == 0.0) {
             if (alerts.size == 1) {
                val a = alerts.first()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(a.latitude, a.longitude), 14f))
            } else {
                val builder = LatLngBounds.Builder()
                alerts.forEach { a -> builder.include(LatLng(a.latitude, a.longitude)) }
                val bounds = builder.build()
                val paddingPx = kotlin.math.max(1, (64f * density.density).toInt())
                runCatching {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
                }
            }
        }
    }
}

private suspend fun createBitmapDescriptorFromUrl(context: android.content.Context, url: String, sizePx: Int): BitmapDescriptor? = withContext(Dispatchers.IO) {
    try {
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
            val bmp = Bitmap.createBitmap(sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            // Stretch to square canvas; upstream Coil requested size already helps keep aspect reasonable
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bmp)
        } else null
    } catch (_: Throwable) { null }
}

private fun getLastKnownLocation(context: Context): android.location.Location? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) return null
        val providers = lm.getProviders(true)
        var best: android.location.Location? = null
        for (p in providers) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
            if (l != null && (best == null || (l.accuracy < best!!.accuracy))) {
                best = l
            }
        }
        best
    } catch (_: Throwable) { null }
}
