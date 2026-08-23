package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPresentationPolicyTest {
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
