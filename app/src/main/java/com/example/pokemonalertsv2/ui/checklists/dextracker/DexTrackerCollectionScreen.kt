package com.example.pokemonalertsv2.ui.checklists.dextracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.dextracker.DexTrackerAccount
import com.example.pokemonalertsv2.data.dextracker.DexTrackerAction
import com.example.pokemonalertsv2.data.dextracker.DexTrackerCatalog
import com.example.pokemonalertsv2.data.dextracker.DexTrackerCollection
import com.example.pokemonalertsv2.data.dextracker.DexTrackerDomain
import com.example.pokemonalertsv2.data.dextracker.DexTrackerEntry
import com.example.pokemonalertsv2.data.dextracker.DexTrackerEvent
import com.example.pokemonalertsv2.data.dextracker.DexTrackerFilter
import com.example.pokemonalertsv2.data.dextracker.DexTrackerFilterKind
import com.example.pokemonalertsv2.data.dextracker.DexTrackerRepository
import com.example.pokemonalertsv2.data.dextracker.DexTrackerSource
import com.example.pokemonalertsv2.data.dextracker.DexTrackerStage
import com.example.pokemonalertsv2.data.dextracker.DexTrackerUndo
import com.example.pokemonalertsv2.data.gametext.GameNames
import com.example.pokemonalertsv2.util.AppText
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private enum class Sort { ORIGINAL, MISSING, ALMOST, NAME }
private enum class Section { TRADE, CATCH, DONE }

private fun sectionOf(entry: DexTrackerEntry): Section {
    val p = DexTrackerDomain.progress(entry)
    return if (p.complete) Section.DONE else if (p.tradable > 0) Section.TRADE else Section.CATCH
}

/**
 * One Dex Tracker collection, laid out like the web app: the overview with its meter and the
 * three totals that filter, search, sort, filter chips and grouping, and a card per entry whose
 * stage tiles tick a stage the way the web list does. Tapping a card opens its sheet: stages,
 * main-account and spare counters, trades and its last events. Every change offers Undo.
 */
