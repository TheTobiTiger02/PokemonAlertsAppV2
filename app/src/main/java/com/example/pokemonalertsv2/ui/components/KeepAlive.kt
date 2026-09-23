package com.example.pokemonalertsv2.ui.components

import com.example.pokemonalertsv2.ui.motion.AppMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Keeps [content] composed while it is off screen, instead of disposing and rebuilding it.
 *
 * Built for the map tab: composing it costs three frames of 150 ms or more on a phone, every
 * time the tab is opened. While [visible] is false the content is not placed, so it neither
 * draws nor takes touches, and it sees a lifecycle held at CREATED - so everything that
 * follows the lifecycle (the map view's start/stop, location tracking, refresh loops) stops
 * exactly as if the app had gone to the background.
 */
@Composable
fun KeepAlive(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { CappedLifecycleOwner(parent) }
    SideEffect { owner.setCap(if (visible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED) }
    DisposableEffect(parent, owner) {
        val observer = LifecycleEventObserver { _, _ -> owner.sync() }
        parent.lifecycle.addObserver(observer)
        onDispose {
            parent.lifecycle.removeObserver(observer)
            owner.destroy()
        }
    }
    // Only the arrival fades; leaving is instant, like any bottom-navigation switch.
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) tween(AppMotion.Quick) else snap(),
        label = "keep_alive_fade"
    )
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                if (visible) placeable.place(0, 0)
            }
        }
    ) {
        CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
    }
}

/** Follows [parent], but never above [cap]. */
private class CappedLifecycleOwner(private val parent: LifecycleOwner) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private var cap = Lifecycle.State.RESUMED
    override val lifecycle: Lifecycle get() = registry

    init {
        sync()
    }

    fun setCap(value: Lifecycle.State) {
        if (cap == value) return
        cap = value
        sync()
    }

    fun sync() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        val parentState = parent.lifecycle.currentState
        // A registry cannot leave DESTROYED, and cannot enter it before INITIALIZED moves on.
        if (parentState == Lifecycle.State.DESTROYED) {
            destroy()
            return
        }
        registry.currentState = minOf(parentState, cap)
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.CREATED
        }
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
