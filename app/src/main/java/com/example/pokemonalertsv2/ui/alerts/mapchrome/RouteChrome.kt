package com.example.pokemonalertsv2.ui.alerts.mapchrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * The floating bar map screens share across the top: a 52 dp pill on the surface colour with a
 * soft shadow and a hairline border, so it reads over any tile.
 */
@Composable
internal fun MapPillBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().height(TopBarHeight),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** A title and a quieter line under it, for the middle of a [MapPillBar]. */
@Composable
internal fun MapPillTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What sits in a [RouteGuidanceCard]'s badge: a stop number or an icon. */
internal sealed interface RouteBadge {
    data class Number(val label: String) : RouteBadge
    data class Glyph(val icon: Painter) : RouteBadge
}

/**
 * Where you are walking next, the same card for a hunt's target and a catch route's next stop: a
 * badge in the route's colour, one bold line, one detail line, and an optional thin progress bar.
 * Tapping it opens what controls the walk.
 */
@Composable
internal fun RouteGuidanceCard(
    badge: RouteBadge,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = if (progress != null) Spacing.sm else Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        when (badge) {
                            is RouteBadge.Number -> Text(badge.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            is RouteBadge.Glyph -> Icon(badge.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                trailing?.invoke()
            }
            if (progress != null) LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).height(4.dp),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {}
            )
        }
    }
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 3.dp, border = border, content = body)
    else Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 3.dp, border = border, content = body)
}
