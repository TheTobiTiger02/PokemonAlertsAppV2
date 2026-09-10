package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The rule behind tapping a pin in the floating window.
 *
 * Kept away from the service so it can be read and tested as a rule, and so an
 * ineligible tap is answered with "no" rather than with the exception
 * ArrivalTrackingRepository.startTracking would throw.
 */
class RetargetHuntTest {

    private val now = 1_700_000_000_000L

    private fun alert(
        id: Int,
        endsInMinutes: Long = 60,
        latitude: Double? = 49.87330,
        longitude: Double? = 8.65200
    ) = PokemonAlert(
        id = id,
        name = "Larvitar",
        pokemon = "Larvitar",
        type = listOf("Spawn"),
        latitude = latitude,
        longitude = longitude,
        endTime = Instant.ofEpochMilli(now + endsInMinutes * 60_000L).toString()
    )

    @Test
    fun `tapping a different live alert switches to it`() {
        val tapped = alert(1)

        assertTrue(shouldRetargetHuntTo(tapped, currentTargetId = "server-2", huntActive = true, nowMillis = now))
    }

    @Test
    fun `tapping the one you are already walking to does nothing`() {
        val tapped = alert(1)

        assertFalse(
            shouldRetargetHuntTo(tapped, currentTargetId = tapped.uniqueId, huntActive = true, nowMillis = now)
        )
    }

    @Test
    fun `a tap outside a hunt is ignored`() {
        val tapped = alert(1)

        assertFalse(shouldRetargetHuntTo(tapped, currentTargetId = null, huntActive = false, nowMillis = now))
    }

    @Test
    fun `an expired pin is not worth walking to`() {
        val stale = alert(1, endsInMinutes = -5)

        assertFalse(shouldRetargetHuntTo(stale, currentTargetId = null, huntActive = true, nowMillis = now))
    }

    @Test
    fun `a pin with no coordinates is refused rather than thrown at startTracking`() {
        val unplaced = alert(1, latitude = null, longitude = null)

        assertFalse(shouldRetargetHuntTo(unplaced, currentTargetId = null, huntActive = true, nowMillis = now))
    }

    @Test
    fun `with no target yet, a tap still commits`() {
        val tapped = alert(1)

        assertTrue(shouldRetargetHuntTo(tapped, currentTargetId = null, huntActive = true, nowMillis = now))
    }
}
