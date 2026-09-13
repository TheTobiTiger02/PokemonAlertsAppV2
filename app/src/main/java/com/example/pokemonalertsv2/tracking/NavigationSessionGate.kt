package com.example.pokemonalertsv2.tracking

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes ownership changes across Hunt, arrival and Catch route stores. */
internal object NavigationSessionGate {
    private val mutex = Mutex()
    suspend fun <T> change(block: suspend () -> T): T = mutex.withLock { block() }
}
