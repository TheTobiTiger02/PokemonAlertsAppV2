package com.example.pokemonalertsv2.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors

@Composable
fun LinearModernBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalLinearModernColors.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()

    // Base background brush
    val baseBackgroundBrush = if (isDark) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF0A0A0F),
                colors.bgBase,
                colors.bgDeep
            ),
            center = Offset(500f, 0f),
            radius = 2000f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFFAF9FD),
                colors.bgBase,
                colors.bgDeep
            ),
            center = Offset(500f, 0f),
            radius = 2000f
        )
    }

    // Animation for floating blobs
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundBlobs")
    
    val floatY1 by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1_y"
    )
    val floatX1 by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1_x"
    )

    val floatY2 by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = -40f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2_y"
    )
    val floatX2 by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2_x"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBackgroundBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Draw floating ambient blobs
            // Blob 1: Top-Center Indigo/Accent Glow
            val blob1Color = colors.accentGlow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob1Color, Color.Transparent),
                    center = Offset(width * 0.5f + floatX1, height * 0.15f + floatY1),
                    radius = width * 0.6f
                ),
                radius = width * 0.6f,
                center = Offset(width * 0.5f + floatX1, height * 0.15f + floatY1)
            )

            // Blob 2: Left-Middle Subtle Purple/Blue Glow
            val blob2Color = if (isDark) Color(0x269C27B0) else Color(0x149C27B0) // Purple
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob2Color, Color.Transparent),
                    center = Offset(width * 0.1f + floatX2, height * 0.5f + floatY2),
                    radius = width * 0.5f
                ),
                radius = width * 0.5f,
                center = Offset(width * 0.1f + floatX2, height * 0.5f + floatY2)
            )

            // Blob 3: Right-Bottom Accent Glow
            val blob3Color = if (isDark) Color(0x1F00BCD4) else Color(0x0F00BCD4) // Teal/Cyan
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob3Color, Color.Transparent),
                    center = Offset(width * 0.8f + floatX1 * 0.8f, height * 0.8f + floatY2 * 0.8f),
                    radius = width * 0.4f
                ),
                radius = width * 0.4f,
                center = Offset(width * 0.8f + floatX1 * 0.8f, height * 0.8f + floatY2 * 0.8f)
            )

            // 2. Draw technical grid overlay
            val gridSpacingPx = with(density) { 64.dp.toPx() }
            val gridColor = if (isDark) Color(0x05FFFFFF) else Color(0x0A000000)
            
            // Vertical lines
            var x = 0f
            while (x < width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                x += gridSpacingPx
            }

            // Horizontal lines
            var y = 0f
            while (y < height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                y += gridSpacingPx
            }
        }

        content()
    }
}
