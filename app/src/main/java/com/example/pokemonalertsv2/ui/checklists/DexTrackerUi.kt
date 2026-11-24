package com.example.pokemonalertsv2.ui.checklists

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.checklists.ChecklistRepository
import com.example.pokemonalertsv2.ui.checklists.dextracker.SegmentBar
import com.example.pokemonalertsv2.ui.checklists.dextracker.dexTrackerPalette
import com.example.pokemonalertsv2.data.dextracker.DexTrackerCollection
import com.example.pokemonalertsv2.data.dextracker.DexTrackerDomain
import com.example.pokemonalertsv2.data.dextracker.DexTrackerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The Dex Tracker part of the Checklists list: connect, or its collections with progress as the
 * web app shows it (collected on the main account, with trades), and their badge settings.
 */
@Composable
internal fun DexTrackerSection(
    onOpen: (DexTrackerCollection) -> Unit,
    onSettings: (DexTrackerCollection) -> Unit,
    badging: Set<String>,
    onMessage: (String) -> Unit
) {
    if (!DexTrackerRepository.isConfigured) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DexTrackerRepository.getInstance(context) }
    if (!repository.available) return
    val uid by repository.uid.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    Text(stringResource(R.string.dextracker_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    val user = uid
    if (user == null) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.dextracker_connect_body), style = MaterialTheme.typography.bodyMedium)
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repository.signIn(context.findActivity() ?: context)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            onMessage(error.message ?: error.javaClass.simpleName)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.dextracker_connect)) }
            }
        }
        return
    }
    val palette = dexTrackerPalette()
    val collections by remember(user) {
        repository.collections(user).catch { onMessage(it.message ?: "Dex Tracker could not be read") }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    collections.filterNot { it.archived }.forEach { collection ->
        val entries by remember(user, collection.id) {
            repository.entries(user, collection.id).catch { }
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        val totals = DexTrackerDomain.totals(entries)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.checklists_open)) { onOpen(collection) }
        ) {
            Row(Modifier.padding(start = 18.dp, top = 10.dp, end = 6.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(collection.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val percent = if (totals.target == 0) 0 else totals.credited * 100 / totals.target
                    Text(
                        listOfNotNull(
                            stringResource(R.string.dextracker_title),
                            stringResource(R.string.dextracker_card_progress, totals.credited, totals.target, percent),
                            stringResource(R.string.checklists_badges_alerts).takeIf { ChecklistRepository.dexTrackerChecklistId(collection.id) in badging }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SegmentBar(totals.credited, totals.tradable, totals.target, palette, height = 6.dp)
                }
                IconButton(onClick = { onSettings(collection) }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.checklists_settings, collection.title))
                }
            }
        }
    }
    TextButton(onClick = { scope.launch { repository.signOut(context) } }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dextracker_sign_out))
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
