@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.pokemonalertsv2.ui.history

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.DatePicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertUiModel
import com.example.pokemonalertsv2.ui.alerts.AlertCard
import com.example.pokemonalertsv2.ui.alerts.AlertDistanceInfo
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import com.example.pokemonalertsv2.util.TimeUtils
import java.util.Calendar
import java.util.Date

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    RAIDS("Raids"),
    QUESTS("Quests"),
    SPAWNS("Spawns"),
    HUNDOS("Hundos"),
    PVP("PvP"),
    NUNDOS("Nundos"),
    ROCKET("Rocket"),
    KECLEON("Kecleon")
}

data class AlertStatistics(
    val totalAlerts: Int,
    val raidCount: Int,
    val questCount: Int,
    val spawnCount: Int,
    val hundoCount: Int,
    val pvpCount: Int,
    val nundoCount: Int,
    val rocketCount: Int,
    val kecleonCount: Int,
    val otherCount: Int
)

private fun calculateStatistics(alerts: List<PokemonAlert>): AlertStatistics {
    var raids = 0
    var quests = 0
    var spawns = 0
    var hundos = 0
    var pvp = 0
    var nundos = 0
    var rocket = 0
    var kecleon = 0
    var other = 0
    
    alerts.forEach { alert ->
        when (alert.type?.lowercase()) {
            "raid" -> raids++
            "quest" -> quests++
            "rare", "spawn" -> spawns++
            "hundo" -> hundos++
            "pvp" -> pvp++
            "nundo" -> nundos++
            "rocket" -> rocket++
            "kecleon" -> kecleon++
            else -> other++
        }
    }
    
    return AlertStatistics(
        totalAlerts = alerts.size,
        raidCount = raids,
        questCount = quests,
        spawnCount = spawns,
        hundoCount = hundos,
        pvpCount = pvp,
        nundoCount = nundos,
        rocketCount = rocket,
        kecleonCount = kecleon,
        otherCount = other
    )
}

@Composable
fun AlertHistoryRoute(
    viewModel: AlertHistoryViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    AlertHistoryScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::fetchHistory,
        onBackClick = onBackClick,
        onAlertClick = { alert ->
            context.startActivity(AlertDetailActivity.createIntent(context, alert))
        }
    )
}