@Composable
internal fun DexTrackerCollectionScreen(collection: DexTrackerCollection, onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val repository = remember(context) { DexTrackerRepository.getInstance(context) }
    val catalog = remember(context) { DexTrackerCatalog.get(context) }
    val palette = dexTrackerPalette()
    val uid by repository.uid.collectAsStateWithLifecycle()
    val user = uid
    if (user == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var loadError by remember { mutableStateOf<String?>(null) }
    val entries by remember(user, collection.id) {
        repository.entries(user, collection.id).catch { loadError = it.message }
    }.collectAsStateWithLifecycle(initialValue = null)
    val accounts by remember(user) { repository.accounts(user).catch { } }.collectAsStateWithLifecycle(initialValue = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(Sort.ORIGINAL.name) }
    var grouped by rememberSaveable { mutableStateOf(false) }
    var filterKind by rememberSaveable { mutableStateOf(DexTrackerFilterKind.MISSING.name) }
    var filterAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var openEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickPoolFor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val filter = DexTrackerFilter(DexTrackerFilterKind.valueOf(filterKind), filterAccount)
    val sort = Sort.valueOf(sortName)
    val editable = !collection.archived

    fun name(id: Int): String = displayName(catalog, id)

    fun run(entryId: String, action: DexTrackerAction) {
        val undo: DexTrackerUndo = try {
            repository.apply(collection.id, entryId, action)
        } catch (error: Exception) {
            scope.launch { snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName) }
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val text = undo.event?.let { eventText(it, accounts, ::name) } ?: AppText.get(R.string.dextracker_saved)
            val result = snackbarHostState.showSnackbar(text, actionLabel = AppText.get(R.string.checklists_undo), withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                runCatching { repository.undo(undo) }.onFailure { snackbarHostState.showSnackbar(it.message ?: "") }
            }
        }
    }

    fun tapStage(entry: DexTrackerEntry, stage: Int) {
        val action = DexTrackerDomain.stageTap(entry, stage)
        if (action == null) pickPoolFor = entry.id to stage else run(entry.id, action)
    }

    val all = entries.orEmpty()
    val totals = DexTrackerDomain.totals(all)
    val needle = query.trim().lowercase()
    val visible = all
        .filter { DexTrackerDomain.matchesFilter(it, filter) }
        .filter { entry ->
            needle.isEmpty() || (listOf(entry.name, entry.label) + entry.targets.map(::name)).joinToString(" ").lowercase().contains(needle)
        }
        .let { list ->
            fun p(e: DexTrackerEntry) = DexTrackerDomain.progress(e)
            when (sort) {
                Sort.NAME -> list.sortedBy { it.name }
                Sort.MISSING -> list.sortedWith(compareByDescending<DexTrackerEntry> { p(it).missing }.thenByDescending { p(it).toCatch }.thenBy { it.order })
                Sort.ALMOST -> list.sortedWith(compareBy<DexTrackerEntry> { p(it).complete }.thenBy { p(it).missing }.thenBy { p(it).toCatch }.thenBy { it.order })
                Sort.ORIGINAL -> list.sortedBy { it.order }
            }
        }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.catchroutes_back)) }
                Text(collection.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.checklists_settings, collection.title)) }
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "overview") {
                    Overview(totals, all.size, filter.kind, palette) { kind ->
                        filterKind = kind.name
                        filterAccount = null
                    }
                }
                item(key = "toolbar") {
                    Toolbar(
                        query = query, onQuery = { query = it },
                        sort = sort, onSort = { sortName = it.name },
                        grouped = grouped, onGrouped = { grouped = it },
                        filter = filter, accounts = accounts, entries = all, palette = palette,
                        onFilter = {
                            filterKind = it.kind.name
                            filterAccount = it.account
                        }
                    )
                }
                if (collection.archived) item(key = "archived") { Text(stringResource(R.string.dextracker_archived), color = palette.muted, style = MaterialTheme.typography.bodySmall) }
                when {
                    entries == null -> item(key = "loading") {
                        Text(loadError ?: stringResource(R.string.dextracker_loading), color = palette.muted, modifier = Modifier.padding(24.dp))
                    }
                    visible.isEmpty() -> item(key = "empty") { EmptyState(all, filter, palette) }
                    grouped -> Section.entries.forEach { section ->
                        val items = visible.filter { sectionOf(it) == section }
                        if (items.isEmpty()) return@forEach
                        item(key = "head-$section") {
                            Text(
                                "${stringResource(sectionLabel(section))} · ${items.size}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.muted,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(items, key = { it.id }) { entry ->
                            EntryCard(entry, accounts, catalog, palette, editable, onStage = { tapStage(entry, it) }, onOpen = { openEntryId = entry.id })
                        }
                    }
                    else -> items(visible, key = { it.id }) { entry ->
                        EntryCard(entry, accounts, catalog, palette, editable, onStage = { tapStage(entry, it) }, onOpen = { openEntryId = entry.id })
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    all.firstOrNull { it.id == openEntryId }?.let { entry ->
        val history by remember(user, collection.id, entry.id) {
            repository.events(user, collection.id, entry.id).catch { }
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        EntrySheet(
            entry = entry,
            accounts = accounts,
            history = history,
            catalog = catalog,
            palette = palette,
            editable = editable,
            onDismiss = { openEntryId = null },
            onStage = { tapStage(entry, it) },
            onAction = { run(entry.id, it) },
            eventText = { eventText(it, accounts, ::name) }
        )
    }
    pickPoolFor?.let { (entryId, stage) ->
        val entry = all.firstOrNull { it.id == entryId }
        if (entry == null) {
            pickPoolFor = null
        } else {
            AlertDialog(
                onDismissRequest = { pickPoolFor = null },
                title = { Text(stringResource(R.string.dextracker_pick_pool, name(stage))) },
                text = {
                    Column {
                        DexTrackerDomain.sparePools(entry).forEach { pool ->
                            TextButton(onClick = {
                                pickPoolFor = null
                                run(entry.id, DexTrackerAction.Assign(stage, DexTrackerSource.Spare(pool)))
                            }) { Text("${poolName(accounts, pool)} · ${DexTrackerDomain.poolCount(entry, pool)}") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pickPoolFor = null }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
            )
        }
    }
}

private fun sectionLabel(section: Section) = when (section) {
    Section.TRADE -> R.string.dextracker_filter_trades
    Section.CATCH -> R.string.dextracker_section_catch
    Section.DONE -> R.string.dextracker_filter_complete
}

/** The catalog name, in the app's language for a base species. */
internal fun displayName(catalog: DexTrackerCatalog, id: Int): String {
    val pokemon = catalog.byId[id] ?: return "#$id"
    return if (pokemon.id == pokemon.speciesId) GameNames.pokemon(pokemon.name, pokemon.speciesId) ?: pokemon.name else pokemon.name
}

internal fun poolName(accounts: List<DexTrackerAccount>, pool: String?): String =
    if (pool == null) AppText.get(R.string.dextracker_no_account) else accounts.firstOrNull { it.id == pool }?.name ?: AppText.get(R.string.dextracker_alt_account)

/** One activity event in words, as the web app's eventText. */
internal fun eventText(event: DexTrackerEvent, accounts: List<DexTrackerAccount>, name: (Int) -> String): String {
    val pokemon = name(event.pokemonId)
    val amount = kotlin.math.abs(event.count)
    val times = if (amount > 1) " ×$amount" else ""
    val pool = event.account?.takeIf { accounts.isNotEmpty() }?.let { poolName(accounts, it.account) }
    return when (event.kind) {
        "caught" -> AppText.get(R.string.dextracker_event_caught, pokemon) + times
        "traded" -> if (pool != null) AppText.get(R.string.dextracker_event_traded_from, pokemon + times, pool) else AppText.get(R.string.dextracker_event_traded, pokemon) + times
        "assigned" -> AppText.get(R.string.dextracker_event_assigned, pokemon)
        "removed" -> AppText.get(R.string.dextracker_event_removed, pokemon) + times
        "spare" -> AppText.get(R.string.dextracker_event_spare, pokemon, (if (event.count > 0) "+" else "−") + amount) + (pool?.let { " · $it" } ?: "")
        "moved" -> AppText.get(R.string.dextracker_event_moved, pokemon) + (pool?.let { " → $it" } ?: "")
        else -> AppText.get(R.string.dextracker_event_edited, event.entryName)
    }
}

@Composable
private fun Overview(
    totals: com.example.pokemonalertsv2.data.dextracker.DexTrackerTotals,
    pokemonCount: Int,
    filter: DexTrackerFilterKind,
    palette: DexTrackerPalette,
    onFilter: (DexTrackerFilterKind) -> Unit
) {
    Surface(shape = RoundedCornerShape(18.dp), color = palette.surface, border = BorderStroke(1.dp, palette.line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (totals.missing == 0 && totals.target > 0) {
                        Text(stringResource(R.string.dextracker_all_collected), color = palette.done, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Swatch(palette.done)
                            Text("${totals.credited}", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("/${totals.target} ${stringResource(R.string.dextracker_on_main)}", color = palette.muted, fontSize = 13.sp)
                        }
                        if (totals.tradable > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Swatch(palette.trade)
                                Text("${totals.credited + totals.tradable}", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("/${totals.target} ${stringResource(R.string.dextracker_with_trades)}", color = palette.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                val percent = if (totals.target == 0) 0 else totals.credited * 100 / totals.target
                Text("$percent%", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            SegmentBar(totals.credited, totals.tradable, totals.target, palette, height = 10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    value = totals.credited, label = stringResource(R.string.dextracker_stat_collected),
                    note = stringResource(R.string.dextracker_stat_done_note, totals.complete, pokemonCount),
                    color = palette.done, selected = filter == DexTrackerFilterKind.COMPLETE, palette = palette,
                    onClick = { onFilter(DexTrackerFilterKind.COMPLETE) }, modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = totals.tradable, label = stringResource(R.string.dextracker_filter_trades),
                    note = stringResource(R.string.dextracker_stat_trade_note),
                    color = palette.trade, selected = filter == DexTrackerFilterKind.TRADES, palette = palette,
                    onClick = { onFilter(DexTrackerFilterKind.TRADES) }, modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = totals.toCatch, label = stringResource(R.string.dextracker_filter_catch),
                    note = stringResource(R.string.dextracker_stat_catch_note),
                    color = palette.lineStrong, selected = filter == DexTrackerFilterKind.CATCH, palette = palette,
                    onClick = { onFilter(DexTrackerFilterKind.CATCH) }, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, note: String, color: Color, selected: Boolean, palette: DexTrackerPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accentSoft else palette.surface2)
            .border(1.dp, if (selected) palette.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Swatch(color)
            Text("$value", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text(label, color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(note, color = palette.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Toolbar(
    query: String,
    onQuery: (String) -> Unit,
    sort: Sort,
    onSort: (Sort) -> Unit,
    grouped: Boolean,
    onGrouped: (Boolean) -> Unit,
    filter: DexTrackerFilter,
    accounts: List<DexTrackerAccount>,
    entries: List<DexTrackerEntry>,
    palette: DexTrackerPalette,
    onFilter: (DexTrackerFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.dextracker_search)) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var open by remember { mutableStateOf(false) }
            Box {
                Pill(text = stringResource(sortLabel(sort)), selected = false, palette = palette, trailing = { Icon(Icons.Filled.ArrowDropDown, null, tint = palette.muted, modifier = Modifier.size(18.dp)) }) { open = true }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    Sort.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(stringResource(sortLabel(option))) }, onClick = { onSort(option); open = false })
                    }
                }
            }
            Pill(text = stringResource(R.string.dextracker_group), selected = grouped, palette = palette) { onGrouped(!grouped) }
        }
        val filters = listOf(
            DexTrackerFilter(DexTrackerFilterKind.MISSING), DexTrackerFilter(DexTrackerFilterKind.CATCH), DexTrackerFilter(DexTrackerFilterKind.TRADES)
        ) + accounts.map { DexTrackerFilter(DexTrackerFilterKind.ALT, it.id) } + listOf(
            DexTrackerFilter(DexTrackerFilterKind.EVOLUTIONS), DexTrackerFilter(DexTrackerFilterKind.COMPLETE), DexTrackerFilter(DexTrackerFilterKind.ALL)
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { option ->
                val label = if (option.kind == DexTrackerFilterKind.ALT) "⇄ ${accounts.firstOrNull { it.id == option.account }?.name.orEmpty()}" else stringResource(filterLabel(option.kind))
                val count = entries.count { DexTrackerDomain.matchesFilter(it, option) }
                Pill(text = "$label  $count", selected = option == filter, palette = palette) { onFilter(option) }
            }
        }
    }
}

private fun sortLabel(sort: Sort) = when (sort) {
    Sort.ORIGINAL -> R.string.dextracker_sort_original
    Sort.MISSING -> R.string.dextracker_sort_missing
    Sort.ALMOST -> R.string.dextracker_sort_almost
    Sort.NAME -> R.string.dextracker_sort_name
}

private fun filterLabel(kind: DexTrackerFilterKind) = when (kind) {
    DexTrackerFilterKind.MISSING -> R.string.dextracker_filter_missing
    DexTrackerFilterKind.CATCH -> R.string.dextracker_filter_catch
    DexTrackerFilterKind.TRADES -> R.string.dextracker_filter_trades
    DexTrackerFilterKind.EVOLUTIONS -> R.string.dextracker_filter_evolutions
    DexTrackerFilterKind.COMPLETE -> R.string.dextracker_filter_complete
    DexTrackerFilterKind.ALL, DexTrackerFilterKind.ALT -> R.string.dextracker_filter_all
}

/** A rounded toggle button as the web app's filter and toolbar buttons. */
@Composable
private fun Pill(text: String, selected: Boolean, palette: DexTrackerPalette, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else palette.surface)
            .border(1.dp, if (selected) palette.accent else palette.line, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = if (selected) palette.onAccent else palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        trailing()
    }
}

@Composable
private fun EmptyState(all: List<DexTrackerEntry>, filter: DexTrackerFilter, palette: DexTrackerPalette) {
    val done = all.isNotEmpty() && filter.kind != DexTrackerFilterKind.ALL && all.all { DexTrackerDomain.progress(it).complete }
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (done) "✨" else "🔍", fontSize = 28.sp)
        Text(
            stringResource(if (done) R.string.dextracker_all_collected else R.string.dextracker_nothing_here),
            color = palette.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EntryCard(
    entry: DexTrackerEntry,
    accounts: List<DexTrackerAccount>,
    catalog: DexTrackerCatalog,
    palette: DexTrackerPalette,
    editable: Boolean,
    onStage: (Int) -> Unit,
    onOpen: () -> Unit
) {
    val p = DexTrackerDomain.progress(entry)
    val type = typeColor(catalog.primaryType(entry.pokemonId)) ?: palette.lineStrong
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            // The web card's wash of the Pokémon's type, gone once it is complete.
            .then(if (p.complete) Modifier else Modifier.background(Brush.verticalGradient(0f to type.copy(alpha = 0.10f), 0.6f to Color.Transparent)))
            .border(1.dp, if (p.complete) palette.doneLine else palette.line, shape)
            .clickable(onClickLabel = stringResource(R.string.checklists_open), onClick = onOpen)
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(type.copy(alpha = 0.8f)))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(displayName(catalog, entry.pokemonId).takeIf { entry.name == catalog.byId[entry.pokemonId]?.name } ?: entry.name,
                        color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.label.isNotBlank()) Text(entry.label, color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CountPill(p.owned, entry.target, p.tradable, p.complete, palette)
            }
            if (!p.complete) SegmentBar(p.owned, p.tradable, entry.target, palette, height = 5.dp)
            Stages(entry, catalog, palette, editable, onStage)
            if (p.tradable > 0 || entry.unassigned > 0) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.tradable > 0) {
                        if (accounts.isEmpty()) Chip("⇄ ${stringResource(R.string.dextracker_trade_chip, p.tradable)}", palette.trade, palette.tradeSoft, onOpen)
                        else DexTrackerDomain.sparePools(entry).filter { DexTrackerDomain.usefulFrom(entry, it) > 0 }.forEach { pool ->
                            Chip("⇄ ${DexTrackerDomain.usefulFrom(entry, pool)} · ${poolName(accounts, pool)}", palette.trade, palette.tradeSoft, onOpen)
                        }
                    }
                    if (entry.unassigned > 0) Chip(stringResource(R.string.dextracker_unassigned_chip, entry.unassigned), palette.accent, palette.accentSoft, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, background: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The evolution stages as tiles, four to a row, in the web app's four states. */
@Composable
private fun Stages(entry: DexTrackerEntry, catalog: DexTrackerCatalog, palette: DexTrackerPalette, editable: Boolean, onStage: (Int) -> Unit, large: Boolean = false) {
    if (entry.targets.isEmpty()) {
        Text(stringResource(R.string.dextracker_no_stages), color = palette.muted, fontSize = 12.sp)
        return
    }
    val status = DexTrackerDomain.stageStatus(entry)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.targets.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { id ->
                    StageTile(id, entry.owned[id.toString()] ?: 0, status[id] ?: DexTrackerStage.MISSING, catalog, palette, editable, large, Modifier.weight(1f)) { onStage(id) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StageTile(
    id: Int,
    count: Int,
    state: DexTrackerStage,
    catalog: DexTrackerCatalog,
    palette: DexTrackerPalette,
    editable: Boolean,
    large: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val name = displayName(catalog, id)
    val stateText = stringResource(
        when (state) {
            DexTrackerStage.OWNED -> R.string.dextracker_stage_owned
            DexTrackerStage.UNASSIGNED -> R.string.dextracker_stage_unassigned
            DexTrackerStage.TRADE -> R.string.dextracker_stage_trade
            DexTrackerStage.MISSING -> R.string.dextracker_stage_missing
        }
    )
    Box(
        modifier
            .clip(shape)
            .then(
                if (state == DexTrackerStage.OWNED) Modifier.background(palette.doneSoft).border(1.5.dp, palette.doneLine, shape)
                else Modifier.dashedBorder(if (state == DexTrackerStage.TRADE) palette.trade else palette.lineStrong, 14.dp)
            )
            .clickable(enabled = editable, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$name, $stateText" }
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = DexTrackerRepository.spriteUrl(id),
                contentDescription = null,
                colorFilter = if (state == DexTrackerStage.OWNED) null else Grayscale,
                modifier = Modifier.size(if (large) 64.dp else 52.dp).alpha(if (state == DexTrackerStage.OWNED) 1f else 0.5f)
            )
            Text(name, color = if (state == DexTrackerStage.OWNED) palette.text else palette.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val corner = Modifier.align(Alignment.TopEnd)
        when {
            count > 1 -> CornerBadge(palette.done, corner, size = 22.dp) { Text("×$count", color = palette.onDone, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            count == 1 -> CornerBadge(palette.done, corner) { Icon(Icons.Filled.Check, null, tint = palette.onDone, modifier = Modifier.size(13.dp)) }
            state == DexTrackerStage.TRADE -> CornerBadge(palette.trade, corner) { Text("⇄", color = palette.surface, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            state == DexTrackerStage.UNASSIGNED -> CornerBadge(palette.accent, corner.padding(4.dp), size = 10.dp)
        }
    }
}

/** An entry's sheet: stages, the main account and spare counters, trading over, recent events. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    entry: DexTrackerEntry,
    accounts: List<DexTrackerAccount>,
    history: List<DexTrackerEvent>,
    catalog: DexTrackerCatalog,
    palette: DexTrackerPalette,
    editable: Boolean,
    onDismiss: () -> Unit,
    onStage: (Int) -> Unit,
    onAction: (DexTrackerAction) -> Unit,
    eventText: (DexTrackerEvent) -> String
) {
    val p = DexTrackerDomain.progress(entry)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = palette.surface) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(model = DexTrackerRepository.spriteUrl(entry.pokemonId), contentDescription = null, modifier = Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (entry.label.isNotBlank()) Text(entry.label, color = palette.muted, fontSize = 13.sp)
                    }
                    CountPill(p.owned, entry.target, p.tradable, p.complete, palette)
                }
            }
            item { Stages(entry, catalog, palette, editable, onStage, large = true) }
            item {
                CounterRow(
                    title = stringResource(R.string.dextracker_main_account),
                    note = stringResource(R.string.dextracker_unassigned_chip, entry.unassigned),
                    value = DexTrackerDomain.ownedCount(entry),
                    palette = palette,
                    canMinus = editable && DexTrackerDomain.canRemoveFromMain(entry),
                    canPlus = editable,
                    onMinus = { onAction(DexTrackerAction.Add(main = true, delta = -1)) },
                    onPlus = { onAction(DexTrackerAction.Add(main = true, delta = 1)) }
                )
            }
            item { Text(stringResource(R.string.dextracker_spares), color = palette.text, fontWeight = FontWeight.SemiBold) }
            val pools: List<String?> = listOf<String?>(null) + accounts.map { it.id }
            items(pools.size) { index ->
                val pool = pools[index]
                val count = DexTrackerDomain.poolCount(entry, pool)
                CounterRow(
                    title = poolName(accounts, pool),
                    note = null,
                    value = count,
                    palette = palette,
                    canMinus = editable && count > 0,
                    canPlus = editable,
                    onMinus = { onAction(DexTrackerAction.Add(main = false, delta = -1, account = pool)) },
                    onPlus = { onAction(DexTrackerAction.Add(main = false, delta = 1, account = pool)) },
                    trailing = if (editable && count > 0 && p.missing > 0) ({
                        TextButton(onClick = { onAction(DexTrackerAction.Trade(1, pool)) }) {
                            Text(stringResource(R.string.dextracker_trade_one), color = palette.trade)
                        }
                    }) else null
                )
            }
            if (history.isNotEmpty()) {
                item { Text(stringResource(R.string.dextracker_recent), color = palette.text, fontWeight = FontWeight.SemiBold) }
                items(history, key = { it.id }) { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(event.at)),
                            color = palette.muted, fontSize = 12.sp, modifier = Modifier.width(52.dp)
                        )
                        Text(eventText(event), color = palette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterRow(
    title: String,
    note: String?,
    value: Int,
    palette: DexTrackerPalette,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, color = palette.muted, fontSize = 12.sp)
        }
        trailing?.invoke()
        StepButton("−", canMinus, palette, onMinus)
        Text("$value", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        StepButton("+", canPlus, palette, onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, palette: DexTrackerPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center
    ) { Text(symbol, color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}
