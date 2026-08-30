@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Immutable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.tracking.rememberArrivalTrackingUiController
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.ui.components.AnimatedEmptyState
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.components.ShimmerAlertCard
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.DistanceSource
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.InputChip
import com.example.pokemonalertsv2.data.FilterPreset
import com.example.pokemonalertsv2.data.FilterPresets
import com.example.pokemonalertsv2.util.TravelTime

@Composable
internal fun AlertHistoryPage(
    uiState: com.example.pokemonalertsv2.ui.history.HistoryUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDateChanged: (String?) -> Unit,
    onTypeChanged: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onAlertClick: (PokemonAlert) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTypeFilter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var selectedAreaFilter by rememberSaveable { mutableStateOf(HistoryAreaFilter.BOTH) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var sortPreference by rememberSaveable { mutableStateOf(SortPreference.POSTED_TIME) }
    var showHistoryFilterSheet by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotBlank()) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(hasForegroundLocationPermission(context))
    }
    var locationLookupComplete by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    suspend fun refreshUserLocation() {
        val permissionGranted = hasForegroundLocationPermission(context)
        hasLocationPermission = permissionGranted
        userLocation = if (permissionGranted) {
            CachedLocationProvider.get(
                context = context,
                timeoutMs = 5_000,
                highAccuracy = false
            )?.takeIf { location ->
                validMapCoordinates(location.latitude, location.longitude) != null
            }
        } else {
            null
        }
        locationLookupComplete = true
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val permissionGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasForegroundLocationPermission(context)
        hasLocationPermission = permissionGranted
        if (permissionGranted) {
            locationLookupComplete = false
            scope.launch { refreshUserLocation() }
        } else {
            userLocation = null
            locationLookupComplete = true
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            locationLookupComplete = false
            refreshUserLocation()
        }
    }

    val alertEndTimes = remember(uiState.alerts) {
        uiState.alerts.associate { alert ->
            alert.uniqueId to TimeUtils.parseEndTimeToMillis(alert.endTime)
        }
    }

    // Trigger pagination when the user scrolls near the bottom of the list.
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 5 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !uiState.isLoading && !uiState.isLoadingMore && uiState.canLoadMore) {
            onLoadMore()
        }
    }

    // Always show every type filter — server has alerts of all types but we
    // only load one page at a time, so deriving chips from loaded items is incomplete.
    val availableFilters = remember { AlertFilter.entries.toSet() }

    val filteredAlerts = remember(uiState.alerts, selectedAreaFilter, sortPreference, userLocation) {
        // Type filtering is now server-side — uiState.alerts already contains
        // only the selected type/search result (or all types when no filter is active).
        var filtered = uiState.alerts.filter { selectedAreaFilter.includes(it) }

        // Sort based on user preference
        when (sortPreference) {
            SortPreference.POSTED_TIME -> filtered.sortedWith(compareByDescending<PokemonAlert> {
                // Higher ID = newer alert. Alerts without ID go to the end
                it.id?.toLong() ?: Long.MIN_VALUE
            }.thenByDescending {
                // Secondary sort by end time for alerts without ID
                alertEndTimes[it.uniqueId] ?: 0L
            })
            SortPreference.DISTANCE -> {
                userLocation?.let { loc ->
                    filtered.sortedBy { alert ->
                        alert.mapCoordinatesOrNull()?.let { coordinates ->
                            val results = FloatArray(1)
                            Location.distanceBetween(
                                loc.latitude,
                                loc.longitude,
                                coordinates.latitude,
                                coordinates.longitude,
                                results
                            )
                            results.getOrNull(0)?.takeUnless { it.isNaN() }
                        } ?: Float.MAX_VALUE
                    }
                } ?: filtered.sortedWith(compareByDescending<PokemonAlert> {
                    it.id?.toLong() ?: Long.MIN_VALUE
                }.thenByDescending {
                    alertEndTimes[it.uniqueId] ?: 0L
                })
            }
            SortPreference.TIME_REMAINING -> filtered.sortedBy {
                alertEndTimes[it.uniqueId] ?: Long.MAX_VALUE
            }
            SortPreference.NAME -> filtered.sortedBy {
                it.name.lowercase()
            }
        }
    }

    // Statistics: prefer /api/stats/total scoped to the active date, fall back to local counting.
    val statistics = remember(
        filteredAlerts,
        uiState.totalStats,
        uiState.totalStatsDate,
        uiState.selectedDate,
        uiState.selectedType,
        uiState.searchQuery,
        uiState.totalServerCount,
        selectedAreaFilter
    ) {
        val stats = uiState.totalStats.takeIf { uiState.totalStatsDate == uiState.selectedDate }
        val serverTotal = uiState.totalServerCount
        val byType = stats?.byType ?: emptyMap()
        val isAreaScoped = selectedAreaFilter != HistoryAreaFilter.BOTH
        val canUseServerStats = stats != null && !isAreaScoped && uiState.searchQuery.isBlank() && byType.isNotEmpty()

        fun emptyStatistics(total: Int, today: Int = 0) = mutableMapOf(
            "total" to total,
            "today" to today,
            "raids" to 0,
            "quests" to 0,
            "rares" to 0,
            "hundos" to 0,
            "pvp" to 0,
            "nundos" to 0,
            "rocket" to 0,
            "kecleon" to 0,
            "other" to 0
        )

        fun serverBreakdown(total: Int, today: Int = 0) = emptyStatistics(total, today).apply {
            this["raids"] = byType["Raid"] ?: 0
            this["quests"] = byType["Quest"] ?: 0
            this["rares"] = byType["Rare"] ?: 0
            this["hundos"] = byType["Hundo"] ?: 0
            this["pvp"] = byType["PvP"] ?: 0
            this["nundos"] = byType["Nundo"] ?: 0
            this["rocket"] = byType["Rocket"] ?: 0
            this["kecleon"] = byType["Kecleon"] ?: 0
        }

        fun serverCountForSelectedType(type: String): Int? = when (type) {
            "Raid" -> byType["Raid"]
            "Quest" -> byType["Quest"]
            "Rare" -> byType["Rare"]
            "Hundo" -> byType["Hundo"]
            "PvP" -> byType["PvP"]
            "Nundo" -> byType["Nundo"]
            "Rocket" -> byType["Rocket"]
            "Kecleon" -> byType["Kecleon"]
            "WeatherChange" -> byType["WeatherChange"] ?: byType["Weather"]
            else -> null
        }

        fun putSelectedTypeCount(target: MutableMap<String, Int>, type: String, count: Int) {
            when (type) {
                "Raid" -> target["raids"] = count
                "Quest" -> target["quests"] = count
                "Rare" -> target["rares"] = count
                "Hundo" -> target["hundos"] = count
                "PvP" -> target["pvp"] = count
                "Nundo" -> target["nundos"] = count
                "Rocket" -> target["rocket"] = count
                "Kecleon" -> target["kecleon"] = count
                else -> target["other"] = count
            }
        }

        // Local breakdown from loaded alerts (always computed as fallback)
        var raids = 0; var quests = 0; var rares = 0; var hundos = 0
        var pvp = 0; var nundos = 0; var rocket = 0; var kecleon = 0; var other = 0
        filteredAlerts.forEach { alert ->
            var categorized = false
            if (alert.hasType("Raid")) { raids++; categorized = true }
            if (alert.hasType("Quest")) { quests++; categorized = true }
            if (alert.hasType("Rare") || alert.hasType("Spawn")) { rares++; categorized = true }
            if (alert.hasType("Hundo")) { hundos++; categorized = true }
            if (alert.hasType("PvP")) { pvp++; categorized = true }
            if (alert.hasType("Nundo")) { nundos++; categorized = true }
            if (alert.hasType("Rocket")) { rocket++; categorized = true }
            if (alert.hasType("Kecleon")) { kecleon++; categorized = true }
            if (!categorized) other++
        }

        when {
            canUseServerStats && uiState.selectedType == null -> {
                serverBreakdown(
                    total = stats?.totalAlerts ?: serverTotal,
                    today = if (uiState.selectedDate == null) stats?.totalToday ?: 0 else 0
                )
            }
            canUseServerStats && uiState.selectedType != null -> {
                val selectedType = uiState.selectedType
                val selectedTypeTotal = serverCountForSelectedType(selectedType) ?: serverTotal
                emptyStatistics(total = if (serverTotal > 0) serverTotal else selectedTypeTotal).apply {
                    putSelectedTypeCount(this, selectedType, selectedTypeTotal)
                }
            }
            else -> {
                mapOf(
                    "total" to if (!isAreaScoped && serverTotal > 0) serverTotal else filteredAlerts.size,
                    "today" to 0,
                    "raids" to raids,
                    "quests" to quests,
                    "rares" to rares,
                    "hundos" to hundos,
                    "pvp" to pvp,
                    "nundos" to nundos,
                    "rocket" to rocket,
                    "kecleon" to kecleon,
                    "other" to other
                )
            }
        }
    }

    val selectedDateLabel = selectedDateMillis?.let { millis ->
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        String.format(
            "%02d/%02d/%04d",
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.YEAR)
        )
    }
    val activeHistoryFilterCount = listOf(
        selectedTypeFilter != AlertFilter.ALL,
        selectedAreaFilter != HistoryAreaFilter.BOTH,
        selectedDateMillis != null,
        uiState.searchQuery.isNotBlank()
    ).count { it }
    val applyHistoryTypeFilter: (AlertFilter) -> Unit = { filter ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selectedTypeFilter = filter
        onTypeChanged(
            when (filter) {
                AlertFilter.ALL -> null
                AlertFilter.RAIDS -> "Raid"
                AlertFilter.QUESTS -> "Quest"
                AlertFilter.RARES -> "Rare"
                AlertFilter.HUNDOS -> "Hundo"
                AlertFilter.PVP -> "PvP"
                AlertFilter.NUNDOS -> "Nundo"
                AlertFilter.KECLEON -> "Kecleon"
                AlertFilter.ROCKET -> "Rocket"
                AlertFilter.WEATHER_CHANGE -> "WeatherChange"
            }
        )
    }
    val showHistoryDatePicker = {
        val calendar = java.util.Calendar.getInstance().apply {
            selectedDateMillis?.let { timeInMillis = it }
        }
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, day: Int ->
                val selectedCalendar = java.util.Calendar.getInstance().apply { set(year, month, day) }
                selectedDateMillis = selectedCalendar.timeInMillis
                onDateChanged(String.format("%04d-%02d-%02d", year, month + 1, day))
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = {
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = rememberPullToRefreshState()
    ) {
        val countdownClock = rememberCountdownClock()
        val goDexMatches = rememberGoDexMatchResults(uiState.alerts)
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryListControls(
                visibleCount = filteredAlerts.size,
                activeFilterCount = activeHistoryFilterCount,
                searchExpanded = searchExpanded,
                onSearchExpandedChange = { searchExpanded = it },
                searchQuery = uiState.searchQuery,
                onSearchChanged = onSearchChanged,
                sortPreference = sortPreference,
                onSortChanged = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sortPreference = it
                },
                onOpenFilters = { showHistoryFilterSheet = true },
                selectedTypeFilter = selectedTypeFilter,
                onClearTypeFilter = { applyHistoryTypeFilter(AlertFilter.ALL) },
                selectedAreaFilter = selectedAreaFilter,
                onClearAreaFilter = { selectedAreaFilter = HistoryAreaFilter.BOTH },
                selectedDateLabel = selectedDateLabel,
                onOpenDateFilter = { showHistoryFilterSheet = true },
                onClearDateFilter = {
                    selectedDateMillis = null
                    onDateChanged(null)
                }
            )
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val columns = if (maxWidth >= 840.dp) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            uiState.errorMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (uiState.alerts.isEmpty()) "Unable to load history" else "Showing saved history · $message",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = onRefresh) { Text("Retry") }
                            }
                        }
                }
            }

            // Statistics Card
            item(span = { GridItemSpan(maxLineSpan) }) {
                var isExpanded by rememberSaveable { mutableStateOf(false) }

                val dateText = if (selectedDateMillis != null) {
                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
                    String.format(
                        "%02d/%02d/%04d",
                        calendar.get(java.util.Calendar.DAY_OF_MONTH),
                        calendar.get(java.util.Calendar.MONTH) + 1,
                        calendar.get(java.util.Calendar.YEAR)
                    )
                } else {
                    "All Time"
                }
                val statsScopeText = if (selectedAreaFilter == HistoryAreaFilter.BOTH) {
                    dateText
                } else {
                    "${selectedAreaFilter.label} - $dateText"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${statistics["total"]} alerts \u00b7 $statsScopeText" +
                                    if ((statistics["today"] ?: 0) > 0) " \u00b7 ${statistics["today"]} today" else "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = appExpandIn(),
                            exit = appCollapseOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if ((statistics["raids"] ?: 0) > 0) {
                                    StatRow("Raids", statistics["raids"] ?: 0, Color(AlertCategory.RAID.accentArgb))
                                }
                                if ((statistics["quests"] ?: 0) > 0) {
                                    StatRow("Quests", statistics["quests"] ?: 0, Color(AlertCategory.QUEST.accentArgb))
                                }
                                if ((statistics["rares"] ?: 0) > 0) {
                                    StatRow("Rare", statistics["rares"] ?: 0, Color(AlertCategory.RARE.accentArgb))
                                }
                                if ((statistics["hundos"] ?: 0) > 0) {
                                    StatRow("Hundos", statistics["hundos"] ?: 0, Color(AlertCategory.HUNDO.accentArgb))
                                }
                                if ((statistics["pvp"] ?: 0) > 0) {
                                    StatRow("PvP", statistics["pvp"] ?: 0, Color(AlertCategory.PVP.accentArgb))
                                }
                                if ((statistics["nundos"] ?: 0) > 0) {
                                    StatRow("Nundos", statistics["nundos"] ?: 0, Color(AlertCategory.NUNDO.accentArgb))
                                }
                                if ((statistics["rocket"] ?: 0) > 0) {
                                    StatRow("Rocket", statistics["rocket"] ?: 0, Color(AlertCategory.ROCKET.accentArgb))
                                }
                                if ((statistics["kecleon"] ?: 0) > 0) {
                                    StatRow("Kecleon", statistics["kecleon"] ?: 0, Color(AlertCategory.KECLEON.accentArgb))
                                }
                                if ((statistics["other"] ?: 0) > 0) {
                                    StatRow("Other", statistics["other"] ?: 0, Color(AlertCategory.GENERIC.accentArgb))
                                }
                            }
                        }
                    }
                }
            }

            // Shimmer placeholders while the first page is loading
            if (uiState.isLoading && uiState.alerts.isEmpty()) {
                repeat(3) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ShimmerAlertCard() }
                }
            } else if (filteredAlerts.isEmpty() && !uiState.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedEmptyState(
                        title = "No history found",
                        message = "No alerts match your search or filters. Try changing the search, area, date, or type.",
                        modifier = Modifier.animateItem()
                    )
                }
            }

            filteredAlerts.forEachIndexed { index, alert ->
                val dayLabel = historyDayLabel(alert)
                val previousDayLabel = filteredAlerts.getOrNull(index - 1)?.let(::historyDayLabel)
                if (sortPreference == SortPreference.POSTED_TIME && dayLabel != previousDayLabel) {
                    item(
                        key = "history_day_$dayLabel",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "history_day"
                    ) {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .animateItem()
                                .padding(top = if (index == 0) 0.dp else 4.dp)
                        )
                    }
                }
                item(key = alert.uniqueId, contentType = "alert_card") {
                    AlertCard(
                        alert = alert,
                        distanceInfo = AlertDistanceInfo(null, null, null),
                        goDexStatus = goDexMatches[alert.uniqueId]
                            ?: GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED),
                        countdownClock = countdownClock,
                        cardContext = AlertCardContext.HISTORY,
                        modifier = Modifier.animateItem(),
                        onOpenMaps = { openMapForAlert(context, alert) },
                        onShowDetails = {
                            onAlertClick(alert)
                        },
                        onSecondaryAction = { action ->
                            when (action) {
                                AlertSecondaryAction.SNOOZE -> Unit
                                AlertSecondaryAction.PICTURE_IN_PICTURE ->
                                    openAlertInPictureInPicture(context, alert)
                                AlertSecondaryAction.SHARE -> {
                                    scope.launch { AlertShareCard.share(context, alert) }
                                }
                            }
                        }
                    )
                }
            }

            // Loading indicator while fetching the next page
            if (uiState.isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            uiState.paginationErrorMessage?.let {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Couldn't load more history", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onLoadMore) { Text("Retry") }
                    }
                }
            }
            }
        }
    }
    }

    if (showHistoryFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showHistoryFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Filter history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Alert type", style = MaterialTheme.typography.titleSmall)
                FilterRow(
                    selectedFilter = selectedTypeFilter,
                    onFilterChanged = applyHistoryTypeFilter,
                    locationAvailable = hasLocationPermission && userLocation != null,
                    locationPermissionGranted = hasLocationPermission,
                    locationLookupComplete = locationLookupComplete,
                    onRequestLocationPermission = {
                        if (hasForegroundLocationPermission(context)) {
                            locationLookupComplete = false
                            scope.launch { refreshUserLocation() }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    availableFilters = availableFilters
                )
                Text("Area", style = MaterialTheme.typography.titleSmall)
                HistoryAreaFilterRow(
                    selectedFilter = selectedAreaFilter,
                    onFilterChanged = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedAreaFilter = it
                    }
                )
                Text("Date", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedDateMillis == null,
                            onClick = {
                                selectedDateMillis = null
                                onDateChanged(null)
                            },
                            label = { Text("All") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.selectedDate == historyDateString(0),
                            onClick = {
                                selectedDateMillis = historyDateMillis(0)
                                onDateChanged(historyDateString(0))
                            },
                            label = { Text("Today") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.selectedDate == historyDateString(1),
                            onClick = {
                                selectedDateMillis = historyDateMillis(1)
                                onDateChanged(historyDateString(1))
                            },
                            label = { Text("Yesterday") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedDateMillis != null &&
                                uiState.selectedDate != historyDateString(0) &&
                                uiState.selectedDate != historyDateString(1),
                            onClick = showHistoryDatePicker,
                            label = { Text("Custom") },
                            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) }
                        )
                    }
                }
                if (activeHistoryFilterCount > 0) {
                    TextButton(
                        onClick = {
                            applyHistoryTypeFilter(AlertFilter.ALL)
                            selectedAreaFilter = HistoryAreaFilter.BOTH
                            selectedDateMillis = null
                            onDateChanged(null)
                            onSearchChanged("")
                        }
                    ) {
                        Text("Clear all filters")
                    }
                }
                Button(
                    onClick = { showHistoryFilterSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show results")
                }
            }
        }
    }
}

