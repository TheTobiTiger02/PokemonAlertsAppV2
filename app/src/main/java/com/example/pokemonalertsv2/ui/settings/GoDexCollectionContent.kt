package com.example.pokemonalertsv2.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import kotlinx.coroutines.launch
import java.util.Locale

internal enum class GoDexCollectionFilter(val label: String) {
    NEEDED("Still needed"),
    CAUGHT("Caught"),
    ALL("All")
}

internal data class GoDexCollectionSelection(
    val entryKey: String,
    val neededAtSelection: Boolean
)

internal fun goDexEntryVariantLabel(entry: GoDexEntryEntity): String = buildList {
    entry.formSlug?.let { add(it.replace('_', ' ')) }
    entry.gender.takeUnless { it == "none" }?.let(::add)
}.joinToString(" \u2022 ")

internal fun toggleGoDexCollectionSelection(
    current: GoDexCollectionSelection?,
    entry: GoDexEntryEntity
): GoDexCollectionSelection? =
    if (current?.entryKey == entry.entryKey) {
        null
    } else {
        GoDexCollectionSelection(entry.entryKey, entry.needed)
    }

internal fun resolveGoDexCollectionSelection(
    selection: GoDexCollectionSelection?,
    entries: List<GoDexEntryEntity>,
    visibleEntryKeys: Set<String>
): GoDexEntryEntity? {
    selection ?: return null
    val entry = entries.firstOrNull { it.entryKey == selection.entryKey } ?: return null
    return entry.takeIf {
        it.entryKey in visibleEntryKeys && it.needed == selection.neededAtSelection
    }
}

internal fun filterGoDexCollectionEntries(
    entries: List<GoDexEntryEntity>,
    filter: GoDexCollectionFilter,
    query: String
): List<GoDexEntryEntity> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return entries.asSequence()
        .filter { entry ->
            when (filter) {
                GoDexCollectionFilter.NEEDED -> entry.needed
                GoDexCollectionFilter.CAUGHT -> !entry.needed
                GoDexCollectionFilter.ALL -> true
            }
        }
        .filter { entry ->
            normalizedQuery.isEmpty() || listOf(
                entry.displayName,
                entry.pokedexId.toString(),
                entry.pokedexId.toString().padStart(4, '0'),
                entry.formSlug.orEmpty(),
                entry.gender,
                entry.entryKey,
                if (entry.needed) "needed" else "caught"
            ).any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
        }
        .sortedWith(compareBy(GoDexEntryEntity::pokedexId, GoDexEntryEntity::entryKey))
        .toList()
}

@Composable
internal fun GoDexCollectionContent(
    entries: List<GoDexEntryEntity>,
    pendingEntryKeys: Set<String>,
    canEdit: Boolean,
    sessionState: GoDexSessionState,
    isSyncing: Boolean,
    syncError: String?,
    pendingCount: Int,
    onSetCaught: (entryKey: String, caught: Boolean) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(GoDexCollectionFilter.NEEDED.name) }
    val filter = GoDexCollectionFilter.entries.firstOrNull { it.name == filterName }
        ?: GoDexCollectionFilter.NEEDED
    val filteredEntries = remember(entries, filter, query) {
        filterGoDexCollectionEntries(entries, filter, query)
    }
    val neededCount = entries.count(GoDexEntryEntity::needed)
    val caughtCount = entries.size - neededCount
    val completion = if (entries.isEmpty()) 0f else caughtCount.toFloat() / entries.size
    var selection by remember { mutableStateOf<GoDexCollectionSelection?>(null) }
    val selectedEntry = resolveGoDexCollectionSelection(
        selection = selection,
        entries = entries,
        visibleEntryKeys = filteredEntries.mapTo(mutableSetOf(), GoDexEntryEntity::entryKey)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(selectedEntry, selection) {
        if (selection != null && selectedEntry == null) selection = null
    }

    fun confirmSelection(entry: GoDexEntryEntity) {
        val caught = entry.needed
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSetCaught(entry.entryKey, caught)
        selection = null
        scope.launch {
            val queuedSuffix = if (sessionState == GoDexSessionState.REAUTH_REQUIRED) {
                " \u2022 Sign in to sync"
            } else {
                ""
            }
            snackbarHostState.showSnackbar(
                message = if (caught) {
                    "${entry.displayName} saved as caught$queuedSuffix"
                } else {
                    "${entry.displayName} saved as needed$queuedSuffix"
                },
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GoDexCollectionProgressCard(
                    neededCount = neededCount,
                    caughtCount = caughtCount,
                    totalCount = entries.size,
                    completion = completion
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                GoDexCollectionAccessBanner(
                    canEdit = canEdit,
                    sessionState = sessionState,
                    isSyncing = isSyncing,
                    syncError = syncError,
                    pendingCount = pendingCount,
                    onSignIn = onSignIn
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search Pokémon, form, number, or key") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GoDexCollectionFilter.entries.forEach { candidate ->
                        val count = when (candidate) {
                            GoDexCollectionFilter.NEEDED -> neededCount
                            GoDexCollectionFilter.CAUGHT -> caughtCount
                            GoDexCollectionFilter.ALL -> entries.size
                        }
                        FilterChip(
                            selected = filter == candidate,
                            onClick = { filterName = candidate.name },
                            label = { Text("${candidate.label} $count") }
                        )
                    }
                }
            }
            if (filteredEntries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GoDexCollectionEmptyState(
                        hasQuery = query.isNotBlank(),
                        filter = filter
                    )
                }
            } else {
                items(filteredEntries, key = GoDexEntryEntity::entryKey) { entry ->
                    GoDexCollectionEntryTile(
                        entry = entry,
                        isPending = entry.entryKey in pendingEntryKeys,
                        canEdit = canEdit,
                        sessionState = sessionState,
                        selected = selection?.entryKey == entry.entryKey,
                        onSelect = {
                            selection = toggleGoDexCollectionSelection(selection, entry)
                        }
                    )
                }
            }
        }
        selectedEntry?.let { entry ->
            GoDexCollectionConfirmationBar(
                entry = entry,
                isPending = entry.entryKey in pendingEntryKeys,
                onConfirm = { confirmSelection(entry) },
                onCancel = { selection = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 92.dp)
        )
    }
}

