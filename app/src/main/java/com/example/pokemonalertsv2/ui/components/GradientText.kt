package com.example.pokemonalertsv2.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.MaterialTheme

@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    gradient: Brush? = null
) {
    
    Text(
        text = text,
        style = if (gradient != null) {
            style.copy(brush = gradient)
        } else {
            style.copy(color = if (isAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        },
        modifier = modifier
    )
}
