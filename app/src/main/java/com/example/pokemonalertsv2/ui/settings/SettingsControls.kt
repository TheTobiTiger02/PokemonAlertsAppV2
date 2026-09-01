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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.example.pokemonalertsv2.notifications.QuietHours
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.data.godex.GoDexSyncUiState
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import androidx.compose.animation.AnimatedContent
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
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.data.backup.SettingsBackupRepository
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

@Composable
internal fun GoDexSettingsProgressCard(
    neededCount: Int,
    totalCount: Int,
    onOpenCollection: () -> Unit
) {
    val caughtCount = totalCount - neededCount
    val progress = if (totalCount == 0) 0f else caughtCount.toFloat() / totalCount
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCollection),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (neededCount == 0) "Checklist complete" else "$neededCount still needed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$caughtCount caught \u2022 $totalCount total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
internal fun GoDexSyncStatusCard(
    config: GoDexConfig,
    syncState: GoDexSyncUiState,
    isStale: Boolean
) {
    val lastSyncText = if (config.lastSuccessfulSyncMillis > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(config.lastSuccessfulSyncMillis))
    } else {
        "Never"
    }
    val lastWriteText = if (syncState.lastSuccessfulWriteMillis > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(syncState.lastSuccessfulWriteMillis))
    } else {
        null
    }
    data class Presentation(
        val title: String,
        val detail: String,
        val containerColor: Color,
        val contentColor: Color,
        val showProgress: Boolean = false
    )
    val presentation = when {
        syncState.isSyncing -> Presentation(
            title = "Syncing latest checklist",
            detail = "Downloading the newest caught and needed state from GoDex.",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            showProgress = true
        )
        syncState.sessionState == GoDexSessionState.REAUTH_REQUIRED -> Presentation(
            title = "Sign-in needed",
            detail = if (syncState.pendingCount > 0) {
                "${syncState.pendingCount} ${if (syncState.pendingCount == 1) "change is" else "changes are"} safe on this device and will resume after sign-in."
            } else {
                "Your cached checklist is still available. Sign in again to resume two-way sync."
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        syncState.errorMessage != null -> Presentation(
            title = "Sync needs attention",
            detail = syncState.errorMessage,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        syncState.pendingCount > 0 -> Presentation(
            title = "Sending ${syncState.pendingCount} ${if (syncState.pendingCount == 1) "change" else "changes"}",
            detail = "Already saved on this device. GoDex will confirm each change in the background.",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            showProgress = true
        )
        isStale -> Presentation(
            title = "Update recommended",
            detail = "Last updated $lastSyncText. The last successful checklist remains in use.",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Presentation(
            title = "Up to date",
            detail = buildString {
                append("Last checklist sync: $lastSyncText")
                lastWriteText?.let { append(" \u2022 Last change sent: $it") }
                append(". Auto-sync runs in the background.")
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = presentation.containerColor,
        contentColor = presentation.contentColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (presentation.showProgress) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = presentation.contentColor
                )
            } else {
                Icon(
                    imageVector = if (
                        syncState.sessionState == GoDexSessionState.REAUTH_REQUIRED ||
                        syncState.errorMessage != null
                    ) {
                        Icons.Filled.Info
                    } else {
                        Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    tint = presentation.contentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = presentation.detail,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun PermissionStatusRow(
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

@Composable
internal fun SettingsOverview(
    themeMode: Int,
    sortPreference: SortPreference,
    selectedArea: String,
    maxDistance: Int,
    notificationsEnabled: Boolean,
    foregroundLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    goDexSummary: String,
    goDexBadge: String?,
    raidCountersSummary: String,
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
                MaterialTheme.colorScheme.surfaceContainer,
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
            title = "Filters",
            summary = "Feed, map, widgets and notifications",
            onClick = { onDestinationSelected(SettingsDestination.ALERT_FILTERS) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        SettingsOverviewRow(
            icon = Icons.Default.AccountCircle,
            title = "GoDex checklist",
            summary = goDexSummary,
            statusBadge = goDexBadge,
            onClick = { onDestinationSelected(SettingsDestination.GODEX) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        SettingsOverviewRow(
            icon = Icons.Default.Star,
            title = "Raid counters",
            summary = raidCountersSummary,
            onClick = { onDestinationSelected(SettingsDestination.RAID_COUNTERS) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        SettingsOverviewRow(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            summary = notificationSummary,
            statusBadge = when {
                notificationsEnabled && !foregroundLocationGranted -> "Location needed"
                notificationsEnabled && !backgroundLocationGranted -> "Background off"
                else -> null
            },
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
internal fun SettingsOverviewRow(
    icon: ImageVector,
    title: String,
    summary: String,
    statusBadge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
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
            statusBadge?.let { badge ->
                val problemBadge = badge.equals("Sync issue", ignoreCase = true) ||
                    badge.contains("needed", ignoreCase = true) ||
                    badge.contains("off", ignoreCase = true)
                Surface(
                    color = if (problemBadge) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (problemBadge) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun AdvancedNotificationFilters(
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
        AnimatedVisibility(
            visible = expanded,
            enter = appExpandIn(),
            exit = appCollapseOut()
        ) {
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
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
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
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
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
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * The standing nightly quiet window.
 *
 * Uses the platform time picker rather than the bespoke wheel next door: that one picks a
 * *duration* for the one-off silence, and a wall-clock time is a different question that a
 * standard picker already answers well.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuietHoursCard(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onWindowChange: (Int, Int) -> Unit
) {
    var editing by remember { mutableStateOf<QuietHoursEdge?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quiet hours",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Hold notifications every night between these times",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            AnimatedVisibility(visible = enabled, enter = appExpandIn(), exit = appCollapseOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { editing = QuietHoursEdge.START },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("From ${QuietHours.format(startMinute)}")
                    }
                    OutlinedButton(
                        onClick = { editing = QuietHoursEdge.END },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("To ${QuietHours.format(endMinute)}")
                    }
                }
            }
        }
    }

    editing?.let { edge ->
        val current = if (edge == QuietHoursEdge.START) startMinute else endMinute
        val state = rememberTimePickerState(
            initialHour = current / 60,
            initialMinute = current % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(if (edge == QuietHoursEdge.START) "Quiet hours start" else "Quiet hours end")
            },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = state.hour * 60 + state.minute
                        if (edge == QuietHoursEdge.START) {
                            onWindowChange(picked, endMinute)
                        } else {
                            onWindowChange(startMinute, picked)
                        }
                        editing = null
                    }
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Cancel") }
            }
        )
    }
}

internal enum class QuietHoursEdge { START, END }

/**
 * Export and restore of every preference in the app.
 *
 * Import follows the prepare/commit shape the Poke Genie import already uses -- read and
 * validate, show what will change, then write -- so a truncated or hand-edited file cannot
 * half-apply and leave settings in a state the user did not choose.
 */
@Composable
internal fun SettingsBackupCard(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { SettingsBackupRepository(context) }
    var pending by remember { mutableStateOf<SettingsBackup.Backup?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SettingsBackup.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            repository.exportTo(uri)
                .onSuccess { snackbarHostState.showSnackbar("Exported $it settings") }
                .onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "Could not write the backup")
                }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            repository.prepareImport(uri)
                .onSuccess { pending = it }
                .onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "That file is not a settings backup")
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Save your filters, species lists, exclusions, quiet hours and counter " +
                "setup to a file, and restore them after a reinstall.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Sign-ins are not included — you will need to sign in to GoDex and " +
                "Pokébattler again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    exportLauncher.launch(SettingsBackupRepository.suggestedFileName())
                },
                modifier = Modifier.weight(1f)
            ) { Text("Export") }
            OutlinedButton(
                onClick = {
                    // Some file pickers hand back a generic MIME type for .json, so accept
                    // anything and let the parser be the judge.
                    importLauncher.launch(arrayOf(SettingsBackup.MIME_TYPE, "text/plain", "*/*"))
                },
                modifier = Modifier.weight(1f)
            ) { Text("Restore") }
        }
    }

    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore settings?") },
            text = {
                Text(
                    "This backup holds ${backup.entries.size} settings" +
                        (backup.appVersion?.let { " from version $it" } ?: "") +
                        ". Restoring replaces the matching settings on this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        scope.launch {
                            repository.commitImport(backup)
                                .onSuccess {
                                    snackbarHostState.showSnackbar("Restored $it settings")
                                }
                                .onFailure {
                                    snackbarHostState.showSnackbar(
                                        it.message ?: "Could not restore the backup"
                                    )
                                }
                        }
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun SilenceNotificationsCard(
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


// Pokemon type constants for filtering
internal val POKEMON_TYPES = listOf(
    "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
    "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
    "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
)

// Rocket grunt type constants
internal val ROCKET_GRUNT_TYPES = listOf(
    "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
    "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
    "Rock", "Ghost", "Dragon", "Dark", "Fairy", "Mixed"
)

/**
 * Expandable filter section showing type chips that can be toggled to exclude
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TypeFilterSection(
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

        AnimatedVisibility(
            visible = expanded,
            enter = appExpandIn(),
            exit = appCollapseOut()
        ) {
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
internal fun SpeciesFilterButton(
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
