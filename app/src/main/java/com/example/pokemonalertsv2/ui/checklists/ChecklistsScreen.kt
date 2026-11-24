package com.example.pokemonalertsv2.ui.checklists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.pokemonalertsv2.data.checklists.ChecklistKind
import com.example.pokemonalertsv2.data.checklists.ChecklistRepository
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.isGoDexAccount
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.ui.godex.GoDexLoginActivity
import com.example.pokemonalertsv2.util.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A checklist someone asked to open from outside the tab (a deep link, the GoDex settings page),
 * waiting for the Checklists tab to show it.
 */
object ChecklistDeepLink {
    /** The GoDex Hundo collection; other checklists use their own ids. */
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
    val scope = rememberCoroutineScope()
    val goDex = remember(context) { GoDexRepository.getInstance(context) }
    val checklistRepository = remember(context) { ChecklistRepository.getInstance(context) }
    val goDexEntries by goDex.entries.collectAsStateWithLifecycle()
    val config by goDex.config.collectAsStateWithLifecycle()
    val allChecklists by checklistRepository.checklists.collectAsStateWithLifecycle(initialValue = emptyList())
    // Dex Tracker collections keep only their settings here and are listed in their own section.
    val others = allChecklists.filter { it.source != ChecklistEntity.SOURCE_DEXTRACKER }
    val progress by checklistRepository.progress.collectAsStateWithLifecycle(initialValue = emptyMap())
    val requested by ChecklistDeepLink.pending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsFor by remember { mutableStateOf<ChecklistEntity?>(null) }
    var dexTrackerOpen by remember { mutableStateOf<com.example.pokemonalertsv2.data.dextracker.DexTrackerCollection?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var accountCollections by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(requested) {
        requested?.let {
            openId = it
            ChecklistDeepLink.consume()
        }
    }
    val openOther = others.firstOrNull { it.id == openId }
    val editingGoDex = openId == ChecklistDeepLink.GODEX && config.isConnected
    BackHandler(enabled = editingGoDex || openOther != null) { openId = null }
    BackHandler(enabled = dexTrackerOpen != null) { dexTrackerOpen = null }

    fun run(action: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            val message = try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error.message ?: error.javaClass.simpleName
            } finally {
                busy = false
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            dexTrackerOpen != null -> com.example.pokemonalertsv2.ui.checklists.dextracker.DexTrackerCollectionScreen(
                dexTrackerOpen!!,
                onBack = { dexTrackerOpen = null },
                onSettings = { scope.launch { settingsFor = checklistRepository.dexTrackerSettings(dexTrackerOpen!!) } }
            )
            editingGoDex -> GoDexHundoEditor(goDex, goDexEntries, config.collectionTitle, onBack = { openId = null })
            openOther != null -> OtherChecklistEditor(checklistRepository, openOther, onBack = { openId = null })
            else -> ChecklistList(
                goDexConnected = config.isConnected,
                goDexTitle = config.collectionTitle,
                goDexCaught = goDexEntries.count { !it.needed },
                goDexTotal = goDexEntries.size,
                others = others,
                progress = progress,
                busy = busy,
                onOpen = { openId = it },
                onSettings = { settingsFor = it },
                onAddGoDex = { adding = true },
                onAccountCollections = if (config.sessionCookies.isNotBlank()) ({ accountCollections = true }) else null,
                onNewChecklist = { creating = true },
                onOpenGoDexSettings = onOpenGoDexSettings,
                extra = {
                    DexTrackerSection(
                        onOpen = { dexTrackerOpen = it },
                        onSettings = { collection -> scope.launch { settingsFor = checklistRepository.dexTrackerSettings(collection) } },
                        badging = allChecklists.filter { it.source == ChecklistEntity.SOURCE_DEXTRACKER && it.useForAlerts }.mapTo(HashSet()) { it.id },
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                    )
                }
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (adding) {
        AddGoDexDialog(
            busy = busy,
            onDismiss = { adding = false },
            onAdd = { url ->
                run {
                    val added = checklistRepository.addGoDex(url)
                    adding = false
                    AppText.get(R.string.checklists_added, added.title)
                }
            }
        )
    }
    if (accountCollections) {
        GoDexCollectionsSheet(
            mode = GoDexCollectionsMode.ADD,
            onDismiss = { accountCollections = false },
            onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        )
    }
    if (creating) {
        NewChecklistDialog(
            busy = busy,
            onDismiss = { creating = false },
            onCreate = { title, kind, range ->
                run {
                    com.example.pokemonalertsv2.data.PokemonSpeciesRepository.getInstance(context).syncIfNeeded()
                    val created = checklistRepository.createLocal(title, kind, range)
                    creating = false
                    openId = created.id
                    null
                }
            }
        )
    }
    settingsFor?.let { checklist ->
        ChecklistSettingsSheet(
            checklist = checklist,
            busy = busy,
            onDismiss = { settingsFor = null },
            onChange = { changed ->
                settingsFor = changed
                scope.launch { checklistRepository.update(changed) }
            },
            onRefresh = { run { checklistRepository.refresh(checklist.id); AppText.get(R.string.checklists_refreshed) } },
            onRemove = {
                settingsFor = null
                run { checklistRepository.delete(checklist.id); null }
            }
        )
    }
}

@Composable
private fun ChecklistList(
    goDexConnected: Boolean,
    goDexTitle: String,
    goDexCaught: Int,
    goDexTotal: Int,
    others: List<ChecklistEntity>,
    progress: Map<String, Pair<Int, Int>>,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onSettings: (ChecklistEntity) -> Unit,
    onAddGoDex: () -> Unit,
    /** Signed in to goDex: lists the account's collections to add. */
    onAccountCollections: (() -> Unit)?,
    onNewChecklist: () -> Unit,
    onOpenGoDexSettings: () -> Unit,
    /** Other sources (Dex Tracker), under the app's own checklists. */
    extra: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.navigation_checklists), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (goDexConnected) {
            ChecklistCard(
                title = goDexTitle.ifBlank { stringResource(R.string.settings_godex_collection) },
                subtitle = "${stringResource(R.string.checklists_source_godex)} · ${kindLabel(ChecklistKind.HUNDO)}",
                caught = goDexCaught,
                total = goDexTotal,
                onOpen = { onOpen(ChecklistDeepLink.GODEX) },
                onSettings = onOpenGoDexSettings
            )
        }
        others.forEach { checklist ->
            val (needed, total) = progress[checklist.id] ?: (0 to 0)
            ChecklistCard(
                title = checklist.title,
                subtitle = "${stringResource(if (checklist.source == ChecklistEntity.SOURCE_LOCAL) R.string.checklists_source_local else R.string.checklists_source_godex)} · ${kindLabel(ChecklistKind.fromName(checklist.kind))}" +
                    if (checklist.useForAlerts) " · ${stringResource(R.string.checklists_badges_alerts)}" else "",
                caught = total - needed,
                total = total,
                onOpen = { onOpen(checklist.id) },
                onSettings = { onSettings(checklist) }
            )
        }
        if (!goDexConnected && others.isEmpty()) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.checklists_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.checklists_empty_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenGoDexSettings) { Text(stringResource(R.string.checklists_connect_godex)) }
                }
            }
        }
        Button(onClick = onNewChecklist, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.checklists_new), modifier = Modifier.padding(start = 8.dp))
        }
        if (onAccountCollections != null) {
            OutlinedButton(onClick = onAccountCollections, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.godex_collections_title), modifier = Modifier.padding(start = 8.dp))
            }
        }
        OutlinedButton(onClick = onAddGoDex, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.checklists_add_godex), modifier = Modifier.padding(start = 8.dp))
        }
        extra()
        if (!goDexConnected && others.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = onOpenGoDexSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.checklists_connect_godex))
            }
        }
    }
}

