package com.example.pokemonalertsv2.events

import com.example.pokemonalertsv2.catchroutes.SpawnpointEvent
import com.example.pokemonalertsv2.catchroutes.activityNotice
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GameEventsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-09-17T10:00:00Z").toEpochMilli()

    private fun event(id: String, type: String, start: String, end: String, localTime: Boolean? = true) =
        GameEvent(id = id, name = id, eventType = type, startAt = start, endAt = end, localTime = localTime)

    @Test fun `the backend response parses, including details and unknown fields`() {
        val response = json.decodeFromString<GameEventsResponse>("""{"generatedAt":"x","data":[{
            "id":"scrapedduck:cd","name":"Zorua Community Day","eventType":"community-day","heading":"Community Day",
            "link":"https://leekduck.com/events/cd/","image":null,"startAt":"2026-10-10T12:00:00.000Z","endAt":"2026-10-10T15:00:00.000Z",
            "localTime":true,"hasSpawns":true,"spawnRelevant":true,
            "featured":[{"name":"Zorua","image":"https://cdn/pm570.icon.png","canBeShiny":true,"pokemonId":570}],
            "bonuses":[{"text":"3x Catch XP","image":null}],"bonusDisclaimers":["* until 9 p.m."],"shinies":[],"raidBosses":[],
            "source":"scrapedduck","somethingNew":1}]}""")
        val cd = response.data.single()
        assertEquals(570, cd.featured.single().pokemonId)
        assertEquals("3x Catch XP", cd.bonuses.single().text)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z").toEpochMilli(), cd.startMillis)
        assertEquals(berlin, cd.displayZone(ZoneId.of("UTC")))
        assertEquals(ZoneId.of("UTC"), cd.copy(localTime = false).displayZone(ZoneId.of("UTC")))
    }

    @Test fun `events split into running and upcoming days, ended and hidden ones left out`() {
        val events = listOf(
            event("season", "season", "2026-09-08T08:00:00Z", "2026-12-01T09:00:00Z"),
            event("raids", "raid-battles", "2026-09-16T04:00:00Z", "2026-09-22T20:00:00Z"),
            event("gbl", "go-battle-league", "2026-09-15T20:00:00Z", "2026-09-22T20:00:00Z", localTime = false),
            event("ended", "raid-hour", "2026-09-16T16:00:00Z", "2026-09-16T17:00:00Z"),
            event("spotlight", "pokemon-spotlight-hour", "2026-09-17T16:00:00Z", "2026-09-17T17:00:00Z"),
            event("raid-hour", "raid-hour", "2026-09-23T16:00:00Z", "2026-09-23T17:00:00Z"),
            // 23:30 UTC is already the next day in Berlin.
            event("late", "event", "2026-09-23T22:30:00Z", "2026-09-24T02:00:00Z"),
        )
        val sections = groupEvents(events, now, DEFAULT_HIDDEN_EVENT_TYPES, berlin)
        assertEquals(listOf("raids", "season"), sections.now.map { it.id })
        assertEquals(
            listOf(LocalDate.of(2026, 9, 17) to listOf("spotlight"), LocalDate.of(2026, 9, 23) to listOf("raid-hour"), LocalDate.of(2026, 9, 24) to listOf("late")),
            sections.upcoming.map { (day, list) -> day to list.map { it.id } },
        )
        assertTrue(groupEvents(events, now, emptySet(), berlin).now.any { it.id == "gbl" })
    }

    @Test fun `labels read naturally`() {
        assertEquals("Spotlight Hour", eventTypeName("pokemon-spotlight-hour"))
        assertEquals("Some New Thing", eventTypeName("some-new-thing"))
        assertEquals("2 h 15 min", durationLabel(135 * 60_000L))
        assertEquals("3 d 4 h", durationLabel((3 * 24 + 4) * 3_600_000L))
        assertEquals("less than a minute", durationLabel(30_000))
        val running = event("x", "event", "2026-09-17T09:00:00Z", "2026-09-17T11:00:00Z")
        assertEquals(0.5f, running.progress(now), 0.001f)
    }

    @Test fun `reminders cover chosen types and starred events in the next two days, before start`() {
        val events = listOf(
            event("spotlight", "pokemon-spotlight-hour", "2026-09-17T16:00:00Z", "2026-09-17T17:00:00Z"),
            event("raid-hour", "raid-hour", "2026-09-17T16:00:00Z", "2026-09-17T17:00:00Z"),
            event("starred-raid", "raid-hour", "2026-09-18T16:00:00Z", "2026-09-18T17:00:00Z"),
            event("soon", "community-day", "2026-09-17T10:05:00Z", "2026-09-17T13:00:00Z"),
            event("running", "community-day", "2026-09-17T09:00:00Z", "2026-09-17T12:00:00Z"),
            event("far", "community-day", "2026-10-10T12:00:00Z", "2026-10-10T15:00:00Z"),
        )
        val reminders = eventsToRemind(events, DEFAULT_REMINDER_EVENT_TYPES, setOf("starred-raid"), leadMinutes = 15, nowMillis = now)
        assertEquals(listOf("soon", "spotlight", "starred-raid"), reminders.map { it.event.id })
        // Five minutes to go with a 15-minute lead: remind right away rather than never.
        assertEquals(now, reminders.first().notifyAtMillis)
        assertEquals(Instant.parse("2026-09-17T15:45:00Z").toEpochMilli(), reminders[1].notifyAtMillis)
    }

    @Test fun `an event spawnpoint's notice names the Pokémon its next event features`() {
        val next = SpawnpointEvent("Rattata Spotlight Hour", "pokemon-spotlight-hour",
            Instant.parse("2026-09-24T16:00:00Z").toEpochMilli(), Instant.parse("2026-09-24T17:00:00Z").toEpochMilli(), listOf("Rattata"))
        val notice = activityNotice("event_only", listOf("pokemon-spotlight-hour"), next, null, now)!!
        assertTrue(notice, notice.endsWith("Featured: Rattata."))
    }
}