internal fun historyDateMillis(daysAgo: Int, nowMillis: Long = System.currentTimeMillis()): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.DAY_OF_YEAR, -daysAgo.coerceAtLeast(0))
    }.timeInMillis

internal fun historyDateString(daysAgo: Int, nowMillis: Long = System.currentTimeMillis()): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(historyDateMillis(daysAgo, nowMillis)))

internal fun historyDayLabel(
    alert: PokemonAlert,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val timestamp = TimeUtils.parseEndTimeToMillis(alert.createdAt)
        ?: TimeUtils.parseEndTimeToMillis(alert.endTime)
        ?: return "Date unavailable"
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM"))
    }
}

@Composable
internal fun StatRow(label: String, count: Int, color: Color) {
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

/**
 * Builds a styled HTML page for sharing an alert that can be opened in any browser.
 * Uses dark theme styling matching the app's Midnight Sky theme.
 */
internal fun buildAlertShareHtml(alert: PokemonAlert): String {
    val title = formatAlertTitle(alert)
    val imageUrl = alert.imageUrl ?: alert.thumbnailUrl ?: ""
    val mapsUrl = alert.googleMapsUri.toString()

    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pokemon Alert: $title</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #0A0A0F;
            min-height: 100vh;
            padding: 20px;
            color: #E2E8F0;
        }
        .card {
            max-width: 480px;
            margin: 0 auto;
            background: rgba(18, 18, 24, 0.95);
            border-radius: 24px;
            overflow: hidden;
            box-shadow: 0 20px 40px rgba(0,0,0,0.6);
            border: 1px solid rgba(255, 107, 53, 0.15);
        }
        .image-container {
            position: relative;
            height: 280px;
            background: linear-gradient(135deg, #1F120E 0%, #121218 100%);
        }
        .image-container img {
            width: 100%;
            height: 100%;
            object-fit: contain;
            background: linear-gradient(135deg, #1F120E 0%, #121218 100%);
        }
        .content { padding: 24px; }
        h1 {
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 16px;
            background: linear-gradient(90deg, #FF6B35, #FFA809);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }
        .stats {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 12px;
            margin-bottom: 20px;
        }
        .stat {
            background: rgba(255, 107, 53, 0.1);
            padding: 12px;
            border-radius: 12px;
            text-align: center;
            border: 1px solid rgba(255, 107, 53, 0.15);
        }
        .stat-value { font-size: 20px; font-weight: 700; color: #FFA809; }
        .stat-label { font-size: 12px; color: #94A3B8; margin-top: 4px; }
        .time-badge {
            display: inline-block;
            background: linear-gradient(90deg, #FF6B35, #D62828);
            color: white;
            padding: 8px 16px;
            border-radius: 20px;
            font-weight: 600;
            margin-bottom: 20px;
        }
        .location { color: #94A3B8; font-size: 14px; margin-bottom: 16px; }
        .maps-btn {
            display: block;
            width: 100%;
            background: linear-gradient(90deg, #FF6B35, #FFA809);
            color: black;
            text-decoration: none;
            padding: 16px;
            border-radius: 12px;
            text-align: center;
            font-weight: 700;
            font-size: 16px;
        }
        .maps-btn:hover { opacity: 0.9; }
        .footer {
            text-align: center;
            margin-top: 20px;
            color: #64748B;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="image-container">
            ${if (imageUrl.isNotBlank()) "<img src=\"$imageUrl\" alt=\"$title\" onerror=\"this.style.display='none'\">" else ""}
        </div>
        <div class="content">
            <h1>$title</h1>
            <div class="time-badge">⏱ Ends: ${alert.endTime}</div>
            <div class="stats">
                ${alert.formattedIv?.let { "<div class='stat'><div class='stat-value'>$it</div><div class='stat-label'>IV</div></div>" } ?: ""}
                ${alert.cp?.let { "<div class='stat'><div class='stat-value'>$it</div><div class='stat-label'>CP</div></div>" } ?: ""}
                ${alert.level?.let { val v = if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString(); "<div class='stat'><div class='stat-value'>$v</div><div class='stat-label'>Level</div></div>" } ?: ""}
            </div>
            ${alert.locationDisplay?.let { "<div class='location'>📍 $it</div>" } ?: ""}
            <a href="$mapsUrl" class="maps-btn">📍 Open in Google Maps</a>
        </div>
    </div>
    <div class="footer">Shared from Pokemon Alerts</div>
</body>
</html>
    """.trimIndent()
}
