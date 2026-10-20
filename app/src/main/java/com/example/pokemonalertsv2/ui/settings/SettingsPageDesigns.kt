package com.example.pokemonalertsv2.ui.settings

import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.data.godex.GoDexSyncUiState
import com.example.pokemonalertsv2.notifications.QuietHours
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelPages
import com.example.pokemonalertsv2.ui.components.PanelRowIcon
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity
import com.example.pokemonalertsv2.ui.theme.Spacing
import kotlinx.coroutines.delay

// The Notifications and GoDex pages, laid out flat like the map's sheets: blue section labels,
// rows with a round icon badge, and pages inside the page instead of dialogs.

/** The grants the Notifications page reports; SettingsScreen re-reads them on every resume. */
internal data class PermissionGrants(
    val notifications: Boolean,
    val foregroundLocation: Boolean,
    val backgroundLocation: Boolean
) {
    val missingCount: Int
        get() = listOf(notifications, foregroundLocation, backgroundLocation).count { !it }
}

private enum class NotificationsPage { ROOT, QUIET_HOURS, SILENCE }

@Composable
internal fun NotificationsPageDesign(
    viewModel: SettingsViewModel,
    grants: PermissionGrants,
    onManageNotifications: () -> Unit,
    onManageLocation: () -> Unit,
    onOpenFilterStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val vibrate by viewModel.notificationVibrate.collectAsStateWithLifecycle()
    val overlay by viewModel.journeyOverlayEnabled.collectAsStateWithLifecycle()
    val lastPush by viewModel.lastPushReceived.collectAsStateWithLifecycle()
    val silenceUntil by viewModel.silenceUntil.collectAsStateWithLifecycle()
    val quietEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val quietStart by viewModel.quietHoursStartMinute.collectAsStateWithLifecycle()
    val quietEnd by viewModel.quietHoursEndMinute.collectAsStateWithLifecycle()
    val definition by viewModel.notificationFilterDefinition.collectAsStateWithLifecycle()
    val now = rememberMinuteClock()

    var pageName by rememberSaveable { mutableStateOf(NotificationsPage.ROOT.name) }
    val page = NotificationsPage.valueOf(pageName)
    val open: (NotificationsPage) -> Unit = { pageName = it.name }
    val back = { open(NotificationsPage.ROOT) }

    val silenced = silenceUntil > now
    val quietSummary = "${QuietHours.format(quietStart)}–${QuietHours.format(quietEnd)}"

    val permissionRows: @Composable () -> Unit = {
        PermissionRows(grants, lastPush, now, onManageNotifications, onManageLocation)
    }
    val deliveryRows: @Composable () -> Unit = {
        DeliveryRows(
            enabled = enabled,
            vibrate = vibrate,
            overlay = overlay,
            onEnabledChange = viewModel::updateNotificationsEnabled,
            onVibrateChange = viewModel::updateNotificationVibrate,
            onOverlayChange = viewModel::updateJourneyOverlayEnabled
        )
    }
    val quietHoursRows: @Composable () -> Unit = {
        QuietHoursRows(
            enabled = quietEnabled,
            startMinute = quietStart,
            endMinute = quietEnd,
            onEnabledChange = viewModel::updateQuietHoursEnabled,
            onWindowChange = viewModel::updateQuietHours
        )
    }
    val silenceRows: @Composable (afterPick: () -> Unit) -> Unit = { afterPick ->
        SilenceRows(
            silenceUntil = silenceUntil,
            now = now,
            onSilenceFor = {
                viewModel.silenceNotificationsFor(it)
                afterPick()
            },
            onClear = viewModel::clearNotificationSilence
        )
    }

    PanelPages(page = page, root = NotificationsPage.ROOT, onBack = back) { shown ->
        PageColumn(modifier) {
            when (shown) {
                NotificationsPage.ROOT -> {
                    permissionRows()
                    deliveryRows()
                    AnimatedVisibility(visible = enabled) {
                        Column {
                            PanelSectionLabel("Quiet time")
                            PanelNavRow(
                                title = "Quiet hours",
                                summary = if (quietEnabled) "Every day $quietSummary" else "Off",
                                active = quietEnabled,
                                onClick = { open(NotificationsPage.QUIET_HOURS) },
                                icon = { Icon(Icons.Filled.DateRange, null, Modifier.size(20.dp)) }
                            )
                            PanelNavRow(
                                title = "Silence now",
                                summary = if (silenced) "Silenced until ${clockTime(silenceUntil, now)}" else "Pause every alert for a while",
                                active = silenced,
                                onClick = { open(NotificationsPage.SILENCE) },
                                modifier = Modifier.testTag("settings_silence_now"),
                                icon = { PanelIcon(R.drawable.ic_timer) }
                            )
                        }
                    }
                    PanelSectionLabel("Alerts")
                    PanelNavRow(
                        title = "What notifies",
                        summary = definition.notifiedTypeSummary(),
                        onClick = onOpenFilterStudio,
                        icon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp)) }
                    )
                }

                NotificationsPage.QUIET_HOURS -> {
                    PanelHeader("Quiet hours", onBack = back)
                    quietHoursRows()
                }

                NotificationsPage.SILENCE -> {
                    PanelHeader("Silence now", subtitle = "Every alert waits until it ends", onBack = back)
                    silenceRows(back)
                }
            }
        }
    }
}

