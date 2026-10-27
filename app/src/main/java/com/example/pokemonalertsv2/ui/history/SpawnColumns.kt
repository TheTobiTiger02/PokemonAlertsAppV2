package com.example.pokemonalertsv2.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** One column: [solid] at the bottom (seen by our scanner), [faded] on top (catalogue only). */
@Immutable
internal data class ColumnValue(val solid: Int, val faded: Int = 0) {
    val total: Int get() = solid + faded
}

/** Which columns get their count written above them. */
internal enum class ValueLabels {
    /** Every column while all counts fit flat over their columns, else as [SELECTED_AND_PEAK]. */
    ALL_IF_FIT,

    /** Only the selected column and the highest one: for charts too narrow to number every column. */
    SELECTED_AND_PEAK
}

/** 11660 -> "11.7k", 120400 -> "120k": short enough to sit on a narrow column. */
internal fun compactCount(value: Int): String = when {
    value >= 100_000 -> "${Math.round(value / 1000.0)}k"
    value >= 1_000 -> String.format(Locale.ROOT, "%.1f", value / 1000.0).removeSuffix(".0") + "k"
    else -> value.toString()
}

/** The columns whose count is drawn, given whether each count fits flat over its column. Empty columns never are. */
internal fun labelledColumns(totals: List<Int>, fits: List<Boolean>, selected: Int?, mode: ValueLabels): Set<Int> {
    val filled = totals.indices.filter { totals[it] > 0 }
    if (mode == ValueLabels.ALL_IF_FIT && filled.all { fits.getOrElse(it) { false } }) return filled.toSet()
    val peak = filled.maxByOrNull { totals[it] }
    return setOfNotNull(selected?.takeIf { it in filled }, peak)
}

/**
 * Stacked columns with their counts written above them: every count where they all fit, else only the
 * selected and the highest column's (see [ValueLabels]). [reference] draws a dashed line labelled
 * [referenceLabel]. The selected column is in the primary colour, flagged ones in the error tone, the rest
 * neutral.
 *
 * Drawn on a canvas rather than from layout weights so the numbers can sit on the columns; a row of
 * transparent buttons over it keeps every column tappable and readable by TalkBack.
 */
@Composable
internal fun StackedColumns(
    columns: List<ColumnValue>,
    labels: List<String>,
    descriptions: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    selected: Int? = null,
    flagged: Set<Int> = emptySet(),
    reference: Int? = null,
    referenceLabel: String? = null,
    valueLabels: ValueLabels = ValueLabels.ALL_IF_FIT,
    /** What the chart sits on; backs the reference label. */
    background: Color = MaterialTheme.colorScheme.surface,
    /** Every how many columns a label is drawn; the selected one always is. */
    labelEvery: Int = 1,
    testTagPrefix: String = "insights_column",
    onSelect: ((Int) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    val tickStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) {
            if (columns.isEmpty()) return@Canvas
            val tickSpace = 18.dp.toPx()
            val top = 18.dp.toPx()
            val plot = size.height - tickSpace - top
            val max = maxOf(columns.maxOf { it.total }, reference ?: 0, 1)
            val band = size.width / columns.size
            val barWidth = (band - 4.dp.toPx()).coerceIn(2.dp.toPx(), 34.dp.toPx())
            val y = { value: Int -> top + plot * (1 - value.toFloat() / max) }
            val centre = { index: Int -> band * index + band / 2 }
            drawLine(colors.outlineVariant, Offset(0f, top + plot), Offset(size.width, top + plot), strokeWidth = 1.dp.toPx())

            columns.forEachIndexed { index, column ->
                val x = centre(index) - barWidth / 2
                val isSelected = index == selected
                val solidColor = when {
                    isSelected -> colors.primary
                    index in flagged -> colors.error
                    else -> colors.onSurfaceVariant.copy(alpha = 0.75f)
                }
                val fadedColor = if (isSelected) colors.primary.copy(alpha = 0.4f) else colors.onSurfaceVariant.copy(alpha = 0.3f)
                val radius = CornerRadius(minOf(3.dp.toPx(), barWidth / 4))
                if (column.total == 0) {
                    drawRect(colors.surfaceVariant, Offset(x, top + plot - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                } else {
                    drawRoundRect(solidColor, Offset(x, y(column.solid)), Size(barWidth, top + plot - y(column.solid)), radius)
                    if (column.faded > 0) {
                        drawRoundRect(fadedColor, Offset(x, y(column.total)), Size(barWidth, y(column.solid) - y(column.total)), radius)
                    }
                }
                // The selected column always gets its label; a regular one too close to it would overlap it.
                val nearSelected = selected != null && index != selected && kotlin.math.abs(index - selected) < labelEvery
                if ((index % labelEvery == 0 && !nearSelected) || isSelected) {
                    val label = labels.getOrNull(index).orEmpty()
                    val layout = measurer.measure(label, if (isSelected) tickStyle.copy(color = colors.primary, fontWeight = FontWeight.SemiBold) else tickStyle)
                    val left = (centre(index) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(left, top + plot + 3.dp.toPx()))
                }
            }

            if (reference != null && reference > 0) {
                val at = y(reference)
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.6f), Offset(0f, at), Offset(size.width, at), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                // At the left: the newest (usually selected) column on the right keeps its count clear.
                referenceLabel?.let { text ->
                    val layout = measurer.measure(text, tickStyle)
                    val left = 4.dp.toPx()
                    val labelTop = at - layout.size.height / 2f
                    drawRoundRect(background, Offset(left - 3.dp.toPx(), labelTop),
                        Size(layout.size.width + 6.dp.toPx(), layout.size.height.toFloat()), CornerRadius(4.dp.toPx()))
                    drawText(layout, topLeft = Offset(left, labelTop))
                }
            }

            val values = columns.map { measurer.measure(compactCount(it.total), valueStyle) }
            val fits = values.map { it.size.width + 4.dp.toPx() <= band }
            val shown = labelledColumns(columns.map { it.total }, fits, selected, valueLabels)
            // The selected count first; the peak's is dropped when it would run into it.
            val drawn = mutableListOf<ClosedFloatingPointRange<Float>>()
            shown.sortedBy { if (it == selected) 0 else 1 }.forEach { index ->
                val isSelected = index == selected
                val layout = if (isSelected) measurer.measure(compactCount(columns[index].total), valueStyle.copy(color = colors.primary)) else values[index]
                val left = (centre(index) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                val span = left - 2.dp.toPx()..left + layout.size.width + 2.dp.toPx()
                if (drawn.any { it.start < span.endInclusive && span.start < it.endInclusive }) return@forEach
                drawn += span
                val textTop = y(columns[index].total) - layout.size.height - 1.dp.toPx()
                // Backed so the dashed reference line breaks behind the count instead of striking through it.
                drawRoundRect(background, Offset(span.start, textTop), Size(span.endInclusive - span.start, layout.size.height.toFloat()), CornerRadius(4.dp.toPx()))
                drawText(layout, topLeft = Offset(left, textTop))
            }
        }
        if (onSelect != null) {
            Row(Modifier.fillMaxSize()) {
                columns.indices.forEach { index ->
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .clickable(onClickLabel = descriptions.getOrNull(index)) { onSelect(index) }
                        .testTag("${testTagPrefix}_$index"))
                }
            }
        }
    }
}
