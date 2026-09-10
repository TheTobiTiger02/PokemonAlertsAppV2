package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.CaughtAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUndoTest {

    private val now = 1_700_000_000_000L

    private fun caught(at: Long) = CaughtAlert(id = "abc", displayName = "Larvitar", caughtAtMillis = at)

    @Test
    fun `a fresh catch can be undone`() {
        assertTrue(isUndoOfferLive(caught(now - 5_000L), now))
    }

    @Test
    fun `the offer expires at the end of the window`() {
        assertTrue(isUndoOfferLive(caught(now - CATCH_UNDO_WINDOW_MILLIS), now))
        assertFalse(isUndoOfferLive(caught(now - CATCH_UNDO_WINDOW_MILLIS - 1L), now))
    }

    @Test
    fun `a record from before the offer had a clock is already expired`() {
        // Upgrading from a build with no timestamp must not leave the old
        // never-expiring button sitting there.
        assertFalse(isUndoOfferLive(caught(0L), now))
    }

    @Test
    fun `a catch from the future is not offered`() {
        // A clock change should not resurrect an offer for longer than the window.
        assertFalse(isUndoOfferLive(caught(now + 1_000L), now))
    }

    @Test
    fun `nothing caught means nothing to offer`() {
        assertFalse(isUndoOfferLive(null, now))
    }

    @Test
    fun `the label names what was caught`() {
        assertEquals("Undo catching Larvitar", undoOfferLabel(caught(now)))
    }
}
