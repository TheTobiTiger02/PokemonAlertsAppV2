package com.example.pokemonalertsv2.ui.checklists

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.dextracker.DexTrackerCollection
import com.example.pokemonalertsv2.data.dextracker.DexTrackerDomain
import com.example.pokemonalertsv2.data.dextracker.DexTrackerEntry
import com.example.pokemonalertsv2.data.dextracker.DexTrackerRepository
import com.example.pokemonalertsv2.data.gametext.GameNames
import com.example.pokemonalertsv2.util.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** The Dex Tracker part of the Checklists list: connect, or its collections with progress. */
@Composable
internal fun DexTrackerSection(onOpen: (DexTrackerCollection) -> Unit, onMessage: (String) -> Unit) {
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
    val collections by remember(user) {
        repository.collections(user).catch { onMessage(it.message ?: "Dex Tracker could not be read") }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    collections.filterNot { it.archived }.forEach { collection ->
        val entries by remember(user, collection.id) {
            repository.entries(user, collection.id).catch { }
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        val (credited, target) = DexTrackerDomain.progress(entries)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.checklists_open)) { onOpen(collection) }
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(collection.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.settings_x_caught_out_of_x, credited, target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(progress = { if (target == 0) 0f else credited.toFloat() / target }, modifier = Modifier.fillMaxWidth().height(6.dp))
            }
        }
    }
    TextButton(onClick = { scope.launch { repository.signOut(context) } }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dextracker_sign_out))
    }
}

/** One Dex Tracker collection: each line's copies against its target, and "+1 caught" with Undo. */
@Composable
internal fun DexTrackerCollectionScreen(collection: DexTrackerCollection, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DexTrackerRepository.getInstance(context) }
    val uid by repository.uid.collectAsStateWithLifecycle()
    val user = uid
    if (user == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val entries by remember(user, collection.id) { repository.entries(user, collection.id).catch { } }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.catchroutes_back)) }
                Text(collection.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val (credited, target) = DexTrackerDomain.progress(entries)
            ChecklistProgressCard(neededCount = target - credited, caughtCount = credited, totalCount = target)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = DexTrackerEntry::id) { entry ->
                    DexTrackerRow(entry) {
                        scope.launch {
                            val undo = try {
                                repository.plusOne(collection.id, entry)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
                                return@launch
                            }
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                AppText.get(R.string.checklists_marked_caught, localName(entry)),
                                actionLabel = AppText.get(R.string.checklists_undo),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) repository.undo(undo)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

private fun localName(entry: DexTrackerEntry): String = GameNames.pokemon(entry.name, entry.pokemonId.takeIf { it <= 1025 }) ?: entry.name

@Composable
private fun DexTrackerRow(entry: DexTrackerEntry, onPlusOne: () -> Unit) {
    val owned = DexTrackerDomain.ownedCount(entry)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = DexTrackerRepository.spriteUrl(entry.pokemonId), contentDescription = null, modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(localName(entry), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.dextracker_entry_count, owned, entry.target),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (owned >= entry.target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onPlusOne) { Text(stringResource(R.string.dextracker_plus_one)) }
        }
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
