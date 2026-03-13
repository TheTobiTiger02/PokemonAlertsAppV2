package com.example.pokemonalertsv2.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import kotlinx.coroutines.delay

/**
 * Animated empty state with a bouncing Pokéball icon, pulsing glow, and
 * fade-in text. Usable on both the active alerts and history screens.
 *
 * @param title   Headline text (e.g. "All caught up")
 * @param message Body text (e.g. "No active alerts right now…")
 * @param ctaText Text for the action button; pass null to hide the button.
 * @param onAction Callback when the user taps the CTA button.
 */
@Composable
fun AnimatedEmptyState(
    title: String,
    message: String,
    ctaText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_state")

    // Vertical bounce (slower, smoother loop)
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // Subtle rotation wobble
    val rotation by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    // Pulsing glow scale
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Pulsing glow alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Staggered fade-in for text
    var textVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        textVisible = true
    }
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(800),
        label = "textAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glow + Pokéball
        Box(contentAlignment = Alignment.Center) {
            // Radial glow backdrop
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(glowScale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            // Bouncing + rotating Pokéball
            Icon(
                painter = painterResource(id = R.drawable.ic_placeholder),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .offset(y = bounceOffset.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.primaryContainer
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(textAlpha)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Message
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(textAlpha)
        )

        // CTA button
        if (ctaText != null && onAction != null) {
            Spacer(modifier = Modifier.height(28.dp))
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.alpha(textAlpha)
            ) {
                Text(text = ctaText)
            }
        }
    }
}
