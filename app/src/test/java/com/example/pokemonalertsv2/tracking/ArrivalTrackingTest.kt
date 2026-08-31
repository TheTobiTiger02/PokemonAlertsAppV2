package com.example.pokemonalertsv2.tracking

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalTrackingTest {
    @Test
    fun `eligible destination accepts future and missing end times`() {
        val now = 1_000_000_000_000L
        assertTrue(alert(endTime = (now + 60_000L).toString()).isEligibleArrivalDestination(now))
        assertTrue(alert(endTime = "").isEligibleArrivalDestination(now))
    }

    @Test
    fun `eligible destination rejects expired invalidated and invalid coordinates`() {
        val now = 1_000_000_000_000L
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
            evaluator.evaluate(20f, 201f, 40, 7_000L)
        )
    }

    @Test
    fun `phone grade accuracy can confirm a direct position already in range`() {
        val evaluator = ArrivalFixEvaluator()
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(40f, 150f, 40, 1_000L, gpsToleranceMeters = 20f)
        )
        assertEquals(
            ArrivalFixResult.ARRIVED,
            evaluator.evaluate(39f, 150f, 40, 3_000L, gpsToleranceMeters = 20f)
        )
    }

    @Test
    fun `coarse accuracy cannot expand the direct interaction radius`() {
        val evaluator = ArrivalFixEvaluator()
        assertEquals(
            ArrivalFixResult.WAITING,
            evaluator.evaluate(50f, 150f, 40, 1_000L, gpsToleranceMeters = 20f)
        )
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(50f, 85f, 40, 2_000L, gpsToleranceMeters = 20f)
        )
    }

    @Test
    fun `gps tolerance is bounded by accuracy and twenty meters`() {
        val evaluator = ArrivalFixEvaluator()
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(60f, 30f, 40, 1_000L, gpsToleranceMeters = 20f)
        )
        assertEquals(
            ArrivalFixResult.WAITING,
            evaluator.evaluate(61f, 30f, 40, 2_000L, gpsToleranceMeters = 20f)
        )
        assertEquals(
            ArrivalFixResult.FIRST_IN_RANGE,
            evaluator.evaluate(50f, 10f, 40, 3_000L, gpsToleranceMeters = 20f)
        )
        assertEquals(
            ArrivalFixResult.WAITING,
            evaluator.evaluate(51f, 10f, 40, 4_000L, gpsToleranceMeters = 20f)
        )
    }

    @Test
    fun `radius normalization clamps and snaps to five meter steps`() {
        assertEquals(20, ArrivalTrackingRepository.normalizeRadius(1))
        assertEquals(40, ArrivalTrackingRepository.normalizeRadius(38))
        assertEquals(45, ArrivalTrackingRepository.normalizeRadius(43))
        assertEquals(200, ArrivalTrackingRepository.normalizeRadius(999))
    }

    @Test
    fun `gyms raids and pokestops always use eighty meters`() {
        assertEquals(80, alert(type = listOf("Raid")).effectiveArrivalRadius(35))
        assertEquals(80, alert(gym = "Central Gym").effectiveArrivalRadius(120))
        assertEquals(80, alert(type = listOf("Quest")).effectiveArrivalRadius(35))
        assertEquals(80, alert(pokestop = "Library Stop").effectiveArrivalRadius(120))
    }

    @Test
    fun `spawns use forty meters or eighty with spacial rend`() {
        assertEquals(40, alert(type = listOf("Spawn")).effectiveArrivalRadius(120))
        assertEquals(
            80,
            alert(type = listOf("Spawn")).effectiveArrivalRadius(
                configuredRadiusMeters = 35,
                spacialRendEnabled = true
            )
        )
    }

    @Test
    fun `other free coordinate alerts retain configured radius`() {
        assertEquals(35, alert(pokemon = null, type = listOf("Other")).effectiveArrivalRadius(35))
        assertEquals(45, alert(pokemon = null, type = listOf("Other")).effectiveArrivalRadius(43))
    }

    private fun alert(
        latitude: Double? = 49.86,
        longitude: Double? = 8.65,
        endTime: String = "",
        invalidatedAt: String? = null,
        pokemon: String? = "Pikachu",
        type: List<String>? = null,
        gym: String? = null,
        pokestop: String? = null
    ) = PokemonAlert(
        name = "Test alert",
        pokemon = pokemon,
        latitude = latitude,
        longitude = longitude,
        endTime = endTime,
        invalidatedAt = invalidatedAt,
        type = type,
        gym = gym,
        pokestop = pokestop
    )
}
