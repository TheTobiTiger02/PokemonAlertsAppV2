package com.example.pokemonalertsv2.raidwatch

import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationField
import com.example.pokemonalertsv2.notifications.NotificationContentInput
import com.example.pokemonalertsv2.notifications.renderNotificationContent
import com.example.pokemonalertsv2.notifications.compactCatchCpText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RaidWatchNotificationsTest {

    private val now = 1_700_000_000_000L
    private val endMillis = now + TimeUnit.MINUTES.toMillis(12)

    @Test
    fun `compact hundo text carries both normal and boosted values`() {
        assertEquals(
            "2387/2984",
            compactCatchCpText(
                raid(HundoCP(level20 = 2387, level25 = 2984))
            )
        )
    }

    @Test
    fun `compact hundo text gracefully handles partial and missing data`() {
        assertEquals(
            "2387",
            compactCatchCpText(raid(HundoCP(level20 = 2387)))
        )
        assertEquals(
            "2984",
            compactCatchCpText(raid(HundoCP(level25 = 2984)))
        )
        assertEquals(null, compactCatchCpText(raid(null)))
    }

    @Test
    fun `the headline pairs the boss moveset with the time left`() {
        assertEquals(
            "12 min left • Confusion / Psystrike",
            headline(raid(null), now)
        )
    }

    @Test
    fun `the headline degrades to the time alone when the feed reports no moveset`() {
        val noMoves = raid(null).copy(moves = null)

        assertEquals("12 min left", headline(noMoves, now))
    }

    @Test
    fun `an expired raid says so instead of showing a zero countdown`() {
        val line = headline(raid(null), endMillis + 1)

        assertTrue(line.contains("Raid ended"))
    }

    @Test
    fun `each team row is numbered and keeps its moves on one line`() {
        val body = body(team(members = 3))

        assertTrue(body.contains("1. Counter 1 ×2  ·  Fast 1 › Charged 1"))
        assertEquals(3, body.lines().size)
    }

    @Test
    fun `the body spends no line on a blank separator`() {
        assertFalse(body(team(members = 3)).lines().any { it.isBlank() })
    }

    @Test
    fun `a team that has not been computed yet says it is on its way`() {
        assertTrue(body(null).contains("Building your team…"))
    }

    @Test
    fun `an empty snapshot shows why there is no team`() {
        val note = "Import a Poké Genie CSV or link Pokébattler to see your team."
        val snapshot = RaidTeamSnapshot(alertUniqueId = "id", note = note)

        assertTrue(body(snapshot).contains(note))
    }

    @Test
    fun `metadata can be hidden while keeping the expanded team`() {
        val alert = raid(HundoCP(level20 = 2387, level25 = 2984)).copy(gym = "Test Gym")

        val preferences = NotificationContentPreferences()
            .withField(FilterAlertType.RAID, true, NotificationField.VENUE, false)
            .withField(FilterAlertType.RAID, true, NotificationField.CATCH_CP, false)
            .withField(FilterAlertType.RAID, true, NotificationField.MOVES, false)
        val body = renderNotificationContent(
            NotificationContentInput(alert, nowMillis = now, endMillis = endMillis, raidWatch = true, teamText = body(team(3))),
            preferences
        ).expanded

        assertTrue(body.startsWith("1. Counter 1"))
        assertFalse(body.contains("Test Gym"))
        assertFalse(body.contains("2387"))
        assertFalse(body.contains("2984"))
        assertFalse(body.contains("Confusion"))
    }

    private fun body(snapshot: RaidTeamSnapshot?) =
        RaidWatchNotifications.buildTeamBody(snapshot)

    private fun headline(alert: PokemonAlert, atMillis: Long) = renderNotificationContent(
        NotificationContentInput(alert, nowMillis = atMillis, endMillis = endMillis),
        NotificationContentPreferences().withField(FilterAlertType.RAID, false, NotificationField.MOVES, true)
    ).collapsed

    private fun team(members: Int) = RaidTeamSnapshot(
        alertUniqueId = "id",
        members = (1..members).map { index ->
            RaidTeamMember(
                displayName = "Counter $index",
                count = 2,
                fastMove = "Fast $index",
                chargedMove = "Charged $index"
            )
        },
        goQuery = "150&CP4178"
    )

    private fun raid(hundo: HundoCP?) = PokemonAlert(
        name = "Legendary Raid",
        pokemon = "Mewtwo",
        type = listOf("Raid"),
        hundoCP = hundo,
        moves = PokemonMoves(fast = "Confusion", charged = "Psystrike")
    )
}
