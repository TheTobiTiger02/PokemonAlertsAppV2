package com.example.pokemonalertsv2.ui.alerts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import com.example.pokemonalertsv2.ui.theme.DangerRed
import com.example.pokemonalertsv2.ui.theme.EmberGradientEnd
import com.example.pokemonalertsv2.ui.theme.EmberGradientStart
import com.example.pokemonalertsv2.ui.theme.SuccessGreen
import com.example.pokemonalertsv2.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay

private enum class AlertFilter(val titleRes: Int) {
    ALL(R.string.alert_filter_all),
    ENDING_SOON(R.string.alert_filter_ending_soon),
    NEARBY(R.string.alert_filter_nearby)
}

private data class AlertDistanceInfo(
    val distanceMeters: Float?,
    val distanceText: String?,
    val walkingText: String?
)

private data class AlertUiModel(
    val alert: PokemonAlert,
    val distanceInfo: AlertDistanceInfo
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonAlertsRoute(
    viewModel: PokemonAlertsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val highlightedAlert = remember(uiState.alerts, uiState.highlightedAlertId) {
        uiState.alerts.firstOrNull { it.uniqueId == uiState.highlightedAlertId }
    }

    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    PokemonAlertsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refreshAlerts,
        onAlertSelected = { alert ->
            viewModel.highlightAlert(alert.uniqueId)
        }
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshAlerts()
                delay(30_000)
            }
        }
    }

    if (highlightedAlert != null) {
        AlertDetailDialog(alert = highlightedAlert, onDismiss = { viewModel.highlightAlert(null) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonAlertsScreen(
    uiState: AlertsUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var lastUpdated by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var selectedFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }

    LaunchedEffect(Unit) {
        userLocation = getLastKnownLocation(context)
    }
    LaunchedEffect(uiState.alerts) {
        if (uiState.alerts.isNotEmpty()) {
            lastUpdated = System.currentTimeMillis()
        }
    }

    val alertsWithDistance = remember(uiState.alerts, userLocation) {
        uiState.alerts.map { alert ->
            val distanceMeters: Float? = userLocation?.let { loc ->
                val results = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude, alert.longitude, results)
                results.getOrNull(0)?.takeUnless { it.isNaN() }
            }
            val distanceText = distanceMeters?.let { if (it >= 1000f) String.format(Locale.getDefault(), "%.1f km", it / 1000f) else String.format(Locale.getDefault(), "%.0f m", it) }
            val walkingText = distanceMeters?.let { formatWalkingTime(it) }
            AlertUiModel(alert, AlertDistanceInfo(distanceMeters, distanceText, walkingText))
        }
    }

    val filteredAlerts = remember(alertsWithDistance, selectedFilter) {
        when (selectedFilter) {
            AlertFilter.ALL -> alertsWithDistance
            AlertFilter.ENDING_SOON -> alertsWithDistance
                .sortedBy { TimeUtils.parseEndTimeToMillis(it.alert.endTime) ?: Long.MAX_VALUE }
                .take(25)
            AlertFilter.NEARBY -> alertsWithDistance
                .filter { it.distanceInfo.distanceMeters != null }
                .sortedBy { it.distanceInfo.distanceMeters }
        }
    }

    val containerGradient = remember {
        Brush.verticalGradient(
            listOf(
                AuroraGradientStart,
                AuroraGradientMid,
                AuroraGradientEnd.copy(alpha = 0.85f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerGradient)
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState()
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AuroraToolbar(
                    onRefresh = onRefresh,
                    onOpenMap = { context.startActivity(Intent(context, AlertsMapActivity::class.java)) },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading && uiState.alerts.isEmpty() -> LoadingState()
                    filteredAlerts.isEmpty() -> EmptyState(onRefresh = onRefresh)
                    else -> AlertsList(
                        uiState = uiState,
                        filteredAlerts = filteredAlerts,
                        selectedFilter = selectedFilter,
                        onFilterChanged = { selectedFilter = it },
                        lastUpdated = lastUpdated,
                        onAlertSelected = onAlertSelected,
                        onOpenMaps = { alert -> openMapForAlert(context, alert) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertsList(
    uiState: AlertsUiState,
    filteredAlerts: List<AlertUiModel>,
    selectedFilter: AlertFilter,
    onFilterChanged: (AlertFilter) -> Unit,
    lastUpdated: Long,
    onAlertSelected: (PokemonAlert) -> Unit,
    onOpenMaps: (PokemonAlert) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "hero") {
            HeroHeader(
                activeCount = uiState.alerts.size,
                endingSoonCount = uiState.alerts.count {
                    val millis = TimeUtils.parseEndTimeToMillis(it.endTime) ?: return@count false
                    val remaining = millis - System.currentTimeMillis()
                    remaining in 1..(30 * 60 * 1000)
                },
                lastUpdated = lastUpdated,
                featuredAlert = uiState.alerts.firstOrNull(),
                onFeaturedClick = { alert -> onAlertSelected(alert) }
            )
        }

        item(key = "filters") {
            FilterRow(
                selectedFilter = selectedFilter,
                onFilterChanged = onFilterChanged,
                locationAvailable = filteredAlerts.any { it.distanceInfo.distanceMeters != null }
            )
        }

        items(filteredAlerts, key = { it.alert.uniqueId }) { model ->
            AlertCard(
                alert = model.alert,
                distanceInfo = model.distanceInfo,
                onOpenMaps = { onOpenMaps(model.alert) },
                onShowDetails = { onAlertSelected(model.alert) }
            )
        }

        if (uiState.isLoading) {
            item(key = "loading_indicator") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun HeroHeader(
    activeCount: Int,
    endingSoonCount: Int,
    lastUpdated: Long,
    featuredAlert: PokemonAlert?,
    onFeaturedClick: (PokemonAlert) -> Unit
) {
    val formattedTime = remember(lastUpdated) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdated))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(30.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(id = R.string.alerts_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(id = R.string.alerts_hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cardWidth = remember(maxWidth) { (maxWidth - 16.dp) / 2 }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HeroStatCard(
                        label = stringResource(id = R.string.alerts_hero_active_label),
                        value = activeCount,
                        brush = Brush.horizontalGradient(listOf(AuroraGradientStart, AuroraGradientEnd)),
                        modifier = Modifier.width(cardWidth)
                    )
                    HeroStatCard(
                        label = stringResource(id = R.string.alerts_hero_ending_label),
                        value = endingSoonCount,
                        brush = Brush.horizontalGradient(listOf(EmberGradientStart, EmberGradientEnd)),
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(id = R.string.alerts_last_updated, formattedTime),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val spotlight = featuredAlert
            if (spotlight != null) {
                Spacer(modifier = Modifier.height(24.dp))
                FeaturedAlertPreview(alert = spotlight, onOpenDetails = { onFeaturedClick(spotlight) })
            }
        }
    }
}

@Composable
private fun HeroStatCard(label: String, value: Int, brush: Brush, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(brush, RoundedCornerShape(24.dp))
                .padding(vertical = 18.dp, horizontal = 16.dp)
        ) {
            Text(text = value.toString(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FeaturedAlertPreview(alert: PokemonAlert, onOpenDetails: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.alerts_featured_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenDetails, shape = RoundedCornerShape(16.dp)) {
                Icon(painter = painterResource(id = R.drawable.ic_map), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(text = stringResource(id = R.string.open_in_maps))
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: AlertFilter,
    onFilterChanged: (AlertFilter) -> Unit,
    locationAvailable: Boolean
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AlertFilter.values().forEach { filter ->
                val enabled = filter != AlertFilter.NEARBY || locationAvailable
                ElevatedAssistChip(
                    onClick = { if (enabled) onFilterChanged(filter) },
                    label = { Text(text = stringResource(id = filter.titleRes)) },
                    enabled = enabled,
                    colors = AssistChipDefaults.elevatedAssistChipColors(
                        containerColor = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        labelColor = if (selectedFilter == filter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedFilter == filter) Color.Transparent else MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        AnimatedVisibility(visible = selectedFilter == AlertFilter.NEARBY && !locationAvailable) {
            Text(
                text = stringResource(id = R.string.alerts_nearby_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AlertMetaRow(alert: PokemonAlert, distanceInfo: AlertDistanceInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val typeLabel = alert.type?.takeIf { it.isNotBlank() }?.uppercase(Locale.getDefault())
        val distanceLabel = distanceInfo.distanceText
        val walkingLabel = distanceInfo.walkingText
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!typeLabel.isNullOrBlank()) {
                AlertTag(text = typeLabel)
            }
            if (!distanceLabel.isNullOrBlank()) {
                AlertTag(text = distanceLabel)
            }
            if (!walkingLabel.isNullOrBlank()) {
                AlertTag(text = walkingLabel)
            }
        }
        CountdownAndEndTimeRow(alert = alert)
    }
}

@Composable
private fun AlertTag(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuroraToolbar(
    onRefresh: () -> Unit,
    onOpenMap: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.alerts_toolbar_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
        },
        actions = {
            FilledIconButton(onClick = onRefresh, shape = CircleShape) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = stringResource(id = R.string.refresh_alerts)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledIconButton(onClick = onOpenMap, shape = CircleShape) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map),
                    contentDescription = stringResource(id = R.string.open_map)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            scrolledContainerColor = MaterialTheme.colorScheme.primary
        ),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.loading_alerts),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_placeholder),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primaryContainer
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(id = R.string.alerts_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.no_alerts_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(onClick = onRefresh, shape = RoundedCornerShape(18.dp)) {
            Text(text = stringResource(id = R.string.alerts_empty_cta))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertCard(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo,
    onOpenMaps: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentBrush = when {
        alert.type?.contains("raid", ignoreCase = true) == true -> Brush.horizontalGradient(listOf(EmberGradientStart, EmberGradientEnd))
        alert.type?.contains("shadow", ignoreCase = true) == true -> Brush.horizontalGradient(listOf(AuroraGradientStart, AuroraGradientEnd))
        else -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
        onClick = onShowDetails,
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
                    Text(
                        text = alert.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val distanceText = distanceInfo.distanceText
                    if (!distanceText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DistanceChip(text = distanceText)
                    }
                }
                FilledIconButton(
                    onClick = onOpenMaps,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map),
                        contentDescription = stringResource(id = R.string.open_in_maps)
                    )
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                if (alert.description.isNotBlank()) {
                    Text(
                        text = alert.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                AlertMetaRow(alert = alert, distanceInfo = distanceInfo)
                Spacer(modifier = Modifier.height(20.dp))
                ElevatedButton(
                    onClick = onOpenMaps,
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
private fun AlertImage(alert: PokemonAlert, modifier: Modifier = Modifier, rounded: Boolean = true) {
    val context = LocalContext.current
    val imageUrl by rememberUpdatedState(alert.imageUrl ?: alert.thumbnailUrl)
    if (imageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(id = R.string.alert_image),
            placeholder = painterResource(id = R.drawable.ic_placeholder),
            error = painterResource(id = R.drawable.ic_placeholder),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(if (rounded) 200.dp else 220.dp)
                .let { m -> if (rounded) m.clip(RoundedCornerShape(16.dp)) else m }
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(if (rounded) 200.dp else 220.dp)
                .background(
                    Brush.linearGradient(listOf(AuroraGradientStart, AuroraGradientEnd)),
                    if (rounded) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_placeholder),
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "No image available",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun AlertDetailDialog(alert: PokemonAlert, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.close))
            }
        },
        title = { 
            Text(
                text = alert.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            AlertDetailDialogContent(
                alert = alert,
                onOpenMaps = { openMapForAlert(context, alert) }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AlertDetailDialogContent(alert: PokemonAlert, onOpenMaps: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AlertImage(alert = alert, rounded = true)
        
        if (alert.description.isNotBlank()) {
            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        val typeLabel = alert.type?.takeIf { it.isNotBlank() }
        if (!typeLabel.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "Type: $typeLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        CountdownAndEndTimeRow(alert = alert)
        
        FilledTonalButton(
            onClick = onOpenMaps,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
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

@Composable
fun AlertDetailScreen(alert: PokemonAlert) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Hero image section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    AlertImage(alert = alert, rounded = false)
                    
                    // Gradient overlay for readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }
                
                // Content section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = alert.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    if (alert.description.isNotBlank()) {
                        Text(
                            text = alert.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    val detailType = alert.type?.takeIf { it.isNotBlank() }
                    if (!detailType.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Type: $detailType",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                    
                    CountdownAndEndTimeRow(alert = alert)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    FilledTonalButton(
                        onClick = { openMapForAlert(context, alert) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_map),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.open_in_maps),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            
            TopStatusBarScrim(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun TopStatusBarScrim(modifier: Modifier = Modifier) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topPadding + 80.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}

private fun openMapForAlert(context: Context, alert: PokemonAlert) {
    val mapsIntent = Intent(Intent.ACTION_VIEW, alert.googleMapsUri)
    try {
        context.startActivity(mapsIntent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_maps_app), Toast.LENGTH_SHORT).show()
    }
}

private fun getLastKnownLocation(context: Context): Location? {
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

@Composable
private fun DistanceChip(text: String) {
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

@Composable
private fun CountdownAndEndTimeRow(alert: PokemonAlert) {
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endMillis) {
        // Tick once per second while countdown is active
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val remaining = endMillis?.let { it - now } ?: -1
    val expiredLabel = stringResource(id = R.string.alert_expired)
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

private fun formatWalkingTime(meters: Float): String {
    // ~5 km/h walking speed (~83.33 m/min)
    val minutes = kotlin.math.ceil((meters / 83.333f).toDouble()).toInt().coerceAtLeast(1)
    return String.format(Locale.getDefault(), "%d min walk", minutes)
}
