package com.example.pokemonalertsv2.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    
    Text(
        text = text,
        style = if (gradient != null) {
            style.copy(brush = gradient)
        } else {
            style.copy(color = if (isAccent) colors.accent else colors.foreground)
        },
        modifier = modifier
    )
}
