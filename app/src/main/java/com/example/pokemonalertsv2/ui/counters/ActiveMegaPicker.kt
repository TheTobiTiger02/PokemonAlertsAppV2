package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.gamemaster.MegaSpecies
import java.util.Locale

/**
 * Which Mega Evolution the trainer currently has active.
 *
 * A mega has to already be evolved to be brought into a raid, and only one can be at a time,
 * so this is the difference between a suggested team the trainer can actually field and one
 * that opens with a Pokémon they do not have available. Null — the default — means none.
 */
@Composable
internal fun ActiveMegaRow(state: RaidCountersUiState, onOpen: () -> Unit) {
    val active = state.activeMegaId
        ?.let { id -> state.megaOptions.firstOrNull { it.pokemonId == id } }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Choose active mega", onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            active?.let {
                CounterSprite(
                    urls = state.spriteUrls[it.pokemonId].orEmpty(),
                    size = 28.dp,
                    type = state.pokemonTypes[it.pokemonId]?.firstOrNull()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Active mega", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = active?.displayName ?: "None — no mega in the team",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * The picker itself.
 *
 * A grid of artwork rather than a list of ~90 names: a mega is recognised by its sprite long
 * before its name is read, and the flat list meant scrolling past eighty megas the trainer
 * does not own to reach one they do.
 *
 * Two modes for the same reason. "In your roster" is what can actually be fielded and is the
 * default whenever the roster reaches any mega at all; "All megas" is always one tap away,
 * because a stale or missing CSV must never hide a mega the trainer really does have.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ActiveMegaSheet(
    state: RaidCountersUiState,
    onSelect: (String?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val owned = remember(state.megaOptions, state.ownedBaseSpeciesIds) {
        state.megaOptions.filter { it.baseSpeciesId in state.ownedBaseSpeciesIds }
    }
    // Defaulting to a filter that would show nothing is worse than not filtering.
    var rosterOnly by rememberSaveable(owned.isNotEmpty()) { mutableStateOf(owned.isNotEmpty()) }
    val matches = remember(state.megaOptions, owned, rosterOnly, query) {
        val pool = if (rosterOnly) owned else state.megaOptions
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) pool
        else pool.filter { it.displayName.lowercase(Locale.ROOT).contains(needle) }
    }
    // The sheet's content slot is a wrap-content column, so a lazy list inside it can be
    // measured with an unbounded height. Bound it against the window explicitly.
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Active mega", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Only a mega you have already evolved can join a raid party, and only one at a " +
                "time. This picks which one the suggested team may use.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        if (state.megaOptions.isEmpty()) {
            Text(
                "The Pokébattler species list has not downloaded yet. Open this again once " +
                    "counters have loaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            return@Column
        }

        if (owned.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                FilterChip(
                    selected = rosterOnly,
                    onClick = { rosterOnly = true },
                    label = { Text("In your roster (${owned.size})") }
                )
                FilterChip(
                    selected = !rosterOnly,
                    onClick = { rosterOnly = false },
                    label = { Text("All megas") }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (matches.isEmpty()) {
            Text(
                text = "No mega matches that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier
                .heightIn(max = listMaxHeight)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Pinned in both modes: "no mega" is a real answer, not the absence of one.
            item(key = "none") {
                MegaTile(
                    label = "No mega",
                    selected = state.activeMegaId == null,
                    sprite = null,
                    inRoster = false,
                    state = state,
                    onClick = { onSelect(null) }
                )
            }
            items(matches, key = { it.pokemonId }) { mega ->
                MegaTile(
                    label = mega.displayName,
                    selected = state.activeMegaId == mega.pokemonId,
                    sprite = mega,
                    inRoster = mega.baseSpeciesId in state.ownedBaseSpeciesIds,
                    state = state,
                    onClick = { onSelect(mega.pokemonId) }
                )
            }
        }
    }
}

@Composable
private fun MegaTile(
    label: String,
    selected: Boolean,
    sprite: MegaSpecies?,
    inRoster: Boolean,
    state: RaidCountersUiState,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.semantics {
            contentDescription = if (selected) "$label, selected" else label
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                // Always reserve the slot. CounterSprite renders nothing at all when it has
                // no URLs, which left tiles with missing artwork shorter than their neighbours.
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (sprite != null) {
                        CounterSprite(
                            urls = state.spriteUrls[sprite.pokemonId].orEmpty(),
                            size = 40.dp,
                            type = state.pokemonTypes[sprite.pokemonId]?.firstOrNull()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (inRoster) {
                Text(
                    text = "Owned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}
