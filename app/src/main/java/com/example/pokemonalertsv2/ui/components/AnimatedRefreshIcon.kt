package com.example.pokemonalertsv2.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import com.example.pokemonalertsv2.R

/** Spins only while a user-visible refresh is active, then settles immediately. */
@Composable
internal fun AnimatedRefreshIcon(
    refreshing: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val rotation = if (refreshing) {
        val transition = rememberInfiniteTransition(label = "refresh")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing)
            ),
            label = "refresh_rotation"
        )
        angle
    } else {
        0f
    }
    Icon(
        painter = painterResource(id = R.drawable.ic_refresh),
        contentDescription = contentDescription,
        modifier = modifier.rotate(rotation)
    )
}
