package com.example.pokemonalertsv2.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize

/**
 * A small, shared motion language for the app.
 *
 * Motion stays short and low-amplitude so it explains hierarchy and state changes
 * without delaying frequent actions such as filtering or switching root tabs.
 */
internal object AppMotion {
    const val Quick = 140
    const val Standard = 240
    const val Emphasized = 320

    /**
     * Springs for motion the user causes directly - a list reordering, a card expanding,
     * a digit ticking over. Physics reads as responsive here in a way a fixed duration
     * does not, because the settle time follows how far the thing actually travelled.
     *
     * Screen and tab transitions keep the tweens above: those fire constantly while
     * filtering and switching sections, where a predictable, clipped duration matters
     * more than the bounce.
     */
    fun <T> springQuick(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> springBouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Size springs need an explicit visibility threshold, otherwise they keep animating
     * across sub-pixel distances nobody can see and the layout stays dirty for longer
     * than the motion is actually visible.
     */
    fun springSize(): SpringSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = IntSize.VisibilityThreshold
    )
}

internal fun appFadeThrough(): ContentTransform =
    (fadeIn(
        animationSpec = tween(
            durationMillis = AppMotion.Standard,
            delayMillis = AppMotion.Quick / 2,
            easing = LinearOutSlowInEasing
        )
    ) + scaleIn(
        initialScale = 0.985f,
        animationSpec = tween(
            durationMillis = AppMotion.Standard,
            delayMillis = AppMotion.Quick / 2,
            easing = LinearOutSlowInEasing
        )
    )).togetherWith(
        fadeOut(
            animationSpec = tween(
                durationMillis = AppMotion.Quick,
                easing = FastOutLinearInEasing
            )
        ) + scaleOut(
            targetScale = 0.995f,
            animationSpec = tween(
                durationMillis = AppMotion.Quick,
                easing = FastOutLinearInEasing
            )
        )
    )

internal fun appSharedAxisX(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(
            animationSpec = tween(AppMotion.Emphasized, easing = LinearOutSlowInEasing),
            initialOffsetX = { fullWidth -> direction * fullWidth / 10 }
        ) + fadeIn(
            animationSpec = tween(AppMotion.Standard, delayMillis = 60, easing = LinearOutSlowInEasing)
        )
    ).togetherWith(
        slideOutHorizontally(
            animationSpec = tween(AppMotion.Standard, easing = FastOutLinearInEasing),
            targetOffsetX = { fullWidth -> -direction * fullWidth / 14 }
        ) + fadeOut(
            animationSpec = tween(AppMotion.Quick, easing = FastOutLinearInEasing)
        )
    )
}

internal fun appExpandIn(): EnterTransition =
    expandVertically(
        expandFrom = Alignment.Top,
        animationSpec = AppMotion.springSize()
    ) + fadeIn(
        animationSpec = tween(AppMotion.Standard, delayMillis = 40, easing = LinearOutSlowInEasing)
    )

internal fun appCollapseOut(): ExitTransition =
    shrinkVertically(
        shrinkTowards = Alignment.Top,
        animationSpec = AppMotion.springSize()
    ) + fadeOut(
        animationSpec = tween(AppMotion.Quick, easing = FastOutLinearInEasing)
    )

internal fun appFadeIn(): EnterTransition =
    fadeIn(
        animationSpec = tween(AppMotion.Standard, easing = LinearOutSlowInEasing)
    )

internal fun appFadeOut(): ExitTransition =
    fadeOut(
        animationSpec = tween(AppMotion.Quick, easing = FastOutLinearInEasing)
    )

internal fun appRiseIn(): EnterTransition =
    slideInVertically(
        initialOffsetY = { height -> height / 5 },
        animationSpec = tween(AppMotion.Emphasized, easing = LinearOutSlowInEasing)
    ) + fadeIn(
        animationSpec = tween(AppMotion.Standard, delayMillis = 40, easing = LinearOutSlowInEasing)
    )

internal fun appSinkOut(): ExitTransition =
    slideOutVertically(
        targetOffsetY = { height -> height / 6 },
        animationSpec = tween(AppMotion.Standard, easing = FastOutLinearInEasing)
    ) + fadeOut(
        animationSpec = tween(AppMotion.Quick, easing = FastOutLinearInEasing)
    )
