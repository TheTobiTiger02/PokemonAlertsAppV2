package com.example.pokemonalertsv2.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import com.example.pokemonalertsv2.ui.motion.AppMotion

/**
 * True when the user has turned system animations off.
 *
 * Countdowns tick once a second, so honouring this matters more here than for one-off
 * transitions: an unwanted animation would never stop.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    return remember(context, inspecting) {
        if (inspecting) {
            true
        } else {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            }.getOrDefault(1f) == 0f
        }
    }
}

/**
 * Renders [text] so that only the characters that actually changed animate.
 *
 * Each character position owns its own [AnimatedContent], which means a countdown going
 * from `12m 05s` to `12m 04s` moves a single digit instead of replacing the whole label.
 * Digits roll downward to match the direction a countdown reads.
 *
 * Falls back to a plain [Text] when animations are disabled system-wide.
 */
@Composable
fun RollingNumberText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = 1
) {
    // Tabular figures keep the row from reflowing as digits change width.
    val digitStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }

    if (rememberReducedMotion()) {
        Text(
            text = text,
            modifier = modifier,
            style = digitStyle,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    Row(
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, character ->
            if (!character.isDigit()) {
                // Separators and unit suffixes never animate; only the numbers move.
                Text(text = character.toString(), style = digitStyle, color = color)
                return@forEachIndexed
            }
            AnimatedContent(
                targetState = character,
                // slideIn/slideOut translate through a graphicsLayer, which is not clipped:
                // without this the outgoing and incoming digits are drawn outside the text
                // line and overlap whatever sits above or below.
                modifier = Modifier.clipToBounds(),
                transitionSpec = {
                    (
                        slideInVertically(
                            animationSpec = tween(AppMotion.Quick),
                            initialOffsetY = { height -> -height }
                        ) + fadeIn(animationSpec = tween(AppMotion.Quick))
                    ).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(AppMotion.Quick),
                            targetOffsetY = { height -> height }
                        ) + fadeOut(animationSpec = tween(AppMotion.Quick))
                    )
                },
                label = "rolling-digit-$index"
            ) { digit ->
                Text(text = digit.toString(), style = digitStyle, color = color)
            }
        }
    }
}
