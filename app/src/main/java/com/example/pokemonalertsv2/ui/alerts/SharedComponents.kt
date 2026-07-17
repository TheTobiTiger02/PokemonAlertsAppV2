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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.notifications.AlertSnoozeScheduler
import com.example.pokemonalertsv2.ui.theme.MetricTextStyle
import com.example.pokemonalertsv2.ui.components.LinearModernCard
import com.example.pokemonalertsv2.ui.components.GradientText
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors
import com.example.pokemonalertsv2.ui.theme.LocalAppDarkTheme
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.MapFallbackImageGenerator
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.util.validAlertCoordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val ALERT_DETAIL_HERO_IMAGE_HEIGHT = 240.dp

@Immutable
data class AlertDistanceInfo(
    val distanceMeters: Float?,
    val distanceText: String?,
    val walkingText: String?
)

@Immutable
data class AlertUiModel(
    val alert: PokemonAlert,
    val distanceInfo: AlertDistanceInfo,
    val endMillis: Long? = null,
    val typeKeys: Set<String> = emptySet()
)

@Composable
fun rememberCountdownNow(tickMillis: Long = 1000L): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMillis) {
        while (true) {
            delay(tickMillis)
            now = System.currentTimeMillis()
        }
    }
    return now
}

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
        weatherTransitionLabel(alert)?.let { return it }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertCard(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo,
    onOpenMaps: () -> Unit,
    onShowDetails: () -> Unit,
    onPipClick: () -> Unit,
    onShareClick: () -> Unit,
    onSnoozeClick: (() -> Unit)? = null,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    val goDexStatus = rememberGoDexStatus(alert)
    val visualStyle = remember(alert) { resolveAlertVisualStyle(alert) }
    val categoryAccent = Color(visualStyle.category.accentArgb)
    val categoryOnAccent = if (categoryAccent.luminance() > 0.55f) Color(0xFF171A20) else Color.White
    val haptic = LocalHapticFeedback.current
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    val remaining = endMillis?.minus(nowMillis)
    val countdown = when {
        remaining == null -> "TIME --"
        remaining <= 0 -> "EXPIRED"
        else -> TimeUtils.formatDurationShort(remaining)
    }
    val displayIv = if (alert.isWeatherChange && alert.newIv != null) alert.newIv else alert.formattedIv
    val resolvedCp = alert.displayCp

    LinearModernCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onShowDetails()
        }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AlertImage(
                    alert = alert,
                    rounded = false,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = MaterialTheme.shapes.small,
                    color = categoryAccent.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = visualStyle.label,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = categoryOnAccent
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (remaining != null && remaining <= 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        categoryAccent.copy(alpha = 0.92f)
                    }
                ) {
                    Text(
                        text = countdown,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MetricTextStyle,
                        color = if (remaining != null && remaining <= 0) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            categoryOnAccent
                        },
                        maxLines = 1
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatAlertTitle(alert),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        alert.pokemonForm?.takeIf { it.isNotBlank() }?.let { form ->
                            Text(
                                text = form,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                alert.locationDisplay?.let { location ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (alert.isWeatherChange &&
                    (weatherTransitionLabel(alert) != null || alert.affectedAlerts.isNotEmpty())
                ) {
                    WeatherChangeCardSummary(alert = alert)
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GoDexStatusPill(goDexStatus)
                    displayIv?.let {
                        AlertPill(
                            text = "IV $it",
                            isPrimary = true
                        )
                    }
                    AlertPill(
                        text = visualStyle.label,
                        containerColor = categoryAccent.copy(alpha = 0.16f),
                        contentColor = categoryAccent
                    )
                    resolvedCp?.let {
                        AlertPill(
                            text = "CP $it",
                            containerColor = categoryAccent.copy(alpha = 0.16f),
                            contentColor = categoryAccent
                        )
                    }
                    invalidationBadgeText(alert)?.let {
                        AlertPill(
                            text = it,
                            icon = Icons.Filled.Warning,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    distanceInfo.distanceText?.takeIf { it.isNotBlank() }?.let {
                        AlertPill(
                            text = it,
                            painter = painterResource(id = R.drawable.ic_map),
                            isPrimary = true
                        )
                    }
                    distanceInfo.walkingText?.takeIf { it.isNotBlank() }?.let {
                        AlertPill(text = it, isPrimary = false)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSnoozeClick != null) {
                        FilledIconButton(
                            onClick = onSnoozeClick,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Snooze alert"
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                            )
                            onPipClick()
                        },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pip),
                            contentDescription = stringResource(id = R.string.open_alert_in_pip)
                        )
                    }
                    FilledIconButton(
                        onClick = onShareClick,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = "Share")
                    }
                    FilledTonalButton(
                        onClick = onOpenMaps,
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = categoryAccent.copy(alpha = 0.22f),
                            contentColor = categoryAccent
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_map),
                            contentDescription = stringResource(id = R.string.open_in_maps)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Navigate", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherChangeCardSummary(alert: PokemonAlert) {
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
private fun AlertPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    isPrimary: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val colors = LocalLinearModernColors.current
    val borderColor = if (isPrimary) colors.borderAccent else colors.borderDefault
    val fallbackContentColor = if (isPrimary) colors.accent else colors.foregroundMuted
    val actualContentColor = contentColor ?: fallbackContentColor
    val actualBgColor = containerColor ?: colors.surfaceTranslucent

    Box(
        modifier = modifier
            .background(actualBgColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 24.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = actualContentColor
                )
            } else if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = actualContentColor
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = fontWeight,
                color = actualContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rememberGoDexStatus(alert: PokemonAlert): GoDexMatchResult {
    if (!alert.hasType("hundo")) return GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED)
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { GoDexRepository.getInstance(context) }
    val entries by repository.entries.collectAsState()
    val config by repository.config.collectAsState()
    return remember(alert, entries, config.url) {
        repository.match(alert, entries)
    }
}

@Composable
private fun GoDexStatusPill(result: GoDexMatchResult) {
    when (result.status) {
        GoDexMatchStatus.NEEDED -> AlertPill(
            text = "Needed in GoDex",
            icon = Icons.Filled.Star,
            isPrimary = true
        )
        GoDexMatchStatus.COLLECTED -> AlertPill(
            text = "Already collected",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GoDexMatchStatus.EVOLUTION_NEEDED -> AlertPill(
            text = "Collected \u2022 Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}",
            icon = Icons.Filled.Star,
            isPrimary = true
        )
        GoDexMatchStatus.UNKNOWN -> AlertPill(
            text = "GoDex form unknown",
            icon = Icons.Filled.Warning,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        GoDexMatchStatus.NOT_CONFIGURED -> Unit
    }
}

fun formatSnoozeDurationLabel(minutes: Int): String {
    return if (minutes >= 60 && minutes % 60 == 0) {
        val hours = minutes / 60
        if (hours == 1) "1 hr" else "$hours hrs"
    } else {
        "$minutes min"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeDurationDialog(
    defaultMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val quickDurations = remember { listOf(5, 10, 15, 30, 60) }
    var customText by remember(defaultMinutes) {
        mutableStateOf(defaultMinutes.coerceAtLeast(1).toString())
    }
    val customMinutes = customText.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(24 * 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Snooze alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickDurations.forEach { minutes ->
                        FilledTonalButton(
                            onClick = { onConfirm(minutes) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (minutes == defaultMinutes)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (minutes == defaultMinutes)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(formatSnoozeDurationLabel(minutes), maxLines = 1)
                        }
                    }
                }
                OutlinedTextField(
                    value = customText,
                    onValueChange = { value ->
                        customText = value.filter { it.isDigit() }.take(4)
                    },
                    singleLine = true,
                    label = { Text("Custom minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = customMinutes != null,
                onClick = { customMinutes?.let(onConfirm) }
            ) {
                Text("Snooze")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

suspend fun snoozeAlertFromUi(context: Context, alert: PokemonAlert, minutes: Int): Boolean {
    val safeMinutes = minutes.coerceIn(1, 24 * 60)
    return withContext(Dispatchers.IO) {
        AlertPreferences(context.alertPreferencesDataStore).updateSnoozeDuration(safeMinutes)
        AlertSnoozeScheduler.schedule(context.applicationContext, alert, safeMinutes)
    }
}

@Composable
private fun ShinyBadge() {
    AlertPill(
        text = "Shiny",
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}

@Composable
private fun IvBadge(iv: String, isPerfect: Boolean, isNundo: Boolean) {
    val backgroundColor = when {
        isPerfect -> MaterialTheme.colorScheme.primary
        isNundo -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when {
        isPerfect -> MaterialTheme.colorScheme.onPrimary
        isNundo -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    AlertPill(text = iv, containerColor = backgroundColor, contentColor = textColor)
}

@Composable
private fun CpBadge(cp: Int, level: Double?) {
    val levelText = level?.let {
        if (it == it.toLong().toDouble()) " L${it.toLong()}" else " L$it"
    }.orEmpty()
    AlertPill(
        text = "CP $cp$levelText",
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun WeatherBoostBadge() {
    AlertPill(
        text = "Boost",
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
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
private fun ContainedAlertImage(
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
private fun AlertImageContent(
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

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertDetailScreen(
    alert: PokemonAlert,
    onBack: (() -> Unit)? = null,
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Unit)? = null
) {
    if (isInPictureInPicture) {
        AlertPictureInPictureContent(alert = alert)
        return
    }

    val context = LocalContext.current
    val goDexStatus = rememberGoDexStatus(alert)
    val actionBarClearance = 84.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val visualStyle = remember(alert) { resolveAlertVisualStyle(alert) }
    val categoryAccent = Color(visualStyle.category.accentArgb)
    val darkTheme = LocalAppDarkTheme.current
    var isMapFallback by remember(alert.uniqueId) {
        mutableStateOf(alert.imageUrl.isNullOrBlank() && validAlertCoordinates(alert) != null)
    }
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showExpandedImage by remember(alert.uniqueId) { mutableStateOf(false) }
    var defaultSnoozeMinutes by remember { mutableStateOf(10) }
    LaunchedEffect(context) {
        defaultSnoozeMinutes = withContext(Dispatchers.IO) {
            AlertPreferences(context.alertPreferencesDataStore).snoozeDuration.first()
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero image section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ALERT_DETAIL_HERO_IMAGE_HEIGHT)
                ) {
                    AlertImage(
                        alert = alert,
                        rounded = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showExpandedImage = true },
                        onMapFallbackChanged = { isMapFallback = it }
                    )
                    
                    // Top Bar Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isMapFallback) 72.dp else 100.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isMapFallback) {
                                        listOf(
                                            MaterialTheme.colorScheme.scrim.copy(
                                                alpha = if (darkTheme) 0.34f else 0f
                                            ),
                                            Color.Transparent
                                        )
                                    } else {
                                        listOf(
                                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                        )
                                    }
                                )
                            )
                    )
                    
                    // Bottom Gradient for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isMapFallback) 24.dp else 96.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isMapFallback) {
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background
                                        )
                                    } else {
                                        listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.64f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    }
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
                            onClick = { onBack?.invoke() ?: activity?.finish() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onSurface
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
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                ),
                                contentColor = MaterialTheme.colorScheme.onSurface,
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
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.enter_pip_short),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
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
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFFFFB300).copy(alpha = 0.20f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFFFB300)
                                )
                                Text(
                                    text = "SHINY",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
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
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            // Pokemon form if available
                            alert.pokemonForm?.takeIf { it.isNotBlank() }?.let { form ->
                                Text(
                                    text = form,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            GoDexStatusPill(goDexStatus)
                            if (goDexStatus.status == GoDexMatchStatus.EVOLUTION_NEEDED) {
                                goDexStatus.evolutionTargets.forEach { target ->
                                    Text(
                                        text = "Evolution needed: ${target.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                typeList.forEach { typeName ->
                                    AlertPill(
                                        text = typeName.uppercase(),
                                        containerColor = categoryAccent.copy(alpha = 0.18f),
                                        contentColor = categoryAccent
                                    )
                                }
                            }
                        }
                    }

                    if (alert.isInvalidated) {
                        InvalidationBanner(alert = alert)
                    }

                    if (alert.isWeatherChange &&
                        (weatherTransitionLabel(alert) != null || alert.affectedAlerts.isNotEmpty())
                    ) {
                        WeatherTransitionCard(alert = alert)
                        alert.affectedAlerts.forEach { affectedAlert ->
                            AffectedAlertDetailCard(alert = affectedAlert)
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
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val statusNow = rememberCountdownNow()
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CountdownAndEndTimeRow(alert = alert, nowMillis = statusNow)
                            
                            // Created at timestamp
                            TimeUtils.formatPostedTime(alert.createdAt)?.let { posted ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = posted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(actionBarClearance))
                }
            }
            AlertDetailActionBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                accent = categoryAccent,
                onSnoozeClick = { showSnoozeDialog = true },
                onNavigateClick = { openMapForAlert(context, alert) },
                onShareClick = {
                    scope.launch {
                        AlertShareCard.share(context, alert)
                    }
                }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = actionBarClearance)
            )
        }
    }

    if (showSnoozeDialog) {
        SnoozeDurationDialog(
            defaultMinutes = defaultSnoozeMinutes,
            onDismiss = { showSnoozeDialog = false },
            onConfirm = { minutes ->
                showSnoozeDialog = false
                scope.launch {
                    val scheduled = snoozeAlertFromUi(context, alert, minutes)
                    snackbarHostState.showSnackbar(
                        if (scheduled) {
                            "Snoozed for ${formatSnoozeDurationLabel(minutes)}"
                        } else {
                            "Alert ends before that snooze time"
                        }
                    )
                }
            }
        )
    }

    if (showExpandedImage) {
        ExpandedAlertImageViewer(
            alert = alert,
            onDismiss = { showExpandedImage = false }
        )
    }
}

@Composable
private fun InvalidationBanner(alert: PokemonAlert) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(
                    text = "Invalidated by weather",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            alert.invalidatedAt?.takeIf { it.isNotBlank() }?.let { raw ->
                DetailValueRow(
                    label = "Removed",
                    value = TimeUtils.formatTimestamp(raw) ?: raw
                )
            }
            alert.invalidationReason?.takeIf { it.isNotBlank() }?.let { reason ->
                DetailValueRow(label = "Reason", value = reason)
            }
            alert.invalidatedByAlertId?.let { alertId ->
                DetailValueRow(label = "Weather alert", value = "#$alertId")
            }
        }
    }
}

@Composable
private fun WeatherTransitionCard(alert: PokemonAlert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Weather change",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            weatherTransitionLabel(alert)?.let { transition ->
                Text(
                    text = transition,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (alert.affectedAlerts.isNotEmpty()) {
                val suffix = if (alert.affectedAlerts.size == 1) {
                    "active Pokémon alert was replaced"
                } else {
                    "active Pokémon alerts were replaced"
                }
                Text(
                    text = "${alert.affectedAlerts.size} $suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AffectedAlertDetailCard(alert: AffectedAlert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = alert.name?.takeIf { it.isNotBlank() } ?: affectedAlertSummary(alert),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            alert.id?.let { DetailValueRow("Alert ID", "#$it") }
            alert.pokemon?.takeIf { it.isNotBlank() }?.let {
                DetailValueRow("Pokémon", it)
            }
            alert.pokemonForm?.takeIf { it.isNotBlank() }?.let {
                DetailValueRow("Form", it)
            }
            alert.cp?.let { DetailValueRow("CP", it.toString()) }
            alert.type
                .orEmpty()
                .mapNotNull { it.takeIf(String::isNotBlank) }
                .takeIf { it.isNotEmpty() }
                ?.let { DetailValueRow("Types", it.joinToString(", ")) }
            alert.area?.takeIf { it.isNotBlank() }?.let {
                DetailValueRow("Area", it)
            }
            alert.endTime?.takeIf { it.isNotBlank() }?.let { raw ->
                DetailValueRow("Original end time", TimeUtils.formatTimestamp(raw) ?: raw)
            }
        }
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(116.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExpandedAlertImageViewer(
    alert: PokemonAlert,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ContainedAlertImage(
                    alert = alert,
                    modifier = Modifier.fillMaxSize()
                )

                FilledIconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.close)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertDetailActionBar(
    modifier: Modifier = Modifier,
    accent: Color,
    onSnoozeClick: () -> Unit,
    onNavigateClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onSnoozeClick,
                modifier = Modifier.weight(0.8f).height(56.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Snooze",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            FilledTonalButton(
                onClick = onNavigateClick,
                modifier = Modifier.weight(1.4f).height(56.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = accent,
                    contentColor = if (accent.luminance() > 0.55f) Color(0xFF171A20) else Color.White
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Navigate",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            FilledTonalButton(
                onClick = onShareClick,
                modifier = Modifier.weight(0.8f).height(56.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Share",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
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
    val pipCpText = remember(alert) { buildPipCpText(alert) }

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
        color = MaterialTheme.colorScheme.scrim
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
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = if (isZoomed) 0.3f else 0.7f)
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
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pipCpText != null) {
                        Text(
                            text = pipCpText,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (pipTimerText != null) {
                        Text(
                            text = pipTimerText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
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
    return MaterialTheme.colorScheme.primary
}

@Composable
private fun StatsCard(alert: PokemonAlert) {
    val isReplacement = alert.isSpeciesReplacement
    val isChanged = alert.isWeatherChange  // includes both weather-only and replacement
    val accentColor = Color(resolveAlertVisualStyle(alert).category.accentArgb)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section title
            Text(
                text = when {
                    isReplacement -> "New species"
                    isChanged -> "Updated stats"
                    else -> "Stats"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isChanged) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Species replacement banner — shows old species info
            if (isReplacement) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = accentColor
                        )
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
                        highlightColor = if (isNewIv) accentColor else if (alert.isPerfect) MaterialTheme.colorScheme.primary else if (alert.isNundo) MaterialTheme.colorScheme.onSurfaceVariant else null
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
                            highlightColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    alert.hundoCP.level25?.let { l25 ->
                        StatItem(
                            label = "100% L25",
                            value = l25.toString(),
                            highlightColor = MaterialTheme.colorScheme.primary
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
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.toFloat() / maxValue)
                    .height(8.dp)
                    .background(
                        when {
                            value == 15 -> MaterialTheme.colorScheme.primary
                            value >= 13 -> MaterialTheme.colorScheme.primary
                            value >= 10 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary
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
    val accent = Color(resolveAlertVisualStyle(alert).category.accentArgb)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Weather Boost
            if (alert.isWeatherBoosted == true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = accent
                    )
                    Text(
                        text = "Weather Boosted",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
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
                        text = "Gender",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = gender.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
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
        shape = MaterialTheme.shapes.large,
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
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "FAST",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "CHARGED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
        shape = MaterialTheme.shapes.large,
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
        shape = MaterialTheme.shapes.large,
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
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "AR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.primary,
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
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
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (gruntType?.isNotBlank() == true) {
                        Text(
                            text = gruntType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pokemon Rewards
            if (!pokemonRewards.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                Text(
                    text = "Possible Rewards",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                pokemonRewards.forEach { reward ->
                    val rarityColor = when (reward.rarity?.lowercase()) {
                        "common" -> MaterialTheme.colorScheme.primary
                        "rare" -> MaterialTheme.colorScheme.primary
                        "legendary", "ultra rare" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurface,
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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
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
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    MaterialTheme.shapes.extraSmall
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(rarityColor, MaterialTheme.shapes.extraSmall)
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
        shape = MaterialTheme.shapes.large,
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
                ranking.league?.contains("Great", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                ranking.league?.contains("Ultra", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                ranking.league?.contains("Master", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                ranking.league?.contains("Little", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary
            }
            
            Surface(
                shape = MaterialTheme.shapes.small,
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
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.onSurfaceVariant
                    3 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                
                if (rank <= 3) {
                    Text(
                        text = rankEmoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertMetaRow(alert: PokemonAlert, distanceInfo: AlertDistanceInfo, nowMillis: Long) {
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

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
        CountdownAndEndTimeRow(alert = alert, nowMillis = nowMillis)
    }
}

@Composable
fun AlertTag(text: String, icon: ImageVector? = null) {
    AlertPill(text = text, icon = icon)
}

@Composable
fun CountdownAndEndTimeRow(alert: PokemonAlert, nowMillis: Long = System.currentTimeMillis()) {
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    val remaining = endMillis?.let { it - nowMillis } ?: -1
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
            val countdownColor = if (remainingText == expiredLabel)
                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.secondaryContainer
            val countdownContentColor = if (remainingText == expiredLabel)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSecondaryContainer
            AlertPill(
                text = remainingText,
                painter = painterResource(id = R.drawable.ic_timer),
                containerColor = countdownColor,
                contentColor = countdownContentColor
            )
        }
        Text(
            text = endMillis?.let { TimeUtils.formatAlertEndTime(it, nowMillis) }
                ?: stringResource(id = R.string.alert_end_time, alert.endTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DistanceChip(text: String) {
    AlertPill(
        text = text,
        painter = painterResource(id = R.drawable.ic_map),
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

fun openMapForAlert(context: Context, alert: PokemonAlert) {
    val mapsIntent = Intent(Intent.ACTION_VIEW, alert.googleMapsUri)
    try {
        context.startActivity(mapsIntent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_maps_app), Toast.LENGTH_SHORT).show()
    }
}

internal fun openAlertInPictureInPicture(context: Context, alert: PokemonAlert) {
    val intent = AlertDetailActivity.createIntent(context, alert).apply {
        putExtra(AlertDetailActivity.EXTRA_LAUNCH_PIP, true)
    }
    context.startActivity(intent)
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

fun formatDistance(meters: Float): String {
    return WalkingRouteUtils.formatDistanceMeters(meters)
}
