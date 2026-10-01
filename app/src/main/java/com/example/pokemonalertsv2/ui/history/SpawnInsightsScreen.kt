package com.example.pokemonalertsv2.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.data.insights.SpawnActivity
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesCount
import androidx.compose.material.icons.filled.Close
import com.example.pokemonalertsv2.data.insights.SpawnInsights
import com.example.pokemonalertsv2.data.insights.UNUSUAL_SHARE
import com.example.pokemonalertsv2.data.insights.summarizeSpawnActivity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * "When and where does this actually turn up?"
 *
 * Reports observed history and says so — the charts describe what has happened,
 * not what will. The truncation note exists so a capped read is never mistaken
 * for a complete count.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SpawnInsightsScreen(
    state: SpawnInsightsUiState,
    onQueryChange: (String) -> Unit,
    onRangeChange: (InsightsRange) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    /** Species seen in the live feed right now: one tap to look one up. */
    suggestions: List<String> = emptyList(),
    onTabChange: (InsightsTab) -> Unit = {},
    onSpawnRangeChange: (SpawnRange) -> Unit = {},
    onSpawnAreaChange: (String?) -> Unit = {},
    onSpawnRetry: () -> Unit = {},
    onSpawnSearchChange: (String) -> Unit = {},
    onSpawnDayChange: (String?) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
            InsightsTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { onTabChange(tab) },
                    text = { Text(tab.label) },
                    // Without this the unselected label took the same primary colour as the selected one.
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when (state.tab) {
            InsightsTab.ALERTS -> AlertInsights(state, onQueryChange, onRangeChange, onRun, suggestions)
            InsightsTab.SPAWNS -> SpawnActivityPane(state.spawns, onSpawnRangeChange, onSpawnAreaChange, onSpawnRetry, onSpawnSearchChange, onSpawnDayChange)
        }
    }
}

@Composable
private fun AlertInsights(
    state: SpawnInsightsUiState,
    onQueryChange: (String) -> Unit,
    onRangeChange: (InsightsRange) -> Unit,
    onRun: () -> Unit,
    suggestions: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "When and where a Pokémon has turned up recently.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Species") },
            placeholder = { Text("e.g. Dratini") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (state.query.isNotBlank()) onRun() }),
            trailingIcon = {
                IconButton(onClick = onRun, enabled = !state.isLoading && state.query.isNotBlank()) {
                    Icon(Icons.Filled.Search, contentDescription = "Look back")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty() && !state.hasRun) {
            Text("Seen today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { species ->
                    AssistChip(onClick = { onQueryChange(species); onRun() }, label = { Text(species) })
                }
            }
        }

        com.example.pokemonalertsv2.ui.settings.SegmentedSetting(
            options = InsightsRange.entries.map { it.label },
            selectedIndex = state.range.ordinal,
            onSelected = { onRangeChange(InsightsRange.entries[it]) }
        )

        state.coverageNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.errorMessage != null -> Text(
                state.errorMessage,
                color = MaterialTheme.colorScheme.error
            )

            state.insights == null -> Text(
                "Type a species, or pick one seen today, to see its busiest hours and places.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.insights.isEmpty -> Text(
                "Nothing matching turned up in the last ${state.range.label}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> InsightsBody(state.insights)
        }
    }
}

