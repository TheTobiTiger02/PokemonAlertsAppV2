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
    emphasized: Boolean = false,
    stackCount: Int = 1
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
    val isHundo = visualStyle.category == AlertCategory.HUNDO || alert.formattedIv == "100%" || alert.iv == "100"
    val isNundo = visualStyle.category == AlertCategory.NUNDO || alert.formattedIv == "0%"
    val isPvp = visualStyle.category == AlertCategory.PVP || !alert.pvpRankings.isNullOrEmpty()
    val isRare = visualStyle.category == AlertCategory.RARE
    val questQuantity = remember(alert.questReward) { extractQuestQuantity(alert.questReward) }
    val isRocket = visualStyle.category == AlertCategory.ROCKET || alert.gruntType != null || alert.type?.contains("Rocket") == true
    val isKecleon = alert.pokemon?.contains("Kecleon", ignoreCase = true) == true
    val raidTier = remember(visualStyle.category, alert.pokemonForm, alert.type) {
        resolveRaidTier(alert, visualStyle.category)
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
        goDexMatchResult.status,
        stackCount,
        visualStyle.category,
        isHundo,
        isNundo,
        isPvp,
        isRare,
        questQuantity,
        raidTier,
        isRocket,
        isKecleon
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
            goDexStatus = goDexMatchResult.status,
            stackCount = stackCount,
            category = visualStyle.category,
            isHundo = isHundo,
            isNundo = isNundo,
            isPvp = isPvp,
            isRare = isRare,
            questQuantity = questQuantity,
            raidTier = raidTier,
            isRocket = isRocket,
            isKecleon = isKecleon
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
                goDexStatus = markerIconRequest.goDexStatus,
                stackCount = markerIconRequest.stackCount,
                category = markerIconRequest.category,
                isHundo = markerIconRequest.isHundo,
                isNundo = markerIconRequest.isNundo,
                isPvp = markerIconRequest.isPvp,
                isRare = markerIconRequest.isRare,
                questQuantity = markerIconRequest.questQuantity,
                raidTier = markerIconRequest.raidTier,
                isRocket = markerIconRequest.isRocket,
                isKecleon = markerIconRequest.isKecleon
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
        title = if (stackCount > 1) {
            "${formatAlertTitle(alert, goDexMatchResult.status)} (+${stackCount - 1} more)"
        } else {
            formatAlertTitle(alert, goDexMatchResult.status)
        },
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
    val goDexStatus: GoDexMatchStatus,
    val stackCount: Int = 1,
    val category: AlertCategory = AlertCategory.SPAWN,
    val isHundo: Boolean = false,
    val isNundo: Boolean = false,
    val isPvp: Boolean = false,
    val isRare: Boolean = false,
    val questQuantity: String? = null,
    val raidTier: String? = null,
    val isRocket: Boolean = false,
    val isKecleon: Boolean = false
)

internal fun extractQuestQuantity(questReward: String?): String? {
    if (questReward.isNullOrBlank()) return null
    val trimmed = questReward.trim()
    val match = Regex("""^(?:x\s*)?(\d+)\s*(.*)""", RegexOption.IGNORE_CASE).find(trimmed) ?: return null
    val amount = match.groupValues[1]
    val item = match.groupValues[2].lowercase()
    return if (item.contains("stardust") || item.contains("xp") || item.contains("dust")) {
        amount
    } else {
        "x$amount"
    }
}

internal fun resolveRaidTier(alert: PokemonAlert, category: AlertCategory): String? {
    if (category != AlertCategory.RAID) return null
    if (alert.pokemonForm?.contains("Mega", ignoreCase = true) == true) return "Mega"
    val tierMatch = alert.type?.firstNotNullOfOrNull { typeStr ->
        Regex("""(?:t|tier\s*)(\d+)""", RegexOption.IGNORE_CASE).find(typeStr)?.groupValues?.get(1)
    }
    return if (tierMatch != null) "T$tierMatch" else "Raid"
}

/**
 * Byte-sized caps instead of entry counts: a single ARGB pin runs ~200 KB, so a 256-entry
 * cache could quietly hold 50 MB. Sizing by bytes keeps the same hit-rate within a budget.
 */
private const val MARKER_ICON_CACHE_BYTES = 32 * 1024 * 1024
private const val MARKER_ARTWORK_CACHE_BYTES = 16 * 1024 * 1024
private const val CLUSTER_BITMAP_CACHE_BYTES = 8 * 1024 * 1024

