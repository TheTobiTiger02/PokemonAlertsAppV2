package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeAppUxTest {
    @Test
    fun syncStatusDistinguishesFreshCachedAndFailedStates() {
        assertEquals(SyncStatus.Loading, AlertsUiState(isLoading = true).toSyncStatus())
        assertEquals(
            SyncStatus.Refreshing,
            AlertsUiState(alerts = listOf(PokemonAlert(name = "Test")), isLoading = true).toSyncStatus()
        )
        assertEquals(
            SyncStatus.Cached(123L),
            AlertsUiState(
                alerts = listOf(PokemonAlert(name = "Test")),
                errorMessage = "offline",
                syncMetadata = SyncMetadata(lastSuccessfulSyncMillis = 123L, isShowingCachedData = true)
            ).toSyncStatus()
        )
        assertTrue(AlertsUiState(errorMessage = "failed").toSyncStatus() is SyncStatus.Failed)
    }

    @Test
    fun syncStatusCopyDoesNotExposeTimestamps() {
        assertEquals(null, SyncStatus.Live(99_000L).alertsStatusMessage())
        assertEquals(
            "Offline · showing cached alerts",
            SyncStatus.Cached(60_000L).alertsStatusMessage()
        )
        assertEquals(null, SyncStatus.Live(99_000L).mapStatusMessage())
    }

    @Test
    fun emptyStateExplainsTheStrongestConstraint() {
        assertTrue(alertEmptyStateMessage("pika", AlertFilter.HUNDOS, "Alsbach", 10, false, true).contains("pika"))
        assertTrue(alertEmptyStateMessage("", AlertFilter.HUNDOS, "All", 0, false, true).contains("hundos"))
        assertTrue(alertEmptyStateMessage("", AlertFilter.ALL, "All", 10, false, false).contains("location"))
    }

    @Test
    fun historyQuickDatesUseServerFormat() {
        val fixed = 1_753_200_000_000L
        assertTrue(historyDateString(0, fixed).matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(86_400_000L, historyDateMillis(0, fixed) - historyDateMillis(1, fixed))
    }
}
