package com.example.pokemonalertsv2.megaboost

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.counters.CounterSprite
import com.example.pokemonalertsv2.ui.theme.typeColor
import com.example.pokemonalertsv2.ui.theme.typeLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Which mega to have active while catching here: the live Pokémon per type in the chosen area, and the
 * megas ranked by how many of those Pokémon their Mega Boost would cover.
 */
@Composable
internal fun MegaBoostContent(
    state: MegaBoostUiState,
    onArea: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (MegaCandidate) -> Unit,
) {
    val counts = state.counts
    val live = counts.values.sum()
    val totals = state.totals
    val ranking = state.ranking
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Best mega for catching", style = MaterialTheme.typography.headlineSmall)
                Text("Pokémon sharing a type with your active mega give extra candy.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.testTag("mega_boost_refresh")) { Text("Refresh") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("mega_boost_error")) }
        if (state.areas.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.areas, key = { it.first }) { (area, total) ->
                FilterChip(selected = state.area == area, onClick = { onArea(area) }, label = { Text("$area · $total") },
                    modifier = Modifier.testTag("mega_boost_area_$area"))
            }
        }
        if (state.live != null) {
            Text(buildString {
                append("$live live Pokémon")
                state.live.generatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let {
                    append(" · updated ").append(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(it.atZone(ZoneId.systemDefault())))
                }
            }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("mega_boost_live"))
            if (live == 0) Text("Nothing live here right now.", style = MaterialTheme.typography.bodyMedium)
        }
        if (totals.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(totals, key = { it.first }) { (type, count) -> TypeChip(type, "$count") }
        }
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
        // Without live data every mega would read "0 of 0"; the ranking only means something once it loads.
        if (state.live != null) LazyColumn(Modifier.heightIn(max = maxHeight).testTag("mega_boost_list"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(ranking, key = { it.mega.pokemonId }) { row ->
                MegaBoostRowItem(row, active = row.mega.pokemonId == state.activeMegaId,
                    urls = state.spriteUrls[row.mega.pokemonId].orEmpty(), onClick = { onSelect(row.mega) })
            }
        }
    }
}

@Composable
private fun MegaBoostRowItem(row: MegaBoostRow, active: Boolean, urls: List<String>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Set as active mega", onClick = onClick)
            .padding(vertical = 8.dp).testTag("mega_boost_row_${row.mega.pokemonId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CounterSprite(urls = urls, size = 40.dp, type = row.mega.types.firstOrNull())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.mega.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                row.mega.boostTypes.forEach { TypeChip(it, null) }
            }
            Text(
                "Boosts ${row.boosted} of ${row.total} (${(row.share * 100).toInt()}%)" +
                    (if (row.mega.owned) " · In your roster" else "") + (if (active) " · Active" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeChip(type: String, count: String?) {
    val color = typeColor(type) ?: MaterialTheme.colorScheme.secondary
    Surface(color = color, contentColor = Color.White, shape = MaterialTheme.shapes.small) {
        Text(listOfNotNull(typeLabel(type) ?: type, count).joinToString(" "), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** The sheet as the map opens it: loads on open, and a tap makes that mega the active one. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MegaBoostSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val controller = androidx.compose.runtime.remember { MegaBoostController(context, scope) }
    val state by controller.uiState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { controller.load() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MegaBoostContent(state, onArea = controller::selectArea, onRefresh = controller::load, onSelect = { mega ->
            controller.setActive(mega.pokemonId)
            android.widget.Toast.makeText(context, "${mega.name} is now your active mega", android.widget.Toast.LENGTH_SHORT).show()
        })
    }
}
