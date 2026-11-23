package com.example.pokemonalertsv2.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.account.Account
import com.example.pokemonalertsv2.account.AccountApiException
import com.example.pokemonalertsv2.account.AccountDeviceDto
import com.example.pokemonalertsv2.account.AccountRepository
import com.example.pokemonalertsv2.account.AccountSync
import com.example.pokemonalertsv2.sync.CollectionStatus
import com.example.pokemonalertsv2.sync.DeviceRow
import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.sync.SyncStateSnapshot
import com.example.pokemonalertsv2.util.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The "Device sync" settings page: the account, the group's devices and per-collection status. */
@Composable
internal fun DeviceSyncSettingsContent(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { DeviceSync.engine(context) }
    val stateStore = remember(context) { DeviceSync.state(context) }
    val snapshot by stateStore.snapshots.collectAsStateWithLifecycle(initialValue = null)
    val running by engine.running.collectAsState()
    val account by AccountRepository.account.collectAsState()
    val accountDevices by AccountSync.devices.collectAsState()
    val paused by AccountSync.paused.collectAsState()
    val signInAvailable = remember(context) { AccountRepository.isAvailable(context) }
    var busy by remember { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    // 1: what deleting the account does; 2: the final "cannot be undone".
    var deleteAccountStep by rememberSaveable { mutableIntStateOf(0) }
    var removeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(account?.uid) { AccountSync.refresh(context) }

    fun run(action: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            val message = try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason = (error as? AccountApiException)?.code ?: error.message ?: error.javaClass.simpleName
                AppText.get(R.string.device_sync_failed, reason)
            } finally {
                busy = false
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    val state = snapshot ?: return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AccountSection(
            account = account,
            state = state,
            paused = paused,
            busy = busy,
            signInAvailable = signInAvailable,
            onSignIn = {
                run {
                    AccountSync.signIn(context.findActivity() ?: context) ?: return@run null
                    AppText.get(R.string.device_sync_signed_in)
                }
            },
            onTurnOn = {
                run {
                    AccountSync.turnOn(context)
                    AppText.get(R.string.device_sync_signed_in)
                }
            },
            onSignOut = { confirmSignOut = true },
            onDeleteAccount = { deleteAccountStep = 1 }
        )
        if (state.connected) {
            Connected(
                state = state,
                syncing = running || busy,
                signedIn = account != null,
                accountDevices = accountDevices,
                onSyncNow = { run { engine.syncNow(); null } },
                onRemove = { id, name -> removeTarget = id to name },
                onLeave = { confirmLeave = true },
                onDelete = { confirmDelete = true }
            )
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.device_sync_leave),
            message = stringResource(R.string.device_sync_leave_message),
            confirm = stringResource(R.string.device_sync_leave_confirm),
            onDismiss = { confirmLeave = false },
            onConfirm = {
                confirmLeave = false
                run { engine.leave(); null }
            }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.device_sync_delete),
            message = stringResource(R.string.device_sync_delete_message),
            confirm = stringResource(R.string.device_sync_delete_confirm),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                run { engine.deleteEverywhere(); null }
            }
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.device_sync_sign_out),
            message = stringResource(R.string.device_sync_sign_out_message),
            confirm = stringResource(R.string.device_sync_sign_out),
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                run { AccountSync.signOut(context); null }
            }
        )
    }
    removeTarget?.let { (id, name) ->
        ConfirmDialog(
            title = stringResource(R.string.device_sync_remove_device_title, name),
            message = stringResource(R.string.device_sync_remove_device_message),
            confirm = stringResource(R.string.device_sync_remove_device),
            destructive = true,
            onDismiss = { removeTarget = null },
            onConfirm = {
                removeTarget = null
                run {
                    AccountSync.removeDevice(context, id)
                    AppText.get(R.string.device_sync_device_removed)
                }
            }
        )
    }
    if (deleteAccountStep == 1) {
        ConfirmDialog(
            title = stringResource(R.string.device_sync_delete_account),
            message = stringResource(R.string.device_sync_delete_account_message),
            confirm = stringResource(R.string.device_sync_continue),
            destructive = true,
            onDismiss = { deleteAccountStep = 0 },
            onConfirm = { deleteAccountStep = 2 }
        )
    }
    if (deleteAccountStep == 2) {
        ConfirmDialog(
            title = stringResource(R.string.device_sync_delete_account_final_title),
            message = stringResource(R.string.device_sync_delete_account_final_message),
            confirm = stringResource(R.string.device_sync_delete_account),
            destructive = true,
            onDismiss = { deleteAccountStep = 0 },
            onConfirm = {
                deleteAccountStep = 0
                run {
                    AccountSync.deleteAccount(context)
                    AppText.get(R.string.device_sync_account_deleted)
                }
            }
        )
    }
}