/**
 * "Was today normal?" Active spawnpoints per day, so a surge or an outage stands out, and every wild
 * Pokémon the live scanner saw, ranked by species. Unlike the Alerts tab this is not limited to
 * the alerts that were posted: it is everything the scanner saw spawn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpawnActivityPane(
    state: SpawnActivityUiState,
    onRangeChange: (SpawnRange) -> Unit,
    onAreaChange: (String?) -> Unit,
    onRetry: () -> Unit,
    onSearchChange: (String) -> Unit,
    onDayChange: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Active spawnpoints and every wild Pokémon the live scanner saw, per Berlin day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        com.example.pokemonalertsv2.ui.settings.SegmentedSetting(
            options = SpawnRange.entries.map { it.label },
            selectedIndex = state.range.ordinal,
            onSelected = { onRangeChange(SpawnRange.entries[it]) }
        )
        val areas = state.activity?.days?.areas.orEmpty()
        if (areas.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.area == null, onClick = { onAreaChange(null) }, label = { Text("All areas") })
                areas.forEach { area ->
                    // "Unknown" is the server's bucket for spawnpoints outside every named area.
                    val label = if (area.equals("Unknown", ignoreCase = true)) "Outside named areas" else area
                    FilterChip(selected = state.area == area, onClick = { onAreaChange(area) }, label = { Text(label) })
                }
            }
        }

        val activity = state.activity
        when {
            state.isLoading && activity == null -> Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.errorMessage != null && activity == null -> Column {
                Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Try again") }
            }

            activity != null -> SpawnActivityBody(activity, isRefreshing = state.isLoading, search = state, onSearchChange = onSearchChange, onDayChange = onDayChange)
        }
    }
}

@Composable
private fun SpawnActivityBody(
    activity: SpawnActivity,
    isRefreshing: Boolean,
    search: SpawnActivityUiState,
    onSearchChange: (String) -> Unit,
    onDayChange: (String?) -> Unit
) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val summary = remember(activity.days) { summarizeSpawnActivity(activity.days) }
    val days = activity.days.summary
    if (isRefreshing) {
        Text("Updating…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Section("Active spawnpoints per day") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Headline("Today so far", summary.today?.let(numbers::format) ?: "—", Modifier.weight(1f))
            Headline("Usual day", summary.usual?.let(numbers::format) ?: "—", Modifier.weight(1f))
            Headline("Busiest", summary.busiest?.let(numbers::format) ?: "—", Modifier.weight(1f))
        }
        if (days.size > 1) {
            BarRow(
                values = days.map { it.active },
                labels = dayLabels(days.map { it.day }),
                highlighted = summary.flagged,
                partial = todayIndex(days.map { it.day })
            )
        }
        val flagged = summary.flagged.sorted().map { shortDay(days[it].day) }
        Text(
            listOfNotNull(
                if (flagged.isEmpty()) {
                    "No unusual days in range."
                } else {
                    "Highlighted: ${flagged.joinToString()} — more than ${(UNUSUAL_SHARE * 100).toInt()}% above or below a usual day, or an unexplained surge."
                },
                if (summary.unscanned > 0) "${summary.unscanned} day(s) before live scanning are left out." else null
            ).joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val species = activity.species
    Section("Wild spawns") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Headline("Sightings", numbers.format(species.totals.sightings), Modifier.weight(1f))
            Headline("Species", numbers.format(species.totals.species), Modifier.weight(1f))
        }
        if (species.perDay.size > 1) {
            val picked = species.perDay.indexOfFirst { it.day == search.day }
            BarRow(
                values = species.perDay.map { it.total },
                labels = dayLabels(species.perDay.map { it.day }),
                highlighted = if (picked >= 0) setOf(picked) else emptySet(),
                partial = todayIndex(species.perDay.map { it.day })
            )
            // Newest first: the day people usually want is today or yesterday.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = search.day == null, onClick = { onDayChange(null) }, label = { Text("Whole range") })
                species.perDay.asReversed().forEach { day ->
                    FilterChip(
                        selected = search.day == day.day,
                        onClick = { onDayChange(if (search.day == day.day) null else day.day) },
                        label = { Text(shortDay(day.day)) }
                    )
                }
            }
        }
    }

    OutlinedTextField(
        value = search.search,
        onValueChange = onSearchChange,
        label = { Text("Search species") },
        placeholder = { Text("Name or Pokédex #") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (search.search.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth()
    )

    val result = search.searchResult
    val listed = search.search.isNotBlank() || search.day != null
    // Shares are of the sightings the list covers: the picked day's, or the range's.
    val total = (result?.takeIf { search.day != null }?.totals ?: species.totals).sightings.coerceAtLeast(1)
    val on = search.day?.let { " on ${shortDay(it)}" }.orEmpty()
    when {
        listed && search.searchError != null -> Text(search.searchError, color = MaterialTheme.colorScheme.error)
        listed && result == null -> Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        search.search.isBlank() && result != null -> Section("Most seen species$on") {
            Text(
                "${numbers.format(result.totals.sightings)} sightings of ${numbers.format(result.totals.species)} species$on",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SpeciesRows(result.species.take(30), total, numbers)
        }
        listed && result != null -> {
            val best = result.species.firstOrNull()
            if (best == null) {
                Text(
                    "No wild sightings match \"${result.query}\"${on.ifEmpty { " in this range" }}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // The best match per day: is it around every day, or did it only show up once? One picked
                // day has no series to draw, and its rank is already in the list below.
                if (search.day == null) {
                    Section("${best.name ?: "#${best.pokemonId}"} per day") {
                        if (best.daily.size > 1) BarRow(values = best.daily, labels = dayLabels(result.days))
                        Text(
                            "#${best.rank} of ${numbers.format(result.totals.species)} species · " +
                                "${numbers.format(best.count)} sightings in range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Section("Matching species$on") {
                    SpeciesRows(result.species, total, numbers)
                }
            }
        }
        species.species.isNotEmpty() -> Section("Most seen species") {
            SpeciesRows(species.species.take(30), total, numbers)
        }
    }
    Text(
        "Each spawn counts once, on the day it was first seen. Today is still running. Sightings are kept 30 days.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SpeciesRows(entries: List<SpawnSpeciesCount>, total: Int, numbers: NumberFormat) {
    val max = entries.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(entry.rank?.let { "#$it" }, entry.name ?: "#${entry.pokemonId}").joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(120.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .weight(entry.count.toFloat() / max)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.weight((max - entry.count).toFloat() / max + 0.0001f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${numbers.format(entry.count)} · ${"%.1f".format(entry.count * 100f / total)}%",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * BarRow gives each label an equal share of the width, starting at its own slot, so a label only sits over
 * its day when the labels split the days evenly: every day up to a week, else an even step (14 days: 7).
 */
