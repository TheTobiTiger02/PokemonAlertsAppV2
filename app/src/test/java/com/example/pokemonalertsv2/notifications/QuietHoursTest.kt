package com.example.pokemonalertsv2.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class QuietHoursTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** A fixed day, so these assertions do not depend on when the suite runs. */
    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.AUGUST, 28, hour, minute, 0)
        }.timeInMillis

    private fun quiet(now: Long, start: Int, end: Int, enabled: Boolean = true) =
        QuietHours.isQuiet(enabled, start, end, now, utc)

    // --- The wrap-past-midnight case, which is the normal configuration ---

    @Test
    fun `a window that wraps past midnight is quiet on both sides of midnight`() {
        val start = 22 * 60
        val end = 7 * 60
        assertTrue("22:00 is the first quiet minute", quiet(at(22, 0), start, end))
        assertTrue("23:30 is quiet", quiet(at(23, 30), start, end))
        assertTrue("00:00 is quiet", quiet(at(0, 0), start, end))
        assertTrue("06:59 is the last quiet minute", quiet(at(6, 59), start, end))
    }

    @Test
    fun `a wrapping window is not quiet during the day`() {
        val start = 22 * 60
        val end = 7 * 60
        assertFalse("07:00 is the end, exclusive", quiet(at(7, 0), start, end))
        assertFalse(quiet(at(12, 0), start, end))
        assertFalse("21:59 is one minute early", quiet(at(21, 59), start, end))
    }

    // --- The simple same-day case ---

    @Test
    fun `a window inside one day is quiet only inside it`() {
        val start = 13 * 60
        val end = 15 * 60
        assertFalse(quiet(at(12, 59), start, end))
        assertTrue(quiet(at(13, 0), start, end))
        assertTrue(quiet(at(14, 30), start, end))
        assertFalse("the end is exclusive", quiet(at(15, 0), start, end))
    }

    // --- Edges ---

    @Test
    fun `disabled is never quiet, whatever the window says`() {
        assertFalse(quiet(at(23, 0), 22 * 60, 7 * 60, enabled = false))
    }

    @Test
    fun `an equal start and end is no quiet period, not a whole day`() {
        // Silencing around the clock would be a surprising reading of a zero-length window.
        assertFalse(quiet(at(3, 0), 9 * 60, 9 * 60))
        assertFalse(quiet(at(9, 0), 9 * 60, 9 * 60))
    }

    @Test
    fun `normalize wraps instead of clamping`() {
        assertEquals(0, QuietHours.normalize(24 * 60))
        assertEquals(60, QuietHours.normalize(25 * 60))
        assertEquals(23 * 60, QuietHours.normalize(-60))
        assertEquals(13 * 60, QuietHours.normalize(13 * 60))
    }

    @Test
    fun `an out of range window still behaves once normalized`() {
        // 24:00 means midnight, so this is a plain 00:00-07:00 window.
        assertTrue(quiet(at(3, 0), 24 * 60, 7 * 60))
        assertFalse(quiet(at(8, 0), 24 * 60, 7 * 60))
    }

    @Test
    fun `format renders a zero padded local time`() {
        assertEquals("22:00", QuietHours.format(22 * 60))
        assertEquals("07:05", QuietHours.format(7 * 60 + 5))
        assertEquals("00:00", QuietHours.format(0))
    }

    @Test
    fun `minuteOfDay reads the local wall clock`() {
        assertEquals(13 * 60 + 45, QuietHours.minuteOfDay(at(13, 45), utc))
    }
}
