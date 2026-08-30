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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlertCard(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo,
    goDexStatus: GoDexMatchResult = NoGoDexMatch,
    onOpenMaps: () -> Unit,
    onShowDetails: () -> Unit,
    onSecondaryAction: (AlertSecondaryAction) -> Unit,
    cardContext: AlertCardContext = AlertCardContext.LIVE,
    snoozeEnabled: Boolean = cardContext == AlertCardContext.LIVE,
    isGoing: Boolean = false,
    onGoingClick: (() -> Unit)? = null,
    countdownClock: State<Long> = rememberCountdownClock(),
    modifier: Modifier = Modifier
) {
    val visualStyle = remember(alert) { resolveAlertVisualStyle(alert) }
    val formattedTitle = remember(alert) { formatAlertTitle(alert) }
    val cardEndMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    val questPresentation = remember(alert) { questAlertPresentation(alert) }
    val categoryAccent = Color(visualStyle.category.accentArgb)
    val categoryOnAccent = if (categoryAccent.luminance() > 0.55f) Color(0xFF171A20) else Color.White
    val displayIv = if (alert.isWeatherChange && alert.newIv != null) alert.newIv else alert.formattedIv
    val resolvedCp = alert.displayCp
    LinearModernCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        onClick = {
            onShowDetails()
        }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.AlertCardHeroHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AlertImage(
                    alert = alert,
                    rounded = false,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (shouldShowAlertCategoryLabel(formattedTitle, visualStyle.label)) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
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
                }
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                    AlertCountdownBadge(
                        endTime = alert.endTime,
                        categoryAccent = categoryAccent,
                        categoryOnAccent = categoryOnAccent,
                        countdownClock = countdownClock
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedTitle,
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
                    AlertActionsOverflow(
                        context = cardContext,
                        endTime = alert.endTime,
                        countdownClock = countdownClock,
                        snoozeEnabled = snoozeEnabled,
                        hasGoingAction = onGoingClick != null,
                        onAction = onSecondaryAction
                    )
                }

                val cardLocationText = alert.venueName ?: alert.locationDisplay
                cardLocationText?.let { location ->
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
                            text = alert.venueTypeLabel?.let { "$it: $location" } ?: location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                questPresentation?.task?.let { task ->
                    Text(
                        text = "Task: $task",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                questPresentation?.reward?.let { reward ->
                    Text(
                        text = "Reward: $reward",
                        style = MaterialTheme.typography.bodySmall,
                        color = categoryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (alert.isWeatherChange &&
                    (weatherTransitionLabel(alert) != null || alert.affectedAlerts.isNotEmpty())
                ) {
                    WeatherChangeCardSummary(alert = alert)
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxLines = 2
                ) {
                    GoDexStatusPill(goDexStatus)
                    displayIv?.let {
                        AlertPill(
                            text = "IV $it",
                            isPrimary = true
                        )
                    }
                    resolvedCp?.let {
                        AlertPill(
                            text = "CP $it",
                            containerColor = categoryAccent.copy(alpha = 0.16f),
                            contentColor = categoryAccent
                        )
                    }
                    // Weather the alert itself reports. Boost is already implied by the styling,
                    // so the pill only ever adds the condition and, if needed, the caveat.
                    currentWeatherDisplay(alert)
                        ?.takeUnless { alert.isWeatherChange }
                        ?.let { weather ->
                            AlertPill(
                                text = if (weather.confirmed) {
                                    weather.labelWithGlyph
                                } else {
                                    "${weather.labelWithGlyph} · unconfirmed"
                                },
                                isPrimary = weather.boosted && weather.confirmed
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
                    val travelText = listOfNotNull(
                        distanceInfo.distanceText?.takeIf { it.isNotBlank() },
                        distanceInfo.walkingText?.takeIf { it.isNotBlank() }
                    ).joinToString(" \u00b7 ")
                    travelText.takeIf { it.isNotBlank() }?.let {
                        AlertPill(
                            text = it,
                            painter = painterResource(id = R.drawable.ic_map),
                            isPrimary = true
                        )
                    }
                    // A warning, never a filter: the user may be cycling, driving, or
                    // already halfway there, and none of that is knowable from here.
                    if (
                        TravelTime.expiresBeforeArrival(
                            walkingDurationSeconds = distanceInfo.walkingDurationSeconds,
                            remainingMillis = cardEndMillis?.minus(countdownClock.value)
                        )
                    ) {
                        AlertPill(
                            text = "Ends before you arrive",
                            icon = Icons.Filled.Warning,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                AlertCardPrimaryActions(
                    alert = alert,
                    goDexStatus = goDexStatus,
                    context = cardContext,
                    snoozeEnabled = snoozeEnabled,
                    countdownClock = countdownClock,
                    isGoing = isGoing,
                    onGoingClick = onGoingClick,
                    onOpenMaps = onOpenMaps,
                    categoryAccent = categoryAccent
                )
            }
        }
    }
}

@Composable
internal fun AlertActionsOverflow(
    context: AlertCardContext,
    endTime: String,
    countdownClock: State<Long>,
    snoozeEnabled: Boolean,
    hasGoingAction: Boolean,
    onAction: (AlertSecondaryAction) -> Unit
) {
    val policy = alertActionPolicy(
        context = context,
        isExpired = TimeUtils.parseEndTimeToMillis(endTime)?.let { it <= countdownClock.value } ?: false,
        snoozeEnabled = snoozeEnabled,
        hasGoingAction = hasGoingAction
    )
    AlertSecondaryActionsMenu(
        actions = policy.overflowActions,
        onAction = onAction
    )
}

@Composable
internal fun AlertSecondaryActionsMenu(
    actions: List<AlertSecondaryAction>,
    onAction: (AlertSecondaryAction) -> Unit,
    contentDescription: String = "More alert actions"
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = contentDescription
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            actions.forEach { action ->
                val label = when (action) {
                    AlertSecondaryAction.SNOOZE -> "Snooze"
                    AlertSecondaryAction.PICTURE_IN_PICTURE -> "Open in picture-in-picture"
                    AlertSecondaryAction.SHARE -> "Share"
                    AlertSecondaryAction.DISMISS -> "Dismiss"
                    AlertSecondaryAction.RESTORE -> "Restore"
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        when (action) {
                            AlertSecondaryAction.SNOOZE -> {
                                Icon(Icons.Filled.Notifications, contentDescription = null)
                            }
                            AlertSecondaryAction.PICTURE_IN_PICTURE -> {
                                Icon(painterResource(R.drawable.ic_pip), contentDescription = null)
                            }
                            AlertSecondaryAction.SHARE -> {
                                Icon(Icons.Filled.Share, contentDescription = null)
                            }
                            AlertSecondaryAction.DISMISS -> {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                            AlertSecondaryAction.RESTORE -> {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onAction(action)
                    }
                )
            }
        }
    }
}

@Composable
internal fun CompactAlertActionButton(
    text: String,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = accessibilityLabel },
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        icon()
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AlertCardPrimaryActions(
    alert: PokemonAlert,
    goDexStatus: GoDexMatchResult,
    context: AlertCardContext,
    snoozeEnabled: Boolean,
    countdownClock: State<Long>,
    isGoing: Boolean,
    onGoingClick: (() -> Unit)?,
    onOpenMaps: () -> Unit,
    categoryAccent: Color
) {
    val policy = alertActionPolicy(
        context = context,
        isExpired = alert.isExpiredAt(countdownClock.value),
        snoozeEnabled = snoozeEnabled,
        hasGoingAction = onGoingClick != null
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoDexCaughtAction(alert = alert, matchResult = goDexStatus)
        if (policy.showGoing && onGoingClick != null) {
            FilledTonalButton(
                onClick = onGoingClick,
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isGoing) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (isGoing) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                AnimatedContent(
                    targetState = isGoing,
                    transitionSpec = { appFadeThrough() },
                    label = "alert_card_going_action"
                ) { going ->
                    Text(
                        if (going) "Stop" else "I\u2019m going",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        if (policy.showNavigate) {
            FilledTonalButton(
                onClick = onOpenMaps,
                modifier = Modifier.weight(1.15f).height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = categoryAccent.copy(alpha = 0.22f),
                    contentColor = categoryAccent
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map),
                    contentDescription = stringResource(id = R.string.open_in_maps)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Navigate", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