// One bitmap per weather condition, so a handful of small circles at most.
private const val WEATHER_BITMAP_CACHE_BYTES = 1 * 1024 * 1024

internal val markerIconCache = object : LruCache<String, MapMarkerIcon>(MARKER_ICON_CACHE_BYTES) {
    override fun sizeOf(key: String, value: MapMarkerIcon): Int = value.bitmap.byteCount
}
internal val markerArtworkCache = object : LruCache<String, Bitmap>(MARKER_ARTWORK_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/**
 * Fallback pins are drawn during composition, so a cold entry with a full screen of markers
 * used to run up to [MAX_RENDERED_MAP_MARKERS] Canvas rasterizations on the main thread.
 * They are deterministic for a given cache key, so caching them separately from the finished
 * icons makes every re-entry a lookup while still letting the IO renderer replace them.
 */
private const val MARKER_FALLBACK_CACHE_BYTES = 8 * 1024 * 1024
internal val markerFallbackCache = object : LruCache<String, MapMarkerIcon>(MARKER_FALLBACK_CACHE_BYTES) {
    override fun sizeOf(key: String, value: MapMarkerIcon): Int = value.bitmap.byteCount
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
    "wingullmap-marker-v1",
    request.sizePx,
    request.categoryCode,
    request.speciesName,
    request.speciesImageUrl.orEmpty(),
    request.showTimeLabel,
    request.timeLabel.orEmpty(),
    isMapMarkerUrgent(request.endTime, nowMillis),
    request.palette,
    request.goDexStatus,
    request.stackCount,
    request.category.name,
    request.isHundo,
    request.isNundo,
    request.isPvp,
    request.isRare,
    request.questQuantity.orEmpty(),
    request.raidTier.orEmpty(),
    request.isRocket,
    request.isKecleon
).joinToString("|")

internal fun resolveInitialMapMarkerIcon(
    request: MapMarkerIconRequest,
    cacheKey: String = mapMarkerIconCacheKey(request)
): MapMarkerIcon = markerIconCache.get(cacheKey)
    ?: markerFallbackCache.get(cacheKey)
    ?: createFallbackMapMarkerIcon(request).also { markerFallbackCache.put(cacheKey, it) }

internal fun renderMapMarkerToCanvas(
    canvas: Canvas,
    request: MapMarkerIconRequest,
    speciesBitmap: Bitmap?,
    totalWidth: Int,
    totalHeight: Int,
    groundY: Float,
    isUrgent: Boolean
) {
    val sizePx = request.sizePx
    val centerX = totalWidth / 2f
    val spriteAreaSize = sizePx * 0.85f
    val spriteCenterY = groundY - spriteAreaSize / 2f

    // 1. BASE AT GROUND (PokéStop disc, Gym base, or perspective ground shadow)
    when {
        request.category == AlertCategory.QUEST -> {
            // PokéStop Base Disc (Wingullmap Quest style)
            val discRadiusX = sizePx * 0.22f
            val discRadiusY = sizePx * 0.08f
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.BLACK
                alpha = 50
            }
            canvas.drawOval(
                centerX - discRadiusX * 1.1f, groundY - discRadiusY * 0.4f,
                centerX + discRadiusX * 1.1f, groundY + discRadiusY * 1.6f,
                shadowPaint
            )
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(30, 136, 229)
            }
            canvas.drawOval(
                centerX - discRadiusX, groundY - discRadiusY,
                centerX + discRadiusX, groundY + discRadiusY,
                discPaint
            )
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.024f
            }
            canvas.drawOval(
                centerX - discRadiusX, groundY - discRadiusY,
                centerX + discRadiusX, groundY + discRadiusY,
                rimPaint
            )
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(187, 222, 251)
            }
            canvas.drawOval(
                centerX - discRadiusX * 0.55f, groundY - discRadiusY * 0.55f,
                centerX + discRadiusX * 0.55f, groundY + discRadiusY * 0.55f,
                corePaint
            )
        }
        request.isRocket || request.category == AlertCategory.ROCKET -> {
            // Dark Rocket PokéStop Base Disc
            val discRadiusX = sizePx * 0.22f
            val discRadiusY = sizePx * 0.08f
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.BLACK
                alpha = 60
            }
            canvas.drawOval(
                centerX - discRadiusX * 1.1f, groundY - discRadiusY * 0.4f,
                centerX + discRadiusX * 1.1f, groundY + discRadiusY * 1.6f,
                shadowPaint
            )
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(33, 33, 33)
            }
            canvas.drawOval(
                centerX - discRadiusX, groundY - discRadiusY,
                centerX + discRadiusX, groundY + discRadiusY,
                discPaint
            )
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(211, 47, 47)
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.025f
            }
            canvas.drawOval(
                centerX - discRadiusX, groundY - discRadiusY,
                centerX + discRadiusX, groundY + discRadiusY,
                rimPaint
            )
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(136, 14, 79)
            }
            canvas.drawOval(
                centerX - discRadiusX * 0.55f, groundY - discRadiusY * 0.55f,
                centerX + discRadiusX * 0.55f, groundY + discRadiusY * 0.55f,
                corePaint
            )
        }
        request.isKecleon || request.category == AlertCategory.KECLEON -> {
            val discRadiusX = sizePx * 0.20f
            val discRadiusY = sizePx * 0.08f
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(30, 136, 229) }
            canvas.drawOval(centerX - discRadiusX, groundY - discRadiusY, centerX + discRadiusX, groundY + discRadiusY, discPaint)
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = sizePx * 0.02f }
            canvas.drawOval(centerX - discRadiusX, groundY - discRadiusY, centerX + discRadiusX, groundY + discRadiusY, rimPaint)
        }
        request.category == AlertCategory.RAID || request.raidTier != null -> {
            val gymRadiusX = sizePx * 0.26f
            val gymRadiusY = sizePx * 0.09f
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; alpha = 50 }
            canvas.drawOval(centerX - gymRadiusX * 1.1f, groundY - gymRadiusY * 0.3f, centerX + gymRadiusX * 1.1f, groundY + gymRadiusY * 1.6f, shadowPaint)
            val gymPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(158, 158, 158) }
            canvas.drawOval(centerX - gymRadiusX, groundY - gymRadiusY, centerX + gymRadiusX, groundY + gymRadiusY, gymPaint)
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(255, 179, 0); style = Paint.Style.STROKE; strokeWidth = sizePx * 0.025f }
            canvas.drawOval(centerX - gymRadiusX, groundY - gymRadiusY, centerX + gymRadiusX, groundY + gymRadiusY, rimPaint)
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(238, 238, 238) }
            canvas.drawOval(centerX - gymRadiusX * 0.6f, groundY - gymRadiusY * 0.6f, centerX + gymRadiusX * 0.6f, groundY + gymRadiusY * 0.6f, corePaint)
        }
        else -> {
            // Ground Drop Shadow for standard/rare/pvp/hundo Pokémon spawns
            val shadowRadiusX = sizePx * 0.24f
            val shadowRadiusY = sizePx * 0.08f
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.BLACK
                alpha = 50
            }
            canvas.drawOval(
                centerX - shadowRadiusX, groundY - shadowRadiusY * 0.5f,
                centerX + shadowRadiusX, groundY + shadowRadiusY * 1.5f,
                shadowPaint
            )
        }
    }

    // 2. GLOWING HALOS BEHIND SPRITE
    // Decision: Hundo/Nundo gets Red glow, PvP gets Blue glow, Rare spawns get NO glow!
    val isHundoOrNundo = request.isHundo || request.isNundo ||
        request.category == AlertCategory.HUNDO || request.category == AlertCategory.NUNDO
    val isPvp = request.isPvp || request.category == AlertCategory.PVP

    if (isHundoOrNundo) {
        val glowRadius = spriteAreaSize * 0.62f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                centerX,
                spriteCenterY,
                glowRadius,
                intArrayOf(
                    AndroidColor.argb(165, 255, 59, 48),
                    AndroidColor.argb(80, 255, 59, 48),
                    AndroidColor.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, spriteCenterY, glowRadius, glowPaint)
    } else if (isPvp) {
        val glowRadius = spriteAreaSize * 0.62f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                centerX,
                spriteCenterY,
                glowRadius,
                intArrayOf(
                    AndroidColor.argb(165, 47, 128, 237),
                    AndroidColor.argb(80, 47, 128, 237),
                    AndroidColor.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, spriteCenterY, glowRadius, glowPaint)
    }

    // 3. SPRITE DRAWING (Borderless cutout, natural silhouette)
    if (speciesBitmap != null) {
        val bmpWidth = speciesBitmap.width.toFloat()
        val bmpHeight = speciesBitmap.height.toFloat()
        val scale = kotlin.math.min(spriteAreaSize / bmpWidth, spriteAreaSize / bmpHeight)
        val drawWidth = bmpWidth * scale
        val drawHeight = bmpHeight * scale
        val left = centerX - drawWidth / 2f
        val top = groundY - drawHeight
        val destRect = android.graphics.RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(
            speciesBitmap,
            null,
            destRect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    } else {
        val fallbackRadius = spriteAreaSize * 0.38f
        val fallbackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = request.palette.surface
            alpha = 230
        }
        canvas.drawCircle(centerX, spriteCenterY, fallbackRadius, fallbackBgPaint)
        val fallbackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = request.palette.primary
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.03f
        }
        canvas.drawCircle(centerX, spriteCenterY, fallbackRadius, fallbackStroke)
        val initialsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = request.palette.onSurface
            textSize = fallbackRadius * 0.85f
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
        val initialsY = spriteCenterY - (initialsPaint.descent() + initialsPaint.ascent()) / 2f
        canvas.drawText(initials, centerX, initialsY, initialsPaint)
    }

    // 4. QUEST QUANTITY LABEL (e.g. "x3", "500")
    if (!request.questQuantity.isNullOrBlank()) {
        val qty = request.questQuantity
        val qtyOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            textSize = sizePx * 0.22f
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.045f
            isFakeBoldText = true
        }
        val qtyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = sizePx * 0.22f
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            isFakeBoldText = true
        }
        val qtyX = centerX + sizePx * 0.16f
        val qtyY = groundY - sizePx * 0.06f
        canvas.drawText(qty, qtyX, qtyY, qtyOutlinePaint)
        canvas.drawText(qty, qtyX, qtyY, qtyFillPaint)
    }

    // 5. ROCKET 'R' BADGE
    if (request.isRocket || request.category == AlertCategory.ROCKET) {
        val rBadgeRadius = sizePx * 0.12f
        val rBadgeX = centerX + sizePx * 0.26f
        val rBadgeY = groundY - sizePx * 0.16f
        val rBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(211, 47, 47)
        }
        val rStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.025f
        }
        val rTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = rBadgeRadius * 1.35f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawCircle(rBadgeX, rBadgeY, rBadgeRadius, rBgPaint)
        canvas.drawCircle(rBadgeX, rBadgeY, rBadgeRadius, rStrokePaint)
        val rTextY = rBadgeY - (rTextPaint.descent() + rTextPaint.ascent()) / 2f
        canvas.drawText("R", rBadgeX, rTextY, rTextPaint)
    }

    // 6. RAID TIER BADGE
    if (!request.raidTier.isNullOrBlank()) {
        val tierText = request.raidTier
        val tierRadius = sizePx * 0.11f
        val tierX = centerX - sizePx * 0.26f
        val tierY = groundY - spriteAreaSize * 0.88f
        val tierTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = tierRadius * 1.15f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val tierWidth = tierTextPaint.measureText(tierText)
        val tierHalfWidth = kotlin.math.max(tierRadius, tierWidth / 2f + sizePx * 0.04f)
        val tierRect = android.graphics.RectF(
            tierX - tierHalfWidth, tierY - tierRadius,
            tierX + tierHalfWidth, tierY + tierRadius
        )
        val tierBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(255, 179, 0)
        }
        val tierStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.02f
        }
        canvas.drawRoundRect(tierRect, tierRadius, tierRadius, tierBgPaint)
        canvas.drawRoundRect(tierRect, tierRadius, tierRadius, tierStrokePaint)
        val tCenterY = tierY - (tierTextPaint.descent() + tierTextPaint.ascent()) / 2f
        canvas.drawText(tierText, tierX, tCenterY, tierTextPaint)
    }

    // 7. STACK COUNT BADGE ("+N")
    if (request.stackCount > 1) {
        val badgeText = if (request.stackCount > 99) "+99" else "+${request.stackCount - 1}"
        val badgeRadius = sizePx * 0.14f
        val badgeX = centerX + sizePx * 0.28f
        val badgeY = groundY - spriteAreaSize * 0.88f
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isUrgent) request.palette.error else AndroidColor.rgb(211, 47, 47)
        }
        val badgeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.025f
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = badgeRadius * 1.15f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textWidth = badgeTextPaint.measureText(badgeText)
        val badgeHalfWidth = kotlin.math.max(badgeRadius, textWidth / 2f + sizePx * 0.05f)
        val badgeRect = android.graphics.RectF(
            badgeX - badgeHalfWidth, badgeY - badgeRadius,
            badgeX + badgeHalfWidth, badgeY + badgeRadius
        )
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeOutlinePaint)
        val textCenterY = badgeY - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(badgeText, badgeX, textCenterY, badgeTextPaint)
    }

    // 8. GODEX NEEDED BADGE
    if (request.goDexStatus == GoDexMatchStatus.NEEDED ||
        request.goDexStatus == GoDexMatchStatus.EVOLUTION_NEEDED ||
        request.goDexStatus == GoDexMatchStatus.FORM_CHANGE_NEEDED ||
        request.goDexStatus == GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED
    ) {
        val badgeRadius = sizePx * 0.11f
        val badgeX = centerX - sizePx * 0.28f
        val badgeY = groundY - spriteAreaSize * 0.88f
        canvas.drawCircle(
            badgeX, badgeY, badgeRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = request.palette.surface }
        )
        canvas.drawCircle(
            badgeX, badgeY, badgeRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = request.palette.outline
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.02f
            }
        )
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = badgeRadius * 1.3f
            textAlign = Paint.Align.CENTER
        }
        val emojiStr = if (request.goDexStatus == GoDexMatchStatus.NEEDED) "🎯" else "🧬"
        val textY = badgeY - (emojiPaint.descent() + emojiPaint.ascent()) / 2f
        canvas.drawText(emojiStr, badgeX, textY, emojiPaint)
    }

    // 9. COUNTDOWN TIMER LABEL
    if (request.showTimeLabel && !request.timeLabel.isNullOrBlank()) {
        val timeHeight = (sizePx * 0.22f).coerceAtLeast(16f)
        val labelGap = sizePx * 0.05f
        canvas.drawMarkerLabel(
            centerX = centerX,
            top = groundY + labelGap,
            height = timeHeight,
            text = request.timeLabel,
            background = if (isUrgent) request.palette.error else AndroidColor.argb(200, 26, 26, 26),
            foreground = AndroidColor.WHITE,
            outline = if (isUrgent) AndroidColor.WHITE else AndroidColor.TRANSPARENT,
            maxWidth = totalWidth.toFloat()
        )
    }
}

