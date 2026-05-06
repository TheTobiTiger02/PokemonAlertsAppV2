package com.example.pokemonalertsv2.ui.alerts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.pokemonalertsv2.ui.theme.EmberGradientEnd
import com.example.pokemonalertsv2.ui.theme.EmberGradientStart
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

@Immutable
data class AlertDistanceInfo(
    val distanceMeters: Float?,
    val distanceText: String?,
    val walkingText: String?
)

@Immutable
data class AlertUiModel(
    val alert: PokemonAlert,
    val distanceInfo: AlertDistanceInfo
)

/**
 * Generates a descriptive title for an alert based on its type.
 * Examples: "100% Togetic", "0% Togetic", "Great #1 Togetic", "Terrakion Raid", "Psychic Rocket"
 */
fun formatAlertTitle(alert: PokemonAlert): String {
    val baseName = alert.pokemon ?: alert.cleanPokemonName
    
    // Handle raids - just show "Pokemon Raid"
    if (alert.hasTypeContaining("raid")) {
        return "$baseName Raid"
    }
    
    // Handle species replacement — show "OldSpecies → NewSpecies"
    if (alert.isSpeciesReplacement) {
        return "🔄 ${alert.oldSpecies} → ${alert.newSpecies}"
    }
    
    // Handle weather change - show "Pokemon Weather Change"
    if (alert.isWeatherChange) {
        return "🌦️ $baseName Changed"
    }
    
    // Handle Team Rocket - show grunt type
    if (alert.hasTypeContaining("rocket") || alert.gruntType != null) {
        val gruntLabel = alert.gruntType?.replaceFirstChar { it.uppercaseChar() } ?: "Rocket"
        return "$gruntLabel Rocket"
    }
    
    // Handle Kecleon
    if (alert.hasTypeContaining("kecleon")) {
        return "Kecleon"
    }
    
    // Handle Quests - show "Pokemon Quest" or task info
    if (alert.hasTypeContaining("quest")) {
        return "$baseName Quest"
    }
    
    // Build prefix parts for spawns (IV%, PvP rank)
    val prefixParts = mutableListOf<String>()
    
    // Add IV percentage for hundos/nundos only
    when {
        alert.isPerfect -> prefixParts.add("100%")
        alert.isNundo -> prefixParts.add("0%")
    }
    
    // Add best PvP ranking if notable (rank 1-10)
    val bestPvp = alert.pvpRankings
        ?.filter { it.rank != null && it.rank <= 10 }
        ?.minByOrNull { it.rank!! }
    
    if (bestPvp != null) {
        val leagueName = when {
            bestPvp.league?.contains("great", ignoreCase = true) == true -> "Great"
            bestPvp.league?.contains("ultra", ignoreCase = true) == true -> "Ultra"
            bestPvp.league?.contains("master", ignoreCase = true) == true -> "Master"
            bestPvp.league?.contains("little", ignoreCase = true) == true -> "Little"
            else -> bestPvp.league?.replaceFirstChar { it.uppercaseChar() }
        }
        prefixParts.add("$leagueName #${bestPvp.rank}")
    }
    
    return if (prefixParts.isNotEmpty()) {
        "${prefixParts.joinToString(" ")} $baseName"
    } else {
        baseName
    }
}

