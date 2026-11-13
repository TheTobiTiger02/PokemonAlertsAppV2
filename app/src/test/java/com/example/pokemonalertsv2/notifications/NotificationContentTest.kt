package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.*
import org.junit.Assert.*
import org.junit.Test

class NotificationContentTest {
    private val now = 1_800_000_000_000L
    private val raid = PokemonAlert(
        name = "Legendary Raid", pokemon = "Mewtwo", type = listOf("Raid"),
        currentWeather = "Windy", isWeatherBoosted = true,
        hundoCP = HundoCP(2387, 2984), gym = "Central Gym",
        moves = PokemonMoves("Confusion", "Psystrike"),
        endTime = (now + 12 * 60_000L).toString()
    )

    @Test
    fun inRangeHidesTravelInBothViewsEvenWithALongDetour() {
        val content = render(raid, inRange = true)
        for (body in listOf(content.collapsed, content.expanded)) {
            assertTrue(body.startsWith("In range"))
            assertFalse(body.contains("320 m"))
            assertFalse(body.contains("4 min walk"))
            assertTrue(body.contains("12 min left"))
        }
    }

    @Test
    fun raidWeatherAndBoostLeadTravelAndMoves() {
        val content = render(raid)
        assertTrue(content.collapsed.startsWith("💨 Windy • Boosted • 320 m"))
        assertTrue(content.expanded.indexOf("Windy") < content.expanded.indexOf("Confusion"))
    }

    @Test
    fun finalMinuteKeepsTheRaidCountdownInSeconds() {
        val content = render(raid.copy(endTime = (now + 20_000L).toString()), raidWatch = true)
        assertTrue(content.collapsed.contains("20s left"))
        assertFalse(content.collapsed.contains("1 min left"))
    }

    @Test
    fun missingOrUnconfirmedWeatherNeverInventsAConditionOrBoost() {
        val unconfirmed = render(raid.copy(currentWeatherConfirmed = false, isWeatherBoosted = false))
        assertTrue(unconfirmed.collapsed.startsWith("💨 Windy?"))
        assertFalse(unconfirmed.collapsed.contains("Boosted"))
        val missing = render(raid.copy(currentWeather = null, isWeatherBoosted = null))
        assertFalse(missing.collapsed.contains("Windy"))
        assertFalse(missing.collapsed.contains("Boosted"))
    }

    @Test
    fun collapsedAndExpandedSelectionsAreIndependentAndDoNotLeakThroughTheChip() {
        val preferences = NotificationContentPreferences()
            .withField(FilterAlertType.RAID, false, NotificationField.WEATHER, false)
            .withField(FilterAlertType.RAID, false, NotificationField.CATCH_CP, false)
            .withField(FilterAlertType.RAID, false, NotificationField.TIME_LEFT, false)
        val content = render(raid, preferences, raidWatch = true)
        assertFalse(content.collapsed.contains("Windy"))
        assertFalse(content.collapsed.contains("2387"))
        assertFalse(content.collapsed.contains("12 min"))
        assertEquals("Raids", content.chip)
        assertTrue(content.expanded.contains("Windy"))
        assertTrue(content.expanded.contains("2387"))
        assertFalse(content.showCountdown)
    }

    @Test
    fun allFieldsOffKeepsIdentityAndCriticalStatus() {
        var preferences = NotificationContentPreferences()
        for (expanded in listOf(false, true)) {
            for (field in notificationFields(FilterAlertType.RAID, expanded)) {
                preferences = preferences.withField(FilterAlertType.RAID, expanded, field, false)
            }
        }
        val content = render(raid, preferences, inRange = true)
        assertEquals("Mewtwo Raid", content.title)
        assertEquals("In range", content.collapsed)
        assertEquals("In range", content.expanded)
        assertEquals("In range", content.chip)
    }

    @Test
    fun encounterOptionalFactsStayOutOfIdentityTitles() {
        val alert = PokemonAlert(name = "Hundo Pikachu", pokemon = "Pikachu", type = listOf("Hundo"), cp = 938, iv = "15/15/15")
        val content = render(alert)
        assertEquals("Pikachu", content.title)
        assertTrue(content.collapsed.contains("CP 938"))
        assertTrue(content.expanded.contains("IV 15/15/15"))
    }

    @Test
    fun raidTeamComesBeforeOtherExpandedDetailsAndCanBeHiddenIndependently() {
        val content = render(raid, raidWatch = true)
        assertTrue(content.expanded.startsWith("1. Tyranitar"))
        val hidden = NotificationContentPreferences().withField(FilterAlertType.RAID, true, NotificationField.TEAM, false)
        val withoutTeam = render(raid, hidden, raidWatch = true)
        assertFalse(withoutTeam.expanded.contains("Tyranitar"))
        assertTrue(withoutTeam.expanded.contains("Windy"))
    }

    @Test
    fun blankLegacyIdentityAndMultilineDescriptionStillProduceUsableText() {
        val content = render(PokemonAlert(name = "", description = "First line\nSecond line"))
        assertEquals("Other", content.title)
        val selected = NotificationContentPreferences().withField(FilterAlertType.OTHER, false, NotificationField.DESCRIPTION, true)
        assertFalse(render(PokemonAlert(name = "", description = "First\nSecond"), selected).collapsed.contains("\n"))
    }

    @Test
    fun descriptionsAreOptInForStructuredAlerts() {
        val alert = raid.copy(description = "Windy and CP 2387")
        assertFalse(render(alert).expanded.contains("Windy and CP"))
        val enabled = NotificationContentPreferences().withField(FilterAlertType.RAID, true, NotificationField.DESCRIPTION, true)
        assertTrue(render(alert, enabled).expanded.contains("Windy and CP"))
    }

    @Test
    fun weatherChangesUseRevisedValuesAndKeepTheTransitionSelectable() {
        val alert = PokemonAlert(
            name = "Weather Pikachu", pokemon = "Pikachu", type = listOf("WeatherChange"),
            cp = 938, newCp = 1040, iv = "15/15/15", newIv = "10/12/14",
            weatherFrom = "Clear", weatherTo = "Windy"
        )
        val content = render(alert)
        assertTrue(content.expanded.contains("CP 1040"))
        assertFalse(content.expanded.contains("CP 938"))
        assertTrue(content.expanded.contains("IV 10/12/14"))
        assertFalse(content.title.contains("Clear"))
    }

    private fun render(
        alert: PokemonAlert,
        preferences: NotificationContentPreferences = NotificationContentPreferences(),
        inRange: Boolean = false,
        raidWatch: Boolean = false
    ) = renderNotificationContent(
        NotificationContentInput(
            alert, "320 m", "4 min walk", inRange = inRange,
            nowMillis = now, raidWatch = raidWatch,
            teamText = "1. Tyranitar ×2 · Bite › Brutal Swing"
        ), preferences
    )
}
