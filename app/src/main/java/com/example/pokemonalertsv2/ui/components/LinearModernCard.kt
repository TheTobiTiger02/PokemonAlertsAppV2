package com.example.pokemonalertsv2.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.motion.AppMotion

@Composable
fun LinearModernCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    /**
     * Off by default. The one shipped caller is the alert card, which lives in a lazy list
     * item that already carries [androidx.compose.foundation.lazy.grid.LazyGridItemScope.animateItem];
     * running both meant two size animations per row and visible jank on pull-to-refresh.
     * Turn it on for a card that genuinely expands in place outside a lazy list.
     */
    animateSizeChanges: Boolean = false,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.985f else 1f,
        animationSpec = AppMotion.springQuick(),
        label = "cardScale"
    )

    Surface(
        modifier = modifier
            // Lambda overload: the non-lambda one reads the animated scale in composition,
            // recomposing the whole card for every frame of the press.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                // Cards that expand in place grow to the new height instead of snapping,
                // so the rows below are pushed rather than teleported.
                if (animateSizeChanges) {
                    Modifier.animateContentSize(animationSpec = AppMotion.springSize())
                } else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}
