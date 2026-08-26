package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.counters.bestOf
import com.example.pokemonalertsv2.data.counters.toCounterMetric

/**
 * The whole ranking, in a lazy list.
 *
 * The inline card stays at [COLLAPSED_COUNT] rows and "Show all" opens this instead of
 * expanding in place. That was the one structural problem with the old card: `GeneralList`
 * built every row in a plain `Column` inside the alert-detail scroller, so expanding a
 * thirty-row list composed thirty rows in a single frame and none of them could ever be
 * recycled.
 *
 * The quick controls are repeated at the top because re-sorting is the main reason to open
 * the full list at all, and reaching the card's own chips would mean closing this first.
 */
@Composable
internal fun AllCountersSheet(state: RaidCountersUiState, actions: RaidCountersActions) {
    val metric = state.options.sort.toCounterMetric()
    // The sheet's content slot is a wrap-content column, so a LazyColumn inside it can be
    // measured with an unbounded height. Bound it against the window instead of relying on
    // the sheet to do it: the heading and controls above are fixed height, so the list gets
    // whatever is left of a nearly full-height sheet.
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SheetHeading(state)
        QuickControls(state, actions)
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        if (state.showingPersonal) {
            val ranked = state.personal?.ranked.orEmpty().take(PERSONAL_SHEET_LIMIT)
            val best = remember(ranked, metric) {
                metric.bestOf(ranked.map { it.metrics.valueFor(metric) })
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = ranked,
                    key = { index, counter -> "${counter.pokemonId}-$index" }
                ) { index, counter ->
                    PersonalRow(
                        rank = index + 1,
                        counter = counter,
                        spriteUrls = state.spriteUrls,
                        types = state.pokemonTypes,
                        moveTypes = state.moveTypes,
                        metric = metric,
                        best = best
                    )
                }
            }
        } else {
            val all = if (state.ownedOnly) state.counters.filter { it.isOwned } else state.counters
            val best = remember(all, metric) {
                metric.bestOf(all.map { it.counter.metrics().valueFor(metric) })
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = all,
                    key = { entry -> "${entry.counter.pokemonId}-${entry.counter.rank}" }
                ) { entry ->
                    GeneralRow(
                        entry = entry,
                        spriteUrls = state.spriteUrls,
                        types = state.pokemonTypes,
                        moveTypes = state.moveTypes,
                        metric = metric,
                        best = best
                    )
                }
            }
        }
    }
}

/** Which boss and which setup, so the sheet stands on its own once the card is covered. */
@Composable
private fun SheetHeading(state: RaidCountersUiState) {
    val shownCount = if (state.showingPersonal) {
        state.personal?.ranked?.size?.coerceAtMost(PERSONAL_SHEET_LIMIT) ?: 0
    } else {
        if (state.ownedOnly) state.counters.count { it.isOwned } else state.counters.size
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CounterSprite(
            urls = listOfNotNull(state.bossThumbnailUrl) +
                state.spriteUrls[state.bossPokemonId].orEmpty(),
            size = 36.dp,
            type = state.pokemonTypes[state.bossPokemonId]?.firstOrNull()
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.bossDisplayName ?: "Best counters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$shownCount counters · by ${state.options.sort.label.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // The card's own share button is behind this sheet, so repeat it here.
        ShareCountersButton(state)
    }
}

/** Pokebattler returns thirty rows for a personal ranking; there is no thirty-first. */
internal const val PERSONAL_SHEET_LIMIT = 30
