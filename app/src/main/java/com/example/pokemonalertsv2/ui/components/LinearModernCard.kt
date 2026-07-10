package com.example.pokemonalertsv2.ui.components

import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors

@Composable
fun LinearModernCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = LocalLinearModernColors.current
    var touchOffset by remember { mutableStateOf(Offset.Zero) }
    var isTouched by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressedByInteraction by interactionSource.collectIsPressedAsState()
    
    val activePressed = isTouched || isPressedByInteraction

    val scale by animateFloatAsState(
        targetValue = if (activePressed && onClick != null) 0.98f else 1.0f,
        animationSpec = tween(200, easing = EaseOutQuad),
        label = "CardScale"
    )

    val spotlightAlpha by animateFloatAsState(
        targetValue = if (activePressed) 1.0f else 0.0f,
        animationSpec = tween(300, easing = EaseOutQuad),
        label = "CardSpotlight"
    )

    val borderBrush = if (activePressed) {
        Brush.sweepGradient(listOf(colors.borderAccent, colors.borderHover, colors.borderAccent))
    } else {
        Brush.verticalGradient(listOf(colors.borderDefault, colors.borderDefault))
    }

    val glassBackgroundBrush = Brush.verticalGradient(
        colors = listOf(
            colors.surfaceTranslucent,
            colors.surfaceTranslucent.copy(alpha = colors.surfaceTranslucent.alpha * 0.4f)
        )
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .background(glassBackgroundBrush)
            .border(1.dp, borderBrush, RoundedCornerShape(16.dp))
            .drawBehind {
                // Draw touch-tracking spotlight
                if (spotlightAlpha > 0f) {
                    val radius = size.minDimension * 0.8f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accentGlow.copy(alpha = colors.accentGlow.alpha * spotlightAlpha),
                                Color.Transparent
                            ),
                            center = touchOffset,
                            radius = radius
                        ),
                        radius = radius,
                        center = touchOffset
                    )
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        isTouched = true
                        touchOffset = offset
                        try {
                            awaitRelease()
                            if (onClick != null) {
                                onClick()
                            }
                        } finally {
                            isTouched = false
                        }
                    }
                )
            }
            .then(
                if (onClick != null && !isTouched) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null, // Disable default material ripple to show custom spotlight
                        enabled = enabled,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        content()
    }
}
