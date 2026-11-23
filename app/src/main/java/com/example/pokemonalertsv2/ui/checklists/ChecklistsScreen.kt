package com.example.pokemonalertsv2.ui.checklists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A checklist someone asked to open from outside the tab (a deep link, the GoDex settings page),
 * waiting for the Checklists tab to show it.
 */
object ChecklistDeepLink {
    const val GODEX = "godex"

    private val pendingState = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = pendingState.asStateFlow()

    fun open(checklistId: String = GODEX) {
        pendingState.value = checklistId
    }

    fun consume() {
        pendingState.value = null
    }
}

/** The Checklists tab: the user's checklists with their progress, and the editor for one. */
@Composable
internal fun ChecklistsRoute(onOpenGoDexSettings: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { GoDexRepository.getInstance(context) }
    val entries by repository.entries.collectAsStateWithLifecycle()
    val config by repository.config.collectAsStateWithLifecycle()
    val pendingKeys by repository.pendingEntryKeys.collectAsStateWithLifecycle()
    val sync by repository.syncUiState.collectAsStateWithLifecycle()
    val requested by ChecklistDeepLink.pending.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(requested) {
        requested?.let {
            openId = it
            ChecklistDeepLink.consume()
        }
    }
    val editing = openId == ChecklistDeepLink.GODEX && config.isConnected
    BackHandler(enabled = editing) { openId = null }

    LaunchedEffect(editing) {
        if (editing) runCatching { repository.refreshForPageEntry() }
    }

    if (editing) {
        val families = remember(repository) { evolutionFamilies(repository.evolutionEdges) }
        var types by remember { mutableStateOf<Map<Pair<Int, String?>, List<String>>>(emptyMap()) }
        val dexNumbers = remember(entries) { entries.map { it.pokedexId }.toSet() }
        LaunchedEffect(dexNumbers) {
            if (dexNumbers.isEmpty()) return@LaunchedEffect
            val gameMaster = GameMasterRepository.getInstance(context)
            runCatching { gameMaster.syncIfNeeded() }
            types = runCatching { gameMaster.typesByDexAndRegion(dexNumbers) }.getOrDefault(emptyMap())
        }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = { openId = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.catchroutes_back))
                }
                Text(
                    config.collectionTitle.ifBlank { stringResource(R.string.settings_godex_collection) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GoDexChecklistEditor(
                entries = entries,
                pendingEntryKeys = pendingKeys,
                canEdit = config.hasWriteBackUrl,
                sessionState = sync.sessionState,
                isSyncing = sync.isSyncing,
                syncError = sync.errorMessage,
                pendingCount = sync.pendingCount,
                families = families,
                typesByDexAndRegion = types,
                onSetCaught = { keys, caught -> repository.setCaught(keys, caught) },
                onUndo = { repository.undo(it) },
                onSignIn = { context.startActivity(GoDexLoginActivity.createIntent(context)) }
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.navigation_checklists), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (config.isConnected) {
                val needed = entries.count { it.needed }
                ChecklistCard(
                    title = config.collectionTitle.ifBlank { stringResource(R.string.settings_godex_collection) },
                    source = stringResource(R.string.checklists_source_godex),
                    caught = entries.size - needed,
                    total = entries.size,
                    onOpen = { openId = ChecklistDeepLink.GODEX }
                )
            } else {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.checklists_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.checklists_empty_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onOpenGoDexSettings) { Text(stringResource(R.string.checklists_connect_godex)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistCard(title: String, source: String, caught: Int, total: Int, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.checklists_open), onClick = onOpen)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "$source · ${stringResource(R.string.settings_x_caught_out_of_x, caught, total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else caught.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }
    }
}
