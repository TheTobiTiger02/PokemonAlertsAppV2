package com.example.pokemonalertsv2.ui.checklists

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.database.GoDexMarkUndo
import com.example.pokemonalertsv2.data.gametext.GameNames
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.ui.motion.AppMotion
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.theme.typeLabel
import com.example.pokemonalertsv2.util.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Form and gender under the name, title-cased. A form the name already states ("Alolan Raichu",
 * "Raichu (Alola)") is left out rather than repeated in lowercase underneath.
 */
internal fun goDexEntryVariantLabel(entry: GoDexEntryEntity): String = buildList {
    entry.formSlug?.replace('_', ' ')
        ?.takeUnless { entry.displayName.contains(it, ignoreCase = true) }
        ?.let { add(it.split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }) }
    entry.gender.takeUnless { it == "none" }?.let { add(it.replaceFirstChar(Char::uppercaseChar)) }
}.joinToString(" • ")

/** The variant line, never blank: a form named only in the title still reads as one. */
internal fun goDexEntryVariantLine(entry: GoDexEntryEntity): String =
    goDexEntryVariantLabel(entry).ifBlank { if (entry.formSlug == null) AppText.get(R.string.settings_base_form) else AppText.get(R.string.settings_alternate_form) }

/** The entry's name in the app language (German species names when the app is in German). */
internal fun goDexEntryLocalName(entry: GoDexEntryEntity): String =
    GameNames.pokemon(entry.displayName, entry.pokedexId) ?: entry.displayName

private const val SEARCH_DEBOUNCE_MILLIS = 200L

