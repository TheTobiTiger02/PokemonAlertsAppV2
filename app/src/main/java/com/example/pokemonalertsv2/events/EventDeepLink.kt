package com.example.pokemonalertsv2.events

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An event someone asked to open from outside the tab (the events widget), waiting for the
 * Events tab to show it. Process-wide like [com.example.pokemonalertsv2.tracking.InAppMapVisibility]:
 * the tab stays composed between visits, so it picks the request up whenever it arrives.
 */
object EventDeepLink {
    private val pendingState = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = pendingState.asStateFlow()

    fun open(eventId: String) {
        pendingState.value = eventId
    }

    fun consume(eventId: String) {
        pendingState.compareAndSet(eventId, null)
    }
}
