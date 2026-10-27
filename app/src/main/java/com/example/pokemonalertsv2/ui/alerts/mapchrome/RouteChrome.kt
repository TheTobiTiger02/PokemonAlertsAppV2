package com.example.pokemonalertsv2.ui.alerts.mapchrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
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
    ChromeSurface(onClick, modifier, shape, body = body)
}

/** A surface that is a button only when there is something to do on tap. */
@Composable
private fun ChromeSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier,
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation: Dp = 3.dp,
    body: @Composable () -> Unit,
) {
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = shape, color = color,
        contentColor = contentColor, shadowElevation = elevation, border = border, content = body)
    else Surface(modifier = modifier, shape = shape, color = color,
        contentColor = contentColor, shadowElevation = elevation, border = border, content = body)
}

/** A detail line's parts; hunts separate them with "•", catch routes with "·". */
private fun detailParts(detail: String): List<String> =
    detail.split(" · ", " • ").map(String::trim).filter(String::isNotEmpty)

private val DISTANCE = Regex("""\d\s?k?m\b""")

/** The distance a detail line gives, such as "~171 m" or "1.2 km"; null when it has none. */
internal fun guidanceDistance(detail: String): String? = detailParts(detail).firstOrNull { DISTANCE.containsMatchIn(it) }

/** Everything in a detail line but its distance. */
internal fun guidanceRest(detail: String): String {
    val distance = guidanceDistance(detail)
    return detailParts(detail).filter { it != distance }.joinToString(" · ")
}

/** Where a walk goes next, for [RouteNavBanner]. */
internal class RouteGuidanceInfo(
    val badge: RouteBadge,
    val title: String,
    val detail: String,
    val progress: Float? = null,
)

@Composable
private fun BadgeCircle(badge: RouteBadge, size: Dp, color: Color, contentColor: Color) {
    Surface(shape = CircleShape, color = color, contentColor = contentColor, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            when (badge) {
                is RouteBadge.Number -> Text(badge.label, style = if (size >= 40.dp) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                is RouteBadge.Glyph -> Icon(badge.icon, contentDescription = null, modifier = Modifier.size(size * 0.55f))
            }
        }
    }
}

/**
 * A walking route's top bar: the next target in large type on the route's colour, like a
 * turn instruction. [leading] and [trailing] hold a screen's back and menu buttons when the banner
 * is its top bar; [subtitle] is the walk's own line under it.
 */
@Composable
internal fun RouteNavBanner(
    info: RouteGuidanceInfo,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ChromeSurface(onClick, modifier.fillMaxWidth(), RoundedCornerShape(20.dp), color = scheme.primary,
        contentColor = scheme.onPrimary, border = null, elevation = 6.dp) {
        Column {
            Row(Modifier.padding(start = if (leading != null) Spacing.xxs else Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                leading?.invoke()
                BadgeCircle(info.badge, 44.dp, scheme.onPrimary, scheme.primary)
                Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
                    val distance = guidanceDistance(info.detail)
                    Text(distance ?: info.title, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (distance != null) Text(info.title, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    val line = listOf(guidanceRest(info.detail), subtitle.orEmpty()).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimary.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                trailing?.invoke()
            }
            if (info.progress != null) LinearProgressIndicator(
                progress = { info.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = scheme.onPrimary,
                trackColor = scheme.onPrimary.copy(alpha = 0.25f),
                drawStopIndicator = {}
            )
        }
    }
}

/**
 * A route screen's top bar. [navigation] and [actions] are its buttons (back, menu). While walking,
 * [guidance] turns it into the [RouteNavBanner]; otherwise it is the plain pill bar.
 */
@Composable
internal fun RouteTopChrome(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    guidance: RouteGuidanceInfo? = null,
    navigation: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    if (guidance != null) RouteNavBanner(guidance, modifier, subtitle = subtitle, leading = navigation, trailing = actions)
    else MapPillBar(modifier) {
        Box(Modifier.padding(start = Spacing.xxs)) { navigation() }
        MapPillTitle(title, subtitle, Modifier.weight(1f))
        Box(Modifier.padding(end = Spacing.xxs)) { actions() }
    }
}
