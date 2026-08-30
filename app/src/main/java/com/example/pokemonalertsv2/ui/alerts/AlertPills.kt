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

@Composable
internal fun AlertPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    isPrimary: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Bold,
    rolling: Boolean = false
) {
    val borderColor = if (isPrimary) AppAccents.borderAccent() else MaterialTheme.colorScheme.outline
    val fallbackContentColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val actualContentColor = contentColor ?: fallbackContentColor
    val actualBgColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainer

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
            val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = fontWeight)
            if (rolling) {
                RollingNumberText(
                    text = text,
                    style = labelStyle,
                    color = actualContentColor
                )
            } else {
                Text(
                    text = text,
                    style = labelStyle,
                    color = actualContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun rememberGoDexStatus(alert: PokemonAlert): GoDexMatchResult {
    if (!alert.hasType("hundo")) return NoGoDexMatch
    return rememberGoDexMatchResults(listOf(alert))[alert.uniqueId] ?: NoGoDexMatch
}

@Composable
internal fun GoDexStatusPill(result: GoDexMatchResult) {
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
        GoDexMatchStatus.FORM_CHANGE_NEEDED -> AlertPill(
            text = "Collected \u2022 Form change needed: ${result.compactFormChangeLabel ?: "form"}",
            icon = Icons.Filled.Star,
            isPrimary = true
        )
        GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED -> AlertPill(
            text = "Collected \u2022 Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}" +
                " \u2022 Form change needed: ${result.compactFormChangeLabel ?: "form"}",
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
internal fun ShinyBadge() {
    AlertPill(
        text = "Shiny",
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}

@Composable
internal fun IvBadge(iv: String, isPerfect: Boolean, isNundo: Boolean) {
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
internal fun CpBadge(cp: Int, level: Double?) {
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
internal fun WeatherBoostBadge() {
    AlertPill(
        text = "Boost",
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}
