package com.example.pokemonalertsv2.ui.history

import com.example.pokemonalertsv2.data.HistoryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertHistoryPagingTest {

    @Test
    fun `short page advances by returned count instead of requested limit`() {
        val progress = historyPageProgress(
            response = HistoryResponse(total = 75, offset = 0, count = 20),
            requestedOffset = 0,
            total = 75
        )

        assertEquals(20, progress.nextOffset)
        assertTrue(progress.canLoadMore)
    }

    @Test
    fun `missing response metadata uses actual data size`() {
        val progress = historyPageProgress(
            response = HistoryResponse(
                total = 75,
                data = List(3) { index -> com.example.pokemonalertsv2.data.PokemonAlert(name = "Alert $index") }
            ),
            requestedOffset = 50,
            total = 75
        )

        assertEquals(53, progress.nextOffset)
        assertTrue(progress.canLoadMore)
    }

    @Test
    fun `empty page stops pagination even when server total is larger`() {
        val progress = historyPageProgress(
            response = HistoryResponse(total = 75, offset = 50, count = 0),
            requestedOffset = 50,
            total = 75
        )

        assertEquals(50, progress.nextOffset)
        assertFalse(progress.canLoadMore)
    }

    @Test
    fun `last page stops at server total`() {
        val progress = historyPageProgress(
            response = HistoryResponse(total = 75, offset = 50, count = 25),
            requestedOffset = 50,
            total = 75
        )

        assertEquals(75, progress.nextOffset)
        assertFalse(progress.canLoadMore)
    }
}