@Composable
internal fun kindLabel(kind: ChecklistKind): String = stringResource(
    when (kind) {
        ChecklistKind.HUNDO -> R.string.checklists_kind_hundo
        ChecklistKind.SHINY -> R.string.checklists_kind_shiny
        ChecklistKind.XXL -> R.string.checklists_kind_xxl
        ChecklistKind.XXS -> R.string.checklists_kind_xxs
        ChecklistKind.COSTUME -> R.string.checklists_kind_costume
        ChecklistKind.LUCKY -> R.string.checklists_kind_lucky
        ChecklistKind.SHADOW -> R.string.checklists_kind_shadow
        ChecklistKind.DYNAMAX -> R.string.checklists_kind_dynamax
        ChecklistKind.ANY -> R.string.checklists_kind_any
    }
)

/** The GoDex Hundo collection: editable when signed in, marks go back to goDex. */
@Composable
private fun GoDexHundoEditor(goDex: GoDexRepository, entries: List<GoDexEntryEntity>, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val config by goDex.config.collectAsStateWithLifecycle()
    val pendingKeys by goDex.pendingEntryKeys.collectAsStateWithLifecycle()
    val sync by goDex.syncUiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { goDex.refreshForPageEntry() } }
    EditorFrame(title.ifBlank { stringResource(R.string.settings_godex_collection) }, entries, onBack) { families, types ->
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
            onSetCaught = { keys, caught -> goDex.setCaught(keys, caught) },
            onUndo = { goDex.undo(it) },
            onSignIn = { context.startActivity(GoDexLoginActivity.createIntent(context)) }
        )
    }
}

