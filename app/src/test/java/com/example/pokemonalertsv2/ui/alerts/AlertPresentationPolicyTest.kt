package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPresentationPolicyTest {
    @Test
    fun itemQuestIsTitledByItsReward() {
        val quest = PokemonAlert(
            name = "Razz Berry ×6 at Vogel Graffiti",
            type = listOf("Quest"),
            pokestop = "Vogel Graffiti",
            questReward = "Razz Berry ×6"
        )

        assertEquals("Razz Berry ×6", formatAlertTitle(quest))
        assertEquals("Quest \u00B7 Vogel Graffiti", formatAlertSubtitle(quest))
    }

    @Test
    fun questWithoutRewardFallsBackToTheNameBeforeTheStop() {
        val quest = PokemonAlert(name = "Sceptile Mega Energy ×10 at Partner-Gemeinde", type = listOf("Quest"))

        assertEquals("Sceptile Mega Energy ×10", formatAlertTitle(quest))
    }

    @Test
    fun subtitleDropsTheCategoryWhenTheTitleAlreadyNamesIt() {
        val rocket = PokemonAlert(
            name = "Rocket",
            type = listOf("Rocket"),
            gruntType = "Water",
            pokestop = "Mit Gott mittendrin"
        )

        assertEquals("Water Rocket", formatAlertTitle(rocket))
        assertEquals("Mit Gott mittendrin", formatAlertSubtitle(rocket))
    }

    @Test
    fun genericRocketUsesTeamRocketTitleAndSuppressesDuplicateCategory() {
        val title = formatAlertTitle(PokemonAlert(name = "Rocket", type = listOf("Rocket")))

        assertEquals("Team Rocket", title)
        assertFalse(shouldShowAlertCategoryLabel(title, "Rocket"))
    }

    @Test
    fun typedRocketKeepsTypeInTitle() {
        val title = formatAlertTitle(
            PokemonAlert(name = "Rocket", type = listOf("Rocket"), gruntType = "Psychic")
        )

        assertEquals("Psychic Rocket", title)
        assertFalse(shouldShowAlertCategoryLabel(title, "Rocket"))
    }

    @Test
    fun unrelatedCategoryRemainsVisible() {
        assertTrue(shouldShowAlertCategoryLabel("100% Pikachu", "Hundo"))
    }

    @Test
    fun liveActionPolicyKeepsPrimaryActionsVisibleAndSecondaryActionsInOverflow() {
        assertEquals(
            AlertActionPolicy(
                showGoing = true,
                showNavigate = true,
                overflowActions = listOf(
                    AlertSecondaryAction.SNOOZE,
                    AlertSecondaryAction.PICTURE_IN_PICTURE,
                    AlertSecondaryAction.SHARE
                )
            ),
            alertActionPolicy(
                context = AlertCardContext.LIVE,
                isExpired = false,
                snoozeEnabled = true,
                hasGoingAction = true
            )
        )
    }

    @Test
    fun activeHistoryPolicyShowsNavigateAndOnlyRelevantOverflowActions() {
        assertEquals(
            AlertActionPolicy(
                showGoing = false,
                showNavigate = true,
                overflowActions = listOf(
                    AlertSecondaryAction.PICTURE_IN_PICTURE,
                    AlertSecondaryAction.SHARE
                )
            ),
            alertActionPolicy(
                context = AlertCardContext.HISTORY,
                isExpired = false,
                snoozeEnabled = true,
                hasGoingAction = true
            )
        )
    }

    @Test
    fun expiredHistoryPolicyLeavesOnlyShare() {
        assertEquals(
            AlertActionPolicy(
                showGoing = false,
                showNavigate = false,
                overflowActions = listOf(AlertSecondaryAction.SHARE)
            ),
            alertActionPolicy(
                context = AlertCardContext.HISTORY,
                isExpired = true,
                snoozeEnabled = true,
                hasGoingAction = true
            )
        )
    }

    @Test
    fun expirationUsesTheStableClockBoundaryAndKeepsMissingTimesActive() {
        val expiresAtBoundary = PokemonAlert(
            name = "Pikachu",
            endTime = "2026-08-23T12:00:00Z"
        )
        val withoutEndTime = PokemonAlert(name = "Quest")
        val boundary = java.time.Instant.parse("2026-08-23T12:00:00Z").toEpochMilli()

        assertFalse(expiresAtBoundary.isExpiredAt(boundary - 1L))
        assertTrue(expiresAtBoundary.isExpiredAt(boundary))
        assertFalse(withoutEndTime.isExpiredAt(boundary))
    }
}
