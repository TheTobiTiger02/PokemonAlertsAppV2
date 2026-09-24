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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import com.example.pokemonalertsv2.util.TravelTime
import com.example.pokemonalertsv2.ui.theme.AppAccents

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertDetailScreen(
    alert: PokemonAlert,
    onBack: (() -> Unit)? = null,
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Unit)? = null,
    // Defaulted so existing call sites and previews keep compiling untouched.
    raidCountersState: RaidCountersUiState = RaidCountersUiState(),
    raidCountersActions: RaidCountersActions = RaidCountersActions.Noop,
    /** Land straight on counters, e.g. from the raid watch notification's Counters action. */
    startOnCounters: Boolean = false
) {
    if (isInPictureInPicture) {
        AlertPictureInPictureContent(alert = alert)
        return
    }

    val context = LocalContext.current
    val arrivalTracking = rememberArrivalTrackingUiController()
    val isGoing = arrivalTracking.isTracking(alert)
    val goDexStatus = rememberGoDexStatus(alert)
    val actionBarClearance = 132.dp +
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
    // The counters are a screen, not a card. Mirrors the SettingsScreen destination
    // pattern -- this project has no Compose Navigation, and a separate Activity would mean
    // duplicating AlertDetailActivity's 45-extra alert flattening and losing PiP.
    // Keyed on the alert so onNewIntent replacing it drops back to the detail view.
    var countersOpen by rememberSaveable(alert.uniqueId) { mutableStateOf(startOnCounters) }
    LaunchedEffect(startOnCounters) {
        if (startOnCounters) countersOpen = true
    }
    val openCounters = { countersOpen = true }
    BackHandler(enabled = countersOpen) { countersOpen = false }

    AnimatedContent(
        targetState = countersOpen,
        transitionSpec = { appSharedAxisX(forward = targetState) },
        label = "alert_detail_destination"
    ) { showCounters ->
    if (showCounters) {
        RaidCountersScreen(
            state = raidCountersState,
            actions = raidCountersActions,
            onBack = { countersOpen = false }
        )
    } else {
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
                                goDexStatus.evolutionTargets.forEach { target ->
                                    Text(
                                        text = "Evolution needed: ${target.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                goDexStatus.formChangeTargets.forEach { target ->
                                    Text(
                                        text = "Form change needed: ${target.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                GoDexCaughtAction(
                                    alert = alert,
                                    matchResult = goDexStatus,
                                    modifier = Modifier.padding(top = 8.dp),
                                    presentation = GoDexCaughtActionPresentation.LABELED
                                )
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

                        // Best counters (raids only; the row hides itself otherwise). The
                        // feature itself lives on RaidCountersScreen -- a screen's worth of UI
                        // does not belong in this scroll column.
                        RaidCountersTeaser(
                            state = raidCountersState,
                            actions = raidCountersActions,
                            onOpen = { openCounters() }
                        )

                        // Location Card
                        LocationCard(alert = alert)
                        AnimatedVisibility(
                            visible = isGoing,
                            enter = appExpandIn(),
                            exit = appCollapseOut()
                        ) {
                            AlertPill(
                                text = "Arrival tracking active",
                                icon = Icons.Filled.LocationOn,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

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
                                val statusClock = rememberCountdownClock()
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CountdownAndEndTimeRow(alert = alert, countdownClock = statusClock)

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
                    isGoing = isGoing,
                    goingEnabled = alert.isEligibleArrivalDestination(),
                    onGoingClick = { arrivalTracking.onToggle(alert) },
                    onNavigateClick = { openMapForAlert(context, alert) },
                    onPipClick = onEnterPictureInPicture,
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
    }
}

@Composable
internal fun InvalidationBanner(alert: PokemonAlert) {
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
internal fun WeatherTransitionCard(alert: PokemonAlert) {
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
internal fun AffectedAlertDetailCard(alert: AffectedAlert) {
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
internal fun DetailValueRow(label: String, value: String) {
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
internal fun ExpandedAlertImageViewer(
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
internal fun AlertDetailActionBar(
    modifier: Modifier = Modifier,
    accent: Color,
    onSnoozeClick: () -> Unit,
    isGoing: Boolean,
    goingEnabled: Boolean,
    onGoingClick: () -> Unit,
    onNavigateClick: () -> Unit,
    onPipClick: (() -> Unit)?,
    onShareClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onGoingClick,
                    enabled = goingEnabled || isGoing,
                    modifier = Modifier.weight(1.2f).height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = isGoing,
                            transitionSpec = { appFadeThrough() },
                            label = "detail_going_action"
                        ) { going ->
                            Text(
                                text = if (going) "Stop" else "I\u2019m going",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = onNavigateClick,
                    modifier = Modifier.weight(1.4f).height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accent,
                        contentColor = if (accent.luminance() > 0.55f) {
                            Color(0xFF171A20)
                        } else {
                            Color.White
                        }
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
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactAlertActionButton(
                    text = "Snooze",
                    accessibilityLabel = "Snooze alert",
                    onClick = onSnoozeClick,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                if (onPipClick != null) {
                    CompactAlertActionButton(
                        text = "PiP",
                        accessibilityLabel = "Open alert in picture-in-picture",
                        onClick = onPipClick,
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pip),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                CompactAlertActionButton(
                    text = "Share",
                    accessibilityLabel = "Share alert",
                    onClick = onShareClick,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun AlertPictureInPictureContent(alert: PokemonAlert) {
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    // The shared clock, so PiP ticks on the same boundaries as every other countdown and
    // stops ticking while the window is not visible.
    val now by rememberCountdownClock()

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
