package com.example.pokemonalertsv2.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {
    @Test
    fun doublesPerFailureUpToTheCap() {
        val delays = (0..8).map { exponentialBackoffMillis(it, baseMillis = 2_000, maxMillis = 60_000, maxDoublings = 5) }
        assertEquals(listOf(0L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L), delays)
    }

    @Test
    fun doublingCapStopsBeforeTheCeiling() {
        assertEquals(8_000L, exponentialBackoffMillis(10, baseMillis = 1_000, maxMillis = 60_000, maxDoublings = 3))
    }
}
