package com.example.pokemonalertsv2.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors

@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    gradient: Brush? = null
) {
    val colors = LocalLinearModernColors.current
    
    val textBrush = when {
        gradient != null -> gradient
        isAccent -> Brush.linearGradient(
            colors = listOf(
                colors.accent,
                Color(0xFF8A76FF),
                colors.accent
            )
        )
        else -> Brush.verticalGradient(
            colors = listOf(
                colors.foreground,
                colors.foreground.copy(alpha = 0.7f)
            )
        )
    }

    Text(
        text = text,
        style = style.copy(brush = textBrush),
        modifier = modifier
    )
}
