package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HuntTargetSummaryTest {

    private val now = 1_700_000_000_000L

    private fun endingIn(millis: Long): String =
        Instant.ofEpochMilli(now + millis).toString()

    @Test
    fun `the species names the target`() {
        val alert = PokemonAlert(name = "Larvitar @ Somewhere", type = listOf("Spawn"), pokemon = "Larvitar")

        assertEquals("Larvitar", huntTargetTitle(alert))
    }

    @Test
    fun `a rocket target drops the stop from its name`() {
        val alert = PokemonAlert(name = "Ground Grunt @ Haus der Gartenfreunde", type = listOf("Rocket"))

        assertEquals("Ground Grunt", huntTargetTitle(alert))
    }

    @Test
    fun `a nameless target still has something to call it`() {
        val alert = PokemonAlert(name = "", type = listOf("Spawn"))

        assertEquals("that one", huntTargetTitle(alert))
    }

    @Test
    fun `the detail carries CP and the estimated walk`() {
        val alert = PokemonAlert(name = "Larvitar", type = listOf("Spawn"), pokemon = "Larvitar", cp = 1234)

        val detail = huntTargetDetail(alert, distanceMeters = 400f, nowMillis = now)

        assertTrue(detail, detail.contains("CP 1234"))
        // Estimates are marked, exactly as they are on a card.
        assertTrue(detail, detail.contains("~"))
        assertTrue(detail, detail.contains("min walk"))
    }

    @Test
    fun `no location means no distance claim`() {
        val alert = PokemonAlert(name = "Larvitar", type = listOf("Spawn"), pokemon = "Larvitar", cp = 1234)

        val detail = huntTargetDetail(alert, distanceMeters = null, nowMillis = now)

        assertEquals("CP 1234", detail)
    }

    @Test
    fun `a live target reports how long is left`() {
        val alert = PokemonAlert(
            name = "Larvitar",
            type = listOf("Spawn"),
            pokemon = "Larvitar",
            cp = 1234,
            endTime = endingIn(5 * 60_000L)
        )

        assertTrue(huntTargetDetail(alert, distanceMeters = null, nowMillis = now).contains("left"))
    }

    @Test
    fun `an expired target does not report a negative countdown`() {
        val alert = PokemonAlert(
            name = "Larvitar",
            type = listOf("Spawn"),
            pokemon = "Larvitar",
            cp = 1234,
            endTime = endingIn(-60_000L)
        )

        assertFalse(huntTargetDetail(alert, distanceMeters = null, nowMillis = now).contains("left"))
    }
}