@Composable
fun AlertHistoryScreen(
    uiState: HistoryUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onBackClick: () -> Unit,
    onAlertClick: (PokemonAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTypeFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }

    // Determine available type filters
    val availableFilters = remember(uiState.alerts) {
        val filters = mutableSetOf(HistoryFilter.ALL)
        val alerts = uiState.alerts
        
        if (alerts.any { it.type?.equals("Raid", ignoreCase = true) == true }) filters.add(HistoryFilter.RAIDS)
        if (alerts.any { it.type?.equals("Quest", ignoreCase = true) == true }) filters.add(HistoryFilter.QUESTS)
        if (alerts.any { 
            val t = it.type
            t?.equals("Rare", ignoreCase = true) == true || t?.equals("Spawn", ignoreCase = true) == true 
        }) filters.add(HistoryFilter.SPAWNS)
        if (alerts.any { it.type?.equals("Hundo", ignoreCase = true) == true }) filters.add(HistoryFilter.HUNDOS)
        if (alerts.any { it.type?.equals("PvP", ignoreCase = true) == true }) filters.add(HistoryFilter.PVP)
        if (alerts.any { it.type?.equals("Nundo", ignoreCase = true) == true }) filters.add(HistoryFilter.NUNDOS)
        if (alerts.any { it.type?.equals("Kecleon", ignoreCase = true) == true }) filters.add(HistoryFilter.KECLEON)
        if (alerts.any { it.type?.equals("Rocket", ignoreCase = true) == true }) filters.add(HistoryFilter.ROCKET)
        filters
    }

    val filteredAlerts = remember(uiState.alerts, selectedTypeFilter, selectedDateMillis) {
        var filtered = uiState.alerts

        // Type Filter
        filtered = when (selectedTypeFilter) {
            HistoryFilter.ALL -> filtered
            HistoryFilter.RAIDS -> filtered.filter { it.type?.equals("Raid", ignoreCase = true) == true }
            HistoryFilter.QUESTS -> filtered.filter { it.type?.equals("Quest", ignoreCase = true) == true }
            HistoryFilter.SPAWNS -> filtered.filter { 
                val t = it.type
                t?.equals("Rare", ignoreCase = true) == true || t?.equals("Spawn", ignoreCase = true) == true 
            }
            HistoryFilter.HUNDOS -> filtered.filter { it.type?.equals("Hundo", ignoreCase = true) == true }
            HistoryFilter.PVP -> filtered.filter { it.type?.equals("PvP", ignoreCase = true) == true }
            HistoryFilter.NUNDOS -> filtered.filter { it.type?.equals("Nundo", ignoreCase = true) == true }
            HistoryFilter.KECLEON -> filtered.filter { it.type?.equals("Kecleon", ignoreCase = true) == true }
            HistoryFilter.ROCKET -> filtered.filter { it.type?.equals("Rocket", ignoreCase = true) == true }
        }

        // Date Filter
        if (selectedDateMillis != null) {
            val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
            filtered = filtered.filter { alert ->
                val alertTime = TimeUtils.parseEndTimeToMillis(alert.endTime)
                if (alertTime != null) {
                    val alertCal = Calendar.getInstance().apply { timeInMillis = alertTime }
                    alertCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                    alertCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                } else {
                    false
                }
            }
        }
        
        // Sort by time descending and convert to immutable list for stability
        filtered.sortedByDescending { TimeUtils.parseEndTimeToMillis(it.endTime) ?: 0L }.toList()
    }
    
    val statistics = remember(filteredAlerts) {
        calculateStatistics(filteredAlerts)
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Alert History", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        FilledIconButton(
                            onClick = {
                                showDatePicker(context) { dateMillis ->
                                    selectedDateMillis = dateMillis
                                }
                            },
                            shape = CircleShape,
                            colors = if (selectedDateMillis != null) {
                                IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            } else {
                                IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Filter by Date")
                        }
                        if (selectedDateMillis != null) {
                            IconButton(onClick = { selectedDateMillis = null }) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.primary
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    state = rememberPullToRefreshState()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item {
                            FilterRow(
                                selectedFilter = selectedTypeFilter,
                                onFilterChanged = { selectedTypeFilter = it },
                                availableFilters = availableFilters
                            )
                        }
                        
                        item {
                            StatisticsCard(
                                statistics = statistics,
                                selectedDate = selectedDateMillis
                            )
                        }

                        if (filteredAlerts.isEmpty() && !uiState.isLoading) {
                             item {
                                Text(
                                    text = "No history found.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(
                            items = filteredAlerts,
                            // No key parameter - let Compose use positional identity
                            // This prevents crashes with duplicate alert data
                            contentType = { "alert_card" }
                        ) { alert ->
                            AlertCard(
                                alert = alert,
                                distanceInfo = AlertDistanceInfo(null, null, null), // No distance for history
                                onOpenMaps = { 
                                    val uri = Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}(${alert.name})")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                },
                                onShowDetails = { onAlertClick(alert) },
                                onShareClick = { /* Share implementation */ },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: HistoryFilter,
    onFilterChanged: (HistoryFilter) -> Unit,
    availableFilters: Set<HistoryFilter>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HistoryFilter.values().filter { it in availableFilters }.forEach { filter ->
            ElevatedAssistChip(
                onClick = { onFilterChanged(filter) },
                label = { Text(text = filter.label) },
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
}

@Composable
private fun StatisticsCard(
    statistics: AlertStatistics,
    selectedDate: Long?,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    
    val dateText = if (selectedDate != null) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        String.format(
            "%02d/%02d/%04d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )
    } else {
        "All Time"
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Statistics for $dateText",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${statistics.totalAlerts} total alerts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expandable breakdown
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (statistics.raidCount > 0) {
                        StatRow("Raids", statistics.raidCount, Color(0xFFE91E63))
                    }
                    if (statistics.questCount > 0) {
                        StatRow("Quests", statistics.questCount, Color(0xFF2196F3))
                    }
                    if (statistics.spawnCount > 0) {
                        StatRow("Spawns", statistics.spawnCount, Color(0xFF4CAF50))
                    }
                    if (statistics.hundoCount > 0) {
                        StatRow("Hundos", statistics.hundoCount, Color(0xFFFFD700))
                    }
                    if (statistics.pvpCount > 0) {
                        StatRow("PvP", statistics.pvpCount, Color(0xFF9C27B0))
                    }
                    if (statistics.nundoCount > 0) {
                        StatRow("Nundos", statistics.nundoCount, Color(0xFF607D8B))
                    }
                    if (statistics.rocketCount > 0) {
                        StatRow("Rocket", statistics.rocketCount, Color(0xFFFF5722))
                    }
                    if (statistics.kecleonCount > 0) {
                        StatRow("Kecleon", statistics.kecleonCount, Color(0xFF00BCD4))
                    }
                    if (statistics.otherCount > 0) {
                        StatRow("Other", statistics.otherCount, Color(0xFF757575))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun showDatePicker(context: Context, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, day: Int ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(year, month, day)
            onDateSelected(selectedCalendar.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}
