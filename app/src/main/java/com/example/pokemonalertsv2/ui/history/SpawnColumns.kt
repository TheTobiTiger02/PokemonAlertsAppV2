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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
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

/** 11660 -> "11.7k", 120400 -> "120k": short enough to sit on a narrow column. */
internal fun compactCount(value: Int): String = when {
    value >= 100_000 -> "${Math.round(value / 1000.0)}k"
    value >= 1_000 -> String.format(Locale.ROOT, "%.1f", value / 1000.0).removeSuffix(".0") + "k"
    else -> value.toString()
}

/**
 * Stacked columns with each total written on its column, for counts people read off the chart: above the
 * column where it fits, upright inside it on a narrow phone. [reference] draws a dashed "usual" line. The
 * selected column is in the primary colour, flagged ones in the error tone, the rest neutral.
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
    /** Every how many columns a label is drawn; the selected one always is. */
    labelEvery: Int = 1,
    testTagPrefix: String = "insights_column",
    onSelect: ((Int) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val tickStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) {
            if (columns.isEmpty()) return@Canvas
            val tickSpace = 18.dp.toPx()
            val top = 16.dp.toPx()
            val plot = size.height - tickSpace - top
            val max = maxOf(columns.maxOf { it.total }, reference ?: 0, 1)
            val band = size.width / columns.size
            val barWidth = (band - 4.dp.toPx()).coerceIn(2.dp.toPx(), 34.dp.toPx())
            val y = { value: Int -> top + plot * (1 - value.toFloat() / max) }
            drawLine(colors.outlineVariant, Offset(0f, top + plot), Offset(size.width, top + plot), strokeWidth = 1.dp.toPx())
            columns.forEachIndexed { index, column ->
                val x = band * index + (band - barWidth) / 2
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
                    val onSolid = when { isSelected -> colors.onPrimary; index in flagged -> colors.onError; else -> colors.surface }
                    drawValue(measurer, compactCount(column.total), valueStyle, x, barWidth, band, y(column.total),
                        y(column.solid), top + plot, if (isSelected) colors.primary else colors.onSurface, onSolid)
                }
                // The selected column always gets its label; a regular one too close to it would overlap it.
                val nearSelected = selected != null && index != selected && kotlin.math.abs(index - selected) < labelEvery
                if ((index % labelEvery == 0 && !nearSelected) || isSelected) {
                    val label = labels.getOrNull(index).orEmpty()
                    val layout = measurer.measure(label, if (isSelected) tickStyle.copy(color = colors.primary, fontWeight = FontWeight.SemiBold) else tickStyle)
                    val left = (x + barWidth / 2 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(left, top + plot + 3.dp.toPx()))
                }
            }
            if (reference != null && reference > 0) {
                val at = y(reference)
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.7f), Offset(0f, at), Offset(size.width, at), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                referenceLabel?.let { text ->
                    val layout = measurer.measure(text, tickStyle)
                    val left = size.width - layout.size.width - 2.dp.toPx()
                    val labelTop = at - layout.size.height - 1.dp.toPx()
                    drawRoundRect(colors.surface.copy(alpha = 0.85f), Offset(left - 3.dp.toPx(), labelTop),
                        Size(layout.size.width + 6.dp.toPx(), layout.size.height.toFloat()), CornerRadius(3.dp.toPx()))
                    drawText(layout, topLeft = Offset(left, labelTop))
                }
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

/** The total above the column when it fits the band, else upright inside the solid part when that is tall enough. */
private fun DrawScope.drawValue(
    measurer: TextMeasurer, text: String, style: TextStyle, x: Float, barWidth: Float, band: Float,
    totalTop: Float, solidTop: Float, base: Float, above: Color, inside: Color
) {
    val flat = measurer.measure(text, style.copy(color = above))
    if (flat.size.width + 6.dp.toPx() <= band) {
        drawText(flat, topLeft = Offset(x + barWidth / 2 - flat.size.width / 2f, totalTop - flat.size.height - 1.dp.toPx()))
        return
    }
    val upright = measurer.measure(text, style.copy(color = inside))
    if (base - solidTop < upright.size.width + 8.dp.toPx() || barWidth < upright.size.height) return
    val cx = x + barWidth / 2
    val cy = solidTop + 4.dp.toPx() + upright.size.width / 2f
    rotate(-90f, pivot = Offset(cx, cy)) {
        drawText(upright, topLeft = Offset(cx - upright.size.width / 2f, cy - upright.size.height / 2f))
    }
}