@Composable
private fun PermissionRows(
    grants: PermissionGrants,
    lastPush: Long,
    now: Long,
    onManageNotifications: () -> Unit,
    onManageLocation: () -> Unit
) {
    PanelSectionLabel("Permissions")
    PermissionRow(
        title = "Notifications",
        granted = grants.notifications,
        summary = if (grants.notifications) "Alerts arrive" else "Alerts can not arrive",
        icon = Icons.Filled.Notifications,
        onClick = onManageNotifications
    )
    PermissionRow(
        title = "Location in use",
        granted = grants.foregroundLocation,
        summary = if (grants.foregroundLocation) "Distances, map" else "No distances",
        icon = Icons.Filled.LocationOn,
        onClick = onManageLocation
    )
    PermissionRow(
        title = "Background location",
        granted = grants.backgroundLocation,
        summary = if (grants.backgroundLocation) "Fresh while closed" else "Stale while closed",
        icon = Icons.Filled.LocationOn,
        onClick = onManageLocation
    )
    // Permissions can all read allowed while push is dead; this row shows delivery working.
    val stale = lastPush <= 0L || now - lastPush >= PUSH_STALE_MILLIS
    InfoRow(
        title = "Last push",
        summary = if (stale) "${pushAge(lastPush, now)} · pull to refresh to catch up" else pushAge(lastPush, now),
        problem = stale,
        icon = { PanelIcon(R.drawable.ic_cloud) }
    )
}

@Composable
private fun DeliveryRows(
    enabled: Boolean,
    vibrate: Boolean,
    overlay: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onOverlayChange: (Boolean) -> Unit
) {
    PanelSectionLabel("Delivery")
    PanelSwitchRow(
        title = "Notifications",
        subtitle = "Alerts for new Pokémon nearby",
        checked = enabled,
        onCheckedChange = onEnabledChange,
        icon = { Icon(Icons.Filled.Notifications, null, Modifier.size(20.dp)) }
    )
    AnimatedVisibility(visible = enabled) {
        Column {
            PanelSwitchRow(
                title = "Vibration",
                subtitle = "Vibrate with each alert",
                checked = vibrate,
                onCheckedChange = onVibrateChange,
                modifier = Modifier.testTag("settings_vibration"),
                icon = { Icon(Icons.Filled.Phone, null, Modifier.size(20.dp)) }
            )
            JourneyOverlaySwitchRow(enabled = overlay, onEnabledChange = onOverlayChange)
        }
    }
}

