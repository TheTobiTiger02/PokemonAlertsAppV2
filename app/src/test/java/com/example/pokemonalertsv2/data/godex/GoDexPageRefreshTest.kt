package com.example.pokemonalertsv2.data.godex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDexPageRefreshTest {
    @Test
    fun pageRefreshRequiresConnectionAndAnIdleSyncPipeline() {
        assertFalse(
            shouldAttemptGoDexPageRefresh(
                isConnected = false,
                isSyncInProgress = false,
                lastAttemptMillis = 0L,
                nowMillis = GoDexRepository.PAGE_REFRESH_INTERVAL_MILLIS
            )
        )
        assertFalse(
            shouldAttemptGoDexPageRefresh(
                isConnected = true,
                isSyncInProgress = true,
                lastAttemptMillis = 0L,
                nowMillis = GoDexRepository.PAGE_REFRESH_INTERVAL_MILLIS
            )
        )
    }

    @Test
    fun pageRefreshIsThrottledForFifteenMinutes() {
        val interval = GoDexRepository.PAGE_REFRESH_INTERVAL_MILLIS
        assertFalse(
            shouldAttemptGoDexPageRefresh(
                isConnected = true,
                isSyncInProgress = false,
                lastAttemptMillis = 1_000L,
                nowMillis = 1_000L + interval - 1L
            )
        )
        assertTrue(
            shouldAttemptGoDexPageRefresh(
                isConnected = true,
                isSyncInProgress = false,
                lastAttemptMillis = 1_000L,
                nowMillis = 1_000L + interval
            )
        )
    }
}
