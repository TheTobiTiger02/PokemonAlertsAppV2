package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertNotificationChannelTest {
    private fun channel(vararg types: String) = AlertNotifier.notificationChannelFor(PokemonAlert(name = "x", type = types.toList()))

    @Test
    fun eachAlertTypeGetsItsOwnChannel() {
        assertEquals(AlertNotifier.CHANNEL_RAIDS, channel("Raid"))
        assertEquals(AlertNotifier.CHANNEL_HUNDOS, channel("Hundo"))
        assertEquals(AlertNotifier.CHANNEL_HUNDOS, channel("Nundo"))
        assertEquals(AlertNotifier.CHANNEL_PVP, channel("PvP"))
        assertEquals(AlertNotifier.CHANNEL_ROCKET, channel("Rocket"))
        assertEquals(AlertNotifier.CHANNEL_KECLEON, channel("Kecleon"))
        assertEquals(AlertNotifier.CHANNEL_SPAWNS, channel("Common"))
        assertEquals(AlertNotifier.CHANNEL_QUESTS, channel("Quest"))
        assertEquals(AlertNotifier.CHANNEL_ID, channel("Tappable"))
    }

    @Test
    fun aHundoThatIsAlsoPvpGoesToTheHundoChannel() {
        assertEquals(AlertNotifier.CHANNEL_HUNDOS, channel("Hundo", "PvP"))
    }
}