@Composable
private fun GoDexCollectionProgressCard(
    neededCount: Int,
    caughtCount: Int,
    totalCount: Int,
    completion: Float
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "$neededCount still needed",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$caughtCount caught out of $totalCount",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun GoDexCollectionAccessBanner(
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
                        if (sessionState == GoDexSessionState.REAUTH_REQUIRED) {
                            "Sign in again to resume sync"
                        } else {
                            "Sign in for two-way sync"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoDexCollectionEntryTile(
    entry: GoDexEntryEntity,
    isPending: Boolean,
    canEdit: Boolean,
    sessionState: GoDexSessionState,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val caught = !entry.needed
    val variant = goDexEntryVariantLabel(entry).ifBlank { "Base form" }
    val actionDescription = if (caught) {
        "Mark ${entry.displayName} as needed"
    } else {
        "Mark ${entry.displayName} as caught"
    }
    val pendingDescription = when {
        !isPending -> ""
        sessionState == GoDexSessionState.REAUTH_REQUIRED -> ", sync queued until sign-in"
        else -> ", sync pending"
    }
    val accessibilityDescription = buildString {
        append(entry.displayName)
        append(", Pok\u00e9dex ")
        append(entry.pokedexId)
        append(", ")
        append(variant)
        append(if (caught) ", caught" else ", still needed")
        append(pendingDescription)
        if (canEdit) {
            append(". Select to ")
            append(actionDescription)
        } else {
            append(". Read-only")
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .heightIn(min = 136.dp)
            .then(if (canEdit) Modifier.clickable(onClick = onSelect) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
                stateDescription = when {
                    selected -> "Selected"
                    isPending -> "Sync pending"
                    caught -> "Caught"
                    else -> "Needed"
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                entry.needed -> {
                MaterialTheme.colorScheme.surfaceContainerHigh
                }
                else -> {
                MaterialTheme.colorScheme.surfaceContainer
                }
            }
        ),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoDexEntryArtwork(
                entry = entry,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 42.dp)
                    .size(84.dp)
                    .alpha(if (caught) 0.62f else 1f)
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp),
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
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (caught) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(27.dp),
                            tint = Color(0xFF2E7D32)
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(25.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {}
                    }
                    if (isPending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = entry.displayName,
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
private fun GoDexEntryArtwork(
    entry: GoDexEntryEntity,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(entry.spriteUrl) { mutableStateOf(false) }
    if (entry.spriteUrl.isNullOrBlank() || imageFailed) {
        GoDexNumberArtwork(entry, modifier)
        return
    }
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
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
private fun GoDexNumberArtwork(
    entry: GoDexEntryEntity,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (entry.needed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "#${entry.pokedexId.toString().padStart(4, '0')}",
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.needed) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun GoDexCollectionConfirmationBar(
    entry: GoDexEntryEntity,
    isPending: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        val variant = goDexEntryVariantLabel(entry).ifBlank { "Base form" }
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "#${entry.pokedexId.toString().padStart(4, '0')} \u2022 $variant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.entryKey,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isPending) {
                Text(
                    text = "Confirming replaces the pending GoDex state for this exact entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(2f)
                        .semantics {
                            contentDescription = if (entry.needed) {
                                "Confirm ${entry.displayName} caught"
                            } else {
                                "Confirm ${entry.displayName} needed"
                            }
                        }
                ) {
                    Text(
                        if (entry.needed) "Mark caught" else "Mark needed"
                    )
                }
            }
        }
    }
}

@Composable
private fun GoDexCollectionEmptyState(
    hasQuery: Boolean,
    filter: GoDexCollectionFilter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(24.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (filter == GoDexCollectionFilter.NEEDED && !hasQuery) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Filled.Search
            },
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = when {
                hasQuery -> "No matching Pokémon"
                filter == GoDexCollectionFilter.NEEDED -> "You caught everything in this checklist"
                filter == GoDexCollectionFilter.CAUGHT -> "No caught Pokémon yet"
                else -> "No synced Pokémon"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
