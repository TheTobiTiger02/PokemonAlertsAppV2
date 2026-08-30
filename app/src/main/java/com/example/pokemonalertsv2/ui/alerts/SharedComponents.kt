@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AffectedAlert
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.counters.RaidCountersActions
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.ui.counters.RaidCountersScreen
import com.example.pokemonalertsv2.ui.counters.RaidCountersTeaser
import com.example.pokemonalertsv2.ui.counters.RaidCountersUiState
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexMatcher
import com.example.pokemonalertsv2.notifications.AlertSnoozeScheduler
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.tracking.rememberArrivalTrackingUiController
import com.example.pokemonalertsv2.ui.theme.Dimens
import com.example.pokemonalertsv2.ui.theme.MetricTextStyle
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.components.LinearModernCard
import com.example.pokemonalertsv2.ui.components.RollingNumberText
import com.example.pokemonalertsv2.ui.components.GradientText
import com.example.pokemonalertsv2.ui.theme.LocalAppDarkTheme
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.MapFallbackImageGenerator
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.util.DistanceSource
import com.example.pokemonalertsv2.util.validAlertCoordinates
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import com.example.pokemonalertsv2.util.TravelTime
import com.example.pokemonalertsv2.ui.theme.AppAccents

internal val ALERT_DETAIL_HERO_IMAGE_HEIGHT = Dimens.AlertDetailHeroHeight

@Immutable
data class AlertDistanceInfo(
    val distanceMeters: Float?,
    val distanceText: String?,
    val walkingText: String?,
    val straightLineDistanceMeters: Float? = distanceMeters,
    val routedWalkingDistanceMeters: Float? = null,
    val walkingDurationSeconds: Long? = null,
    val source: DistanceSource = when {
        routedWalkingDistanceMeters != null -> DistanceSource.ROUTED
        distanceMeters != null -> DistanceSource.DIRECT
        else -> DistanceSource.UNAVAILABLE
    }
)

@Immutable
data class AlertUiModel(
    val alert: PokemonAlert,
    val distanceInfo: AlertDistanceInfo,
    val endMillis: Long? = null,
    val typeKeys: Set<String> = emptySet()
)

internal enum class AlertCardContext {
    LIVE,
    HISTORY
}

internal enum class AlertSecondaryAction {
    SNOOZE,
    PICTURE_IN_PICTURE,
    SHARE
}

@Composable
fun rememberCountdownClock(tickMillis: Long = 1_000L): State<Long> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now = remember(tickMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner, tickMillis) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val currentTime = System.currentTimeMillis()
                now.longValue = currentTime
                delay(countdownTickDelay(currentTime, tickMillis))
            }
        }
    }
    return now
}

internal fun countdownTickDelay(nowMillis: Long, tickMillis: Long): Long {
    require(tickMillis > 0L) { "tickMillis must be positive" }
    val positionInTick = Math.floorMod(nowMillis, tickMillis)
    return (tickMillis - positionInTick).coerceAtLeast(1L)
}

@Immutable
internal data class AlertActionPolicy(
    val showGoing: Boolean,
    val showNavigate: Boolean,
    val overflowActions: List<AlertSecondaryAction>
)

internal fun alertActionPolicy(
    context: AlertCardContext,
    isExpired: Boolean,
    snoozeEnabled: Boolean,
    hasGoingAction: Boolean
): AlertActionPolicy {
    val isLiveAndActive = context == AlertCardContext.LIVE && !isExpired
    return AlertActionPolicy(
        showGoing = isLiveAndActive && hasGoingAction,
        showNavigate = !isExpired,
        overflowActions = buildList {
            if (isLiveAndActive && snoozeEnabled) add(AlertSecondaryAction.SNOOZE)
            if (!isExpired) add(AlertSecondaryAction.PICTURE_IN_PICTURE)
            add(AlertSecondaryAction.SHARE)
        }
    )
}

internal val NoGoDexMatch = GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED)

@Composable
internal fun rememberGoDexMatchResults(
    alerts: List<PokemonAlert>
): Map<String, GoDexMatchResult> {
    if (alerts.none { it.hasType("hundo") }) return emptyMap()
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { GoDexRepository.getInstance(context) }
    val entries by repository.entries.collectAsStateWithLifecycle()
    val config by repository.config.collectAsStateWithLifecycle()
    return rememberGoDexMatchResults(alerts, entries, config.isConnected)
}

@Composable
internal fun rememberGoDexMatchResults(
    alerts: List<PokemonAlert>,
    entries: List<GoDexEntryEntity>,
    configured: Boolean
): Map<String, GoDexMatchResult> {
    if (alerts.none { it.hasType("hundo") }) return emptyMap()
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { GoDexRepository.getInstance(context) }
    val matches by produceState<Map<String, GoDexMatchResult>>(
        initialValue = emptyMap(),
        alerts,
        entries,
        configured
    ) {
        value = withContext(Dispatchers.Default) {
            alerts.asSequence()
                .filter { it.hasType("hundo") }
                .associate { alert ->
                    alert.uniqueId to repository.match(
                        alert = alert,
                        snapshot = entries,
                        configured = configured
                    )
                }
        }
    }
    return matches
}

