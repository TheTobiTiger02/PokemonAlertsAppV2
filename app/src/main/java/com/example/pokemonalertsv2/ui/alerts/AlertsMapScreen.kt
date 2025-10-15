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
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = padding,
                onMapLoaded = { mapLoaded = true }
            ) {
                alerts.forEach { alert ->
                    val position = LatLng(alert.latitude, alert.longitude)
                    var icon by remember(alert.imageUrl, alert.thumbnailUrl) { mutableStateOf<BitmapDescriptor?>(null) }
                    val imageUrl = alert.thumbnailUrl ?: alert.imageUrl
                    val markerSizePx = kotlin.math.max(1, (56f * density.density).toInt())
                    LaunchedEffect(imageUrl, markerSizePx) {
                        icon = imageUrl?.let { createBitmapDescriptorFromUrl(context, it, markerSizePx) }
                    }
                    MarkerInfoWindowContent(
                        state = MarkerState(position = position),
                        icon = icon,
                        onClick = {
                            onMarkerClick(alert)
                            false
                        },
                        onInfoWindowClick = {
                            context.startActivity(AlertDetailActivity.createIntent(context, alert))
                        }
                    ) {
                        // Custom info window content
                        ElevatedCard {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .widthIn(max = 220.dp)
                            ) {
                                // Thumbnail
                                val thumbUrl = alert.thumbnailUrl ?: alert.imageUrl
                                if (thumbUrl != null) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(thumbUrl)
                                            .allowHardware(false)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                androidx.compose.material3.Text(
                                    text = alert.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2
                                )
                                val subtitle = alert.description.takeIf { it.isNotBlank() } ?: alert.endTime
                                if (subtitle.isNotBlank()) {
                                    androidx.compose.material3.Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.Text(
                                    text = stringResource(id = R.string.tap_for_details),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-fit camera to markers when map and data are ready
    LaunchedEffect(mapLoaded, alerts) {
        if (!mapLoaded) return@LaunchedEffect
        if (alerts.isEmpty()) return@LaunchedEffect
        if (alerts.size == 1) {
            val a = alerts.first()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(a.latitude, a.longitude), 14f))
        } else {
            val builder = LatLngBounds.Builder()
            alerts.forEach { a -> builder.include(LatLng(a.latitude, a.longitude)) }
            val bounds = builder.build()
            val paddingPx = kotlin.math.max(1, (64f * density.density).toInt())
            // Try animate, if bounds invalid or map too small, ignore errors gracefully
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
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
