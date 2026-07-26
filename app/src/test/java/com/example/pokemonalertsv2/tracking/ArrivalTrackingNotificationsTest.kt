package com.example.pokemonalertsv2.tracking

import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalTrackingNotificationsTest {
    @Test
    fun `encounter arrival includes visibility cp iv form and remaining time`() {
        val body = ArrivalTrackingNotifications.buildArrivalBody(
            alert = PokemonAlert(
                name = "Hundo Pikachu",
                pokemon = "Pikachu",
                pokemonForm = "Normal",
                type = listOf("Hundo"),
                cp = 938,
                iv = "15/15/15",
                latitude = 49.86,
                longitude = 8.65,
                endTime = "1060000"
            ),
            radiusMeters = 40,
            nowMillis = 1_000_000L
        )

        assertTrue(body.contains("visible in Pok\u00e9mon GO"))
        assertTrue(body.contains("CP 938"))
        assertTrue(body.contains("IV 15/15/15"))
        assertTrue(body.contains("Normal"))
        assertTrue(body.contains("1m 00s left"))
    }

    @Test
    fun `raid arrival includes gym and hundo catch values`() {
        val body = ArrivalTrackingNotifications.buildArrivalBody(
            alert = PokemonAlert(
                name = "Legendary Raid",
                pokemon = "Mewtwo",
                type = listOf("Raid"),
                gym = "Central Gym",
                hundoCP = HundoCP(level20 = 2387, level25 = 2984),
                latitude = 49.86,
                longitude = 8.65
            ),
            radiusMeters = 40,
            nowMillis = 1_000_000L
        )

        assertTrue(body.contains("Central Gym"))
        assertTrue(body.contains("100% L20 2387"))
        assertTrue(body.contains("100% L25 2984"))
    }

    @Test
    fun `quest arrival includes stop task and reward`() {
        val body = ArrivalTrackingNotifications.buildArrivalBody(
            alert = PokemonAlert(
                name = "Field Research",
                type = listOf("Quest"),
                pokestop = "Library Stop",
                questTask = "Make 3 Great Throws",
                questReward = "Rare Candy",
                latitude = 49.86,
                longitude = 8.65
            ),
            radiusMeters = 80,
            nowMillis = 1_000_000L
        )

        assertTrue(body.contains("Library Stop"))
        assertTrue(body.contains("Make 3 Great Throws"))
        assertTrue(body.contains("Rare Candy"))
    }

    @Test
    fun `generic arrival reports selected radius`() {
        val body = ArrivalTrackingNotifications.buildArrivalBody(
            alert = PokemonAlert(
                name = "Other alert",
                latitude = 49.86,
                longitude = 8.65
            ),
            radiusMeters = 75,
            nowMillis = 1_000_000L
        )

        assertTrue(body.contains("within 75 m"))
    }
}