/** Credential Manager shows Google's picker over an Activity. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun AccountSection(
    account: Account?,
    state: SyncStateSnapshot,
    paused: Boolean,
    busy: Boolean,
    signInAvailable: Boolean,
    onSignIn: () -> Unit,
    onTurnOn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_device_sync)) {
        val notice = when (state.notice) {
            DeviceSync.NOTICE_DELETED_ELSEWHERE -> R.string.device_sync_deleted_elsewhere
            AccountSync.NOTICE_REMOVED -> R.string.device_sync_removed_notice
            else -> null
        }
        if (notice != null && !state.connected) {
            Text(stringResource(notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (account == null) {
            if (state.connected) {
                Text(
                    stringResource(if (paused) R.string.device_sync_paused_banner else R.string.device_sync_legacy_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            } else {
                Text(stringResource(R.string.device_sync_intro), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.device_sync_local_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (signInAvailable) {
                Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.device_sync_sign_in))
                }
            } else {
                Text(
                    stringResource(R.string.device_sync_sign_in_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                stringResource(R.string.device_sync_signed_in_as, account.email ?: account.displayName ?: account.uid),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (!state.connected) {
                Text(stringResource(R.string.device_sync_off_signed_in), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onTurnOn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.device_sync_turn_on))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.device_sync_sign_out))
                }
                TextButton(onClick = onDeleteAccount, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.device_sync_delete_account), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Connected(
    state: SyncStateSnapshot,
    syncing: Boolean,
    signedIn: Boolean,
    accountDevices: List<AccountDeviceDto>,
    onSyncNow: () -> Unit,
    onRemove: (id: String, name: String) -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.device_sync_status)) {
        val lastSync = state.statuses.values.maxOfOrNull { it.lastSyncAtMillis } ?: 0L
        val live by DeviceSync.streamConnected.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.device_sync_status_on), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        syncing -> stringResource(R.string.device_sync_syncing)
                        live -> stringResource(R.string.device_sync_live)
                        else -> syncedLabel(lastSync)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (live && !syncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (syncing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Button(onClick = onSyncNow, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.device_sync_now))
        }
    }

    SettingsSection(title = stringResource(R.string.device_sync_devices)) {
        val removable = if (signedIn) accountDevices.filterNot { it.self }.map { it.deviceId }.toSet() else emptySet()
        // Signed in on the account but not synced yet: no row in the devices collection.
        val waiting = if (signedIn) {
            accountDevices.filter { !it.self && it.deviceId !in state.devices }
                .map { it.deviceId to DeviceRow(name = it.name ?: it.deviceId, model = it.model.orEmpty()) }
        } else {
            emptyList()
        }
        val rows = listOfNotNull(state.ownRow?.let { Triple("", it, true) }) +
            (state.devices.entries.map { it.key to it.value } + waiting)
                .sortedBy { it.second.name }
                .map { (id, row) -> Triple(id, row, false) }
        rows.forEachIndexed { index, (id, row, isSelf) ->
            if (index > 0) HorizontalDivider()
            DeviceRowItem(row, isSelf, onRemove = if (id in removable) ({ onRemove(id, row.name) }) else null)
        }
    }

    SettingsSection(title = stringResource(R.string.device_sync_this_device_settings)) {
        LocalOverrides()
    }

    SettingsSection(title = stringResource(R.string.device_sync_data)) {
        DeviceSync.COLLECTIONS.forEach { name ->
            CollectionRow(name, state.statuses[name] ?: CollectionStatus())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Signed in, signing out is how this device stops; leaving stays for groups without an account.
        if (!signedIn) {
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.device_sync_leave))
            }
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.device_sync_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeviceRowItem(row: DeviceRow, isSelf: Boolean, onRemove: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                if (isSelf) "${row.name} · ${stringResource(R.string.device_sync_this_device)}" else row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                stringResource(R.string.device_sync_device_details, row.model.ifBlank { row.name }, row.appVersion.ifBlank { "?" }, relative(row.seenAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.device_sync_remove_device), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** "Only on this device" switches, one per group of [DeviceSync.OVERRIDE_GROUPS]. */
@Composable
private fun LocalOverrides() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overrides by remember(context) { DeviceSync.state(context).localOverrides }.collectAsStateWithLifecycle(initialValue = emptySet())
    Text(
        stringResource(R.string.device_sync_this_device_settings_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    DeviceSync.OVERRIDE_GROUPS.forEach { (group, keys) ->
        val label = when (group) {
            "notifications" -> R.string.device_sync_override_notifications
            "vibrate" -> R.string.device_sync_override_vibrate
            "content" -> R.string.device_sync_override_content
            "quiet_hours" -> R.string.device_sync_override_quiet_hours
            "silence" -> R.string.device_sync_override_silence
            "theme" -> R.string.device_sync_override_theme
            "pip" -> R.string.device_sync_override_pip
            "journey_overlay" -> R.string.device_sync_override_journey_overlay
            else -> R.string.device_sync_override_watch
        }
        val local = keys.all { it in overrides }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = local,
                onCheckedChange = { checked ->
                    scope.launch { runCatching { DeviceSync.setLocalOverride(context, group, checked) } }
                }
            )
        }
    }
}

