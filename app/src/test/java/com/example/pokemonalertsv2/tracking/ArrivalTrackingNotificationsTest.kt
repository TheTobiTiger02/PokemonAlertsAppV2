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

    @Test
    fun `ongoing title includes cp for encounter`() {
        val title = ArrivalTrackingNotifications.ongoingTitle(
            PokemonAlert(
                name = "Hundo Pikachu",
                pokemon = "Pikachu",
                cp = 938
            )
        )

        assertTrue(title.contains("Going to Pikachu"))
        assertTrue(title.contains("CP 938"))
    }

    @Test
    fun `ongoing title uses new cp for weather change`() {
        val title = ArrivalTrackingNotifications.ongoingTitle(
            PokemonAlert(
                name = "Weather Gyarados",
                pokemon = "Gyarados",
                type = listOf("WeatherChange"),
                cp = 2000,
                newCp = 2500
            )
        )

        assertTrue(title.contains("Going to Gyarados"))
        assertTrue(title.contains("CP 2500"))
        assertTrue(!title.contains("CP 2000"))
    }

    @Test
    fun `ongoing title omits cp for raid without exact cp`() {
        val title = ArrivalTrackingNotifications.ongoingTitle(
            PokemonAlert(
                name = "Legendary Raid",
                pokemon = "Mewtwo",
                type = listOf("Raid"),
                hundoCP = HundoCP(level20 = 2387, level25 = 2984)
            )
        )

        assertTrue(title.contains("Going to Mewtwo"))
        assertTrue(!title.contains("CP"))
    }

    @Test
    fun `ongoing title falls back to alert name`() {
        val title = ArrivalTrackingNotifications.ongoingTitle(
            PokemonAlert(
                name = "Field Research",
                type = listOf("Quest"),
                questTask = "Make 3 Great Throws"
            )
        )

        assertTrue(title.contains("Going to Field Research"))
    }

    @Test
    fun `expanded body puts remaining time and cp on second line`() {
        val body = ArrivalTrackingNotifications.buildExpandedBody(
            alert = PokemonAlert(
                name = "Hundo Pikachu",
                pokemon = "Pikachu",
                cp = 938,
                endTime = "1060000"
            ),
            content = "120 m away \u2022 Arrival at 40 m",
            remaining = " \u2022 1m 00s left"
        )

        assertTrue(body.startsWith("120 m away \u2022 Arrival at 40 m"))
        assertTrue(body.contains("\n"))
        assertTrue(body.contains("1m 00s left"))
        assertTrue(body.contains("CP 938"))
    }

    @Test
    fun `expanded body falls back to content without detail`() {
        val body = ArrivalTrackingNotifications.buildExpandedBody(
            alert = PokemonAlert(
                name = "Legendary Raid",
                pokemon = "Mewtwo",
                type = listOf("Raid")
            ),
            content = "2.1 km away \u2022 Arrival at 40 m",
            remaining = ""
        )

        assertTrue(body == "2.1 km away \u2022 Arrival at 40 m")
    }
}
