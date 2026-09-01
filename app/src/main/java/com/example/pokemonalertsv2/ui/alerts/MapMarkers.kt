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
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
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
internal fun MapMarker(
    alert: PokemonAlert,
    countdownClock: State<Long>,
    density: androidx.compose.ui.unit.Density,
    showTimeLabel: Boolean,
    minutePrecisionCountdown: Boolean,
    goDexMatchResult: GoDexMatchResult,
    onClick: () -> Unit,
    markerSizeDp: Float = MAP_FULL_MARKER_SIZE_DP,
    emphasized: Boolean = false
) {
    val context = LocalContext.current
    val coordinates = remember(alert.latitude, alert.longitude) {
        alert.mapCoordinatesOrNull()
    }
    if (coordinates == null) return
    val position = remember(coordinates) { LatLng(coordinates.latitude, coordinates.longitude) }
    val visualStyle = resolveAlertVisualStyle(alert)

    val markerLabel = alert.displayCp?.let { "CP $it" } ?: when (visualStyle.category) {
        AlertCategory.HUNDO -> "100%"
        AlertCategory.NUNDO -> "0%"
        else -> visualStyle.shortCode
    }
    val speciesName = alert.pokemon?.takeIf { it.isNotBlank() } ?: alert.cleanPokemonName
    val speciesImageUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: alert.imageUrl?.takeIf { it.isNotBlank() }
    val now = countdownClock.value
    val timeLabel = remember(now, alert.endTime, minutePrecisionCountdown) {
        mapCountdownLabel(alert.endTime, now, minutePrecisionCountdown)
    }
    val markerSizePx = remember(density, markerSizeDp) {
        with(density) { markerSizeDp.dp.toPx().toInt() }
    }

    val colors = MaterialTheme.colorScheme
    val basePalette = remember(
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
    val palette = remember(basePalette, visualStyle.category) {
        basePalette.copy(primary = visualStyle.category.accentArgb.toInt())
    }
    val markerIconRequest = remember(
        markerSizePx,
        markerLabel,
        speciesName,
        speciesImageUrl,
        alert.endTime,
        showTimeLabel,
        timeLabel,
        palette,
        goDexMatchResult.status
    ) {
        MapMarkerIconRequest(
            sizePx = markerSizePx,
            categoryCode = markerLabel,
            speciesName = speciesName,
            speciesImageUrl = speciesImageUrl,
            endTime = alert.endTime,
            showTimeLabel = showTimeLabel,
            timeLabel = if (showTimeLabel) timeLabel else null,
            palette = palette,
            goDexStatus = goDexMatchResult.status
        )
    }
    val markerCacheKey = remember(markerIconRequest) {
        mapMarkerIconCacheKey(markerIconRequest)
    }
    var markerIcon by remember(markerCacheKey) {
        mutableStateOf(resolveInitialMapMarkerIcon(markerIconRequest, markerCacheKey))
    }

    LaunchedEffect(alert.uniqueId, markerIconRequest, markerCacheKey) {
        val renderedIcon = withContext(Dispatchers.IO) {
            createMapMarkerIcon(
                context = context,
                sizePx = markerIconRequest.sizePx,
                categoryCode = markerIconRequest.categoryCode,
                speciesName = markerIconRequest.speciesName,
                speciesImageUrl = markerIconRequest.speciesImageUrl,
                endTime = markerIconRequest.endTime,
                showTimeLabel = markerIconRequest.showTimeLabel,
                timeLabel = markerIconRequest.timeLabel,
                palette = markerIconRequest.palette,
                goDexStatus = markerIconRequest.goDexStatus
            )
        }
        currentCoroutineContext().ensureActive()
        if (renderedIcon != null) markerIcon = renderedIcon
    }
    val googleMarkerDescriptor = remember(markerIcon) {
        BitmapDescriptorFactory.fromBitmap(markerIcon.bitmap)
    }

    MarkerInfoWindowContent(
        state = remember(position) { MarkerState(position = position) },
        icon = googleMarkerDescriptor,
        anchor = markerIcon.anchor,
        title = formatAlertTitle(alert, goDexMatchResult.status),
        visible = true,
        // Keep a tracked/browsed PiP marker clear of count bubbles and ordinary alerts.
        zIndex = if (emphasized) MAP_EMPHASIZED_MARKER_Z_INDEX else 0f,
        onClick = {
            onClick()
            true
        }
    ) {
        // The map uses the Material bottom sheet/side panel instead of an info window.
    }
}

internal data class MapMarkerPalette(
    val primary: Int,
    val onPrimary: Int,
    val surface: Int,
    val onSurface: Int,
    val outline: Int,
    val error: Int,
    val onError: Int
)

internal data class MapMarkerIcon(
    val bitmap: Bitmap,
    val anchor: Offset
)

internal data class MapMarkerIconRequest(
    val sizePx: Int,
    val categoryCode: String,
    val speciesName: String,
    val speciesImageUrl: String?,
    val endTime: String?,
    val showTimeLabel: Boolean,
    val timeLabel: String?,
    val palette: MapMarkerPalette,
    val goDexStatus: GoDexMatchStatus
)

/**
 * Byte-sized caps instead of entry counts: a single ARGB pin runs ~200 KB, so a 256-entry
 * cache could quietly hold 50 MB. Sizing by bytes keeps the same hit-rate within a budget.
 */
private const val MARKER_ICON_CACHE_BYTES = 32 * 1024 * 1024
private const val MARKER_ARTWORK_CACHE_BYTES = 16 * 1024 * 1024
private const val CLUSTER_BITMAP_CACHE_BYTES = 8 * 1024 * 1024

internal val markerIconCache = object : LruCache<String, MapMarkerIcon>(MARKER_ICON_CACHE_BYTES) {
    override fun sizeOf(key: String, value: MapMarkerIcon): Int = value.bitmap.byteCount
}
internal val markerArtworkCache = object : LruCache<String, Bitmap>(MARKER_ARTWORK_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

internal fun mapMarkerArtworkCacheKey(url: String, sizePx: Int): String = "$sizePx|$url"

internal suspend fun loadMapMarkerArtwork(
    context: android.content.Context,
    url: String,
    sizePx: Int
): Bitmap? {
    val cacheKey = mapMarkerArtworkCacheKey(url, sizePx)
    markerArtworkCache.get(cacheKey)?.let { return it }
    val imageRequest = ImageRequest.Builder(context)
        .data(url)
        .allowHardware(false)
        .size(sizePx, sizePx)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
    val drawable = (PokemonAlertsApplication.imageLoader(context).execute(imageRequest) as? SuccessResult)
        ?.drawable ?: return null
    currentCoroutineContext().ensureActive()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(Canvas(bitmap))
    markerArtworkCache.put(cacheKey, bitmap)
    return bitmap
}

internal fun mapMarkerIconCacheKey(
    request: MapMarkerIconRequest,
    nowMillis: Long = System.currentTimeMillis()
): String = listOf(
    "material3-marker-compact",
    request.sizePx,
    request.categoryCode,
    request.speciesName,
    request.speciesImageUrl.orEmpty(),
    request.showTimeLabel,
    request.timeLabel.orEmpty(),
    isMapMarkerUrgent(request.endTime, nowMillis),
    request.palette,
    request.goDexStatus
).joinToString("|")

internal fun resolveInitialMapMarkerIcon(
    request: MapMarkerIconRequest,
    cacheKey: String = mapMarkerIconCacheKey(request)
): MapMarkerIcon = markerIconCache.get(cacheKey) ?: createFallbackMapMarkerIcon(request)

internal fun createFallbackMapMarkerIcon(
    request: MapMarkerIconRequest,
    nowMillis: Long = System.currentTimeMillis()
): MapMarkerIcon {
    val sizePx = request.sizePx
    val padding = (sizePx * 0.14f).toInt()
    val pinRadius = sizePx * 0.33f
    val tailHeight = sizePx * 0.20f
    val labelGap = (sizePx * 0.07f).toInt()
    val timeHeight = if (request.showTimeLabel && request.timeLabel != null) {
        (sizePx * 0.26f).toInt().coerceAtLeast(16)
    } else {
        0
    }
    val totalWidth = sizePx + padding * 2
    val totalHeight = (
        padding + pinRadius * 2 + tailHeight +
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
    val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = request.palette.primary
    }
    canvas.drawPath(tailPath, primaryPaint)
    canvas.drawCircle(centerX, centerY, pinRadius, primaryPaint)
    val innerRadius = pinRadius * 0.71f
    canvas.drawCircle(
        centerX,
        centerY,
        innerRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = request.palette.surface }
    )
    val cachedArtwork = request.speciesImageUrl?.let { url ->
        markerArtworkCache.get(mapMarkerArtworkCacheKey(url, sizePx))
    }
    if (cachedArtwork != null) {
        val artworkPath = android.graphics.Path().apply {
            addCircle(centerX, centerY, innerRadius, android.graphics.Path.Direction.CW)
        }
        val checkpoint = canvas.save()
        canvas.clipPath(artworkPath)
        val availableSize = innerRadius * 2.45f
        canvas.drawBitmap(
            cachedArtwork,
            null,
            android.graphics.RectF(
                centerX - availableSize / 2f,
                centerY - availableSize / 2f,
                centerX + availableSize / 2f,
                centerY + availableSize / 2f
            ),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
        canvas.restoreToCount(checkpoint)
    } else {
        val initialsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = request.palette.onSurface
            textSize = innerRadius * 0.68f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val initials = request.speciesName
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifBlank { request.categoryCode }
        val initialsY = centerY - (initialsPaint.descent() + initialsPaint.ascent()) / 2f
        canvas.drawText(initials, centerX, initialsY, initialsPaint)
    }
    canvas.drawMarkerLabel(
        centerX = centerX,
        top = centerY + innerRadius * 0.34f,
        height = sizePx * 0.18f,
        text = request.categoryCode,
        background = request.palette.surface,
        foreground = request.palette.primary,
        outline = request.palette.primary,
        maxWidth = pinRadius * 1.55f
    )
    if (timeHeight > 0 && request.timeLabel != null) {
        val urgent = isMapMarkerUrgent(request.endTime, nowMillis)
        canvas.drawMarkerLabel(
            centerX = centerX,
            top = pinTipY + labelGap,
            height = timeHeight.toFloat(),
            text = request.timeLabel,
            background = if (urgent) request.palette.error else request.palette.surface,
            foreground = if (urgent) request.palette.onError else request.palette.onSurface,
            outline = if (urgent) request.palette.error else request.palette.outline,
            maxWidth = totalWidth - padding * 2f
        )
    }
    return MapMarkerIcon(
        bitmap = bitmap,
        anchor = Offset(0.5f, (pinTipY / totalHeight).coerceIn(0f, 1f))
    )
}

internal fun isMapMarkerUrgent(endTime: String?, nowMillis: Long): Boolean {
    val timeRemainingMs = endTime?.let(TimeUtils::parseEndTimeToMillis)?.let {
        it - nowMillis
    } ?: Long.MAX_VALUE
    return timeRemainingMs < 10 * 60 * 1_000
}

internal suspend fun createMapMarkerIcon(
    context: android.content.Context,
    sizePx: Int,
    categoryCode: String,
    speciesName: String,
    speciesImageUrl: String?,
    endTime: String?,
    showTimeLabel: Boolean,
    timeLabel: String?,
    palette: MapMarkerPalette,
    goDexStatus: GoDexMatchStatus = GoDexMatchStatus.NOT_CONFIGURED
): MapMarkerIcon? {
    try {
        val request = MapMarkerIconRequest(
            sizePx = sizePx,
            categoryCode = categoryCode,
            speciesName = speciesName,
            speciesImageUrl = speciesImageUrl,
            endTime = endTime,
            showTimeLabel = showTimeLabel,
            timeLabel = timeLabel,
            palette = palette,
            goDexStatus = goDexStatus
        )
        val isUrgent = isMapMarkerUrgent(endTime, System.currentTimeMillis())
        val cacheKey = mapMarkerIconCacheKey(request)
        markerIconCache.get(cacheKey)?.let { return it }

        // Artwork is static. Keep it out of the once-per-second countdown path so a slow
        // image request can never hold back the next visible second.
        val speciesBitmap = speciesImageUrl?.let { url ->
            loadMapMarkerArtwork(context, url, sizePx)
        }

        val padding = (sizePx * 0.14f).toInt()
        val pinRadius = sizePx * 0.33f
        val tailHeight = sizePx * 0.20f
        val labelGap = (sizePx * 0.07f).toInt()
        val timeHeight = if (showTimeLabel && timeLabel != null) {
            (sizePx * 0.26f).toInt().coerceAtLeast(16)
        } else {
            0
        }
        val totalWidth = sizePx + padding * 2
        val totalHeight = (
            padding + pinRadius * 2 + tailHeight +
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

        if (speciesBitmap != null) {
            val spritePath = android.graphics.Path().apply {
                addCircle(centerX, centerY, innerRadius, android.graphics.Path.Direction.CW)
            }
            val checkpoint = canvas.save()
            canvas.clipPath(spritePath)
            val availableSize = innerRadius * 2.45f
            val destination = android.graphics.RectF(
                centerX - availableSize / 2f,
                centerY - availableSize / 2f,
                centerX + availableSize / 2f,
                centerY + availableSize / 2f
            )
            canvas.drawBitmap(speciesBitmap, null, destination, Paint(Paint.ANTI_ALIAS_FLAG))
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

        if (goDexStatus == GoDexMatchStatus.NEEDED ||
            goDexStatus == GoDexMatchStatus.EVOLUTION_NEEDED ||
            goDexStatus == GoDexMatchStatus.FORM_CHANGE_NEEDED ||
            goDexStatus == GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED
        ) {
            val badgeRadius = sizePx * 0.16f
            val badgeX = centerX - pinRadius * 0.70f
            val badgeY = centerY - pinRadius * 0.66f

            canvas.drawCircle(badgeX, badgeY, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.surface
            })
            canvas.drawCircle(
                badgeX,
                badgeY,
                badgeRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.outline
                    style = Paint.Style.STROKE
                    strokeWidth = sizePx * 0.022f
                }
            )

            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = badgeRadius * 1.3f
                textAlign = Paint.Align.CENTER
            }
            val emojiStr = if (goDexStatus == GoDexMatchStatus.NEEDED) "🎯" else "🧬"
            val textY = badgeY - (emojiPaint.descent() + emojiPaint.ascent()) / 2f
            canvas.drawText(emojiStr, badgeX, textY, emojiPaint)
        }

        // Keep the category identity on the pin itself. A permanent label below every
        // marker quickly overlaps at real alert density; the optional countdown remains
        // user-controlled and the selected sheet carries the full label and details.
        val categoryHeight = sizePx * 0.18f
        canvas.drawMarkerLabel(
            centerX = centerX,
            top = centerY + innerRadius * 0.34f,
            height = categoryHeight,
            text = categoryCode,
            background = palette.surface,
            foreground = palette.primary,
            outline = palette.primary,
            maxWidth = pinRadius * 1.55f
        )

        if (timeHeight > 0 && timeLabel != null) {
            canvas.drawMarkerLabel(
                centerX = centerX,
                top = pinTipY + labelGap,
                height = timeHeight.toFloat(),
                text = timeLabel,
                background = if (isUrgent) palette.error else palette.surface,
                foreground = if (isUrgent) palette.onError else palette.onSurface,
                outline = if (isUrgent) palette.error else palette.outline,
                maxWidth = totalWidth - padding * 2f
            )
        }

        val icon = MapMarkerIcon(
            bitmap = bitmap,
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

internal fun Canvas.drawMarkerLabel(
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

/**
 * Cluster bubbles differ only by category, count and size, so a membership change that keeps
 * those three stable reuses the same bitmap instead of redrawing one per cluster per update.
 */
private val clusterBitmapCache = object : LruCache<String, Bitmap>(CLUSTER_BITMAP_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

internal fun createClusterMarkerBitmap(
    context: android.content.Context,
    count: Int,
    sharedCategory: AlertCategory?,
    sizeDp: Float = MAP_FULL_CLUSTER_SIZE_DP
): Bitmap {
    val cacheKey = listOf(sharedCategory?.name.orEmpty(), count, sizeDp).joinToString("|")
    clusterBitmapCache.get(cacheKey)?.let { return it }
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = sharedCategory?.accentArgb?.toInt() ?: 0xFF455A64.toInt()
    canvas.drawCircle(size / 2f, size / 2f, size * 0.44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
    })
    canvas.drawCircle(size / 2f, size / 2f, size * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill
    })
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = size * if (count >= 100) 0.31f else 0.38f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(
        if (count > 999) "999+" else count.toString(),
        size / 2f,
        size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f,
        textPaint
    )
    clusterBitmapCache.put(cacheKey, bitmap)
    return bitmap
}

internal const val ALSBACH_LATITUDE = 49.74677
internal const val ALSBACH_LONGITUDE = 8.62492
internal const val DARMSTADT_LATITUDE = 49.87275
internal const val DARMSTADT_LONGITUDE = 8.65112

/**
 * Rough centre of each scanned area, for answering "which area am I standing in?".
 *
 * The backend has no geometry to hand out, so a centre and a generous radius is as precise as
 * this can get — and it only ever decides whether to show a weather badge.
 */
internal val AREA_CENTRES: Map<String, Pair<Double, Double>> = mapOf(
    "Alsbach" to (ALSBACH_LATITUDE to ALSBACH_LONGITUDE),
    "Darmstadt" to (DARMSTADT_LATITUDE to DARMSTADT_LONGITUDE)
)

/** The scanned areas, in picker order. Single source for onboarding, settings and history. */
val SCANNED_AREAS: List<String> = AREA_CENTRES.keys.toList()

/** [SCANNED_AREAS] prefixed with the "no area filter" option. */
val AREA_FILTER_OPTIONS: List<String> = listOf("All") + SCANNED_AREAS

private const val AREA_RADIUS_METERS = 15_000.0
private const val EARTH_RADIUS_METERS = 6_371_000.0

/** The nearest scanned area the user is standing in, or null when they are outside all of them. */
internal fun areaAtLocation(latitude: Double, longitude: Double): String? = AREA_CENTRES
    .mapNotNull { (area, centre) ->
        val distance = metersBetween(latitude, longitude, centre.first, centre.second)
        if (distance <= AREA_RADIUS_METERS) area to distance else null
    }
    .minByOrNull { it.second }
    ?.first

/**
 * Great-circle distance, so the area check stays a pure function.
 *
 * `Location.distanceBetween` would do the same job but only on a device, which would put an
 * emulator between this rule and any test of it.
 */
private fun metersBetween(
    latitude: Double,
    longitude: Double,
    otherLatitude: Double,
    otherLongitude: Double
): Double {
    val deltaLat = Math.toRadians(otherLatitude - latitude)
    val deltaLon = Math.toRadians(otherLongitude - longitude)
    val a = sin(deltaLat / 2).pow(2) +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(otherLatitude)) *
        sin(deltaLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}
internal const val MAP_FULL_MARKER_SIZE_DP = 68f
internal const val MAP_PIP_MARKER_SIZE_DP = 44f
internal const val MAP_PIP_EMPHASIZED_MARKER_SIZE_DP = 50f
internal const val MAP_FULL_CLUSTER_SIZE_DP = 48f
internal const val MAP_PIP_CLUSTER_SIZE_DP = 44f
internal const val MAP_CLUSTER_MARKER_Z_INDEX = 850f
internal const val MAP_EMPHASIZED_MARKER_Z_INDEX = 900f

internal fun mapAlertMarkerSizeDp(
    compactPictureInPicture: Boolean,
    emphasized: Boolean
): Float = when {
    !compactPictureInPicture -> MAP_FULL_MARKER_SIZE_DP
    emphasized -> MAP_PIP_EMPHASIZED_MARKER_SIZE_DP
    else -> MAP_PIP_MARKER_SIZE_DP
}

internal fun mapClusterMarkerSizeDp(compactPictureInPicture: Boolean): Float =
    if (compactPictureInPicture) MAP_PIP_CLUSTER_SIZE_DP else MAP_FULL_CLUSTER_SIZE_DP

internal const val USER_LOCATION_ZOOM = 16f
internal const val ALERT_LOCATION_ZOOM = 14f
internal const val ALSBACH_ZOOM = 13f

internal const val LightMapStyle = """
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

internal const val DarkMapStyle = """
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