/** [JourneyOverlayRow] as a panel row: switched on without the overlay grant, it says so. */
@Composable
private fun JourneyOverlaySwitchRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var prompt by rememberSaveable { mutableStateOf(false) }
    val granted = Settings.canDrawOverlays(context)
    PanelSwitchRow(
        title = stringResource(R.string.journey_overlay_title),
        subtitle = stringResource(R.string.journey_overlay_summary),
        checked = enabled,
        onCheckedChange = { wanted ->
            onEnabledChange(wanted)
            if (wanted && !granted) prompt = true
        },
        icon = { PanelIcon(R.drawable.ic_route) }
    )
    if (enabled && !granted) {
        ActionRow(
            title = stringResource(R.string.journey_overlay_permission_needed),
            summary = "Allow display over other apps",
            problem = true,
            onClick = { prompt = true },
            icon = { Icon(Icons.Filled.Warning, null, Modifier.size(20.dp)) }
        )
    }
    if (prompt) JourneyOverlayPermissionDialog(onDismiss = { prompt = false })
}

@Composable
private fun QuietHoursRows(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onWindowChange: (Int, Int) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf<QuietHoursEdge?>(null) }
    PanelSwitchRow(
        title = "Quiet hours",
        subtitle = "Mute alerts at the same time every day",
        checked = enabled,
        onCheckedChange = onEnabledChange,
        icon = { Icon(Icons.Filled.DateRange, null, Modifier.size(20.dp)) }
    )
    AnimatedVisibility(visible = enabled) {
        // The two buttons the quiet hours card always had: each says its time and opens the clock.
        Row(
            modifier = Modifier.fillMaxWidth().panelGutter().padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            OutlinedButton(
                onClick = { editing = QuietHoursEdge.START },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("quiet_from")
            ) { Text("From ${QuietHours.format(startMinute)}", maxLines = 1) }
            OutlinedButton(
                onClick = { editing = QuietHoursEdge.END },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("quiet_to")
            ) { Text("To ${QuietHours.format(endMinute)}", maxLines = 1) }
        }
    }
    editing?.let { edge ->
        ClockTimeDialog(
            title = if (edge == QuietHoursEdge.START) "Quiet hours start" else "Quiet hours end",
            initialMinute = if (edge == QuietHoursEdge.START) startMinute else endMinute,
            onDismiss = { editing = null },
            onPick = { picked ->
                if (edge == QuietHoursEdge.START) onWindowChange(picked, endMinute) else onWindowChange(startMinute, picked)
                editing = null
            }
        )
    }
}

private val SILENCE_DURATIONS = listOf(30 to "30 min", 60 to "1 hour", 120 to "2 hours", 480 to "8 hours")

