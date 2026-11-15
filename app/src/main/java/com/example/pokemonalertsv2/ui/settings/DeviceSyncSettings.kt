package com.example.pokemonalertsv2.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.sync.CollectionStatus
import com.example.pokemonalertsv2.sync.DeviceRow
import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.sync.SyncCrypto
import com.example.pokemonalertsv2.sync.SyncGroup
import com.example.pokemonalertsv2.sync.SyncJoinException
import com.example.pokemonalertsv2.sync.SyncStateSnapshot
import com.example.pokemonalertsv2.util.AppText
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Device sync" settings page: set up, join, the group's devices and per-collection status. */
@Composable
internal fun DeviceSyncSettingsContent(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { DeviceSync.engine(context) }
    val stateStore = remember(context) { DeviceSync.state(context) }
    val snapshot by stateStore.snapshots.collectAsStateWithLifecycle(initialValue = null)
    val running by engine.running.collectAsState()
    val pendingLink by DeviceSync.pendingJoinCode.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var joinCode by rememberSaveable { mutableStateOf<String?>(null) }
    var showCode by remember { mutableStateOf<SyncGroup?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // A pokemonalerts://sync/join link opens the join dialog pre-filled; it never joins by itself.
    LaunchedEffect(pendingLink) {
        pendingLink?.let {
            joinCode = it
            DeviceSync.pendingJoinCode.value = null
        }
    }

    fun run(action: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            val message = try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SyncJoinException) {
                AppText.get(
                    if (error.reason == SyncJoinException.Reason.NOT_FOUND) R.string.device_sync_code_not_found
                    else R.string.device_sync_invalid_code
                )
            } catch (error: Exception) {
                AppText.get(R.string.device_sync_failed, error.message ?: error.javaClass.simpleName)
            } finally {
                busy = false
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    val state = snapshot ?: return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!state.connected) {
            NotConnected(
                state = state,
                busy = busy,
                onSetUp = {
                    run {
                        val group = engine.create()
                        showCode = group
                        AppText.get(R.string.device_sync_created)
                    }
                },
                onJoin = { joinCode = "" }
            )
        } else {
            Connected(
                state = state,
                syncing = running || busy,
                onSyncNow = { run { engine.syncNow(); null } },
                onAddDevice = {
                    scope.launch { showCode = stateStore.group() }
                },
                onLeave = { confirmLeave = true },
                onDelete = { confirmDelete = true }
            )
        }
    }

    joinCode?.let { code ->
        JoinDialog(
            initialCode = code,
            busy = busy,
            onDismiss = { joinCode = null },
            onScanFailed = { scope.launch { snackbarHostState.showSnackbar(AppText.get(R.string.device_sync_scan_failed)) } },
            onJoin = { entered ->
                if (SyncCrypto.decodePairingCode(entered) == null) {
                    scope.launch { snackbarHostState.showSnackbar(AppText.get(R.string.device_sync_invalid_code)) }
                } else {
                    joinCode = null
                    run {
                        engine.join(entered)
                        AppText.get(R.string.device_sync_joined)
                    }
                }
            }
        )
    }

    showCode?.let { group -> PairingCodeDialog(group, onDismiss = { showCode = null }, onCopied = {
        scope.launch { snackbarHostState.showSnackbar(AppText.get(R.string.device_sync_code_copied)) }
    }) }

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
}

@Composable
private fun NotConnected(state: SyncStateSnapshot, busy: Boolean, onSetUp: () -> Unit, onJoin: () -> Unit) {
    SettingsSection(title = stringResource(R.string.settings_device_sync)) {
        if (state.notice == DeviceSync.NOTICE_DELETED_ELSEWHERE) {
            Text(
                stringResource(R.string.device_sync_deleted_elsewhere),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(stringResource(R.string.device_sync_intro), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.device_sync_local_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSetUp, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.device_sync_set_up))
            }
            OutlinedButton(onClick = onJoin, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.device_sync_join))
            }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Connected(
    state: SyncStateSnapshot,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    onAddDevice: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_device_sync)) {
        val lastSync = state.statuses.values.maxOfOrNull { it.lastSyncAtMillis } ?: 0L
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.device_sync_status_on), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (syncing) stringResource(R.string.device_sync_syncing) else syncedLabel(lastSync),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (syncing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSyncNow, enabled = !syncing, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.device_sync_now))
            }
            OutlinedButton(onClick = onAddDevice, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.device_sync_add_device))
            }
        }
    }

    SettingsSection(title = stringResource(R.string.device_sync_devices)) {
        val rows = listOfNotNull(state.ownRow?.let { it to true }) +
            state.devices.values.sortedBy { it.name }.map { it to false }
        rows.forEachIndexed { index, (row, isSelf) ->
            if (index > 0) HorizontalDivider()
            DeviceRowItem(row, isSelf)
        }
    }

    SettingsSection(title = stringResource(R.string.device_sync_data)) {
        DeviceSync.COLLECTIONS.forEach { name ->
            CollectionRow(name, state.statuses[name] ?: CollectionStatus())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.device_sync_leave))
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.device_sync_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeviceRowItem(row: DeviceRow, isSelf: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
}

@Composable
private fun CollectionRow(name: String, status: CollectionStatus) {
    val label = when (name) {
        DeviceSync.SETTINGS -> R.string.device_sync_collection_settings
        DeviceSync.HUNTS -> R.string.device_sync_collection_hunts
        DeviceSync.ROUTES -> R.string.device_sync_collection_routes
        DeviceSync.POKEGENIE -> R.string.device_sync_collection_pokegenie
        DeviceSync.SIGNINS -> R.string.device_sync_collection_signins
        else -> R.string.device_sync_collection_devices
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val (text, color) = when {
            status.unsupported -> stringResource(R.string.device_sync_update_app) to MaterialTheme.colorScheme.error
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
private fun JoinDialog(
    initialCode: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onScanFailed: () -> Unit,
    onJoin: (String) -> Unit
) {
    val context = LocalContext.current
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_sync_join_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.device_sync_join_rule), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.device_sync_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { scanCode(context, onScanned = { code = it }, onFailed = onScanFailed) }) {
                    Text(stringResource(R.string.device_sync_scan))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }, enabled = code.isNotBlank() && !busy) {
                Text(stringResource(R.string.device_sync_join_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
    )
}

/** Google's code scanner runs in Play services: no camera permission, nothing to bundle. */
private fun scanCode(context: Context, onScanned: (String) -> Unit, onFailed: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    runCatching {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScanned) ?: onFailed() }
            .addOnFailureListener { onFailed() }
    }.onFailure { onFailed() }
}

@Composable
private fun PairingCodeDialog(group: SyncGroup, onDismiss: () -> Unit, onCopied: () -> Unit) {
    val context = LocalContext.current
    val link = remember(group) { SyncCrypto.joinLink(group) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(link) { bitmap = withContext(Dispatchers.Default) { qrBitmap(link) } }
    val description = stringResource(R.string.device_sync_qr_description)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_sync_add_device)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.device_sync_add_device_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(stringResource(R.string.device_sync_add_device_steps), style = MaterialTheme.typography.bodySmall)
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(220.dp)
                            .background(Color.White)
                            .padding(8.dp)
                            .semantics { contentDescription = description }
                    )
                }
                SelectionContainer {
                    Text(group.pairingCode, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText(AppText.get(R.string.device_sync_code_label), group.pairingCode))
                onCopied()
            }) { Text(stringResource(R.string.device_sync_copy_code)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { index ->
        if (matrix.get(index % size, index / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
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
