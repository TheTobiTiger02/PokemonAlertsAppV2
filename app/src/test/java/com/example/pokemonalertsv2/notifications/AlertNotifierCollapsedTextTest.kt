package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertNotifierCollapsedTextTest {
    private val now = 1_800_000_000_000L
    private fun endsIn(minutes: Int) = java.time.Instant.ofEpochMilli(now + minutes * 60_000L).toString()

    @Test
    fun distanceLeadsTheCollapsedLine() {
        val alert = PokemonAlert(name = "Hundo Rookidee", cp = 419, type = listOf("Hundo"), endTime = endsIn(20))

        assertEquals(
            "76 m \u2022 1 min walk \u2022 20 min left \u2022 CP 419",
            AlertNotifier.buildCollapsedNotificationText(alert, "76 m", "1 min walk", isInRange = false, nowMillis = now)
        )
    }

    @Test
    fun inRangePrecedesTheDistance() {
        val alert = PokemonAlert(name = "Rayquaza", type = listOf("Raid"), endTime = endsIn(5))

        assertEquals(
            "In range \u2022 40 m \u2022 5 min left",
            AlertNotifier.buildCollapsedNotificationText(alert, "40 m", null, isInRange = true, nowMillis = now)
        )
    }

    @Test
    fun withoutAnyDetailTheCategoryStandsIn() {
        val alert = PokemonAlert(name = "Rayquaza", type = listOf("Raid"))

        assertTrue(
            AlertNotifier.buildCollapsedNotificationText(alert, null, null, isInRange = false, nowMillis = now).isNotBlank()
        )
    }
}
