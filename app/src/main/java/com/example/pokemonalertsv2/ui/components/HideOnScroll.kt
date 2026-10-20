package com.example.pokemonalertsv2.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A toolbar that steps aside while a list scrolls down and comes back as soon as it scrolls up,
 * the way app bars "enter always": the list gets the toolbar's height on a short screen, and the
 * toolbar is never more than a flick away. Attach [connection] to the list's parent with
 * `Modifier.nestedScroll`, and show the toolbar while [visible].
 */
@Stable
internal class HideOnScrollState(private val thresholdPx: Float) {
    var visible by mutableStateOf(true)
        private set

    private var travel = 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Count travel in one direction only; a change of direction starts over, so small
            // jitters at the end of a fling do not flip the toolbar.
            if (travel != 0f && (travel > 0) != (available.y > 0)) travel = 0f
            travel += available.y
            when {
                travel < -thresholdPx -> { visible = false; travel = 0f }
                travel > thresholdPx -> { visible = true; travel = 0f }
            }
            return Offset.Zero
        }
    }

    fun show() {
        visible = true
        travel = 0f
    }
}

@Composable
internal fun rememberHideOnScrollState(): HideOnScrollState {
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    return remember(threshold) { HideOnScrollState(threshold) }
}
