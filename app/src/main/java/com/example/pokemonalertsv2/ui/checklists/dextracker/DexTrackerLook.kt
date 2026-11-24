package com.example.pokemonalertsv2.ui.checklists.dextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dex Tracker's own colour tokens (src/styles.css :root and [data-theme=dark]), so the screen
 * reads like the web app: accent indigo, done green, trade amber.
 */
@Immutable
data class DexTrackerPalette(
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val done: Color,
    val doneSoft: Color,
    val doneLine: Color,
    val onDone: Color,
    val trade: Color,
    val tradeSoft: Color
)

private val Light = DexTrackerPalette(
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1F3F6), text = Color(0xFF13161D), muted = Color(0xFF5D6474),
    line = Color(0xFFE3E6EB), lineStrong = Color(0xFFD3D7DE), accent = Color(0xFF4B57D6), accentSoft = Color(0xFFEBEDFC),
    onAccent = Color(0xFFFFFFFF), done = Color(0xFF16935A), doneSoft = Color(0xFFE2F4EA), doneLine = Color(0xFFB7E3CB),
    onDone = Color(0xFFFFFFFF), trade = Color(0xFFB86E00), tradeSoft = Color(0xFFFDF0D9)
)

private val Dark = DexTrackerPalette(
    surface = Color(0xFF191C23), surface2 = Color(0xFF20242D), text = Color(0xFFE8EBF1), muted = Color(0xFFA2A9B7),
    line = Color(0xFF272B35), lineStrong = Color(0xFF353A46), accent = Color(0xFF8D98FF), accentSoft = Color(0xFF242A4D),
    onAccent = Color(0xFF10132A), done = Color(0xFF45CF8B), doneSoft = Color(0xFF15301F), doneLine = Color(0xFF23523A),
    onDone = Color(0xFF0D2116), trade = Color(0xFFF1B54A), tradeSoft = Color(0xFF33280F)
)

/** The palette for the app's current light or dark theme. */
@Composable
fun dexTrackerPalette(): DexTrackerPalette = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Dark else Light

/** Dex Tracker's type tints (--type-*), for each card's top line and wash. */
fun typeColor(type: String?): Color? = when (type) {
    "normal" -> Color(0xFF9A9C95); "fire" -> Color(0xFFEC7A2F); "water" -> Color(0xFF3B8EE3)
    "electric" -> Color(0xFFE5B412); "grass" -> Color(0xFF47A63A); "ice" -> Color(0xFF3EC2D6)
    "fighting" -> Color(0xFFCF4A3C); "poison" -> Color(0xFFA24FC6); "ground" -> Color(0xFFC08A3A)
    "flying" -> Color(0xFF7FA2EA); "psychic" -> Color(0xFFEA5480); "bug" -> Color(0xFF8EA41A)
    "rock" -> Color(0xFFB19E5F); "ghost" -> Color(0xFF6B58AD); "dragon" -> Color(0xFF4F5FDC)
    "dark" -> Color(0xFF6A5C69); "steel" -> Color(0xFF5D9BB0); "fairy" -> Color(0xFFE384D9)
    else -> null
}

/** A dashed rounded border, as the web app draws missing stages. */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    )
}

/**
 * Progress in three parts, as the web app's SegmentBar: collected on the main account (green),
 * waiting on other accounts (amber), the rest empty.
 */
@Composable
fun SegmentBar(collected: Int, trade: Int, target: Int, palette: DexTrackerPalette, height: Dp, modifier: Modifier = Modifier) {
    fun share(part: Int) = if (target == 0) 0f else minOf(part, target).toFloat() / target
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(palette.surface2)
    ) {
        val done = share(collected)
        val waiting = minOf(share(trade), 1f - done)
        if (done > 0f) Box(Modifier.fillMaxHeight().weight(done).background(palette.done))
        if (waiting > 0f) Box(Modifier.fillMaxHeight().weight(waiting).background(palette.trade.copy(alpha = 0.75f)))
        val rest = 1f - done - waiting
        if (rest > 0f) Box(Modifier.fillMaxHeight().weight(rest))
    }
}

/** "owned/target +N ⇄", green with a check when complete (the web app's count pill). */
@Composable
fun CountPill(owned: Int, target: Int, tradable: Int, complete: Boolean, palette: DexTrackerPalette, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (complete) palette.done else palette.surface2)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val content = if (complete) palette.onDone else palette.muted
        if (complete) Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text("$owned", color = if (complete) palette.onDone else palette.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("/$target", color = content, fontSize = 13.sp)
        if (tradable > 0 && !complete) Text(" +$tradable ⇄", color = palette.trade, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

/** A small round badge in a stage tile's corner. */
@Composable
fun CornerBadge(background: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, content: @Composable () -> Unit = {}) {
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) { content() }
}

/** A swatch dot as in the web app's legend. */
@Composable
fun Swatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color).border(0.dp, Color.Transparent))
}