@Composable
fun AlertCard(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo,
    onOpenMaps: () -> Unit,
    onShowDetails: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
    tickerNow: Long = System.currentTimeMillis()
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accentBrush = remember(alert.type, alert.isPerfect, alert.isNundo, alert.isSpeciesReplacement, primary, secondary) {
        when {
            alert.isSpeciesReplacement -> Brush.horizontalGradient(listOf(Color(0xFF26C6DA), Color(0xFF00ACC1)))
            alert.isWeatherChange -> Brush.horizontalGradient(listOf(Color(0xFFFF9800), Color(0xFFF57C00)))
            alert.hasTypeContaining("raid") -> Brush.horizontalGradient(listOf(EmberGradientStart, EmberGradientEnd))
            alert.hasTypeContaining("shadow") -> Brush.horizontalGradient(listOf(AuroraGradientStart, AuroraGradientEnd))
            alert.isPerfect -> Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000)))
            alert.isNundo -> Brush.horizontalGradient(listOf(Color(0xFF607D8B), Color(0xFF455A64)))
            else -> Brush.horizontalGradient(listOf(primary, secondary))
        }
    }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onShowDetails()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(accentBrush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AlertImage(alert = alert, rounded = false)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    // Pokemon name with shiny indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatAlertTitle(alert),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (alert.isShiny == true) {
                            ShinyBadge()
                        }
                    }
                    
                    // Pokemon form if available
                    alert.pokemonForm?.takeIf { it.isNotBlank() }?.let { form ->
                        Text(
                            text = form,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Quick stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // IV Badge — show new IV for weather change alerts
                        val displayCardIv = if (alert.isWeatherChange && alert.newIv != null) alert.newIv else alert.formattedIv
                        displayCardIv?.let { iv ->
                            IvBadge(iv = iv, isPerfect = alert.isPerfect, isNundo = alert.isNundo)
                        }
                        
                        // CP Badge — show new CP for weather change alerts
                        val displayCardCp = if (alert.isWeatherChange && alert.newCp != null) alert.newCp else alert.cp
                        displayCardCp?.let { cp ->
                            CpBadge(cp = cp, level = alert.level)
                        }
                        
                        // Weather boost indicator
                        if (alert.isWeatherBoosted == true) {
                            WeatherBoostBadge()
                        }
                        
                        // Species replacement indicator
                        if (alert.isSpeciesReplacement) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF26C6DA).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "🔄",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        } else if (alert.isWeatherChange) {
                            // Weather change indicator
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "🌦️",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        // Distance
                        val distanceText = distanceInfo.distanceText
                        if (!distanceText.isNullOrBlank()) {
                            DistanceChip(text = distanceText)
                        }
                    }
                }
                
                // Top Actions
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Share Button
                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onShareClick()
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.3f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share"
                        )
                    }

                    FilledIconButton(
                        onClick = onOpenMaps,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_map),
                            contentDescription = stringResource(id = R.string.open_in_maps)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                // Location info
                alert.locationDisplay?.let { location ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AlertMetaRow(alert = alert, distanceInfo = distanceInfo, tickerNow = tickerNow)
                Spacer(modifier = Modifier.height(20.dp))
                ElevatedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onOpenMaps()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = stringResource(id = R.string.open_in_maps))
                }
            }
        }
    }
}

