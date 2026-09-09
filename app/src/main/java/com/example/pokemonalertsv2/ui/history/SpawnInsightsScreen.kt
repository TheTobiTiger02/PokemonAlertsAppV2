package com.example.pokemonalertsv2.ui.history

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
import com.example.pokemonalertsv2.data.insights.SpawnInsights
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * "When and where does this actually turn up?"
 *
 * Reports observed history and says so — the charts describe what has happened,
 * not what will. The truncation note exists so a capped read is never mistaken
 * for a complete count.
 */
@Composable
fun SpawnInsightsScreen(
    state: SpawnInsightsUiState,
    onQueryChange: (String) -> Unit,
    onRangeChange: (InsightsRange) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Species or search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightsRange.entries.forEach { range ->
                FilterChip(
                    selected = state.range == range,
                    onClick = { onRangeChange(range) },
                    label = { Text(range.label) }
                )
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onRun, enabled = !state.isLoading) {
                Text(if (state.hasRun) "Refresh" else "Look back")
            }
        }

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
                "Pick a species and a window, then look back over the history.",
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
private fun BarRow(values: List<Int>, labels: List<String>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEach { value ->
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