@Composable
internal fun AlertCountdownBadge(
    endTime: String,
    categoryAccent: Color,
    categoryOnAccent: Color,
    countdownClock: State<Long>
) {
    val endMillis = remember(endTime) { TimeUtils.parseEndTimeToMillis(endTime) }
    val remaining = endMillis?.minus(countdownClock.value)
    val countdown = when {
        remaining == null -> "TIME --"
        remaining <= 0 -> "EXPIRED"
        else -> TimeUtils.formatDurationShort(remaining)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (remaining != null && remaining <= 0) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            categoryAccent.copy(alpha = 0.92f)
        }
    ) {
        val badgeColor = if (remaining != null && remaining <= 0) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            categoryOnAccent
        }
        if (remaining != null && remaining > 0) {
            RollingNumberText(
                text = countdown,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MetricTextStyle,
                color = badgeColor
            )
        } else {
            // "EXPIRED" / "TIME --" hold still; only a running clock rolls.
            Text(
                text = countdown,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MetricTextStyle,
                color = badgeColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Generates a descriptive title for an alert based on its type.
 * Examples: "100% Togetic", "0% Togetic", "Great #1 Togetic", "Terrakion Raid", "Psychic Rocket"
 */
fun formatAlertTitle(alert: PokemonAlert, goDexStatus: GoDexMatchStatus = GoDexMatchStatus.NOT_CONFIGURED): String {
    val baseTitle = formatAlertTitleRaw(alert)
    val prefix = when (goDexStatus) {
        GoDexMatchStatus.NEEDED -> "🎯 "
        GoDexMatchStatus.EVOLUTION_NEEDED,
        GoDexMatchStatus.FORM_CHANGE_NEEDED,
        GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED -> "🧬 "
        else -> ""
    }
    return "$prefix$baseTitle"
}

internal fun formatAlertTitleRaw(alert: PokemonAlert): String {
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
        weatherTransitionLabel(alert)?.let { return it }
        return "🌦️ $baseName Changed"
    }
    
    // Handle Team Rocket - show grunt type
    if (alert.hasTypeContaining("rocket") || alert.gruntType != null) {
        val gruntLabel = alert.gruntType
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercaseChar() }
        return when {
            gruntLabel == null || gruntLabel.equals("rocket", ignoreCase = true) -> "Team Rocket"
            gruntLabel.endsWith("rocket", ignoreCase = true) -> gruntLabel
            else -> "$gruntLabel Rocket"
        }
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

internal fun shouldShowAlertCategoryLabel(title: String, categoryLabel: String): Boolean {
    val titleWords = title.lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9]+"))
        .filterTo(mutableSetOf()) { it.isNotBlank() }
    val categoryWords = categoryLabel.lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }
    return categoryWords.isEmpty() || !titleWords.containsAll(categoryWords)
}


internal fun PokemonAlert.isExpiredAt(nowMillis: Long): Boolean =
    TimeUtils.parseEndTimeToMillis(endTime)?.let { it <= nowMillis } ?: false