internal fun createFallbackMapMarkerIcon(
    request: MapMarkerIconRequest,
    nowMillis: Long = System.currentTimeMillis()
): MapMarkerIcon {
    val sizePx = request.sizePx
    val isUrgent = isMapMarkerUrgent(request.endTime, nowMillis)
    val cachedArtwork = request.speciesImageUrl?.let { url ->
        markerArtworkCache.get(mapMarkerArtworkCacheKey(url, sizePx))
    }

    val padding = (sizePx * 0.12f).toInt()
    val labelGap = (sizePx * 0.05f).toInt()
    val timeHeight = if (request.showTimeLabel && request.timeLabel != null) {
        (sizePx * 0.22f).toInt().coerceAtLeast(16)
    } else {
        0
    }
    val spriteAreaSize = sizePx * 0.85f
    val groundY = padding + spriteAreaSize
    val totalHeight = (groundY + (if (timeHeight > 0) labelGap + timeHeight else 0) + padding).toInt()
    val totalWidth = (sizePx * 1.25f).toInt()

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    renderMapMarkerToCanvas(
        canvas = canvas,
        request = request,
        speciesBitmap = cachedArtwork,
        totalWidth = totalWidth,
        totalHeight = totalHeight,
        groundY = groundY,
        isUrgent = isUrgent
    )

    return MapMarkerIcon(
        bitmap = bitmap,
        anchor = Offset(0.5f, (groundY / totalHeight).coerceIn(0f, 1f))
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
    goDexStatus: GoDexMatchStatus = GoDexMatchStatus.NOT_CONFIGURED,
    stackCount: Int = 1,
    category: AlertCategory = AlertCategory.SPAWN,
    isHundo: Boolean = false,
    isNundo: Boolean = false,
    isPvp: Boolean = false,
    isRare: Boolean = false,
    questQuantity: String? = null,
    raidTier: String? = null,
    isRocket: Boolean = false,
    isKecleon: Boolean = false
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
            goDexStatus = goDexStatus,
            stackCount = stackCount,
            category = category,
            isHundo = isHundo,
            isNundo = isNundo,
            isPvp = isPvp,
            isRare = isRare,
            questQuantity = questQuantity,
            raidTier = raidTier,
            isRocket = isRocket,
            isKecleon = isKecleon
        )
        val isUrgent = isMapMarkerUrgent(endTime, System.currentTimeMillis())
        val cacheKey = mapMarkerIconCacheKey(request)
        markerIconCache.get(cacheKey)?.let { return it }

        val speciesBitmap = speciesImageUrl?.let { url ->
            loadMapMarkerArtwork(context, url, sizePx)
        }

        val padding = (sizePx * 0.12f).toInt()
        val labelGap = (sizePx * 0.05f).toInt()
        val timeHeight = if (showTimeLabel && timeLabel != null) {
            (sizePx * 0.22f).toInt().coerceAtLeast(16)
        } else {
            0
        }
        val spriteAreaSize = sizePx * 0.85f
        val groundY = padding + spriteAreaSize
        val totalHeight = (groundY + (if (timeHeight > 0) labelGap + timeHeight else 0) + padding).toInt()
        val totalWidth = (sizePx * 1.25f).toInt()

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        renderMapMarkerToCanvas(
            canvas = canvas,
            request = request,
            speciesBitmap = speciesBitmap,
            totalWidth = totalWidth,
            totalHeight = totalHeight,
            groundY = groundY,
            isUrgent = isUrgent
        )

        val icon = MapMarkerIcon(
            bitmap = bitmap,
            anchor = Offset(0.5f, (groundY / totalHeight).coerceIn(0f, 1f))
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

/**
 * The weather glyph that sits in the middle of a weather cell.
 *
 * Emoji drawn onto a canvas rather than a vector asset: the app already speaks weather in
 * emoji everywhere else (see CurrentWeatherPresentation), and eight new drawables that must
 * stay in step with that mapping is a worse trade than one drawText.
 */
private val weatherCellBitmapCache = object : LruCache<String, Bitmap>(WEATHER_BITMAP_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

internal fun createWeatherCellBitmap(
    context: android.content.Context,
    glyph: String,
    confirmed: Boolean,
    sizeDp: Float = MAP_WEATHER_CELL_SIZE_DP
): Bitmap {
    val cacheKey = listOf(glyph, confirmed, sizeDp).joinToString("|")
    weatherCellBitmapCache.get(cacheKey)?.let { return it }
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val accent = AlertCategory.WEATHER.accentArgb.toInt()
    // Unconfirmed weather is still the best reading available, so it is shown rather than
    // hidden - just faded, exactly as the corner badge used to fade it.
    val alpha = if (confirmed) 255 else 150

    canvas.drawCircle(size / 2f, size / 2f, size * 0.46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        this.alpha = alpha
    })
    canvas.drawCircle(size / 2f, size / 2f, size * 0.44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 0.07f
        color = accent
        this.alpha = alpha
    })
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.46f
        textAlign = Paint.Align.CENTER
        this.alpha = alpha
    }
    canvas.drawText(
        glyph,
        size / 2f,
        size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f,
        textPaint
    )
    weatherCellBitmapCache.put(cacheKey, bitmap)
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
internal const val MAP_FULL_MARKER_SIZE_DP = 48f
internal const val MAP_PIP_MARKER_SIZE_DP = 36f
internal const val MAP_PIP_EMPHASIZED_MARKER_SIZE_DP = 44f
internal const val MAP_WEATHER_CELL_SIZE_DP = 34f
internal const val MAP_FULL_CLUSTER_SIZE_DP = 40f
internal const val MAP_PIP_CLUSTER_SIZE_DP = 34f
internal const val MAP_CLUSTER_MARKER_Z_INDEX = 850f
internal const val MAP_EMPHASIZED_MARKER_Z_INDEX = 900f

internal fun mapAlertMarkerSizeDp(
    compactPictureInPicture: Boolean,
    emphasized: Boolean,
    zoom: Float? = null
): Float = when {
    compactPictureInPicture && emphasized -> MAP_PIP_EMPHASIZED_MARKER_SIZE_DP
    compactPictureInPicture -> MAP_PIP_MARKER_SIZE_DP
    zoom != null -> when {
        zoom < 13f -> 36f
        zoom < 15.5f -> 44f
        else -> 50f
    }
    else -> MAP_FULL_MARKER_SIZE_DP
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
