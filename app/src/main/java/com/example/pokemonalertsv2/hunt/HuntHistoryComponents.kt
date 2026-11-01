package com.example.pokemonalertsv2.hunt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.health.WatchStats
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

/** Shiny gold, readable on both light and dark surfaces. */
internal val ShinyGold = Color(0xFFE0A800)
internal val HundoPink = Color(0xFFE5407A)
internal val HeartRed = Color(0xFFE53935)

/** A small rounded label with an optional leading icon: "3 caught", "1.2 km", "♥ 112". */
@Composable
internal fun StatPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}

/** The catches' sprites overlapping like a hand of cards, with "+N" for the rest. */
@Composable
internal fun CatchSpriteStack(catches: List<HuntCatch>, modifier: Modifier = Modifier, max: Int = 7) {
    val shown = catches.take(max)
    val extra = catches.size - shown.size
    val size = 36.dp
    val step = 24.dp
    Box(modifier.height(size).width(step * (shown.size + if (extra > 0) 1 else 0) + (size - step))) {
        shown.forEachIndexed { index, catch ->
            CatchAvatar(catch, size, Modifier.offset(x = step * index))
        }
        if (extra > 0) {
            Box(
                Modifier.offset(x = step * shown.size).size(size).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+$extra", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/** One catch's sprite in a ring: gold for a shiny, pink for a hundo. */
@Composable
internal fun CatchAvatar(catch: HuntCatch, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val ring = when {
        catch.shiny -> ShinyGold
        catch.isHundo -> HundoPink
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Box(
        modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (catch.imageUrl != null) {
            AsyncImage(model = catch.imageUrl, contentDescription = catch.name, modifier = Modifier.size(size - 6.dp))
        } else {
            Text(catch.name.take(1), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * The walked path's shape, drawn locally on a Canvas: no map tiles, so a long history list
 * costs no data. Start is a hollow dot, the end a filled one; catches are small gold dots.
 */
@Composable
internal fun HuntPathThumbnail(
    points: List<CatchPoint>,
    catches: List<CatchPoint>,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val dot = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(background)) {
        val all = points + catches
        if (all.size < 2) return@Canvas
        val midLat = all.sumOf { it.latitude } / all.size
        val xScale = cos(Math.toRadians(midLat))
        val minX = all.minOf { it.longitude * xScale }
        val maxX = all.maxOf { it.longitude * xScale }
        val minY = all.minOf { it.latitude }
        val maxY = all.maxOf { it.latitude }
        val pad = 8.dp.toPx()
        val spanX = max(maxX - minX, 1e-6)
        val spanY = max(maxY - minY, 1e-6)
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2
        fun at(p: CatchPoint) = Offset(
            (offsetX + (p.longitude * xScale - minX) * scale).toFloat(),
            (offsetY + (maxY - p.latitude) * scale).toFloat()
        )
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { i, p -> val o = at(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(dot, radius = 3.dp.toPx(), center = at(points.first()), style = Stroke(1.5.dp.toPx()))
            drawCircle(dot, radius = 3.dp.toPx(), center = at(points.last()))
        }
        catches.forEach { drawCircle(ShinyGold, radius = 2.5.dp.toPx(), center = at(it)) }
    }
}

/** Heart rate, steps and calories from a connected watch, with a per-minute heart-rate chart. */
@Composable
internal fun WatchStatsSection(watch: WatchStats, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = HeartRed, modifier = Modifier.size(18.dp))
                Text("From your watch", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                watch.deviceName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                watch.averageHeartRate?.let { StatTile("$it", "Avg bpm", Modifier.weight(1f)) }
                if (watch.maxHeartRate > 0) StatTile("${watch.maxHeartRate}", "Max bpm", Modifier.weight(1f))
                if (watch.steps > 0) StatTile(String.format(Locale.getDefault(), "%,d", watch.steps), "Steps", Modifier.weight(1f))
                if (watch.calories >= 1.0) StatTile("${watch.calories.toInt()}", "kcal", Modifier.weight(1f))
            }
            if (watch.heartRatePerMinute.count { it > 0 } >= 2) {
                HeartRateChart(watch.heartRatePerMinute, Modifier.fillMaxWidth().height(72.dp))
                Text(
                    "Heart rate per minute · ${watch.minHeartRate}–${watch.maxHeartRate} bpm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeartRateChart(perMinute: List<Int>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = remember(perMinute) { perMinute }
    Canvas(modifier) {
        val known = values.filter { it > 0 }
        if (known.size < 2) return@Canvas
        val lo = (known.min() - 5).toFloat()
        val hi = (known.max() + 5).toFloat()
        val stepX = size.width / max(values.size - 1, 1)
        fun y(v: Int) = size.height - (v - lo) / (hi - lo) * size.height
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val x = i * stepX
            if (!started) { path.moveTo(x, y(v)); started = true } else path.lineTo(x, y(v))
        }
        drawPath(path, HeartRed, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** "Shiny", "100%", "IV 87", "CP 1234" badges for one catch. */
@Composable
internal fun CatchBadges(catch: HuntCatch) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (catch.shiny) StatPill("Shiny", icon = Icons.Filled.Star, tint = Color.Black, container = ShinyGold)
        catch.iv?.let { iv ->
            if (iv == 100) StatPill("100%", tint = Color.White, container = HundoPink)
            else StatPill("IV $iv", container = MaterialTheme.colorScheme.surfaceContainerHighest, tint = MaterialTheme.colorScheme.onSurface)
        }
        catch.cp?.let { StatPill("CP $it", container = MaterialTheme.colorScheme.surfaceContainerHighest, tint = MaterialTheme.colorScheme.onSurface) }
    }
}

/** "4.2 km/h", or null when too short to mean anything. */
internal fun formatHuntPace(summary: HuntSummary): String? =
    if (summary.activeMillis < 60_000L || summary.distanceMeters < 50) null
    else String.format(Locale.US, "%.1f km/h", summary.averageKmh)
