package com.example.pokemonalertsv2.ui.settings

import com.example.pokemonalertsv2.ui.alerts.shortLabel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.SortPreference
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.data.backup.SettingsBackupRepository
import androidx.compose.material3.SnackbarHostState

/**
 * The floating journey pill toggle, with the overlay grant folded in.
 *
 * Two states that look the same to the user but are not: switched off, and
 * switched on without the "Display over other apps" permission. The row says
 * which, because a pill that silently never appears reads as a broken feature.
 */
@Composable
internal fun JourneyOverlayRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var permissionPromptOpen by rememberSaveable { mutableStateOf(false) }
    // Re-read on every recomposition rather than caching: the grant is handed out
    // in Settings, so the app comes back to this screen with it already changed.
    val granted = Settings.canDrawOverlays(context)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchSetting(
            title = stringResource(R.string.journey_overlay_title),
            subtitle = stringResource(R.string.journey_overlay_summary),
            checked = enabled,
            onCheckedChange = { wanted ->
                onEnabledChange(wanted)
                if (wanted && !granted) permissionPromptOpen = true
            }
        )
        if (enabled && !granted) {
            TextButton(onClick = { permissionPromptOpen = true }) {
                Text(
                    stringResource(R.string.journey_overlay_permission_needed),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (permissionPromptOpen) {
        JourneyOverlayPermissionDialog(onDismiss = { permissionPromptOpen = false })
    }
}

/** Explains the "Display over other apps" grant and opens Android's page for it. */
@Composable
internal fun JourneyOverlayPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.journey_overlay_permission_title)) },
            text = { Text(stringResource(R.string.journey_overlay_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + context.packageName)
                                )
                            )
                        }.onFailure {
                            // Some builds refuse the package-scoped form; the list
                            // screen still gets the trainer to the same switch.
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                )
                            }
                        }
                    }
                ) { Text(stringResource(R.string.journey_overlay_permission_positive)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.journey_overlay_permission_negative))
                }
            }
        )
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
    onDestinationSelected: (SettingsDestination) -> Unit,
    systemNotificationsGranted: Boolean = true,
    cardStyle: com.example.pokemonalertsv2.data.AlertCardStyle = com.example.pokemonalertsv2.data.AlertCardStyle.COMPACT
) {
    val themeLabel = listOf("System", "Light", "Dark").getOrElse(themeMode) { "System" }
    val notificationSummary = if (notificationsEnabled) "On" else "Off"
    val missingPermissions = buildList {
        if (!systemNotificationsGranted) add("notifications")
        if (!foregroundLocationGranted) add("location")
        else if (!backgroundLocationGranted) add("background location")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // One place that says what is missing, instead of a dialog on every launch.
        if (missingPermissions.isNotEmpty()) {
            Surface(
                onClick = { onDestinationSelected(SettingsDestination.NOTIFICATIONS) },
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("Permissions needed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allow ${missingPermissions.joinToString(" and ")} for alerts and distances to work.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }

        SettingsOverviewGroup("Alerts") {
            SettingsOverviewRow(
                icon = rememberVectorPainter(Icons.Default.Notifications),
                title = "Notifications & permissions",
                summary = notificationSummary,
                onClick = { onDestinationSelected(SettingsDestination.NOTIFICATIONS) }
            )
            OverviewDivider()
            SettingsOverviewRow(
                icon = painterResource(R.drawable.ic_filter),
                title = "Filters",
                summary = "What the feed, map, widgets and notifications show",
                onClick = { onDestinationSelected(SettingsDestination.ALERT_FILTERS) }
            )
        }
        SettingsOverviewGroup("Display") {
            SettingsOverviewRow(
                icon = painterResource(R.drawable.ic_theme),
                title = "Appearance",
                summary = "$themeLabel theme · ${cardStyle.label} rows · ${sortPreference.shortLabel} first",
                onClick = { onDestinationSelected(SettingsDestination.APPEARANCE_BEHAVIOR) }
            )
            OverviewDivider()
            SettingsOverviewRow(
                icon = painterResource(R.drawable.ic_map),
                title = "Map & tracking",
                summary = "Marker grouping and arrival radius",
                onClick = { onDestinationSelected(SettingsDestination.MAP_TRACKING) }
            )
        }
        SettingsOverviewGroup("Accounts") {
            SettingsOverviewRow(
                icon = rememberVectorPainter(Icons.Default.AccountCircle),
                title = "GoDex checklist",
                summary = goDexSummary,
                statusBadge = goDexBadge,
                onClick = { onDestinationSelected(SettingsDestination.GODEX) }
            )
            OverviewDivider()
            SettingsOverviewRow(
                icon = painterResource(R.drawable.ic_insights),
                title = "Raid counters",
                summary = raidCountersSummary,
                onClick = { onDestinationSelected(SettingsDestination.RAID_COUNTERS) }
            )
        }
        SettingsOverviewGroup("App") {
            SettingsOverviewRow(
                icon = rememberVectorPainter(Icons.Default.Info),
                title = "Backup & about",
                summary = "Version ${com.example.pokemonalertsv2.BuildConfig.VERSION_NAME} · export or restore settings",
                onClick = { onDestinationSelected(SettingsDestination.ABOUT_UPDATES) }
            )
        }
    }
}

@Composable
internal fun SettingsOverviewGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
        ) { content() }
    }
}

@Composable
private fun OverviewDivider() = HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

/** A setting with a handful of options, as one segmented control instead of a radio list. */
@Composable
internal fun SegmentedSetting(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    com.example.pokemonalertsv2.ui.components.SpringSegmentedRow(
        selectedIndex = selectedIndex,
        segmentCount = options.size
    ) {
        options.forEachIndexed { index, label ->
            com.example.pokemonalertsv2.ui.components.SegmentedChoice(
                label = label,
                selected = index == selectedIndex,
                transparent = true,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(index) }
            )
        }
    }
}

@Composable
internal fun SettingsOverviewRow(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    summary: String,
    statusBadge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
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
                painter = icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp)
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
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