@Composable
internal fun WeatherChangeCardSummary(alert: PokemonAlert) {
    val transition = weatherTransitionLabel(alert)
    val summaries = affectedAlertCardLines(alert)
    val overflow = affectedAlertOverflowCount(alert)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            transition?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (alert.affectedAlerts.isNotEmpty()) {
                Text(
                    text = "Affected Pokémon (${alert.affectedAlerts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                summaries.forEach { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (overflow > 0) {
                    Text(
                        text = "+$overflow more",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}


@Composable
fun AlertImage(
    alert: PokemonAlert,
    modifier: Modifier = Modifier,
    rounded: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    onMapFallbackChanged: ((Boolean) -> Unit)? = null
) {
    AlertImageContent(
        alert = alert,
        modifier = modifier,
        rounded = rounded,
        contentScale = contentScale,
        containEntireImage = false,
        onMapFallbackChanged = onMapFallbackChanged
    )
}

@Composable
internal fun ContainedAlertImage(
    alert: PokemonAlert,
    modifier: Modifier = Modifier
) {
    AlertImageContent(
        alert = alert,
        modifier = modifier,
        rounded = false,
        contentScale = ContentScale.Fit,
        containEntireImage = true
    )
}

@Composable
internal fun AlertImageContent(
    alert: PokemonAlert,
    modifier: Modifier,
    rounded: Boolean,
    contentScale: ContentScale,
    containEntireImage: Boolean,
    onMapFallbackChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val primaryUrl = alert.imageUrl?.takeIf { it.isNotBlank() }
    val thumbnailUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
    val coordinates = validAlertCoordinates(alert)
    var primaryFailed by remember(primaryUrl) { mutableStateOf(false) }
    var mapFailed by remember(coordinates, thumbnailUrl) { mutableStateOf(false) }

    val imageHeight = if (rounded) 200.dp else 220.dp
    val shape = if (rounded && !containEntireImage) MaterialTheme.shapes.large else RectangleShape
    val containerColor = if (containEntireImage) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    // When rounded=false the caller (detail hero) controls height via its own modifier
    val heightModifier = when {
        containEntireImage -> Modifier.fillMaxSize()
        rounded -> Modifier.height(imageHeight)
        else -> Modifier.fillMaxHeight()
    }
    val showPrimaryImage = primaryUrl != null && !primaryFailed
    val showMapFallback = !showPrimaryImage && coordinates != null && !mapFailed

    LaunchedEffect(showMapFallback) {
        onMapFallbackChanged?.invoke(showMapFallback)
    }

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
                    .apply {
                        if (containEntireImage) size(coil.size.Size.ORIGINAL)
                    }
                    .build(),
                contentDescription = stringResource(id = R.string.alert_image),
                placeholder = painterResource(id = R.drawable.ic_placeholder),
                error = painterResource(id = R.drawable.ic_placeholder),
                onError = { primaryFailed = true },
                contentScale = contentScale,
                modifier = modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .background(containerColor, shape)
                    .clip(shape)
            )
        }

        // Fallback: composite map + thumbnail sprite overlay
        showMapFallback -> {
            val safeCoordinates = requireNotNull(coordinates)

            BoxWithConstraints(
                modifier = modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .background(containerColor, shape)
                    .clipToBounds()
                    .clip(shape),
                contentAlignment = Alignment.Center
            ) {
                val rawWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else if (rounded) 512 else 1024
                val rawHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else if (rounded) 320 else 640
                val largestDimension = maxOf(rawWidth, rawHeight).coerceAtLeast(1)
                val outputScale = if (containEntireImage) {
                    (1024f / rawWidth.coerceAtLeast(1)).coerceAtMost(1f)
                } else {
                    (1024f / largestDimension).coerceAtMost(1f)
                }
                val outputWidth = (rawWidth * outputScale).roundToInt().coerceAtLeast(128)
                val outputHeight = (rawHeight * outputScale).roundToInt().coerceAtLeast(96)
                var fallbackBitmap by remember(
                    safeCoordinates,
                    thumbnailUrl,
                    outputWidth,
                    outputHeight
                ) { mutableStateOf<Bitmap?>(null) }
                var mapLoadFinished by remember(
                    safeCoordinates,
                    thumbnailUrl,
                    outputWidth,
                    outputHeight
                ) { mutableStateOf(false) }

                LaunchedEffect(
                    safeCoordinates,
                    thumbnailUrl,
                    outputWidth,
                    outputHeight,
                    containEntireImage
                ) {
                    mapLoadFinished = false
                    fallbackBitmap = withContext(Dispatchers.IO) {
                        MapFallbackImageGenerator.generate(
                            context = context,
                            latitude = safeCoordinates.latitude,
                            longitude = safeCoordinates.longitude,
                            thumbnailUrl = thumbnailUrl,
                            outputWidth = outputWidth,
                            outputHeight = outputHeight
                        )
                    }
                    mapLoadFinished = true
                }

                val bitmap = fallbackBitmap
                LaunchedEffect(mapLoadFinished, bitmap) {
                    if (mapLoadFinished && bitmap == null) mapFailed = true
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(id = R.string.alert_image),
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(if (rounded) 120.dp else 240.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (rounded) 0.14f else 0.18f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (rounded) 0.05f else 0.07f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
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
                            modifier = Modifier.size(if (rounded) 64.dp else 140.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_placeholder),
                            contentDescription = null,
                            modifier = Modifier.size(if (rounded) 36.dp else 56.dp),
                            tint = Color.Unspecified
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
                    .background(containerColor, shape)
                    .clip(shape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (rounded) 120.dp else 240.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (rounded) 0.12f else 0.16f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (rounded) 0.04f else 0.06f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
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
                    contentScale = contentScale,
                    modifier = if (containEntireImage) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.size(if (rounded) 64.dp else 140.dp)
                    }
                )
            }
        }

        // No images at all — dark bg + gold glow + Pokéball icon
        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .background(containerColor, shape)
                    .clip(shape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (rounded) 100.dp else 200.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (rounded) 0.10f else 0.14f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (rounded) 0.04f else 0.05f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                )
                            )
                        )
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_placeholder),
                    contentDescription = null,
                    modifier = Modifier.size(if (rounded) 48.dp else 80.dp),
                    tint = Color.Unspecified
                )
            }
        }
    }
}