@Composable
private fun ShinyBadge() {
    Surface(
        shape = CircleShape,
        color = Color(0xFFFFD700).copy(alpha = 0.9f)
    ) {
        Text(
            text = "✨",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun IvBadge(iv: String, isPerfect: Boolean, isNundo: Boolean) {
    val backgroundColor = when {
        isPerfect -> Color(0xFFFFD700)
        isNundo -> Color(0xFF607D8B)
        else -> Color.White.copy(alpha = 0.2f)
    }
    val textColor = when {
        isPerfect -> Color.Black
        isNundo -> Color.White
        else -> Color.White
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = iv,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun CpBadge(cp: Int, level: Double?) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "CP $cp",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            level?.let {
                val levelText = if (it == it.toLong().toDouble()) "L${it.toLong()}" else "L$it"
                Text(
                    text = levelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun WeatherBoostBadge() {
    Surface(
        shape = CircleShape,
        color = Color(0xFF4FC3F7).copy(alpha = 0.9f)
    ) {
        Text(
            text = "☁️",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun AlertImage(alert: PokemonAlert, modifier: Modifier = Modifier, rounded: Boolean = true) {
    val context = LocalContext.current
    val primaryUrl = alert.imageUrl?.takeIf { it.isNotBlank() }
    val thumbnailUrl = alert.thumbnailUrl
    val lat = alert.latitude
    val lon = alert.longitude
    val hasValidCoords = lat != null && lon != null &&
        lat.isFinite() && lon.isFinite() &&
        lat in -85.0511..85.0511 && lon in -180.0..180.0 &&
        !(abs(lat) < 0.0001 && abs(lon) < 0.0001)
    var useFallback by remember(primaryUrl, thumbnailUrl) { mutableStateOf(false) }

    val imageHeight = if (rounded) 200.dp else 220.dp
    val shape = if (rounded) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)
    // When rounded=false the caller (detail hero) controls height via its own modifier
    val heightModifier = if (rounded) Modifier.height(imageHeight) else Modifier.fillMaxHeight()
    val showPrimaryImage = primaryUrl != null && !useFallback
    val showMapFallback = !showPrimaryImage && hasValidCoords

    when {
        // Primary image available and not failed yet
        showPrimaryImage -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(primaryUrl)
                    .crossfade(300)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = stringResource(id = R.string.alert_image),
                placeholder = painterResource(id = R.drawable.ic_placeholder),
                error = painterResource(id = R.drawable.ic_placeholder),
                onError = { useFallback = true },
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .clip(shape)
            )
        }

        // Fallback: composite map + thumbnail sprite overlay
        showMapFallback -> {
            val safeLat = lat!!
            val safeLon = lon!!
            val zoom = if (rounded) 16 else 17
            val n = 1 shl zoom

            // Exact fractional tile coordinates
            val tileXExact = (safeLon + 180.0) / 360.0 * n
            val latRad = Math.toRadians(safeLat)
            val tileYExact = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n

            // Integer tile coordinates (which tile the point falls in)
            val centerTileX = floor(tileXExact).toInt()
            val centerTileY = floor(tileYExact).toInt()

            // Fractional position within the center tile (0.0–1.0)
            val fracX = (tileXExact - centerTileX).toFloat()
            val fracY = (tileYExact - centerTileY).toFloat()

            BoxWithConstraints(
                modifier = modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .clipToBounds()
                    .clip(shape)
            ) {
                val density = LocalDensity.current
                val viewWidthPx = constraints.maxWidth.toFloat()
                val viewHeightPx = constraints.maxHeight.toFloat()

                // Keep tile size near native OSM resolution
                val tileSizePx = 256f
                val tileSizeDp = with(density) { tileSizePx.toDp() }

                // Build a large enough tile grid to fully cover the viewport
                val tilesAcross = ceil(viewWidthPx / tileSizePx).toInt() + 2
                val tilesDown = ceil(viewHeightPx / tileSizePx).toInt() + 2
                // Limit tile grid for card views (rounded=true) to reduce composable count
                val rawRadius = maxOf(1, maxOf(tilesAcross, tilesDown) / 2)
                val radius = if (rounded) rawRadius.coerceAtMost(1) else rawRadius
                val tileRange = -radius..radius

                // For each tile, compute offset so the coordinate lands at view center.
                // The coordinate is at (fracX, fracY) within the center tile (dx=0, dy=0).
                // Center tile left edge in view px = viewWidth/2 - fracX * tileSizePx
                // Tile at (dx, dy) left edge = viewWidth/2 + (dx - fracX) * tileSizePx
                Box(modifier = Modifier.fillMaxSize()) {
                    for (dx in tileRange) {
                        for (dy in tileRange) {
                            val tileLeftPx = viewWidthPx / 2f + (dx - fracX) * tileSizePx
                            val tileTopPx = viewHeightPx / 2f + (dy - fracY) * tileSizePx
                            val tileLeftDp = with(density) { tileLeftPx.toDp() }
                            val tileTopDp = with(density) { tileTopPx.toDp() }

                            val x = ((centerTileX + dx) % n + n) % n
                            val y = (centerTileY + dy).coerceIn(0, n - 1)
                            val tileUrl = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(tileUrl)
                                    .crossfade(300)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .addHeader("User-Agent", "PokemonAlertsV2/1.0")
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .requiredSize(tileSizeDp)
                                    .offset(x = tileLeftDp, y = tileTopDp)
                                    .background(Color(0xFF16213E))
                            )
                        }
                    }
                }
                // Scrim overlay — stronger vignette for detail view
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (rounded) 0.15f else 0.35f))
                )
                // Pokemon sprite overlay (centered = at the coordinate)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Exact coordinate marker (at view center = coordinate location)
                    Box(
                        modifier = Modifier
                            .size(if (rounded) 8.dp else 10.dp)
                            .background(Color(0xFFFFD700), CircleShape)
                    )

                    // Radial glow backdrop behind sprite
                    Box(
                        modifier = Modifier
                            .size(if (rounded) 80.dp else 130.dp)
                            .offset(y = if (rounded) (-8).dp else (-12).dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700).copy(alpha = if (rounded) 0.30f else 0.40f),
                                        Color(0xFFFFD700).copy(alpha = if (rounded) 0.10f else 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(thumbnailUrl)
                                .crossfade(300)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build(),
                            contentDescription = stringResource(id = R.string.alert_image),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(if (rounded) 44.dp else 72.dp)
                                .offset(y = if (rounded) (-8).dp else (-12).dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_placeholder),
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (rounded) 36.dp else 56.dp)
                                .offset(y = if (rounded) (-8).dp else (-12).dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Fallback: thumbnail sprite with dark bg + gold glow
        thumbnailUrl != null -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .background(Color(0xFF1A1A2E), shape)
                    .clip(shape),
                contentAlignment = Alignment.Center
            ) {
                // Gold radial glow behind sprite
                Box(
                    modifier = Modifier
                        .size(if (rounded) 120.dp else 240.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = if (rounded) 0.35f else 0.40f),
                                    Color(0xFFFFD700).copy(alpha = if (rounded) 0.12f else 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .crossfade(300)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = stringResource(id = R.string.alert_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(if (rounded) 64.dp else 140.dp)
                )
            }
        }

        // No images at all — dark bg + gold glow + Pokéball icon
        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .background(Color(0xFF1A1A2E), shape)
                    .clip(shape),
                contentAlignment = Alignment.Center
            ) {
                // Gold radial glow
                Box(
                    modifier = Modifier
                        .size(if (rounded) 100.dp else 200.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = if (rounded) 0.30f else 0.38f),
                                    Color(0xFFFFD700).copy(alpha = if (rounded) 0.10f else 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_placeholder),
                    contentDescription = null,
                    modifier = Modifier.size(if (rounded) 48.dp else 80.dp),
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AlertDetailScreen(
    alert: PokemonAlert,
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Unit)? = null
) {
    if (isInPictureInPicture) {
        AlertPictureInPictureContent(alert = alert)
        return
    }

    val context = LocalContext.current
    val containerGradient = remember {
        Brush.verticalGradient(
            listOf(
                AuroraGradientStart,
                AuroraGradientMid,
                AuroraGradientEnd.copy(alpha = 0.85f)
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent // Let the Box gradient show through
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(containerGradient)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero image section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    AlertImage(alert = alert, rounded = false, modifier = Modifier.fillMaxSize())
                    
                    // Top Bar Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    // Bottom Gradient for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF0F172A).copy(alpha = 0.85f) // Lighter so map remains visible
                                    )
                                )
                            )
                    )
                    
                    // Top-left actions
                    val activity = LocalContext.current as? android.app.Activity
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { activity?.finish() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.3f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }

                        if (onEnterPictureInPicture != null) {
                            Surface(
                                onClick = { onEnterPictureInPicture() },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = Color.Black.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            AuroraGradientStart.copy(alpha = 0.6f),
                                            AuroraGradientEnd.copy(alpha = 0.4f)
                                        )
                                    )
                                ),
                                contentColor = Color.White,
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pip),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.enter_pip_short),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    
                    // Shiny indicator in top right
                    if (alert.isShiny == true) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.9f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "✨", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = "SHINY",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
                
                // Content section - scrollable
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Fill remaining space
                        .verticalScroll(rememberScrollState())
                        .background(Color(0xFF0F172A)) // Solid background for text area
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pokemon Name and Type Badge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatAlertTitle(alert),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            // Pokemon form if available
                            alert.pokemonForm?.takeIf { it.isNotBlank() }?.let { form ->
                                Text(
                                    text = form,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            // Pokedex ID
                            alert.pokedexId?.let { dexId ->
                                Text(
                                    text = "#${dexId.toString().padStart(4, '0')}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Type Badges
                        val typeList = alert.type?.takeIf { it.isNotEmpty() }
                        if (typeList != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                typeList.forEach { typeName ->
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = getTypeColor(typeName),
                                    ) {
                                        Text(
                                            text = typeName.uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Stats Card (IVs, CP, Level, HundoCP)
                    if (alert.formattedIv != null || alert.cp != null || alert.level != null || alert.hundoCP != null) {
                        StatsCard(alert = alert)
                    }
                    
                    // PvP Rankings Card
                    alert.pvpRankings?.takeIf { it.isNotEmpty() }?.let { rankings ->
                        PvpRankingsCard(rankings = rankings)
                    }
                    
                    // Weather & Gender Info
                    if (alert.isWeatherBoosted == true || alert.gender != null || alert.currentWeather != null) {
                        WeatherAndGenderCard(alert = alert)
                    }
                    
                    // Moves Card (for raids)
                    alert.moves?.let { moves ->
                        MovesCard(moves = moves)
                    }
                    
                    // Location Card
                    LocationCard(alert = alert)
                    
                    // Quest Info Card (for quests)
                    if (alert.questTask != null || alert.questReward != null) {
                        QuestCard(alert = alert)
                    }
                    
                    // Rocket Info Card (for Rocket encounters)
                    if (alert.gruntType?.isNotBlank() == true || alert.pokemonRewards?.isNotEmpty() == true) {
                        RocketCard(
                            gruntType = alert.gruntType,
                            pokemonRewards = alert.pokemonRewards
                        )
                    }
                    
                    // Time & Status
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CountdownAndEndTimeRow(alert = alert)
                            
                            // Created at timestamp
                            alert.createdAt?.takeIf { it.isNotBlank() }?.let { created ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Posted: $created",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Button
                    FilledTonalButton(
                        onClick = { openMapForAlert(context, alert) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_map),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.open_in_maps),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AlertPictureInPictureContent(alert: PokemonAlert) {
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    var now by remember(endMillis) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endMillis) {
        if (endMillis == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remainingMs = endMillis?.minus(now)
    val isExpired = remainingMs != null && remainingMs <= 0
    val pipTimerText = when {
        endMillis == null -> null
        isExpired -> stringResource(id = R.string.alert_expired)
        else -> TimeUtils.formatDurationShort(remainingMs ?: 0L)
    }

    // Zoom & pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = scale > 1.05f

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        // Clamp translation so the image can't be panned off-screen
        val maxX = (newScale - 1f) * 500f / 2f
        val maxY = (newScale - 1f) * 300f / 2f
        val newOffset = Offset(
            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
        )
        scale = newScale
        offset = newOffset
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .transformable(state = transformableState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                AlertImage(
                    alert = alert,
                    rounded = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Gradient scrim (not affected by zoom)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (isZoomed) 0.3f else 0.7f)
                            )
                        )
                    )
            )
            // Info overlay (hidden when zoomed for cleaner view)
            AnimatedVisibility(
                visible = !isZoomed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatAlertTitle(alert),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pipTimerText != null) {
                        Text(
                            text = pipTimerText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isExpired) MaterialTheme.colorScheme.error else Color.White
                        )
                    }
                }
            }
            // Zoom level indicator (shown only when zoomed)
            AnimatedVisibility(
                visible = isZoomed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ) {
                    Text(
                        text = "%.1f×".format(scale),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun getTypeColor(type: String): Color {
    return when (type.lowercase()) {
        "hundo" -> Color(0xFFFFD700)
        "nundo" -> Color(0xFF607D8B)
        "raid" -> Color(0xFFE91E63)
        "quest" -> Color(0xFF2196F3)
        "pvp" -> Color(0xFF9C27B0)
        "rare", "spawn" -> Color(0xFF4CAF50)
        "rocket" -> Color(0xFFFF5722)
        "kecleon" -> Color(0xFF00BCD4)
        "weatherchange" -> Color(0xFFFF9800)
        else -> Color(0xFF757575)
    }
}

@Composable
private fun StatsCard(alert: PokemonAlert) {
    val isReplacement = alert.isSpeciesReplacement
    val isChanged = alert.isWeatherChange  // includes both weather-only and replacement
    val accentColor = if (isReplacement) Color(0xFF26C6DA) else Color(0xFFFF9800)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section title
            Text(
                text = when {
                    isReplacement -> "🔄 New Species"
                    isChanged -> "🌦️ New Stats"
                    else -> "Stats"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isChanged) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Species replacement banner — shows old species info
            if (isReplacement) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "🔄", style = MaterialTheme.typography.titleMedium)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replacing ${alert.oldSpecies}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val oldDetails = listOfNotNull(
                                alert.oldIv?.let { "IV: $it" },
                                alert.oldCp?.let { "CP: $it" }
                            )
                            if (oldDetails.isNotEmpty()) {
                                Text(
                                    text = oldDetails.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            
            // For weather change / replacement alerts, display the new stats as primary values
            val displayIv = if (isChanged && alert.newIv != null) alert.newIv else alert.formattedIv
            val displayCp = if (isChanged && alert.newCp != null) alert.newCp else alert.cp
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // IVs
                displayIv?.let { iv ->
                    val isNewIv = isChanged && alert.newIv != null
                    StatItem(
                        label = if (isNewIv) "New IV" else "IV",
                        value = iv,
                        subValue = if (isNewIv && alert.formattedIv != null) "was ${alert.formattedIv}" else alert.ivPercentage?.let { "$it%" },
                        highlight = alert.isPerfect,
                        highlightColor = if (isNewIv) accentColor else if (alert.isPerfect) Color(0xFFFFD700) else if (alert.isNundo) Color(0xFF607D8B) else null
                    )
                }
                
                // CP
                displayCp?.let { cp ->
                    val isNewCp = isChanged && alert.newCp != null
                    StatItem(
                        label = if (isNewCp) "New CP" else "CP",
                        value = cp.toString(),
                        subValue = if (isNewCp && alert.cp != null) "was ${alert.cp}" else alert.hundoCP?.formatted(),
                        highlightColor = if (isNewCp) accentColor else null
                    )
                }
                
                // Hundo CP (standalone for raids when no individual CP)
                if (displayCp == null && alert.hundoCP != null) {
                    alert.hundoCP.level20?.let { l20 ->
                        StatItem(
                            label = "100% L20",
                            value = l20.toString(),
                            highlightColor = Color(0xFFFFD700)
                        )
                    }
                    alert.hundoCP.level25?.let { l25 ->
                        StatItem(
                            label = "100% L25",
                            value = l25.toString(),
                            highlightColor = Color(0xFFFFD700)
                        )
                    }
                }
                
                // Level — show for species replacement (it's the new species' level), hide for weather-only change
                if (!isChanged || isReplacement) {
                    alert.level?.let { level ->
                        StatItem(
                            label = if (isReplacement) "New Level" else "Level",
                            value = if (level == level.toLong().toDouble()) level.toLong().toString() else level.toString(),
                            highlightColor = if (isReplacement) accentColor else null
                        )
                    }
                }
            }

            // Individual IVs breakdown — hide for weather change alerts (old breakdown is no longer relevant)
            if (!isChanged && alert.ivAttack != null && alert.ivDefense != null && alert.ivStamina != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IvBar(label = "ATK", value = alert.ivAttack, maxValue = 15)
                    IvBar(label = "DEF", value = alert.ivDefense, maxValue = 15)
                    IvBar(label = "STA", value = alert.ivStamina, maxValue = 15)
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    subValue: String? = null,
    highlight: Boolean = false,
    highlightColor: Color? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface
        )
        subValue?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IvBar(label: String, value: Int, maxValue: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.toFloat() / maxValue)
                    .height(8.dp)
                    .background(
                        when {
                            value == 15 -> Color(0xFFFFD700)
                            value >= 13 -> Color(0xFF4CAF50)
                            value >= 10 -> Color(0xFFFFC107)
                            else -> Color(0xFFFF5722)
                        }
                    )
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WeatherAndGenderCard(alert: PokemonAlert) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Weather Boost
            if (alert.isWeatherBoosted == true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "☁️", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Weather Boosted",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4FC3F7)
                    )
                    alert.currentWeather?.let { weather ->
                        Text(
                            text = weather,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Gender
            alert.gender?.takeIf { it.isNotBlank() }?.let { gender ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (gender.lowercase()) {
                            "male" -> "♂️"
                            "female" -> "♀️"
                            else -> "⚧"
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = gender.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MovesCard(moves: PokemonMoves) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚔️", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Moves",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            // Fast Move
            moves.fast?.let { fast ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "FAST",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = fast,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Charged Move
            moves.charged?.let { charged ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFE91E63).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "CHARGED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE91E63),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = charged,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(alert: PokemonAlert) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // Location address
            alert.locationDisplay?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Gym name (separate from address)
            alert.gym?.takeIf { it.isNotBlank() && it != alert.pokemonLocation }?.let { gym ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gym: $gym",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Pokestop name
            alert.pokestop?.takeIf { it.isNotBlank() && it != alert.pokemonLocation }?.let { stop ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PokéStop: $stop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Coordinates
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${String.format("%.6f", alert.latitude)}, ${String.format("%.6f", alert.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun QuestCard(alert: PokemonAlert) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "📜", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Quest",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (alert.requiresAR == true) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF9800)
                    ) {
                        Text(
                            text = "AR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            alert.questTask?.let { task ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Task: $task",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            alert.questReward?.let { reward ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reward: $reward",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RocketCard(
    gruntType: String? = null,
    pokemonRewards: List<PokemonReward>? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "🚀", style = MaterialTheme.typography.headlineMedium)
                Column {
                    Text(
                        text = "Team GO Rocket",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE91E63)
                    )
                    if (gruntType?.isNotBlank() == true) {
                        Text(
                            text = gruntType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pokemon Rewards
            if (!pokemonRewards.isNullOrEmpty()) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text(
                    text = "Possible Rewards",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                pokemonRewards.forEach { reward ->
                    val rarityColor = when (reward.rarity?.lowercase()) {
                        "common" -> Color(0xFF4CAF50)
                        "rare" -> Color(0xFFFFD700)
                        "legendary", "ultra rare" -> Color(0xFFE040FB)
                        else -> Color(0xFF90A4AE)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Rarity dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(rarityColor, CircleShape)
                        )

                        // Pokemon name
                        Text(
                            text = reward.pokemon ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        // Rarity label
                        reward.rarity?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = rarityColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Percentage
                        reward.percentage?.let { pct ->
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Percentage bar
                    reward.percentage?.let { pct ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(2.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(rarityColor, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PvpRankingsCard(rankings: List<com.example.pokemonalertsv2.data.PvpRanking>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚔️", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "PvP Rankings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            rankings.forEachIndexed { index, ranking ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                PvpRankingItem(ranking = ranking)
            }
        }
    }
}

@Composable
private fun PvpRankingItem(ranking: com.example.pokemonalertsv2.data.PvpRanking) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // League name with badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val leagueColor = when {
                ranking.league?.contains("Great", ignoreCase = true) == true -> Color(0xFF2196F3)
                ranking.league?.contains("Ultra", ignoreCase = true) == true -> Color(0xFFFFD700)
                ranking.league?.contains("Master", ignoreCase = true) == true -> Color(0xFF9C27B0)
                ranking.league?.contains("Little", ignoreCase = true) == true -> Color(0xFFFF9800)
                else -> MaterialTheme.colorScheme.primary
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = leagueColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = ranking.league ?: "League",
                    style = MaterialTheme.typography.labelMedium,
                    color = leagueColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            
            // Rank badge
            ranking.rank?.let { rank ->
                val rankEmoji = when (rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "#$rank"
                }
                val rankColor = when (rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                
                if (rank <= 3) {
                    Text(
                        text = rankEmoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        
        // Pokemon name if different from main alert
        ranking.pokemon?.let { pokemon ->
            Text(
                text = pokemon,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Stats row: CP and Level
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ranking.cp?.let { cp ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "CP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = cp.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            ranking.level?.let { level ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Level",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (level == level.toLong().toDouble()) level.toLong().toString() else level.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            ranking.percentage?.let { percentage ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Stat Product",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%.1f", percentage)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AlertMetaRow(alert: PokemonAlert, distanceInfo: AlertDistanceInfo, tickerNow: Long = System.currentTimeMillis()) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val typeLabel = alert.typeDisplay?.uppercase(Locale.getDefault())
        val distanceLabel = distanceInfo.distanceText
        val walkingLabel = distanceInfo.walkingText
        
        val typeIcon = when {
            alert.hasType("Hundo") -> Icons.Filled.Star
            alert.hasType("PvP") -> Icons.Filled.Star
            alert.hasType("Nundo") -> Icons.Filled.Close
            alert.hasType("Raid") -> Icons.Filled.Warning
            alert.hasType("Quest") -> Icons.Filled.Star
            alert.hasType("Rocket") -> Icons.Filled.Warning
            alert.hasType("Kecleon") -> Icons.Filled.LocationOn
            else -> Icons.Filled.LocationOn
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!typeLabel.isNullOrBlank()) {
                AlertTag(text = typeLabel, icon = typeIcon)
            }
            if (!distanceLabel.isNullOrBlank()) {
                AlertTag(text = distanceLabel, icon = Icons.Filled.LocationOn)
            }
            if (!walkingLabel.isNullOrBlank()) {
                AlertTag(text = walkingLabel, icon = null)
            }
        }
        CountdownAndEndTimeRow(alert = alert, now = tickerNow)
    }
}

@Composable
fun AlertTag(text: String, icon: ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun CountdownAndEndTimeRow(alert: PokemonAlert, now: Long = System.currentTimeMillis()) {
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    val remaining = endMillis?.let { it - now } ?: -1
    val expiredLabel = if (endMillis != null && remaining <= 0) {
        "Expired ${TimeUtils.formatTimeAgo(endMillis)}"
    } else {
        stringResource(id = R.string.alert_expired)
    }
    val remainingText = if (endMillis != null) {
        if (remaining > 0) TimeUtils.formatDurationShort(remaining) else expiredLabel
    } else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!remainingText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (remainingText == expiredLabel)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.height(16.dp),
                        tint = if (remainingText == expiredLabel)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (remainingText == expiredLabel) remainingText else "⏱ $remainingText",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (remainingText == expiredLabel)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = stringResource(id = R.string.alert_end_time, alert.endTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DistanceChip(text: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.15f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_map),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun openMapForAlert(context: Context, alert: PokemonAlert) {
    val mapsIntent = Intent(Intent.ACTION_VIEW, alert.googleMapsUri)
    try {
        context.startActivity(mapsIntent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_maps_app), Toast.LENGTH_SHORT).show()
    }
}

fun getLastKnownLocation(context: Context): Location? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) return null
        val providers = lm.getProviders(true)
        var best: Location? = null
        for (p in providers) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
            if (l != null && (best == null || (l.accuracy < best!!.accuracy))) {
                best = l
            }
        }
        best
    } catch (_: Throwable) { null }
}

fun formatWalkingTime(meters: Float): String {
    val minutes = ceil((meters / 83.333f).toDouble()).toInt().coerceAtLeast(1)
    return String.format(Locale.getDefault(), "%d min walk", minutes)
}

fun formatDistance(meters: Float): String {
    return if (meters >= 1000f) String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
    else String.format(Locale.getDefault(), "%.0f m", meters)
}