@Composable
private fun CollectionRow(name: String, status: CollectionStatus) {
    val label = when (name) {
        DeviceSync.SETTINGS -> R.string.device_sync_collection_settings
        DeviceSync.HUNTS -> R.string.device_sync_collection_hunts
        DeviceSync.ROUTES -> R.string.device_sync_collection_routes
        DeviceSync.POKEGENIE -> R.string.device_sync_collection_pokegenie
        DeviceSync.SIGNINS -> R.string.device_sync_collection_signins
        DeviceSync.LIVE -> R.string.device_sync_collection_live
        DeviceSync.GODEX -> R.string.device_sync_collection_godex
        DeviceSync.WIDGETS -> R.string.device_sync_collection_widgets
        DeviceSync.CHECKLISTS -> R.string.device_sync_collection_checklists
        else -> R.string.device_sync_collection_devices
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val (text, color) = when {
            status.unsupported -> stringResource(R.string.device_sync_update_app) to MaterialTheme.colorScheme.error
            status.error == com.example.pokemonalertsv2.sync.DeviceSyncEngine.SERVER_LACKS_COLLECTION ->
                stringResource(R.string.device_sync_server_update) to MaterialTheme.colorScheme.onSurfaceVariant
            status.error != null -> stringResource(R.string.device_sync_failed, status.error) to MaterialTheme.colorScheme.error
            else -> syncedLabel(status.lastSyncAtMillis) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun syncedLabel(millis: Long): String =
    if (millis <= 0) stringResource(R.string.device_sync_never)
    else stringResource(R.string.device_sync_synced_at, relative(millis))

/** "just now", "5 minutes ago", "yesterday"; lowercased in German, where it sits mid-sentence. */
private fun relative(millis: Long): String {
    if (millis <= 0) return "–"
    val now = System.currentTimeMillis()
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) return AppText.get(R.string.device_sync_just_now)
    val text = DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    return if (java.util.Locale.getDefault().language == "de" && text.startsWith("Vor ")) text.replaceFirstChar { it.lowercase() } else text
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
    )
}

/** The overview row's summary. */
@Composable
internal fun deviceSyncSummary(context: Context): String {
    val snapshot by remember(context) { DeviceSync.state(context).snapshots }.collectAsStateWithLifecycle(initialValue = null)
    val state = snapshot
    return if (state?.connected == true) {
        (state.devices.size + 1).let { androidx.compose.ui.res.pluralStringResource(R.plurals.device_sync_overview_on, it, it) }
    } else {
        stringResource(R.string.device_sync_overview_off)
    }
}
