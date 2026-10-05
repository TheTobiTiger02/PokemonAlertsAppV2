package com.example.pokemonalertsv2.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app's own Map tab is on screen right now.
 *
 * The floating hunt map steps aside while it is: the tab already shows the same hunt, full
 * size, and the window would only sit over its controls. Process-wide state rather than an
 * intent because the tracking service and the UI share one process and the answer changes on
 * every tab switch.
 */
internal object InAppMapVisibility {
    private val visibleState = MutableStateFlow(false)

    val visible: StateFlow<Boolean> = visibleState.asStateFlow()

    fun set(visible: Boolean) {
        visibleState.value = visible
    }
}
