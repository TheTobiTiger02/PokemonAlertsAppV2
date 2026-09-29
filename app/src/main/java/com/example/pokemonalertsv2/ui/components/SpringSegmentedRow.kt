package com.example.pokemonalertsv2.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.motion.AppMotion

private val TrackHeight = 40.dp
private val TrackInset = 3.dp

/**
 * A segmented control whose selection is one pill that travels between segments.
 *
 * Colouring each segment separately makes the selection blink from one place to another;
 * moving a single pill shows which way the selection went, which is the whole point of
 * putting the options side by side.
 *
 * Segments are laid out at equal width, so the pill's position is just an index, and the
 * caller does not have to report child bounds back up.
 */
@Composable
fun SpringSegmentedRow(
    selectedIndex: Int,
    segmentCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    if (segmentCount <= 0) return
    val shape = MaterialTheme.shapes.small

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
    ) {
        val segmentWidth: Dp = maxWidth / segmentCount
        // Clamped so a caller passing -1 for "nothing selected yet" cannot push the pill
        // off the track.
        val target = segmentWidth * selectedIndex.coerceIn(0, segmentCount - 1)
        val pillOffset by animateDpAsState(
            targetValue = target,
            animationSpec = AppMotion.springBouncy(),
            label = "segment-pill-offset"
        )
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                // Lambda overload keeps the animated value in the layout phase rather than
                // recomposing this Box on every frame of the travel.
                .offset { IntOffset(with(density) { pillOffset.roundToPx() }, 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(TrackInset)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .selectableGroup(),
            horizontalArrangement = Arrangement.Start,
            content = content
        )
    }
}

/** One option inside a [SpringSegmentedRow] (with `transparent = true`), or standalone. */
@Composable
fun SegmentedChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Set when the choice sits inside a SpringSegmentedRow, which paints the track and
    // the travelling selection pill itself.
    transparent: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        color = when {
            transparent -> Color.Transparent
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        },
        contentColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
