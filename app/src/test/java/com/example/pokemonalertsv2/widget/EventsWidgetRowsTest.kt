package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.events.GameEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class EventsWidgetRowsTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("UTC")

    private fun event(id: String, type: String, start: String, end: String) =
        GameEvent(id = id, name = id, eventType = type, startAt = start, endAt = end)

    private val events = listOf(
        event("cd", "community-day", "2026-10-25T14:00:00Z", "2026-10-25T17:00:00Z"),
        event("season", "season", "2026-09-01T10:00:00Z", "2026-12-01T10:00:00Z"),
        event("raid", "raid-hour", "2026-10-21T18:00:00Z", "2026-10-21T19:00:00Z"),
        event("over", "event", "2026-10-10T10:00:00Z", "2026-10-11T10:00:00Z")
    )

    @Test fun `groups running events under now and the rest under upcoming`() {
        val rows = eventsWidgetRows(events, now, shownTypes = null, zone = zone)
        assertEquals(
            listOf("Now", "season", "Upcoming", "raid", "cd"),
            rows.map { if (it is EventsWidgetRow.Header) it.title else (it as EventsWidgetRow.Event).event.id }
        )
        val season = rows[1] as EventsWidgetRow.Event
        assertTrue(season.running)
        assertTrue(season.detail.startsWith("Ends in "))
        assertTrue((rows[3] as EventsWidgetRow.Event).detail.endsWith(" 18:00 – 19:00"))
    }

    @Test fun `only the chosen types, and no empty section headers`() {
        val rows = eventsWidgetRows(events, now, shownTypes = setOf("community-day"), zone = zone)
        assertEquals(listOf("Upcoming", "cd"), rows.map { if (it is EventsWidgetRow.Header) it.title else (it as EventsWidgetRow.Event).event.id })
    }

    @Test fun `caps the upcoming list`() {
        val many = (1..20).map { event("e$it", "event", "2026-10-2${it % 9 + 1}T10:00:00Z", "2026-10-29T10:00:00Z") }
        val rows = eventsWidgetRows(many, now, shownTypes = null, zone = zone, maxUpcoming = 5)
        assertEquals(6, rows.size)
    }
}
