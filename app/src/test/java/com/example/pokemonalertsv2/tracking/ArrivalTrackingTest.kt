package com.example.pokemonalertsv2.tracking

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalTrackingTest {
    @Test
    fun `eligible destination accepts future and missing end times`() {
        val now = 1_000_000L
        assertTrue(alert(endTime = (now + 60_000L).toString()).isEligibleArrivalDestination(now))
        assertTrue(alert(endTime = "").isEligibleArrivalDestination(now))
    }

    @Test
    fun `eligible destination rejects expired invalidated and invalid coordinates`() {
        val now = 1_000_000L
        assertFalse(alert(endTime = (now - 1L).toString()).isEligibleArrivalDestination(now))
        assertFalse(alert(invalidatedAt = "now").isEligibleArrivalDestination(now))
        assertFalse(alert(latitude = 0.0, longitude = 0.0).isEligibleArrivalDestination(now))
        assertFalse(alert(latitude = 91.0).isEligibleArrivalDestination(now))
    }

    @Test
    fun `arrival requires two qualifying fixes at least two seconds apart`() {
        val evaluator = ArrivalFixEvaluator()
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(40f, 10f, 40, 1_000L)
        )
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(39f, 10f, 40, 2_999L)
        )
        assertEquals(
            ArrivalFixResult.ARRIVED,
            evaluator.evaluate(40f, 40f, 40, 3_000L)
        )
    }

    @Test
    fun `outside or inaccurate fix resets arrival confirmation`() {
        val evaluator = ArrivalFixEvaluator()
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(20f, 10f, 40, 1_000L)
        )
        assertEquals(
            ArrivalFixResult.WAITING,
            evaluator.evaluate(41f, 10f, 40, 2_000L)
        )
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(20f, 10f, 40, 4_000L)
        )
        assertEquals(
            ArrivalFixResult.WAITING,
            evaluator.evaluate(20f, 41f, 40, 7_000L)
        )
    }

    @Test
    fun `radius normalization clamps and snaps to five meter steps`() {
        assertEquals(20, ArrivalTrackingRepository.normalizeRadius(1))
        assertEquals(40, ArrivalTrackingRepository.normalizeRadius(38))
        assertEquals(45, ArrivalTrackingRepository.normalizeRadius(43))
        assertEquals(200, ArrivalTrackingRepository.normalizeRadius(999))
    }

    private fun alert(
        latitude: Double? = 49.86,
        longitude: Double? = 8.65,
        endTime: String = "",
        invalidatedAt: String? = null
    ) = PokemonAlert(
        name = "Test alert",
        pokemon = "Pikachu",
        latitude = latitude,
        longitude = longitude,
        endTime = endTime,
        invalidatedAt = invalidatedAt
    )
}
