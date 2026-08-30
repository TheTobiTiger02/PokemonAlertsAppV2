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
internal fun AlertListControls(
    visibleCount: Int,
    activeFilterCount: Int,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    sortPreference: SortPreference,
    onSortChanged: (SortPreference) -> Unit,
    onOpenFilters: () -> Unit,
    locationPrecisionInsufficient: Boolean,
    onRequestLocationPermission: () -> Unit,
    selectedFilter: AlertFilter,
    onFilterChanged: (AlertFilter) -> Unit,
    showDismissed: Boolean,
    onShowDismissedChanged: (Boolean) -> Unit,
    selectedArea: String,
    onClearAreaFilter: () -> Unit,
    maxDistance: Int,
    onClearDistanceFilter: () -> Unit,
    maxWalkingMinutes: Int = TravelTime.NO_LIMIT,
    onClearWalkingFilter: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = visibleCount,
                    transitionSpec = { appFadeThrough() },
                    label = "active_alert_count"
                ) { count ->
                    Text(
                        text = pluralStringResource(R.plurals.alerts_active_count, count, count),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = { onSearchExpandedChange(!searchExpanded) }) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchExpanded) "Close search" else "Search alerts"
                        )
                    }
                    SortingButton(currentSort = sortPreference, onSortChanged = onSortChanged)
                    Box {
                        IconButton(onClick = onOpenFilters) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filter),
                                contentDescription = "Filter alerts"
                            )
                        }
                        AnimatedContent(
                            targetState = activeFilterCount,
                            transitionSpec = { appFadeThrough() },
                            modifier = Modifier.align(Alignment.TopEnd),
                            label = "active_filter_count"
                        ) { count ->
                            if (count > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = searchExpanded,
                enter = appExpandIn(),
                exit = appCollapseOut()
            ) {
                AlertSearchBar(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    placeholder = stringResource(R.string.alerts_search_hint)
                )
            }
            AnimatedVisibility(
                visible = locationPrecisionInsufficient,
                enter = appExpandIn(),
                exit = appCollapseOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Location is approximate. Enable Precise location for walking routes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRequestLocationPermission) { Text("Improve") }
                }
            }
            AnimatedVisibility(
                visible = activeFilterCount > 0,
                enter = appExpandIn(),
                exit = appCollapseOut()
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedFilter != AlertFilter.ALL) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { onFilterChanged(AlertFilter.ALL) },
                                label = { Text(selectedFilter.label) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear type filter", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    if (showDismissed) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { onShowDismissedChanged(false) },
                                label = { Text("Dismissed") },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Remove dismissed filter", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    if (selectedArea != "All") {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = onClearAreaFilter,
                                label = { Text(selectedArea) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear area filter", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    if (maxDistance > 0) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = onClearDistanceFilter,
                                label = { Text("Within $maxDistance km") },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear distance filter", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    if (maxWalkingMinutes > TravelTime.NO_LIMIT) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = onClearWalkingFilter,
                                label = { Text("Under $maxWalkingMinutes min walk") },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear walking time filter", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    if (searchQuery.isNotBlank()) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    onSearchQueryChanged("")
                                    onSearchExpandedChange(false)
                                },
                                label = { Text("“$searchQuery”") },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear search", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryListControls(
    visibleCount: Int,
    activeFilterCount: Int,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    sortPreference: SortPreference,
    onSortChanged: (SortPreference) -> Unit,
    onOpenFilters: () -> Unit,
    selectedTypeFilter: AlertFilter,
    onClearTypeFilter: () -> Unit,
    selectedAreaFilter: HistoryAreaFilter,
    onClearAreaFilter: () -> Unit,
    selectedDateLabel: String?,
    onOpenDateFilter: () -> Unit,
    onClearDateFilter: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = visibleCount,
                    transitionSpec = { appFadeThrough() },
                    label = "history_loaded_count"
                ) { count ->
                    Text(
                        text = pluralStringResource(R.plurals.history_loaded_count, count, count),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSearchExpandedChange(!searchExpanded) }) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchExpanded) {
                                "Close history search"
                            } else {
                                "Search history"
                            }
                        )
                    }
                    SortingButton(currentSort = sortPreference, onSortChanged = onSortChanged)
                    Box {
                        IconButton(onClick = onOpenFilters) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filter),
                                contentDescription = "Filter history"
                            )
                        }
                        if (activeFilterCount > 0) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    text = activeFilterCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = searchExpanded,
                enter = appExpandIn(),
                exit = appCollapseOut()
            ) {
                AlertSearchBar(
                    query = searchQuery,
                    onQueryChanged = onSearchChanged,
                    placeholder = "Search history…"
                )
            }
            AnimatedVisibility(
                visible = activeFilterCount > 0,
                enter = appExpandIn(),
                exit = appCollapseOut()
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedTypeFilter != AlertFilter.ALL) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = onClearTypeFilter,
                                label = { Text(selectedTypeFilter.label) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear type filter", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    if (selectedAreaFilter != HistoryAreaFilter.BOTH) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = onClearAreaFilter,
                                label = { Text(selectedAreaFilter.label) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear area filter", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    selectedDateLabel?.let { label ->
                        item {
                            FilterChip(
                                selected = true,
                                onClick = onOpenDateFilter,
                                label = { Text(label) },
                                trailingIcon = {
                                    IconButton(onClick = onClearDateFilter) {
                                        Icon(Icons.Filled.Close, "Clear date", Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }
                    if (searchQuery.isNotBlank()) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    onSearchChanged("")
                                    onSearchExpandedChange(false)
                                },
                                label = { Text("“$searchQuery”") },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Clear history search", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun alertEmptyStateMessage(
    searchQuery: String,
    selectedFilter: AlertFilter,
    selectedArea: String,
    maxDistance: Int,
    showDismissed: Boolean,
    locationAvailable: Boolean
): String = when {
    searchQuery.isNotBlank() -> "No alerts match ‘${searchQuery.trim()}’. Try a different search."
    selectedFilter != AlertFilter.ALL -> "No ${selectedFilter.label.lowercase()} alerts match the current filters."
    selectedArea != "All" -> "No active alerts are available in $selectedArea right now."
    maxDistance > 0 && !locationAvailable -> "Distance filtering needs location access. Enable location or clear the distance limit."
    maxDistance > 0 -> "No active alerts are within $maxDistance km."
    showDismissed -> "There are no dismissed alerts to show."
    else -> "No alerts match the current filters."
}

@Composable
internal fun AlertSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            AnimatedContent(
                targetState = query.isNotEmpty(),
                transitionSpec = { appFadeThrough() },
                label = "search_clear_action"
            ) { showClear ->
                if (showClear) {
                    androidx.compose.material3.IconButton(onClick = { onQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
internal fun SortingButton(
    currentSort: SortPreference,
    onSortChanged: (SortPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Sort alerts, current: ${
                    when (currentSort) {
                        SortPreference.POSTED_TIME -> "posted time"
                        SortPreference.DISTANCE -> "distance"
                        SortPreference.TIME_REMAINING -> "time remaining"
                        SortPreference.NAME -> "name"
                    }
                }"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Sort by Posted Time") },
                onClick = {
                    onSortChanged(SortPreference.POSTED_TIME)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by Distance") },
                onClick = {
                    onSortChanged(SortPreference.DISTANCE)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by Time Remaining") },
                onClick = {
                    onSortChanged(SortPreference.TIME_REMAINING)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by Name") },
                onClick = {
                    onSortChanged(SortPreference.NAME)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.Star, contentDescription = null)
                }
            )
        }
    }
}

/**
 * Saved combinations of the four controls in this sheet.
 *
 * Lives in the filter sheet rather than above the feed: this is where the state it captures
 * gets edited, and the feed already carries a filter row, a chip row and a search field.
 */
@Composable
internal fun FilterPresetsSection(
    controls: FilterPresetControls,
    currentFilter: AlertFilter,
    currentSort: SortPreference,
    currentArea: String,
    currentMaxDistance: Int,
    onApplied: () -> Unit
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = { draftName = ""; naming = true }) { Text("Save current") }
        }
        if (controls.presets.isEmpty()) {
            Text(
                text = "Save the filters you keep coming back to and apply them in one tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(controls.presets, key = { it.name }) { preset ->
                    InputChip(
                        selected = false,
                        onClick = {
                            controls.onApply(preset)
                            onApplied()
                        },
                        label = { Text(preset.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Delete preset " + preset.name,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { controls.onDelete(preset.name) }
                            )
                        }
                    )
                }
            }
        }
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save these filters") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it.take(FilterPresets.MAX_NAME_LENGTH) },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    Text(
                        text = FilterPresets.describe(
                            FilterPreset(
                                name = draftName,
                                filter = currentFilter.name,
                                sort = currentSort.name,
                                area = currentArea,
                                maxDistance = currentMaxDistance
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draftName.isNotBlank(),
                    onClick = {
                        controls.onSaveCurrent(draftName)
                        naming = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun FilterRow(
    selectedFilter: AlertFilter,
    onFilterChanged: (AlertFilter) -> Unit,
    locationAvailable: Boolean,
    locationPermissionGranted: Boolean,
    locationLookupComplete: Boolean,
    onRequestLocationPermission: () -> Unit,
    availableFilters: Set<AlertFilter>
) {
    val visibleFilters = remember(availableFilters) {
        AlertFilter.entries.filter { it in availableFilters }
    }
    Column {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, _ ->
                        // Consume horizontal drag to prevent parent pager from intercepting
                    }
                }
        ) {
            items(visibleFilters, key = { it.name }) { filter ->
                val chipAccent = Color(resolveAlertVisualStyle(filter.label).category.accentArgb)
                ElevatedAssistChip(
                    onClick = { onFilterChanged(filter) },
                    label = { Text(text = filter.label) },
                    colors = AssistChipDefaults.elevatedAssistChipColors(
                        containerColor = if (selectedFilter == filter) chipAccent.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        labelColor = if (selectedFilter == filter) chipAccent else MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedFilter == filter) chipAccent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = locationLookupComplete && !locationAvailable,
            enter = appExpandIn(),
            exit = appCollapseOut()
        ) {
            TextButton(onClick = onRequestLocationPermission) {
                Text(
                    text = stringResource(
                        id = if (locationPermissionGranted) {
                            R.string.map_current_location_unavailable
                        } else {
                            R.string.alerts_nearby_permission_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun HistoryAreaFilterRow(
    selectedFilter: HistoryAreaFilter,
    onFilterChanged: (HistoryAreaFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, _ ->
                    // Consume horizontal drag to prevent parent pager from intercepting
                }
            }
    ) {
        items(HistoryAreaFilter.entries, key = { it.name }) { filter ->
            ElevatedAssistChip(
                onClick = { onFilterChanged(filter) },
                label = { Text(text = filter.label) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = AssistChipDefaults.elevatedAssistChipColors(
                    containerColor = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    },
                    labelColor = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    leadingIconContentColor = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selectedFilter == filter) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            )
        }
    }
}

@Composable
internal fun AlertsToolbar(
    onRefresh: () -> Unit,
    refreshing: Boolean,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        windowInsets = WindowInsets(0),
        title = {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            IconButton(onClick = onRefresh) {
                AnimatedRefreshIcon(
                    refreshing = refreshing,
                    contentDescription = stringResource(id = R.string.refresh_alerts)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        scrollBehavior = scrollBehavior
    )
}

@Composable
internal fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            ShimmerAlertCard()
        }
    }
}

@Composable
internal fun EmptyState(onRefresh: () -> Unit) {
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
            tint = Color.Unspecified
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