internal fun dayLabels(days: List<String>): List<String> {
    if (days.size <= 7) return days.map(::shortDay)
    val count = listOf(6, 7, 5, 4, 3, 2).firstOrNull { days.size % it == 0 } ?: 1
    return (0 until count).map { shortDay(days[it * days.size / count]) }
}

private val SHORT_DAY = DateTimeFormatter.ofPattern("d.M.")

/** Index of today's still-running day in [days] (yyyy-MM-dd), or null. */
internal fun todayIndex(days: List<String>, today: LocalDate = LocalDate.now()): Int? =
    days.indexOf(today.toString()).takeIf { it >= 0 }

private fun shortDay(day: String): String = runCatching { LocalDate.parse(day).format(SHORT_DAY) }.getOrDefault(day)

@Composable
private fun InsightsBody(insights: SpawnInsights) {
    if (insights.truncated) {
        Text(
            "Showing the most recent ${insights.totalSightings} sightings — there were more " +
                "than this window can read, so the totals are a floor, not a count.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Headline("Sightings", insights.totalSightings.toString(), Modifier.weight(1f))
        Headline("Per day", String.format("%.1f", insights.perDayAverage), Modifier.weight(1f))
        Headline(
            "Busiest",
            insights.busiestHour?.let { formatHour(it) } ?: "—",
            Modifier.weight(1f)
        )
    }

    insights.topArea?.let { top ->
        Text(
            "Most often in ${top.area} (${top.count} of ${insights.totalSightings})",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Section("By hour of day") {
        // One label per six hours: a label under every bar has a 24th of the
        // width to live in, which is not enough for "12h" and silently clips it.
        BarRow(values = insights.byHour, labels = (0..18 step 6).map(::formatHour))
    }

    Section("By day of week") {
        BarRow(values = insights.byWeekday, labels = WEEKDAYS)
    }

    if (insights.byArea.isNotEmpty()) {
        Section("By area") {
            val max = insights.byArea.first().count.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                insights.byArea.take(8).forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.area,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(110.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .weight(entry.count.toFloat() / max)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.weight((max - entry.count).toFloat() / max + 0.0001f))
                        Spacer(Modifier.width(8.dp))
                        Text(entry.count.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (insights.hundoCount > 0) {
        Text(
            "${insights.hundoCount} of them were 100%.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Headline(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

/**
 * Bars drawn from layout weights rather than a chart library: the project has
 * none, and a bar chart does not justify adding one.
 */
@Composable
private fun BarRow(values: List<Int>, labels: List<String>, highlighted: Set<Int> = emptySet(), partial: Int? = null) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // A zero bar still gets a hairline so the axis reads as a
                        // row of slots rather than a gap of unknown width.
                        .height((6f + 84f * value / max).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (value == 0) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else if (index in highlighted) {
                                // The theme's tertiary is its primary, so an unusual day borrows the error tone to stand out.
                                MaterialTheme.colorScheme.error
                            } else if (index == partial) {
                                // Today is still running; a full-strength bar read as a slump.
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // Each label spans the slots it stands for, so it has room to render in
        // full instead of being clipped to its first character. Aligned to the
        // start of its span: a centred "0h" would sit above hour 3.
        val alignment = if (labels.size == values.size) TextAlign.Center else TextAlign.Start
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = alignment,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")

private val HOUR_FORMAT = DateTimeFormatter.ofPattern("H'h'")

private fun formatHour(hour: Int): String = LocalTime.of(hour, 0).format(HOUR_FORMAT)