/**
 * A goDex collection (read-only when added by link; marked here and written back when it is the
 * signed-in account's own) or a checklist made in the app (marked here, Pokémon added and
 * removed here).
 */
@Composable
private fun OtherChecklistEditor(repository: ChecklistRepository, checklist: ChecklistEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by remember(checklist.id) { repository.entries(checklist.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val entries = remember(rows) { rows.map { it.asGoDexEntry() } }
    val local = checklist.source == ChecklistEntity.SOURCE_LOCAL
    val account = checklist.isGoDexAccount
    val goDex = remember(context) { GoDexRepository.getInstance(context) }
    val sync by goDex.syncUiState.collectAsStateWithLifecycle()
    val pendingByChecklist by repository.pendingEntryKeys.collectAsStateWithLifecycle(initialValue = emptyMap())
    val pending = pendingByChecklist[checklist.id].orEmpty()
    var addingSpecies by rememberSaveable { mutableStateOf(false) }
    EditorFrame(
        checklist.title,
        entries,
        onBack,
        actions = {
            if (local) {
                IconButton(onClick = { addingSpecies = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.checklists_add_pokemon))
                }
            }
        }
    ) { families, types ->
        GoDexChecklistEditor(
            entries = entries,
            pendingEntryKeys = pending,
            canEdit = local || account,
            sessionState = if (account) sync.sessionState else GoDexSessionState.NONE,
            isSyncing = false,
            syncError = if (account) sync.errorMessage else null,
            pendingCount = pending.size,
            families = families,
            typesByDexAndRegion = types,
            onSetCaught = { keys, caught ->
                if (account) repository.setGoDexCaught(checklist.id, keys, caught) else repository.setLocalCaught(checklist.id, keys, caught)
            },
            onUndo = { if (account) repository.undoGoDex(checklist.id, it) else repository.undoLocal(checklist.id, it) },
            onSignIn = if (account) ({ context.startActivity(GoDexLoginActivity.createIntent(context)) }) else null,
            onRemove = if (local) ({ keys -> scope.launch { repository.removeEntries(checklist.id, keys) } }) else null
        )
    }
    if (addingSpecies) {
        AddSpeciesDialog(
            present = remember(entries) { entries.mapTo(HashSet()) { it.pokedexId } },
            onDismiss = { addingSpecies = false },
            onAdd = { dex ->
                addingSpecies = false
                scope.launch { repository.addSpecies(checklist.id, dex) }
            }
        )
    }
}

/** Back arrow and title over an editor, plus the families and types its filters need. */
@Composable
private fun EditorFrame(
    title: String,
    entries: List<GoDexEntryEntity>,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (families: Map<Int, Int>, types: Map<Pair<Int, String?>, List<String>>) -> Unit
) {
    val context = LocalContext.current
    val families = remember(context) { evolutionFamilies(GoDexRepository.getInstance(context).evolutionEdges) }
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.catchroutes_back))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        content(families, types)
    }
}

@Composable
private fun ChecklistCard(title: String, subtitle: String, caught: Int, total: Int, onOpen: () -> Unit, onSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.checklists_open), onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 18.dp, top = 10.dp, end = 6.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "$subtitle · ${stringResource(R.string.settings_x_caught_out_of_x, caught, total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else caught.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.checklists_settings, title))
            }
        }
    }
}
