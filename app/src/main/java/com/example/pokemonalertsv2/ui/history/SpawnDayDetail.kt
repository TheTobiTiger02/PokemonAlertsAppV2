package com.example.pokemonalertsv2.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pokemonalertsv2.data.insights.SpawnHourPoint
import com.example.pokemonalertsv2.data.insights.hourLabels
import com.example.pokemonalertsv2.ui.theme.Spacing
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Seen by our scanner / catalogue only / unusual: the key under the per-day and per-hour columns. */
@Composable
internal fun ColumnKey(showUnusual: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        KeyItem(colors.onSurfaceVariant.copy(alpha = 0.75f), "Seen by our scanner")
        KeyItem(colors.onSurfaceVariant.copy(alpha = 0.3f), "Catalogue only")
        if (showUnusual) KeyItem(colors.error, "Unusual")
    }
}

@Composable
private fun KeyItem(color: Color, label: String, shape: Shape = RoundedCornerShape(2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(shape).background(color))
        Spacer(Modifier.width(Spacing.xxs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The opened day as three cards: the day hour by hour, the opened hour's Pokémon and map, and the day's
 * alerts per hour. Tapping a column in either chart, or the arrows on the hour card, opens another hour.
 */
@Composable
internal fun SpawnDayDetailSection(state: SpawnActivityUiState, onOpenHour: (Long) -> Unit, onRetry: () -> Unit) {
    val day = state.detailDay ?: return
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val detail = state.dayDetail
    val isToday = state.activity?.days?.days?.lastOrNull() == day
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth().testTag("insights_day_detail")) {
        val spawns = detail?.spawns
        val labels = spawns?.let { hourLabels(it.hours.map { h -> h.hour }) }.orEmpty()
        val selected = spawns?.hours?.indexOfFirst { it.start == state.hourStart }?.takeIf { it >= 0 }

        DetailCard {
            Column {
                val oddLength = spawns?.takeIf { it.complete && it.hours.size != 24 }?.let { " · ${it.hours.size}-hour day" }.orEmpty()
                Text((if (isToday) "Today so far" else "Day") + oddLength, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(longDay(day), style = MaterialTheme.typography.titleMedium)
            }
            when {
                spawns == null && state.isDayLoading -> Loading(150)
                spawns == null && state.dayError != null -> Failed(state.dayError, onRetry)
            }
            if (spawns != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    InlineStat(numbers.format(spawns.totals.active), "Active", Modifier.weight(1f))
                    InlineStat(numbers.format(spawns.totals.sightings), "Sightings", Modifier.weight(1f))
                    InlineStat(detail.alerts?.let { numbers.format(it.total) } ?: "—", "Alerts", Modifier.weight(1f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CardLabel("Active spawnpoints by hour")
                    StackedColumns(
                        columns = spawns.hours.map { ColumnValue(it.live, it.catalogueOnly) },
                        labels = labels,
                        descriptions = spawns.hours.mapIndexed { i, h -> "${labels[i]}:00, ${numbers.format(h.active)} active spawnpoints" },
                        height = 140.dp,
                        selected = selected,
                        valueLabels = ValueLabels.SELECTED_AND_PEAK,
                        background = MaterialTheme.colorScheme.surfaceContainer,
                        labelEvery = 3,
                        testTagPrefix = "insights_hour",
                        onSelect = { onOpenHour(spawns.hours[it].start) }
                    )
                    ColumnKey(showUnusual = false)
                }
            }
        }

        if (spawns != null && selected != null) {
            HourCard(state, spawns.hours.map { it.start }, labels, selected, spawns.hours[selected].hour, spawns.hours[selected].active, numbers, onOpenHour)
        }

        detail?.alerts?.let { alerts ->
            DetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CardLabel("Alerts by hour", trailing = "${numbers.format(alerts.total)} alerts")
                    val alertLabels = hourLabels(alerts.hours.map { it.hour })
                    StackedColumns(
                        columns = alerts.hours.map { ColumnValue(it.count) },
                        labels = alertLabels,
                        descriptions = alerts.hours.mapIndexed { i, h -> "${alertLabels[i]}:00, ${h.count} alerts" },
                        height = 110.dp,
                        selected = alerts.hours.indexOfFirst { it.start == state.hourStart }.takeIf { it >= 0 },
                        valueLabels = ValueLabels.SELECTED_AND_PEAK,
                        background = MaterialTheme.colorScheme.surfaceContainer,
                        labelEvery = 3,
                        testTagPrefix = "insights_alert_hour",
                        onSelect = { onOpenHour(alerts.hours[it].start) }
                    )
                }
                Text(alertTypes(alerts.byType, alerts.total), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The opened hour: arrows to step through the day, its Pokémon, and where its spawnpoints are. */
@Composable
private fun HourCard(
    state: SpawnActivityUiState,
    starts: List<Long>,
    labels: List<String>,
    index: Int,
    hour: Int,
    active: Int,
    numbers: NumberFormat,
    onOpenHour: (Long) -> Unit
) {
    val start = starts[index]
    val hourDetail = state.hourDetail?.takeIf { it.start == start }
    DetailCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onOpenHour(starts[index - 1]) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous hour")
                }
                Text("${labels[index]}:00–${"%02d".format((hour + 1) % 24)}:00", style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { onOpenHour(starts[index + 1]) }, enabled = index < starts.lastIndex) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next hour")
                }
            }
            Text(
                listOfNotNull(
                    "${numbers.format(active)} active",
                    hourDetail?.let { "${numbers.format(it.sightings)} sightings" },
                    hourDetail?.let { "${numbers.format(it.species.size)} species" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        when {
            hourDetail == null && state.isHourLoading -> Loading(120)
            hourDetail == null && state.hourError != null -> Failed(state.hourError) { onOpenHour(start) }
            hourDetail != null -> {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CardLabel("Pokémon first seen")
                    if (hourDetail.species.isEmpty()) {
                        Text("No wild Pokémon were first seen in this hour.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        SpeciesRows(hourDetail.species.take(10), hourDetail.sightings.coerceAtLeast(1), numbers, compact = true)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CardLabel("Where they spawned")
                    HourMap(hourDetail.points)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        KeyItem(Color(android.graphics.Color.parseColor(SpawnHourMapView.LIVE_COLOR)), "Seen by our scanner", CircleShape)
                        KeyItem(Color(android.graphics.Color.parseColor(SpawnHourMapView.CATALOGUE_COLOR)), "Catalogue only", CircleShape)
                    }
                }
            }
        }
    }
}

/** The app's detail-card surface (as the alert detail's stats card), holding one block of the opened day. */
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
    }
}

@Composable
private fun CardLabel(text: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun InlineStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Loading(height: Int) {
    Box(Modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit) {
    Column {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun HourMap(points: List<SpawnHourPoint>) {
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).testTag("insights_hour_map")) {
        AndroidView(
            factory = { SpawnHourMapView(it) },
            update = { it.setPoints(points) },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** "Rare 12 · Hundo 4 · Raid 3 · Quest 2 · +3 more": the day's alert types, most first. */
internal fun alertTypes(byType: Map<String, Int>, total: Int): String {
    if (total == 0) return "No alerts received this day."
    val sorted = byType.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    val shown = sorted.take(4).joinToString(" · ") { "${it.key} ${it.value}" }
    return if (sorted.size > 4) "$shown · +${sorted.size - 4} more" else shown
}

private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private fun longDay(day: String): String = runCatching { LocalDate.parse(day).format(LONG_DAY) }.getOrDefault(day)
