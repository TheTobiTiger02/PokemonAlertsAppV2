package com.example.pokemonalertsv2.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexDebugEntry
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import java.util.Calendar
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.UpdateCheckSource
import com.example.pokemonalertsv2.util.UpdateState
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.NotificationCategoryState

internal enum class SettingsDestination(val title: String) {
    OVERVIEW("Settings"),
    APPEARANCE_BEHAVIOR("Appearance & behavior"),
    ALERT_FILTERS("Alert filters"),
    NOTIFICATIONS("Notifications"),
    ABOUT_UPDATES("About & updates")
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onManageLocationPermissions: () -> Unit
) {
    var destinationName by rememberSaveable { mutableStateOf(SettingsDestination.OVERVIEW.name) }
    val destination = SettingsDestination.entries.firstOrNull { it.name == destinationName }
        ?: SettingsDestination.OVERVIEW
    val navigateTo: (SettingsDestination) -> Unit = { destinationName = it.name }

    BackHandler(enabled = destination != SettingsDestination.OVERVIEW) {
        navigateTo(SettingsDestination.OVERVIEW)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var foregroundLocationGranted by remember { mutableStateOf(false) }
    var backgroundLocationGranted by remember { mutableStateOf(false) }
    var systemNotificationsGranted by remember { mutableStateOf(true) }
    fun refreshLocationPermissionStatus() {
        foregroundLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        backgroundLocationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        systemNotificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    DisposableEffect(lifecycleOwner) {
        refreshLocationPermissionStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshLocationPermissionStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val raidsNotifications by viewModel.raidsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val spawnsNotifications by viewModel.spawnsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val questsNotifications by viewModel.questsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val hundosNotifications by viewModel.hundosNotifications.collectAsStateWithLifecycle(initialValue = true)
    val pvpNotifications by viewModel.pvpNotifications.collectAsStateWithLifecycle(initialValue = true)
    val nundosNotifications by viewModel.nundosNotifications.collectAsStateWithLifecycle(initialValue = true)
    val kecleonNotifications by viewModel.kecleonNotifications.collectAsStateWithLifecycle(initialValue = true)
    val rocketNotifications by viewModel.rocketNotifications.collectAsStateWithLifecycle(initialValue = true)
    val notificationVibrate by viewModel.notificationVibrate.collectAsStateWithLifecycle(initialValue = true)
    val silenceUntil by viewModel.silenceUntil.collectAsStateWithLifecycle(initialValue = 0L)
    val selectedArea by viewModel.selectedArea.collectAsStateWithLifecycle(initialValue = "All")
    val maxDistance by viewModel.maxDistance.collectAsStateWithLifecycle(initialValue = 0)
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = 0)
    val savedSortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
        initialValue = SortPreference.POSTED_TIME
    )
    
    // Excluded types for granular filtering
    val excludedHundoTypes by viewModel.excludedHundoTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedNundoTypes by viewModel.excludedNundoTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedPvpTypes by viewModel.excludedPvpTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedSpawnTypes by viewModel.excludedSpawnTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedRocketTypes by viewModel.excludedRocketTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedRaidTiers by viewModel.excludedRaidTiers.collectAsStateWithLifecycle(initialValue = emptySet())
    val goDexConfig by viewModel.goDexConfig.collectAsStateWithLifecycle()
    val goDexEntries by viewModel.goDexEntries.collectAsStateWithLifecycle()
    val goDexSyncUiState by viewModel.goDexSyncUiState.collectAsStateWithLifecycle()
    var goDexUrlInput by rememberSaveable { mutableStateOf("") }
    var showGoDexDebugList by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(goDexConfig.url) {
        if (goDexConfig.url.isNotBlank() || goDexUrlInput.isBlank()) {
            goDexUrlInput = goDexConfig.url
        }
    }

    LinearModernBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(destination.title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (destination != SettingsDestination.OVERVIEW) {
                            FilledIconButton(
                                onClick = { navigateTo(SettingsDestination.OVERVIEW) },
                                shape = CircleShape
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                if (destination == SettingsDestination.OVERVIEW) {
                    SettingsOverview(
                        themeMode = themeMode,
                        sortPreference = savedSortPreference,
                        selectedArea = selectedArea,
                        maxDistance = maxDistance,
                        notificationsEnabled = notificationsEnabled,
                        foregroundLocationGranted = foregroundLocationGranted,
                        backgroundLocationGranted = backgroundLocationGranted,
                        onDestinationSelected = navigateTo
                    )
                }

                if (destination == SettingsDestination.APPEARANCE_BEHAVIOR) {
                SettingsSection(title = "Display and sorting") {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(0 to "System", 1 to "Light", 2 to "Dark").forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateThemeMode(mode) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.updateThemeMode(mode) }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = "Default sort",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(
                        SortPreference.POSTED_TIME to "Newest",
                        SortPreference.TIME_REMAINING to "Time remaining",
                        SortPreference.DISTANCE to "Distance",
                        SortPreference.NAME to "Name"
                    ).forEach { (preference, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSortPreference(preference) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = savedSortPreference == preference,
                                onClick = { viewModel.updateSortPreference(preference) }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                }

                if (destination == SettingsDestination.ALERT_FILTERS) {
                SettingsSection(title = "Location filters") {
                    Column {
                        Text(
                            text = "Area",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All", "Alsbach", "Darmstadt").forEach { area ->
                                val isSelected = selectedArea == area
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateSelectedArea(area) },
                                    label = { Text(area) }
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Maximum Distance",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (maxDistance == 0) "Unlimited" else "$maxDistance km",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Hide alerts further than this distance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        androidx.compose.material3.Slider(
                            value = maxDistance.toFloat(),
                            onValueChange = { viewModel.updateMaxDistance(kotlin.math.round(it).toInt()) },
                            valueRange = 0f..50f
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("0", "10", "20", "30", "40", "50+").forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                SettingsSection(title = "GoDex Hundo checklist") {
                    val totalCount = goDexEntries.size
                    val neededCount = goDexEntries.count { it.needed }
                    val isStale = goDexConfig.lastSuccessfulSyncMillis > 0L &&
                        System.currentTimeMillis() - goDexConfig.lastSuccessfulSyncMillis >=
                        GoDexRepository.STALE_WARNING_MILLIS

                     if (!goDexConfig.isConnected) {
                        val context = LocalContext.current
                        Text(
                            "Connect your GoDex Hundo collection to track needed targets, filter notifications, and sync catches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ElevatedButton(
                            onClick = {
                                context.startActivity(
                                    com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in to GoDex (Two-Way Sync)")
                        }
                        Text(
                            "Logging in allows you to mark Pokémon as caught directly inside the app to sync them back to your GoDex checklist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Or, connect with a Public URL (Read-Only):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = goDexUrlInput,
                            onValueChange = { goDexUrlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Public GoDex collection URL") },
                            placeholder = { Text("https://godex.site/public-collection/\u2026") },
                            singleLine = true,
                            enabled = !goDexSyncUiState.isSyncing
                        )
                        ElevatedButton(
                            onClick = { viewModel.connectGoDex(goDexUrlInput) },
                            enabled = goDexUrlInput.isNotBlank() && !goDexSyncUiState.isSyncing
                        ) {
                            Text(if (goDexSyncUiState.isSyncing) "Connecting\u2026" else "Connect")
                        }
                    } else {
                        Text(
                            goDexConfig.collectionTitle.ifBlank { "GoDex Hundo collection" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$neededCount needed \u2022 ${totalCount - neededCount} collected \u2022 $totalCount total",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Last synchronized ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(goDexConfig.lastSuccessfulSyncMillis))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (goDexSyncUiState.pendingCount > 0) {
                            Text(
                                "${goDexSyncUiState.pendingCount} checklist " +
                                    if (goDexSyncUiState.pendingCount == 1) {
                                        "change is pending"
                                    } else {
                                        "changes are pending"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (goDexSyncUiState.lastSuccessfulWriteMillis > 0L) {
                            Text(
                                "Last change sent ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(goDexSyncUiState.lastSuccessfulWriteMillis))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isStale) {
                            Text(
                                "Checklist data is over 48 hours old. The last successful cache is still in use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        SwitchSetting(
                            title = "Only notify for needed GoDex Hundos",
                            subtitle = "Confirmed collected Hundos are suppressed. Unknown forms still notify.",
                            checked = goDexConfig.notificationFilterEnabled,
                            onCheckedChange = viewModel::updateGoDexNotificationFilter
                        )
                        if (goDexConfig.notificationFilterEnabled) {
                            Text(
                                "Your manual Hundo species selection is preserved and resumes when this filter is disabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = viewModel::syncGoDex,
                                enabled = !goDexSyncUiState.isSyncing
                            ) {
                                Text(if (goDexSyncUiState.isSyncing) "Syncing\u2026" else "Sync now")
                            }
                            OutlinedButton(
                                onClick = viewModel::disconnectGoDex,
                                enabled = !goDexSyncUiState.isSyncing
                            ) {
                                Text("Disconnect")
                            }
                        }
                        val context = LocalContext.current
                        if (goDexConfig.hasSession) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (goDexConfig.hasWriteBackUrl) {
                                    val shortUrl = goDexConfig.writeBackUrl.substringAfterLast("/")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Two-way sync enabled for checklist: $shortUrl",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(onClick = {
                                            context.startActivity(
                                                com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context, startAtPicker = true)
                                            )
                                        }) {
                                            Text("Change checklist")
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(onClick = { viewModel.clearGoDexSession() }) {
                                            Text("Sign out")
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Signed in, but no checklist is selected.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ElevatedButton(
                                            onClick = {
                                                context.startActivity(
                                                    com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context, startAtPicker = true)
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Select checklist to sync")
                                        }
                                        TextButton(onClick = { viewModel.clearGoDexSession() }) {
                                            Text("Sign out")
                                        }
                                    }
                                }
                            }
                        } else {
                            val requiresReauthentication =
                                goDexSyncUiState.sessionState == GoDexSessionState.REAUTH_REQUIRED
                            if (requiresReauthentication) {
                                Text(
                                    "Your GoDex session expired. Sign in again to resume " +
                                        "${goDexSyncUiState.pendingCount} pending checklist " +
                                        if (goDexSyncUiState.pendingCount == 1) "change." else "changes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            ElevatedButton(
                                onClick = {
                                    context.startActivity(
                                        com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity.createIntent(context)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (requiresReauthentication) {
                                        "Sign in again to resume sync"
                                    } else {
                                        "Sign in to GoDex for two-way sync"
                                    }
                                )
                            }
                            Text(
                                "Sign in to mark Pokémon as caught directly from alerts, and have changes sync back to your GoDex checklist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showGoDexDebugList = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = goDexEntries.isNotEmpty()
                        ) {
                            Text("View synced Pok\u00E9mon ($totalCount)")
                        }
                    }

                    goDexSyncUiState.errorMessage?.let { error ->
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                }
                
                if (destination == SettingsDestination.NOTIFICATIONS) {
                SettingsSection(title = "Permission status") {
                    PermissionStatusRow(
                        title = "Notifications",
                        granted = systemNotificationsGranted,
                        description = "Required for instant alert notifications",
                        actionLabel = if (systemNotificationsGranted) "Manage" else "Enable",
                        onAction = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    )
                    HorizontalDivider()
                    PermissionStatusRow(
                        title = "Location while using the app",
                        granted = foregroundLocationGranted,
                        description = "Enables distance, nearby sorting, and map tracking",
                        actionLabel = if (foregroundLocationGranted) "Manage" else "Grant",
                        onAction = onManageLocationPermissions
                    )
                    HorizontalDivider()
                    PermissionStatusRow(
                        title = "Background location",
                        granted = backgroundLocationGranted,
                        description = "Keeps location-based features accurate when the app is closed",
                        actionLabel = if (backgroundLocationGranted) "Manage" else "Grant",
                        onAction = onManageLocationPermissions
                    )
                }
                SettingsSection(title = "Notification preferences") {
                    val notificationState = NotificationCategoryState(
                        raidsNotifications, spawnsNotifications, questsNotifications, hundosNotifications,
                        pvpNotifications, nundosNotifications, kecleonNotifications, rocketNotifications
                    )
                    val detectedPreset = NotificationPreset.detect(notificationState)
                    Text("Preset: ${detectedPreset.label}", style = MaterialTheme.typography.titleSmall)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            NotificationPreset.EVERYTHING,
                            NotificationPreset.HIGH_VALUE,
                            NotificationPreset.QUIET_ESSENTIALS
                        ).forEach { preset ->
                            FilterChip(
                                selected = detectedPreset == preset,
                                onClick = { viewModel.applyNotificationPreset(preset) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    Text(
                        "Presets change categories only; species and raid-tier filters are preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    SwitchSetting(
                        title = "Enable Notifications",
                        subtitle = "Receive alerts for new Pokemon nearby",
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
                    )
                    
                    if (notificationsEnabled) {
                        SwitchSetting(
                            title = "Raids",
                            subtitle = "Notifications for raid battles",
                            checked = raidsNotifications,
                            onCheckedChange = { viewModel.updateRaidsNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Spawns",
                            subtitle = "Notifications for wild spawns",
                            checked = spawnsNotifications,
                            onCheckedChange = { viewModel.updateSpawnsNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Quests",
                            subtitle = "Notifications for field research",
                            checked = questsNotifications,
                            onCheckedChange = { viewModel.updateQuestsNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Hundos",
                            subtitle = "Notifications for 100% IV Pok\u00E9mon",
                            checked = hundosNotifications,
                            onCheckedChange = { viewModel.updateHundosNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "PvP",
                            subtitle = "Notifications for PvP ranked Pok\u00E9mon",
                            checked = pvpNotifications,
                            onCheckedChange = { viewModel.updatePvpNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Nundos",
                            subtitle = "Notifications for 0% IV Pok\u00E9mon",
                            checked = nundosNotifications,
                            onCheckedChange = { viewModel.updateNundosNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Kecleon",
                            subtitle = "Notifications for Kecleon sightings",
                            checked = kecleonNotifications,
                            onCheckedChange = { viewModel.updateKecleonNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Rocket",
                            subtitle = "Notifications for Team GO Rocket",
                            checked = rocketNotifications,
                            onCheckedChange = { viewModel.updateRocketNotifications(it) }
                        )
                        AdvancedNotificationFilters(
                            raidsEnabled = raidsNotifications,
                            spawnsEnabled = spawnsNotifications,
                            hundosEnabled = hundosNotifications,
                            pvpEnabled = pvpNotifications,
                            nundosEnabled = nundosNotifications,
                            rocketEnabled = rocketNotifications,
                            excludedRaidTiers = excludedRaidTiers,
                            excludedRocketTypes = excludedRocketTypes,
                            onToggleRaidTier = viewModel::toggleExcludedRaidTier,
                            onToggleRocketType = viewModel::toggleExcludedRocketType
                        )
                        
                        SwitchSetting(
                            title = "Vibration",
                            subtitle = "Vibrate when receiving notifications",
                            checked = notificationVibrate,
                            onCheckedChange = { viewModel.updateNotificationVibrate(it) }
                        )
                        
                        // Silence notifications section
                        SilenceNotificationsCard(
                            silenceUntil = silenceUntil,
                            onSilenceFor = { minutes -> viewModel.silenceNotificationsFor(minutes) },
                            onClearSilence = { viewModel.clearNotificationSilence() }
                        )
                    }
                }
                }
                
                if (destination == SettingsDestination.ABOUT_UPDATES) {
                SettingsSection(title = "App information") {
                    val coroutineScope = rememberCoroutineScope()
                    val updateState by InAppUpdateManager.updateState.collectAsStateWithLifecycle(initialValue = UpdateState.Idle)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version ${com.example.pokemonalertsv2.BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        InAppUpdateManager.checkForUpdates(UpdateCheckSource.MANUAL)
                                    }
                                },
                                enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading
                            ) {
                                if (updateState is UpdateState.Checking) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Checking...")
                                } else {
                                    Text("Check for Updates")
                                }
                            }
                        }
                    }
                }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showGoDexDebugList) {
        val debugEntries = remember(goDexEntries) {
            viewModel.buildGoDexDebugEntries(goDexEntries)
        }
        GoDexDebugListDialog(
            entries = debugEntries,
            onDismiss = { showGoDexDebugList = false }
        )
    }

}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (granted) "On · $description" else "Off · $description",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoDexDebugListDialog(
    entries: List<GoDexDebugEntry>,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedStatusName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedStatus = selectedStatusName?.let { name ->
        GoDexMatchStatus.entries.firstOrNull { it.name == name }
    }
    val filteredEntries = remember(entries, query, selectedStatus) {
        val normalizedQuery = query.trim().lowercase()
        entries.filter { entry ->
            val statusMatches = selectedStatus == null || entry.result.status == selectedStatus
            val queryMatches = normalizedQuery.isEmpty() || listOf(
                entry.displayName,
                entry.entryKey,
                entry.pokedexId.toString(),
                entry.formSlug.orEmpty(),
                entry.gender,
                entry.statusLabel
            ).any { it.lowercase().contains(normalizedQuery) }
            statusMatches && queryMatches
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Synced GoDex Pok\u00E9mon")
                Text(
                    "${filteredEntries.size} of ${entries.size} entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search name, number, form or status") },
                    singleLine = true
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        null to "All",
                        GoDexMatchStatus.NEEDED to "Needed",
                        GoDexMatchStatus.EVOLUTION_NEEDED to "Evolution needed",
                        GoDexMatchStatus.FORM_CHANGE_NEEDED to "Form change needed",
                        GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED to "Evolution + form change",
                        GoDexMatchStatus.COLLECTED to "Collected",
                        GoDexMatchStatus.UNKNOWN to "Unknown"
                    ).forEach { (status, label) ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatusName = status?.name },
                            label = { Text(label) }
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 520.dp)
                ) {
                    items(filteredEntries, key = { it.entryKey }) { entry ->
                        GoDexDebugEntryRow(entry)
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun GoDexDebugEntryRow(entry: GoDexDebugEntry) {
    val statusColor = when (entry.result.status) {
        GoDexMatchStatus.NEEDED -> MaterialTheme.colorScheme.primary
        GoDexMatchStatus.EVOLUTION_NEEDED -> MaterialTheme.colorScheme.tertiary
        GoDexMatchStatus.FORM_CHANGE_NEEDED -> MaterialTheme.colorScheme.tertiary
        GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED -> MaterialTheme.colorScheme.tertiary
        GoDexMatchStatus.COLLECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        GoDexMatchStatus.UNKNOWN -> MaterialTheme.colorScheme.error
        GoDexMatchStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            "#${entry.pokedexId.toString().padStart(4, '0')} ${entry.displayName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        val variant = buildList {
            entry.formSlug?.let { add("form: $it") }
            entry.gender.takeUnless { it == "none" }?.let { add("gender: $it") }
        }.joinToString(" \u2022 ")
        if (variant.isNotEmpty()) {
            Text(
                variant,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            entry.entryKey,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Text(
            entry.statusLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}
@Composable
private fun SettingsOverview(
    themeMode: Int,
    sortPreference: SortPreference,
    selectedArea: String,
    maxDistance: Int,
    notificationsEnabled: Boolean,
    foregroundLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    onDestinationSelected: (SettingsDestination) -> Unit
) {
    val themeLabel = listOf("System", "Light", "Dark").getOrElse(themeMode) { "System" }
    val sortLabel = when (sortPreference) {
        SortPreference.POSTED_TIME -> "Newest"
        SortPreference.TIME_REMAINING -> "Time remaining"
        SortPreference.DISTANCE -> "Distance"
        SortPreference.NAME -> "Name"
    }
    val distanceLabel = if (maxDistance == 0) "Unlimited distance" else "$maxDistance km maximum"
    val notificationSummary = when {
        !notificationsEnabled -> "Off"
        !foregroundLocationGranted -> "On - location access needed"
        !backgroundLocationGranted -> "On - background location off"
        else -> "On - background location granted"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                RoundedCornerShape(28.dp)
            )
    ) {
        SettingsOverviewRow(
            icon = Icons.Default.Settings,
            title = "Appearance & behavior",
            summary = "$themeLabel theme - $sortLabel sort",
            onClick = { onDestinationSelected(SettingsDestination.APPEARANCE_BEHAVIOR) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        SettingsOverviewRow(
            icon = Icons.Default.DateRange,
            title = "Alert filters",
            summary = "$selectedArea - $distanceLabel",
            onClick = { onDestinationSelected(SettingsDestination.ALERT_FILTERS) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        SettingsOverviewRow(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            summary = notificationSummary,
            onClick = { onDestinationSelected(SettingsDestination.NOTIFICATIONS) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        SettingsOverviewRow(
            icon = Icons.Default.Info,
            title = "About & updates",
            summary = "Version ${com.example.pokemonalertsv2.BuildConfig.VERSION_NAME}",
            onClick = { onDestinationSelected(SettingsDestination.ABOUT_UPDATES) }
        )
    }
}

@Composable
private fun SettingsOverviewRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(24.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdvancedNotificationFilters(
    raidsEnabled: Boolean,
    spawnsEnabled: Boolean,
    hundosEnabled: Boolean,
    pvpEnabled: Boolean,
    nundosEnabled: Boolean,
    rocketEnabled: Boolean,
    excludedRaidTiers: Set<String>,
    excludedRocketTypes: Set<String>,
    onToggleRaidTier: (String) -> Unit,
    onToggleRocketType: (String) -> Unit
) {
    val hasAdvancedFilters = raidsEnabled || spawnsEnabled || hundosEnabled ||
        pvpEnabled || nundosEnabled || rocketEnabled
    if (!hasAdvancedFilters) return

    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Advanced exclusions", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Species, raid tier, and Rocket filters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (expanded) {
                    "Collapse advanced exclusions"
                } else {
                    "Expand advanced exclusions"
                }
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (raidsEnabled) {
                    TypeFilterSection(
                        title = "Exclude Raid Tiers",
                        types = listOf("1", "3", "5", "Mega", "Elite", "Primal"),
                        excludedTypes = excludedRaidTiers,
                        onToggleType = onToggleRaidTier
                    )
                }
                if (spawnsEnabled) {
                    SpeciesFilterButton("Filter Spawns by Species") {
                        context.startActivity(SpeciesSelectionActivity.createIntent(context, "spawn"))
                    }
                }
                if (hundosEnabled) {
                    SpeciesFilterButton("Filter Hundos by Species") {
                        context.startActivity(SpeciesSelectionActivity.createIntent(context, "hundo"))
                    }
                }
                if (pvpEnabled) {
                    SpeciesFilterButton("Filter PvP by Species") {
                        context.startActivity(SpeciesSelectionActivity.createIntent(context, "pvp"))
                    }
                }
                if (nundosEnabled) {
                    SpeciesFilterButton("Filter Nundos by Species") {
                        context.startActivity(SpeciesSelectionActivity.createIntent(context, "nundo"))
                    }
                }
                if (rocketEnabled) {
                    TypeFilterSection(
                        title = "Exclude Grunt Types",
                        types = ROCKET_GRUNT_TYPES,
                        excludedTypes = excludedRocketTypes,
                        onToggleType = onToggleRocketType
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SilenceNotificationsCard(
    silenceUntil: Long,
    onSilenceFor: (Int) -> Unit,
    onClearSilence: () -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val isSilenced = silenceUntil > now
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSilenced) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (isSilenced) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Silence Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (isSilenced) {
                val remainingMinutes = ((silenceUntil - now) / 60000).toInt()
                val hours = remainingMinutes / 60
                val minutes = remainingMinutes % 60
                
                val timeText = when {
                    hours > 0 && minutes > 0 -> "$hours hr $minutes min"
                    hours > 0 -> "$hours hr"
                    else -> "$minutes min"
                }
                
                Text(
                    text = "Silenced for $timeText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                
                FilledTonalButton(
                    onClick = onClearSilence,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Clear Silence")
                }
            } else {
                Text(
                    text = "Temporarily silence all notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Quick duration buttons - 2x2 grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSilenceFor(30) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("30 min")
                        }
                        OutlinedButton(
                            onClick = { onSilenceFor(60) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1 hour")
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSilenceFor(120) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("2 hours")
                        }
                        OutlinedButton(
                            onClick = { onSilenceFor(480) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("8 hours")
                        }
                    }
                }
                
                // Custom time section
                var showCustomDialog by remember { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    
                    OutlinedButton(
                        onClick = { showCustomDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Custom Duration or Time")
                    }
                }
                
                if (showCustomDialog) {
                    CustomSilenceDialog(
                        onDismiss = { showCustomDialog = false },
                        onSilenceFor = { minutes ->
                            onSilenceFor(minutes)
                            showCustomDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomSilenceDialog(
    onDismiss: () -> Unit,
    onSilenceFor: (Int) -> Unit
) {
    val context = LocalContext.current
    var showDurationPicker by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    
    if (showDurationPicker) {
        DurationPickerDialog(
            onDismiss = { showDurationPicker = false },
            onConfirm = { totalMinutes ->
                onSilenceFor(totalMinutes)
                showDurationPicker = false
            }
        )
        return
    }
    
    if (showDateTimePicker) {
        DateTimePickerDialog(
            onDismiss = { showDateTimePicker = false },
            onConfirm = { minutes ->
                onSilenceFor(minutes)
                showDateTimePicker = false
            }
        )
        return
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null
            )
        },
        title = {
            Text("Custom Silence Duration")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose how you want to set the silence duration:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Duration option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDurationPicker = true }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Set Duration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose hours and minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Until time option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDateTimePicker = true }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Until Specific Time",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pick a date and time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DurationPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedHours by remember { mutableStateOf(1) }
    var selectedMinutes by remember { mutableStateOf(0) }
    
    val hoursList = remember { (0..24).toList() }
    val minutesList = remember { (0..59).toList() }
    val hoursListState = rememberLazyListState(initialFirstVisibleItemIndex = 1)
    val minutesListState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null
            )
        },
        title = {
            Text("Select Duration")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hour and Minute selectors side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    // Hour selector - scrollable list
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selectedHours == 1) "hour" else "hours",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = hoursListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(hoursList) { hour ->
                                    val isSelected = hour == selectedHours
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d", hour),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedHours = hour }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Minute selector - scrollable list
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "minutes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = minutesListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(minutesList) { minute ->
                                    val isSelected = minute == selectedMinutes
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d", minute),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedMinutes = minute }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Quick presets
                Text(
                    text = "Quick select:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1 to 0, 2 to 0, 4 to 0).forEach { (hours, minutes) ->
                            FilledTonalButton(
                                onClick = { 
                                    selectedHours = hours
                                    selectedMinutes = minutes
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selectedHours == hours && selectedMinutes == minutes)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("${hours}h")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(8 to 0, 12 to 0, 24 to 0).forEach { (hours, minutes) ->
                            FilledTonalButton(
                                onClick = { 
                                    selectedHours = hours
                                    selectedMinutes = minutes
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selectedHours == hours && selectedMinutes == minutes)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("${hours}h")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { 
                    val totalMinutes = selectedHours * 60 + selectedMinutes
                    if (totalMinutes > 0) {
                        onConfirm(totalMinutes)
                    }
                },
                enabled = selectedHours > 0 || selectedMinutes > 0
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DateTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(0) }
    var showingDatePicker by remember { mutableStateOf(true) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    
    if (showCustomDatePicker) {
        CustomDatePickerDialog(
            onDismiss = { showCustomDatePicker = false },
            onDateSelected = { year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                showCustomDatePicker = false
            }
        )
    }
    
    if (showingDatePicker) {
        // Date Picker Dialog
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null
                )
            },
            title = {
                Text("Select Date")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val today = Calendar.getInstance()
                    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val dayAfter = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
                    
                    // Quick select cards
                    listOf(
                        "Today" to today,
                        "Tomorrow" to tomorrow,
                        "Day After Tomorrow" to dayAfter
                    ).forEach { (label, date) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSameDay(selectedDate, date))
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDate = date.clone() as Calendar }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = String.format(
                                            "%02d/%02d/%04d",
                                            date.get(Calendar.DAY_OF_MONTH),
                                            date.get(Calendar.MONTH) + 1,
                                            date.get(Calendar.YEAR)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSameDay(selectedDate, date)) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Custom date button
                    OutlinedButton(
                        onClick = { showCustomDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Custom Date")
                    }
                    
                    // Show currently selected date
                    Text(
                        text = "Selected: ${String.format(
                            "%02d/%02d/%04d",
                            selectedDate.get(Calendar.DAY_OF_MONTH),
                            selectedDate.get(Calendar.MONTH) + 1,
                            selectedDate.get(Calendar.YEAR)
                        )}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = { showingDatePicker = false }) {
                    Text("Next")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else {
        // Time Picker Dialog
        val hoursList = remember { (0..23).toList() }
        val minutesList = remember { (0..59).toList() }
        val hoursListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedHour)
        val minutesListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedMinute)
        
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null
                )
            },
            title = {
                Text("Select Time")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Hour picker - scrollable list
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(100.dp)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "hours",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                LazyColumn(
                                    modifier = Modifier
                                        .height(180.dp)
                                        .fillMaxWidth(),
                                    state = hoursListState,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(hoursList) { hour ->
                                        val isSelected = hour == selectedHour
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else 
                                                Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = String.format("%02d", hour),
                                                style = if (isSelected) 
                                                    MaterialTheme.typography.titleLarge 
                                                else 
                                                    MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) 
                                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedHour = hour }
                                                    .padding(vertical = 8.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Minute picker - scrollable list
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(100.dp)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "minutes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                LazyColumn(
                                    modifier = Modifier
                                        .height(180.dp)
                                        .fillMaxWidth(),
                                    state = minutesListState,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(minutesList) { minute ->
                                        val isSelected = minute == selectedMinute
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else 
                                                Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = String.format("%02d", minute),
                                                style = if (isSelected) 
                                                    MaterialTheme.typography.titleLarge 
                                                else 
                                                    MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) 
                                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedMinute = minute }
                                                    .padding(vertical = 8.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        val targetCalendar = selectedDate.clone() as Calendar
                        targetCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                        targetCalendar.set(Calendar.MINUTE, selectedMinute)
                        targetCalendar.set(Calendar.SECOND, 0)
                        targetCalendar.set(Calendar.MILLISECOND, 0)
                        
                        val durationMinutes = ((targetCalendar.timeInMillis - System.currentTimeMillis()) / 60000).toInt()
                        if (durationMinutes > 0) {
                            onConfirm(durationMinutes)
                        }
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showingDatePicker = true }) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
private fun CustomDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int, Int) -> Unit
) {
    val today = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableStateOf(today.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(today.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    
    val years = remember { (today.get(Calendar.YEAR)..today.get(Calendar.YEAR) + 1).toList() }
    val months = remember { (0..11).toList() }
    
    val daysInMonth = remember(selectedYear, selectedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val days = remember(daysInMonth) { (1..daysInMonth).toList() }
    
    val yearListState = rememberLazyListState(initialFirstVisibleItemIndex = years.indexOf(selectedYear))
    val monthListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedMonth)
    val dayListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedDay - 1)
    
    val monthNames = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null
            )
        },
        title = {
            Text("Select Date")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Scrollable pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    // Day picker
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Day",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = dayListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(days) { day ->
                                    val isSelected = day == selectedDay
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d", day),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedDay = day }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Month picker
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Month",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = monthListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(months) { month ->
                                    val isSelected = month == selectedMonth
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = monthNames[month],
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { 
                                                    selectedMonth = month
                                                    // Adjust day if it exceeds new month's days
                                                    val newDaysInMonth = Calendar.getInstance().apply {
                                                        set(Calendar.YEAR, selectedYear)
                                                        set(Calendar.MONTH, month)
                                                    }.getActualMaximum(Calendar.DAY_OF_MONTH)
                                                    if (selectedDay > newDaysInMonth) {
                                                        selectedDay = newDaysInMonth
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Year picker
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Year",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = yearListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(years) { year ->
                                    val isSelected = year == selectedYear
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = year.toString(),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedYear = year }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Selected date display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "${selectedDay} ${monthNames[selectedMonth]} ${selectedYear}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onDateSelected(selectedYear, selectedMonth, selectedDay) }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

// Pokemon type constants for filtering
private val POKEMON_TYPES = listOf(
    "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
    "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
    "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
)

// Rocket grunt type constants
private val ROCKET_GRUNT_TYPES = listOf(
    "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
    "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
    "Rock", "Ghost", "Dragon", "Dark", "Fairy", "Mixed"
)

/**
 * Expandable filter section showing type chips that can be toggled to exclude
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterSection(
    title: String,
    types: List<String>,
    excludedTypes: Set<String>,
    onToggleType: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (excludedTypes.isNotEmpty()) {
                    Text(
                        text = "${excludedTypes.size} excluded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        AnimatedVisibility(visible = expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { type ->
                    val isExcluded = excludedTypes.any { it.equals(type, ignoreCase = true) }
                    FilterChip(
                        selected = isExcluded,
                        onClick = { onToggleType(type) },
                        label = { Text(type) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }
        }
    }
}
@Composable
private fun SpeciesFilterButton(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
