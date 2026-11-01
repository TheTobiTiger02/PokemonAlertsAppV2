package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.events.GameEvent
import com.example.pokemonalertsv2.events.parseEventTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class EventsWidgetRowsTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("UTC")
    private val locale = Locale.UK

    private fun event(id: String, type: String, start: String, end: String) =
        GameEvent(id = id, name = id, eventType = type, startAt = start, endAt = end)

    private val events = listOf(
        event("cd", "community-day", "2026-10-25T14:00:00Z", "2026-10-25T17:00:00Z"),
        event("season", "season", "2026-09-01T10:00:00Z", "2026-12-01T10:00:00Z"),
        event("raid", "raid-hour", "2026-10-21T18:00:00Z", "2026-10-21T19:00:00Z"),
        event("over", "event", "2026-10-10T10:00:00Z", "2026-10-11T10:00:00Z")
    )

    private fun ids(rows: List<EventsWidgetRow>) =
        rows.map { if (it is EventsWidgetRow.Header) it.title else (it as EventsWidgetRow.Event).event.id }

    @Test fun `groups running events under now and the rest under upcoming`() {
        val rows = eventsWidgetRows(events, now, hiddenTypes = emptySet(), zone = zone, locale = locale)
        assertEquals(listOf("Now", "season", "Upcoming", "raid", "cd"), ids(rows))
        assertTrue((rows[1] as EventsWidgetRow.Event).running)
    }

    @Test fun `running events show their end date and time left`() {
        val rows = eventsWidgetRows(events, now, hiddenTypes = emptySet(), zone = zone, locale = locale)
        assertEquals("Ends Tue 1 Dec 10:00 · 41 d 22 h left", (rows[1] as EventsWidgetRow.Event).detail)
    }

    @Test fun `upcoming events show their date and how many days away`() {
        val rows = eventsWidgetRows(events, now, hiddenTypes = emptySet(), zone = zone, locale = locale)
        assertEquals("Wed 21 Oct 18:00 – 19:00 · tomorrow", (rows[3] as EventsWidgetRow.Event).detail)
        assertEquals("Sun 25 Oct 14:00 – 17:00 · in 5 days", (rows[4] as EventsWidgetRow.Event).detail)
    }

    @Test fun `an event later today says how long until it starts`() {
        val later = event("later", "raid-hour", "2026-10-20T18:00:00Z", "2026-10-20T19:00:00Z")
        val row = eventsWidgetRows(listOf(later), now, emptySet(), zone, locale)[1] as EventsWidgetRow.Event
        assertEquals("Tue 20 Oct 18:00 – 19:00 · today, in 6 h", row.detail)
    }

    @Test fun `multi-day upcoming events name the end date`() {
        val long = event("long", "event", "2026-10-30T10:00:00Z", "2026-11-03T20:00:00Z")
        val row = eventsWidgetRows(listOf(long), now, emptySet(), zone, locale)[1] as EventsWidgetRow.Event
        assertEquals("Fri 30 Oct 10:00 – Tue 3 Nov 20:00 · in 10 days", row.detail)
    }

    @Test fun `hidden types are left out, and no empty section headers`() {
        val rows = eventsWidgetRows(events, now, hiddenTypes = setOf("season", "raid-hour"), zone = zone, locale = locale)
        assertEquals(listOf("Upcoming", "cd"), ids(rows))
    }

    @Test fun `types unknown when the widget was configured are shown`() {
        val fresh = events + event("new", "brand-new-type", "2026-10-22T10:00:00Z", "2026-10-22T11:00:00Z")
        val rows = eventsWidgetRows(fresh, now, hiddenTypes = setOf("season"), zone = zone, locale = locale)
        assertTrue("new" in ids(rows))
    }

    @Test fun `lists every upcoming event`() {
        val many = (1..40).map { event("e$it", "event", "2026-11-${(it % 28 + 1).toString().padStart(2, '0')}T10:00:00Z", "2026-12-29T10:00:00Z") }
        val rows = eventsWidgetRows(many, now, hiddenTypes = emptySet(), zone = zone, locale = locale)
        assertEquals(41, rows.size)
    }

    @Test fun `event times without an offset still parse`() {
        assertEquals(Instant.parse("2026-11-09T14:00:00Z").toEpochMilli(), parseEventTime("2026-11-09T14:00:00Z", null))
        assertEquals(Instant.parse("2026-11-09T13:00:00Z").toEpochMilli(), parseEventTime("2026-11-09T14:00:00+01:00", null))
        assertEquals(Instant.parse("2026-11-09T14:00:00Z").toEpochMilli(), parseEventTime("2026-11-09T14:00:00", null))
        // Wall-clock times are Berlin times (CET in November).
        assertEquals(Instant.parse("2026-11-09T13:00:00Z").toEpochMilli(), parseEventTime("2026-11-09T14:00:00", true))
        assertEquals(Instant.parse("2026-11-09T00:00:00Z").toEpochMilli(), parseEventTime("2026-11-09", null))
        assertEquals(0L, parseEventTime("soon", null))
    }
}
