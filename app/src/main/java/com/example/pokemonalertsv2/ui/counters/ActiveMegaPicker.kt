package com.example.pokemonalertsv2.ui.counters

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
 * Lists every mega the game master knows rather than only the ones the roster can reach: a
 * stale or missing CSV must not hide a mega the trainer really does have. Ones whose base
 * species *is* in the roster are already sorted first by the ViewModel and are marked here.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ActiveMegaSheet(
    state: RaidCountersUiState,
    onSelect: (String?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(state.megaOptions, query) {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) state.megaOptions
        else state.megaOptions.filter { it.displayName.lowercase(Locale.ROOT).contains(needle) }
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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier
                .heightIn(max = listMaxHeight)
                .padding(top = 8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "none") {
                MegaRow(
                    label = "None — no mega active",
                    selected = state.activeMegaId == null,
                    sprite = null,
                    inRoster = false,
                    state = state,
                    onClick = { onSelect(null) }
                )
            }
            items(matches, key = { it.pokemonId }) { mega ->
                MegaRow(
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
private fun MegaRow(
    label: String,
    selected: Boolean,
    sprite: MegaSpecies?,
    inRoster: Boolean,
    state: RaidCountersUiState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Always reserve the slot. CounterSprite renders nothing at all when it has no
        // URLs, which left rows with missing artwork shifted left against their neighbours.
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (sprite != null) {
                CounterSprite(
                    urls = state.spriteUrls[sprite.pokemonId].orEmpty(),
                    size = 32.dp,
                    type = state.pokemonTypes[sprite.pokemonId]?.firstOrNull()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (inRoster) {
                Text(
                    text = "In your roster",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