@Composable
private fun SilenceRows(
    silenceUntil: Long,
    now: Long,
    onSilenceFor: (Int) -> Unit,
    onClear: () -> Unit
) {
    var pickingLength by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    if (silenceUntil > now) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .panelGutter()
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Silenced until ${clockTime(silenceUntil, now)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onClear) { Text("End now", maxLines = 1) }
        }
    }
    // One tile per common length, then any length ("For…", such as 40 minutes) or any date and
    // time ("Until…").
    val tiles: List<Pair<String, () -> Unit>> =
        SILENCE_DURATIONS.map { (minutes, label) -> label to { onSilenceFor(minutes) } } +
            ("For…" to { pickingLength = true }) +
            ("Until…" to { pickingDate = true })
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .panelGutter()
            .padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { (label, onClick) ->
                    Surface(
                        onClick = onClick,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .testTag("silence_tile_$label")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(Spacing.sm)) {
                            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
    if (pickingLength) {
        DurationPickerDialog(
            onDismiss = { pickingLength = false },
            onConfirm = { minutes ->
                pickingLength = false
                if (minutes > 0) onSilenceFor(minutes)
            }
        )
    }
    if (pickingDate) {
        DateTimePickerDialog(
            onDismiss = { pickingDate = false },
            onConfirm = { minutes ->
                pickingDate = false
                if (minutes > 0) onSilenceFor(minutes)
            }
        )
    }
}

/** The one wall-clock picker both quiet hours and "silence until" use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockTimeDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---- GoDex ----

private enum class GoDexPage { ROOT, CHECKLIST, ACCOUNT, PUBLIC_LINK }

/** What the GoDex page's rows do; [GoDexPageDesign] wires them to the view model. */
internal class GoDexPageActions(
    val onConnect: (String) -> Unit,
    val onSync: () -> Unit,
    val onNotifyNeededChange: (Boolean) -> Unit,
    val onSignOut: () -> Unit,
    val onOpenCollection: () -> Unit,
    val onDisconnect: () -> Unit
)

@Composable
internal fun GoDexPageDesign(
    viewModel: SettingsViewModel,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onOpenCollection: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by viewModel.goDexConfig.collectAsStateWithLifecycle()
    val entries by viewModel.goDexEntries.collectAsStateWithLifecycle()
    val sync by viewModel.goDexSyncUiState.collectAsStateWithLifecycle()
    GoDexPageContent(
        config = config,
        needed = entries.count { it.needed },
        total = entries.size,
        sync = sync,
        urlInput = urlInput,
        onUrlInputChange = onUrlInputChange,
        actions = GoDexPageActions(
            onConnect = viewModel::connectGoDex,
            onSync = viewModel::syncGoDex,
            onNotifyNeededChange = viewModel::updateGoDexNotificationFilter,
            onSignOut = viewModel::clearGoDexSession,
            onOpenCollection = onOpenCollection,
            onDisconnect = onDisconnect
        ),
        modifier = modifier
    )
}

@Composable
internal fun GoDexPageContent(
    config: GoDexConfig,
    needed: Int,
    total: Int,
    sync: GoDexSyncUiState,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    actions: GoDexPageActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val now = rememberMinuteClock()

    var pageName by rememberSaveable { mutableStateOf(GoDexPage.ROOT.name) }
    val page = GoDexPage.valueOf(pageName)
    val open: (GoDexPage) -> Unit = { pageName = it.name }
    val back = { open(GoDexPage.ROOT) }
    // Connecting by link or disconnecting changes which root applies; drop a page that no
    // longer belongs to it.
    LaunchedEffect(config.isConnected) { if (page != GoDexPage.ROOT) back() }

    val signIn = { context.startActivity(GoDexLoginActivity.createIntent(context)) }
    val pickChecklist = { context.startActivity(GoDexLoginActivity.createIntent(context, startAtPicker = true)) }
    val syncLine = goDexSyncLine(config, sync, now)
    val account = goDexAccountLine(config, sync)

    val checklistRows: @Composable () -> Unit = {
        PanelNavRow(
            title = "Collection",
            summary = if (needed == 0) "Complete · $total caught" else "$needed needed of $total",
            active = needed > 0,
            onClick = actions.onOpenCollection,
            icon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp)) }
        )
        PanelSwitchRow(
            title = "Only notify needed",
            subtitle = "Hundos you have stay quiet. Unknown forms still notify.",
            checked = config.notificationFilterEnabled,
            onCheckedChange = actions.onNotifyNeededChange,
            icon = { Icon(Icons.Filled.Notifications, null, Modifier.size(20.dp)) }
        )
        ActionRow(
            title = if (sync.isSyncing) "Syncing…" else "Sync now",
            summary = syncLine.first,
            problem = syncLine.second,
            enabled = !sync.isSyncing,
            onClick = actions.onSync,
            icon = {
                if (sync.isSyncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else PanelIcon(R.drawable.ic_refresh)
            }
        )
    }

    PanelPages(page = page, root = GoDexPage.ROOT, onBack = back) { shown ->
        PageColumn(modifier) {
            when (shown) {
                GoDexPage.ROOT -> when {
                    !config.isConnected -> GoDexNotConnected(
                        onSignIn = signIn,
                        onPublicLink = { open(GoDexPage.PUBLIC_LINK) }
                    )

                    else -> {
                        GoDexStatusHeader(config.collectionTitle, needed, total, syncLine)
                        PanelSectionLabel("Checklist")
                        checklistRows()
                        PanelSectionLabel("Account")
                        PanelNavRow(
                            title = "Account",
                            summary = account.first,
                            active = !account.second && config.hasSession,
                            onClick = { open(GoDexPage.ACCOUNT) },
                            icon = { Icon(Icons.Filled.AccountCircle, null, Modifier.size(20.dp)) }
                        )
                    }
                }

                GoDexPage.CHECKLIST -> {
                    PanelHeader("Checklist", subtitle = config.collectionTitle.ifBlank { null }, onBack = back)
                    checklistRows()
                }

                GoDexPage.ACCOUNT -> {
                    PanelHeader("Account", onBack = back)
                    GoDexAccountRows(
                        config = config,
                        sync = sync,
                        onSignIn = signIn,
                        onPickChecklist = pickChecklist,
                        onSignOut = actions.onSignOut,
                        onDisconnect = actions.onDisconnect
                    )
                }

                GoDexPage.PUBLIC_LINK -> {
                    PanelHeader("Public link", subtitle = "Read-only: catches stay in GoDex", onBack = back)
                    Column(
                        modifier = Modifier.panelGutter().padding(top = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = onUrlInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Public collection link") },
                            placeholder = { Text("https://godex.site/public-collection/…") },
                            singleLine = true,
                            enabled = !sync.isSyncing
                        )
                        sync.errorMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = { actions.onConnect(urlInput) },
                            enabled = urlInput.isNotBlank() && !sync.isSyncing,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text(if (sync.isSyncing) "Connecting…" else "Connect") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoDexNotConnected(onSignIn: () -> Unit, onPublicLink: () -> Unit) {
    Column(
        modifier = Modifier.panelGutter().padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("Not connected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Connect your GoDex Hundo checklist to see what you still need and keep alerts for Hundos you have quiet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Sign in to GoDex", maxLines = 1)
        }
    }
    PanelNavRow(
        title = "Use a public link instead",
        summary = "Read-only: catches stay in GoDex",
        onClick = onPublicLink,
        icon = { Icon(Icons.Filled.Info, null, Modifier.size(20.dp)) }
    )
}

@Composable
private fun GoDexStatusHeader(title: String, needed: Int, total: Int, syncLine: Pair<String, Boolean>) {
    Column(modifier = Modifier.panelGutter().padding(top = Spacing.lg, bottom = Spacing.xs)) {
        Text(
            text = title.ifBlank { "GoDex Hundo collection" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (needed == 0) "Complete · $total caught" else "$needed still needed · ${total - needed} caught",
            style = MaterialTheme.typography.bodyLarge
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (syncLine.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = syncLine.first,
                style = MaterialTheme.typography.bodyMedium,
                color = if (syncLine.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GoDexAccountRows(
    config: GoDexConfig,
    sync: GoDexSyncUiState,
    onSignIn: () -> Unit,
    onPickChecklist: () -> Unit,
    onSignOut: () -> Unit,
    onDisconnect: () -> Unit
) {
    if (config.hasSession) {
        ActionRow(
            title = if (config.hasWriteBackUrl) "Change checklist" else "Choose checklist",
            summary = if (config.hasWriteBackUrl) "Sending catches to ${config.writeBackUrl.substringAfterLast("/")}"
            else "Signed in, but no checklist is chosen",
            problem = !config.hasWriteBackUrl,
            onClick = onPickChecklist,
            icon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp)) }
        )
        ActionRow(
            title = "Sign out",
            summary = "Stop sending catches; keep the checklist here",
            onClick = onSignOut,
            icon = { Icon(Icons.Filled.AccountCircle, null, Modifier.size(20.dp)) }
        )
    } else {
        val expired = sync.sessionState == GoDexSessionState.REAUTH_REQUIRED
        ActionRow(
            title = if (expired) "Sign in again" else "Sign in for two-way sync",
            summary = when {
                expired && sync.pendingCount > 0 -> "${changes(sync.pendingCount)} wait to be sent"
                expired -> "Your session expired"
                else -> "Mark catches here and send them to GoDex"
            },
            problem = expired,
            onClick = onSignIn,
            icon = { Icon(Icons.Filled.AccountCircle, null, Modifier.size(20.dp)) }
        )
    }
    ActionRow(
        title = "Disconnect",
        summary = "Remove the checklist from this app",
        destructive = true,
        enabled = !sync.isSyncing,
        onClick = onDisconnect,
        icon = { Icon(Icons.Filled.Warning, null, Modifier.size(20.dp)) }
    )
}

/** The sync state in one line, and whether it needs the trainer. */
private fun goDexSyncLine(config: GoDexConfig, sync: GoDexSyncUiState, now: Long): Pair<String, Boolean> {
    val last = if (config.lastSuccessfulSyncMillis > 0L) {
        DateUtils.getRelativeTimeSpanString(config.lastSuccessfulSyncMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    } else {
        "never"
    }
    val stale = config.lastSuccessfulSyncMillis > 0L &&
        now - config.lastSuccessfulSyncMillis >= GoDexRepository.STALE_WARNING_MILLIS
    return when {
        sync.isSyncing -> "Fetching the latest checklist" to false
        sync.sessionState == GoDexSessionState.REAUTH_REQUIRED ->
            (if (sync.pendingCount > 0) "Sign in again · ${changes(sync.pendingCount)} waiting" else "Sign in again to sync") to true
        sync.errorMessage != null -> "Sync failed · ${sync.errorMessage}" to true
        sync.pendingCount > 0 -> "Sending ${changes(sync.pendingCount)}" to false
        stale -> "Synced $last · update recommended" to true
        else -> "Up to date · synced $last" to false
    }
}

private fun goDexAccountLine(config: GoDexConfig, sync: GoDexSyncUiState): Pair<String, Boolean> = when {
    config.hasSession && config.hasWriteBackUrl -> "Signed in · two-way sync" to false
    config.hasSession -> "Signed in · no checklist chosen" to true
    sync.sessionState == GoDexSessionState.REAUTH_REQUIRED -> "Sign-in expired" to true
    else -> "Public link · read-only" to false
}

private fun changes(count: Int) = if (count == 1) "1 change" else "$count changes"

// ---- Shared parts ----

/** A page of rows that scrolls under the Settings top bar. */
@Composable
private fun PageColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.xl)
    ) { content() }
}

/** A permission with its state as a badge; the row opens Android's page for it. */
@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    RowFrame(onClick = onClick, icon = { PanelRowIcon(active = granted) { Icon(icon, null, Modifier.size(20.dp)) } }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Surface(
            shape = RoundedCornerShape(50),
            color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
        ) {
            Text(
                text = if (granted) "Allowed" else "Off",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
            )
        }
    }
}

/** A row that does something at once, so it has no chevron. */
@Composable
private fun ActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    problem: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    RowFrame(onClick = onClick, enabled = enabled, icon = { PanelRowIcon(content = icon) }) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A read-only fact laid out like the rows around it. */
@Composable
private fun InfoRow(title: String, summary: String, problem: Boolean, icon: @Composable () -> Unit) {
    RowFrame(onClick = null, icon = { PanelRowIcon(content = icon) }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowFrame(
    onClick: (() -> Unit)?,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = PanelGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(Spacing.lg))
        content()
    }
}

/** The current time, moved on each minute so "until 15:30" lines lapse on their own. */
@Composable
private fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - now % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private fun clockTime(millis: Long, now: Long): String {
    val days = (millis - now) / DateUtils.DAY_IN_MILLIS
    val sameDay = DateUtils.isToday(millis)
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))
    return when {
        sameDay -> time
        days < 1 -> "$time tomorrow"
        else -> DateUtils.formatDateTime(null, millis, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY)
    }
}

private fun pushAge(lastPush: Long, now: Long): String {
    val elapsed = now - lastPush
    return when {
        lastPush <= 0L -> "none yet"
        elapsed < 60_000L -> "just now"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} min ago"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L} h ago"
        else -> "${elapsed / 86_400_000L} d ago"
    }
}

private const val PUSH_STALE_MILLIS = 6 * 60 * 60 * 1000L

private fun FilterDefinition.notifiedTypeSummary(): String {
    val total = FilterAlertType.entries.size
    val count = when (alertTypes.mode) {
        FilterSelectionMode.ALL -> total
        FilterSelectionMode.NONE -> 0
        FilterSelectionMode.ONLY -> alertTypes.selectedCount.coerceAtMost(total)
    }
    return if (count == total) "All $total alert types" else "$count of $total alert types"
}