/**
 * The goDex checklist editor: a tap marks an entry caught or needed at once (with Undo), a
 * long press starts selecting several for one mark. Marks go to goDex after the undo window.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GoDexChecklistEditor(
    entries: List<GoDexEntryEntity>,
    pendingEntryKeys: Set<String>,
    canEdit: Boolean,
    sessionState: GoDexSessionState,
    isSyncing: Boolean,
    syncError: String?,
    pendingCount: Int,
    families: Map<Int, Int>,
    typesByDexAndRegion: Map<Pair<Int, String?>, List<String>>,
    onSetCaught: suspend (keys: Set<String>, caught: Boolean) -> List<GoDexMarkUndo>,
    onUndo: suspend (List<GoDexMarkUndo>) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query.isEmpty()) debouncedQuery = query else {
            delay(SEARCH_DEBOUNCE_MILLIS)
            debouncedQuery = query
        }
    }
    var statusName by rememberSaveable { mutableStateOf(ChecklistStatus.NEEDED.name) }
    var sortName by rememberSaveable { mutableStateOf(ChecklistSort.DEX.name) }
    var generation by rememberSaveable { mutableStateOf<Int?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var formsOnly by rememberSaveable { mutableStateOf(false) }
    val filter = ChecklistFilter(
        status = ChecklistStatus.valueOf(statusName),
        query = debouncedQuery,
        generation = generation,
        type = type,
        formsOnly = formsOnly,
        sort = ChecklistSort.valueOf(sortName)
    )
    val typesOf: (GoDexEntryEntity) -> List<String> = remember(typesByDexAndRegion) {
        { entry ->
            typesByDexAndRegion[entry.pokedexId to regionalFormOf(entry)]
                ?: typesByDexAndRegion[entry.pokedexId to null]
                ?: emptyList()
        }
    }
    val shown = remember(entries, filter, typesOf, families) {
        filterChecklist(entries, filter, ::goDexEntryLocalName, typesOf, families)
    }
    val neededCount = remember(entries) { entries.count(GoDexEntryEntity::needed) }
    val caughtCount = entries.size - neededCount
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selecting = selection.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val resources = LocalResources.current

    fun mark(keys: Set<String>, caught: Boolean, label: String) {
        if (keys.isEmpty()) return
        scope.launch {
            val changes = try {
                onSetCaught(keys, caught)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
                return@launch
            }
            if (changes.isEmpty()) return@launch
            val message = if (changes.size == 1 && label.isNotEmpty()) {
                AppText.get(if (caught) R.string.checklists_marked_caught else R.string.checklists_marked_needed, label)
            } else {
                resources.getQuantityString(if (caught) R.plurals.checklists_marked_many_caught else R.plurals.checklists_marked_many_needed, changes.size, changes.size)
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = AppText.get(R.string.checklists_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onUndo(changes)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            // 96dp keeps three columns on a narrow phone or one set to a larger display size.
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ChecklistProgressCard(neededCount = neededCount, caughtCount = caughtCount, totalCount = entries.size)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                GoDexAccessBanner(canEdit, sessionState, isSyncing, syncError, pendingCount, onSignIn)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_search_name_or_number), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.checklists_clear_search)) } }
                    } else null,
                    singleLine = true
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChecklistStatus.entries.forEach { candidate ->
                            val (label, count) = when (candidate) {
                                ChecklistStatus.NEEDED -> stringResource(R.string.settings_still_needed_2) to neededCount
                                ChecklistStatus.CAUGHT -> stringResource(R.string.hunt_caught) to caughtCount
                                ChecklistStatus.ALL -> stringResource(R.string.alerts_all) to entries.size
                            }
                            FilterChip(
                                selected = filter.status == candidate,
                                onClick = { statusName = candidate.name },
                                label = { Text("$label $count") }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuChip(
                            label = generation?.let { stringResource(R.string.checklists_generation, it) } ?: stringResource(R.string.checklists_generation_any),
                            selected = generation != null,
                            options = listOf<Pair<Int?, String>>(null to stringResource(R.string.checklists_generation_any)) +
                                GENERATION_RANGES.indices.map { (it + 1) to stringResource(R.string.checklists_generation, it + 1) },
                            onSelect = { generation = it }
                        )
                        val types = remember(typesByDexAndRegion) { typesByDexAndRegion.values.flatten().distinct().sorted() }
                        if (types.isNotEmpty()) {
                            MenuChip(
                                label = type?.let(::localTypeLabel) ?: stringResource(R.string.checklists_type_any),
                                selected = type != null,
                                options = listOf<Pair<String?, String>>(null to stringResource(R.string.checklists_type_any)) +
                                    types.map { it to localTypeLabel(it) },
                                onSelect = { type = it }
                            )
                        }
                        FilterChip(
                            selected = formsOnly,
                            onClick = { formsOnly = !formsOnly },
                            label = { Text(stringResource(R.string.checklists_forms_only)) }
                        )
                        MenuChip(
                            label = stringResource(R.string.checklists_sort, sortLabel(filter.sort)),
                            selected = false,
                            options = ChecklistSort.entries.map { it to sortLabel(it) },
                            onSelect = { sortName = it.name }
                        )
                    }
                }
            }
            if (canEdit && !selecting) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.checklists_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChecklistEmptyState(hasQuery = query.isNotBlank() || filter.narrowed, status = filter.status)
                }
            } else {
                items(shown, key = GoDexEntryEntity::entryKey) { entry ->
                    val selected = entry.entryKey in selection
                    ChecklistEntryTile(
                        entry = entry,
                        isPending = entry.entryKey in pendingEntryKeys,
                        canEdit = canEdit,
                        sessionState = sessionState,
                        selected = selected,
                        selecting = selecting,
                        modifier = Modifier.animateItem(),
                        onClick = {
                            when {
                                selecting -> selection = if (selected) selection - entry.entryKey else selection + entry.entryKey
                                else -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    mark(setOf(entry.entryKey), caught = entry.needed, label = goDexEntryLocalName(entry))
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selection = if (selected) selection - entry.entryKey else selection + entry.entryKey
                        }
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            SelectionBar(
                count = selection.size,
                onFamily = { selection = familyKeys(entries, selection, families) },
                onAllShown = { selection = selection + shown.map { it.entryKey } },
                onCaught = {
                    mark(selection, caught = true, label = "")
                    selection = emptySet()
                },
                onNeeded = {
                    mark(selection, caught = false, label = "")
                    selection = emptySet()
                },
                onClear = { selection = emptySet() }
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = if (selecting) 140.dp else 16.dp)
        )
    }
}

private fun localTypeLabel(type: String): String {
    val english = typeLabel(type) ?: type
    return GameNames.type(english) ?: english
}

@Composable
private fun sortLabel(sort: ChecklistSort): String = when (sort) {
    ChecklistSort.DEX -> stringResource(R.string.checklists_sort_dex)
    ChecklistSort.NAME -> stringResource(R.string.checklists_sort_name)
    ChecklistSort.FAMILY -> stringResource(R.string.checklists_sort_family)
}

/** A filter chip that opens a menu of [options]. */
@Composable
private fun <T> MenuChip(label: String, selected: Boolean, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text(label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onSelect(value)
                })
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onFamily: () -> Unit,
    onAllShown: () -> Unit,
    onCaught: () -> Unit,
    onNeeded: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.checklists_clear_selection)) }
                Text(
                    pluralStringResource(R.plurals.checklists_selected_count, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onFamily) { Text(stringResource(R.string.checklists_select_family)) }
                TextButton(onClick = onAllShown) { Text(stringResource(R.string.checklists_select_all)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onNeeded, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.alerts_mark_needed), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onCaught, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.alerts_mark_caught), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun ChecklistProgressCard(neededCount: Int, caughtCount: Int, totalCount: Int) {
    val completion = if (totalCount == 0) 0f else caughtCount.toFloat() / totalCount
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_x_still_needed, neededCount),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.settings_x_caught_out_of_x, caughtCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "${(completion * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            LinearProgressIndicator(
                progress = { completion },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun GoDexAccessBanner(
    canEdit: Boolean,
    sessionState: GoDexSessionState,
    isSyncing: Boolean,
    syncError: String?,
    pendingCount: Int,
    onSignIn: () -> Unit
) {
    val presentation = when {
        sessionState == GoDexSessionState.REAUTH_REQUIRED ->
            Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                if (pendingCount > 0) {
                    "$pendingCount ${if (pendingCount == 1) "change is" else "changes are"} safe on this device. Sign in again from GoDex settings to send ${if (pendingCount == 1) "it" else "them"}."
                } else {
                    "Your GoDex session expired. You can keep updating the list; sign in again from GoDex settings to send changes."
                }
            )
        syncError != null ->
            Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "Couldn't refresh GoDex: $syncError. The last saved checklist is still shown."
            )
        isSyncing ->
            Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                "Refreshing GoDex in the background. Your saved checklist is ready to use."
            )
        !canEdit ->
            Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                "Read-only collection. Sign in from GoDex settings to mark entries caught here."
            )
        pendingCount > 0 ->
            Triple(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                "Sending $pendingCount ${if (pendingCount == 1) "change" else "changes"} to GoDex. Your list is already updated on this device."
            )
        else -> null
    } ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = presentation.first,
        contentColor = presentation.second,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = presentation.third, style = MaterialTheme.typography.bodyMedium)
            if (!canEdit || sessionState == GoDexSessionState.REAUTH_REQUIRED) {
                Button(onClick = onSignIn) {
                    Text(
                        if (sessionState == GoDexSessionState.REAUTH_REQUIRED) "Sign in again to resume sync" else "Sign in for two-way sync"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistEntryTile(
    entry: GoDexEntryEntity,
    isPending: Boolean,
    canEdit: Boolean,
    sessionState: GoDexSessionState,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val caught = !entry.needed
    val name = goDexEntryLocalName(entry)
    val variant = goDexEntryVariantLine(entry)
    val pendingDescription = when {
        !isPending -> ""
        sessionState == GoDexSessionState.REAUTH_REQUIRED -> stringResource(R.string.settings_sync_queued_until_sign_in)
        else -> stringResource(R.string.settings_sync_pending)
    }
    val action = when {
        !canEdit -> stringResource(R.string.checklists_read_only)
        selecting -> stringResource(if (selected) R.string.checklists_tap_to_unselect else R.string.checklists_tap_to_select)
        caught -> stringResource(R.string.checklists_tap_to_mark_needed)
        else -> stringResource(R.string.checklists_tap_to_mark_caught)
    }
    val accessibilityDescription = "$name, Pokédex ${entry.pokedexId}, $variant" +
        (if (caught) stringResource(R.string.settings_caught) else stringResource(R.string.settings_still_needed)) +
        pendingDescription + ". " + action
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            entry.needed -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = AppMotion.springQuick(),
        label = "checklist_tile_color"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = AppMotion.springQuick(),
        label = "checklist_tile_border"
    )
    val artworkAlpha by animateFloatAsState(
        targetValue = if (caught) 0.62f else 1f,
        animationSpec = AppMotion.springQuick(),
        label = "checklist_artwork_alpha"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .heightIn(min = 136.dp)
            .then(if (canEdit) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
                stateDescription = when {
                    selected -> AppText.get(R.string.alerts_selected)
                    isPending -> AppText.get(R.string.alerts_sync_pending)
                    caught -> AppText.get(R.string.hunt_caught)
                    else -> AppText.get(R.string.alerts_needed)
                }
            },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = selectionBorderWidth.takeIf { it > 0.dp }?.let { BorderStroke(it, MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            EntryArtwork(
                entry = entry,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 42.dp)
                    .size(84.dp)
                    .alpha(artworkAlpha)
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            ) {
                Text(
                    text = "#${entry.pokedexId.toString().padStart(4, '0')}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(targetState = caught, transitionSpec = { appFadeThrough() }, label = "checklist_caught_state") { isCaught ->
                        if (isCaught) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(27.dp), tint = Color(0xFF2E7D32))
                        } else {
                            Surface(
                                modifier = Modifier.size(25.dp),
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {}
                        }
                    }
                    if (isPending) {
                        CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = variant,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryArtwork(entry: GoDexEntryEntity, modifier: Modifier = Modifier) {
    var imageFailed by remember(entry.spriteUrl) { mutableStateOf(false) }
    if (entry.spriteUrl.isNullOrBlank() || imageFailed) {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = if (entry.needed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "#${entry.pokedexId.toString().padStart(4, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry.needed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    val context = LocalContext.current
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(entry.spriteUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { imageFailed = true },
            modifier = Modifier.padding(5.dp)
        )
    }
}

@Composable
private fun ChecklistEmptyState(hasQuery: Boolean, status: ChecklistStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (status == ChecklistStatus.NEEDED && !hasQuery) Icons.Filled.CheckCircle else Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = when {
                hasQuery -> stringResource(R.string.settings_no_matching_pok_mon)
                status == ChecklistStatus.NEEDED -> stringResource(R.string.settings_you_caught_everything_in_this_checklist)
                status == ChecklistStatus.CAUGHT -> stringResource(R.string.settings_no_caught_pok_mon_yet)
                else -> stringResource(R.string.settings_no_synced_pok_mon)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
