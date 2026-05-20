package com.example.pokemonalertsv2.wear

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearMessageListenerServiceTest {
    @Test
    fun resolveAlertByKey_prefersActiveUniqueIdMatch() {
        val expired = alert(name = "Togetic", endTime = "1000")
        val active = alert(name = "Togetic", endTime = "2000000000000")

        val resolved = WearMessageListenerService.resolveAlertByKey(
            alerts = listOf(expired, active),
            key = active.uniqueId
        )

        assertEquals(active, resolved)
    }

    @Test
    fun shouldScheduleWearSnooze_returnsFalseForExpiredAlert() {
        val expired = alert(name = "Expired", endTime = "1000")
        val active = alert(name = "Active", endTime = "2000000000000")

        assertFalse(WearMessageListenerService.shouldScheduleWearSnooze(expired, nowMillis = 2_000L))
        assertTrue(WearMessageListenerService.shouldScheduleWearSnooze(active, nowMillis = 2_000L))
    }

    private fun alert(name: String, endTime: String): PokemonAlert {
        return PokemonAlert(name = name, endTime = endTime)
    }
}
