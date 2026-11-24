package com.example.pokemonalertsv2.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.pokemonalertsv2.data.godex.GoDexAccountCollection
import com.example.pokemonalertsv2.data.godex.GoDexAuthenticationException
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity
import com.example.pokemonalertsv2.util.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the collection list is opened for. */
enum class GoDexCollectionsMode {
    /** Checklists tab: add any number of the account's collections as checklists. */
    ADD,

    /** goDex settings: pick the collection behind Hundo alerts and notifications. */
    HUNDO
}

/**
 * Where one of the account's collections already is in the app. One added by its share link
 * ([LINKED]) can be ticked again: it then becomes editable, keeping its settings.
 */
private enum class CollectionPlace { NONE, HUNDO, ADDED, LINKED }

private fun GoDexAccountCollection.place(config: GoDexConfig, addedUrls: Set<String?>): CollectionPlace = when {
    url == config.writeBackUrl || url == config.url || (publicUrl != null && publicUrl == config.url) -> CollectionPlace.HUNDO
    url in addedUrls -> CollectionPlace.ADDED
    publicUrl != null && publicUrl in addedUrls -> CollectionPlace.LINKED
    else -> CollectionPlace.NONE
}

private val CollectionPlace.canAdd get() = this == CollectionPlace.NONE || this == CollectionPlace.LINKED

/**
 * The signed-in goDex account's collections, read from goDex. In [GoDexCollectionsMode.ADD]
 * the user ticks the ones to add; in [GoDexCollectionsMode.HUNDO] picks the Hundo collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoDexCollectionsSheet(mode: GoDexCollectionsMode, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val goDex = remember(context) { GoDexRepository.getInstance(context) }
    val checklists = remember(context) { ChecklistRepository.getInstance(context) }
    val config by goDex.config.collectAsStateWithLifecycle()
    val added by checklists.checklists.collectAsStateWithLifecycle(initialValue = emptyList())
    val addedUrls = remember(added) { added.mapTo(HashSet()) { it.url } }

    var collections by remember { mutableStateOf<List<GoDexAccountCollection>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var signInNeeded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var progress by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt, config.sessionCookies.isNotBlank()) {
        error = null
        signInNeeded = false
        try {
            collections = checklists.goDexAccountCollections()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expired: GoDexAuthenticationException) {
            signInNeeded = true
            error = expired.message
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
        }
    }

    fun addSelected(chosen: List<GoDexAccountCollection>) {
        scope.launch {
            var done = 0
            val failures = mutableListOf<String>()
            chosen.forEachIndexed { index, collection ->
                progress = AppText.get(R.string.godex_collections_adding, index + 1, chosen.size)
                try {
                    checklists.addGoDexAccount(collection)
                    done++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    failures += "${collection.title}: ${failure.message ?: failure.javaClass.simpleName}"
                }
            }
            progress = null
            selected = emptySet()
            onMessage(
                if (failures.isEmpty()) AppText.get(R.string.godex_collections_added, done)
                else failures.joinToString("\n")
            )
            if (failures.isEmpty()) onDismiss()
        }
    }

    fun chooseHundo(collection: GoDexAccountCollection) {
        scope.launch {
            progress = AppText.get(R.string.godex_collections_importing, collection.title)
            try {
                goDex.chooseHundo(collection)
                onMessage(AppText.get(R.string.godex_collections_hundo_set, collection.title))
                onDismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onMessage(failure.message ?: failure.javaClass.simpleName)
            } finally {
                progress = null
            }
        }
    }

    val busy = progress != null
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (mode == GoDexCollectionsMode.HUNDO) R.string.godex_collections_hundo_title else R.string.godex_collections_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(if (mode == GoDexCollectionsMode.HUNDO) R.string.godex_collections_hundo_hint else R.string.godex_collections_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val list = collections
            when {
                error != null -> {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (signInNeeded) {
                        Button(onClick = { context.startActivity(GoDexLoginActivity.createIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_sign_in_to_godex))
                        }
                    } else {
                        OutlinedButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.godex_collections_retry)) }
                    }
                }

                list == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }

                else -> {
                    val shown = if (mode == GoDexCollectionsMode.HUNDO) list.filter { it.isHundoDex } else list
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(if (mode == GoDexCollectionsMode.HUNDO) R.string.godex_collections_no_hundo else R.string.godex_collections_none),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // No Hundo collection yet: offer the account's one first.
                    val hundoOffer = if (mode == GoDexCollectionsMode.ADD && !config.isConnected) list.firstOrNull { it.isHundoDex } else null
                    if (hundoOffer != null) {
                        OutlinedButton(onClick = { chooseHundo(hundoOffer) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.godex_collections_use_for_hundo, hundoOffer.title), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(shown, key = { it.collectionId }) { collection ->
                            val place = collection.place(config, addedUrls)
                            CollectionRow(
                                collection = collection,
                                place = place,
                                mode = mode,
                                checked = when (mode) {
                                    GoDexCollectionsMode.ADD -> !place.canAdd || collection.collectionId in selected
                                    GoDexCollectionsMode.HUNDO -> place == CollectionPlace.HUNDO
                                },
                                enabled = !busy && when (mode) {
                                    GoDexCollectionsMode.ADD -> place.canAdd
                                    GoDexCollectionsMode.HUNDO -> place != CollectionPlace.HUNDO
                                },
                                onClick = {
                                    when (mode) {
                                        GoDexCollectionsMode.ADD -> selected =
                                            if (collection.collectionId in selected) selected - collection.collectionId else selected + collection.collectionId
                                        GoDexCollectionsMode.HUNDO -> chooseHundo(collection)
                                    }
                                }
                            )
                        }
                    }
                    if (mode == GoDexCollectionsMode.ADD) {
                        Button(
                            onClick = { addSelected(list.filter { it.collectionId in selected }) },
                            enabled = selected.isNotEmpty() && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(progress ?: stringResource(R.string.godex_collections_add, selected.size)) }
                    } else if (progress != null) {
                        Text(progress.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionRow(
    collection: GoDexAccountCollection,
    place: CollectionPlace,
    mode: GoDexCollectionsMode,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = if (mode == GoDexCollectionsMode.ADD) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        if (mode == GoDexCollectionsMode.ADD) Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(end = 12.dp).size(24.dp))
        else RadioButton(selected = checked, onClick = null, enabled = enabled, modifier = Modifier.padding(end = 12.dp).size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(collection.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = when (place) {
                CollectionPlace.HUNDO -> " · ${stringResource(R.string.godex_collections_is_hundo)}"
                CollectionPlace.ADDED -> " · ${stringResource(R.string.godex_collections_is_added)}"
                CollectionPlace.LINKED -> " · ${stringResource(R.string.godex_collections_is_linked)}"
                CollectionPlace.NONE -> ""
            }
            Text(
                "${kindLabel(collection.kind)} · ${stringResource(R.string.settings_x_caught_out_of_x, collection.caught, collection.total)}$where",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
