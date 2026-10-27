package com.example.pokemonalertsv2.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pokemonalertsv2.data.insights.SpawnHourPoint
import com.example.pokemonalertsv2.data.insights.hourLabels
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Seen by our scanner / catalogue only / unusual: the key under the per-day and per-hour columns. */
@Composable
internal fun ColumnKey(showUnusual: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyItem(colors.onSurfaceVariant.copy(alpha = 0.75f), "Seen by our scanner")
        KeyItem(colors.onSurfaceVariant.copy(alpha = 0.3f), "Catalogue only")
        if (showUnusual) KeyItem(colors.error, "Unusual")
    }
}

@Composable
private fun KeyItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The opened day hour by hour: active spawnpoints per hour, the opened hour's Pokémon and map, and the
 * day's alerts per hour. Tapping a column in either chart opens that hour.
 */
@Composable
internal fun SpawnDayDetailSection(state: SpawnActivityUiState, onOpenHour: (Long) -> Unit, onRetry: () -> Unit) {
    val day = state.detailDay ?: return
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val detail = state.dayDetail
    val isToday = state.activity?.days?.days?.lastOrNull() == day
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("insights_day_detail")) {
        Column {
            Text(if (isToday) "TODAY SO FAR" else "DAY DETAIL", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(longDay(day), style = MaterialTheme.typography.titleMedium)
        }
        when {
            detail == null && state.isDayLoading -> Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            detail == null && state.dayError != null -> Column {
                Text(state.dayError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
        detail ?: return@Column
        val spawns = detail.spawns
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("${numbers.format(spawns.totals.active)} active") })
            AssistChip(onClick = {}, label = { Text("${numbers.format(spawns.totals.sightings)} sightings") })
            detail.alerts?.let { AssistChip(onClick = {}, label = { Text("${numbers.format(it.total)} alerts") }) }
            if (spawns.complete && spawns.hours.size != 24) AssistChip(onClick = {}, label = { Text("${spawns.hours.size}-hour day") })
        }

        val labels = hourLabels(spawns.hours.map { it.hour })
        val selected = spawns.hours.indexOfFirst { it.start == state.hourStart }.takeIf { it >= 0 }
        Text("Active spawnpoints by hour", style = MaterialTheme.typography.titleSmall)
        StackedColumns(
            columns = spawns.hours.map { ColumnValue(it.live, it.catalogueOnly) },
            labels = labels,
            descriptions = spawns.hours.mapIndexed { i, h -> "${labels[i]}:00, ${numbers.format(h.active)} active spawnpoints" },
            height = 150.dp,
            selected = selected,
            labelEvery = 3,
            testTagPrefix = "insights_hour",
            onSelect = { onOpenHour(spawns.hours[it].start) }
        )
        ColumnKey(showUnusual = false)

        val hour = selected?.let { spawns.hours[it] }
        if (hour != null) {
            val label = labels[selected]
            val next = "%02d".format((hour.hour + 1) % 24)
            Text("Pokémon seen $label:00–$next:00", style = MaterialTheme.typography.titleSmall)
            val hourDetail = state.hourDetail?.takeIf { it.start == hour.start }
            when {
                hourDetail == null && state.isHourLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                hourDetail == null && state.hourError != null -> Column {
                    Text(state.hourError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onOpenHour(hour.start) }) { Text("Try again") }
                }
                hourDetail != null -> {
                    Text("${numbers.format(hourDetail.sightings)} sightings · ${numbers.format(hourDetail.species.size)} species",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (hourDetail.species.isEmpty()) {
                        Text("No wild Pokémon were first seen in this hour.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        SpeciesRows(hourDetail.species.take(10), hourDetail.sightings.coerceAtLeast(1), numbers)
                    }
                    Text("Where they spawned", style = MaterialTheme.typography.titleSmall)
                    HourMap(hourDetail.points)
                    Text("${numbers.format(hourDetail.points.size)} spawnpoints active in this hour.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        detail.alerts?.let { alerts ->
            Text("Alerts by hour", style = MaterialTheme.typography.titleSmall)
            val alertLabels = hourLabels(alerts.hours.map { it.hour })
            StackedColumns(
                columns = alerts.hours.map { ColumnValue(it.count) },
                labels = alertLabels,
                descriptions = alerts.hours.mapIndexed { i, h -> "${alertLabels[i]}:00, ${h.count} alerts" },
                height = 120.dp,
                selected = alerts.hours.indexOfFirst { it.start == state.hourStart }.takeIf { it >= 0 },
                labelEvery = 3,
                testTagPrefix = "insights_alert_hour",
                onSelect = { onOpenHour(alerts.hours[it].start) }
            )
            val types = alerts.byType.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" }
            Text(if (alerts.total == 0) "No alerts received this day." else types,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private fun longDay(day: String): String = runCatching { LocalDate.parse(day).format(LONG_DAY) }.getOrDefault(day)
