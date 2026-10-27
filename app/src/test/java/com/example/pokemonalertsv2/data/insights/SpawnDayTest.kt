package com.example.pokemonalertsv2.data.insights

import com.example.pokemonalertsv2.ui.history.compactCount
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpawnDayTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun `day, hour and alert hour responses parse as the server sends them`() {
        val day = json.decodeFromString<SpawnDayResponse>(
            """{"generatedAt":"x","day":"2026-10-25","area":null,"areas":["Alsbach"],"complete":true,
              "hours":[{"hour":0,"start":1792879200000,"active":12315,"live":12069,"catalogueOnly":246,"sightings":12663}],
              "totals":{"active":13433,"live":12159,"catalogueOnly":1274,"sightings":297308}}"""
        )
        assertEquals(1792879200000L, day.hours.single().start)
        assertEquals(13433, day.totals.active)
        val hour = json.decodeFromString<SpawnHourResponse>(
            """{"start":1793005200000,"end":1793008800000,"hour":10,"area":null,"complete":true,"sightings":3,
              "points":[{"id":"derived:abc","latitude":49.74,"longitude":8.61,"area":"Alsbach","source":"catalogue"}],
              "species":[{"pokemonId":179,"formId":null,"count":3,"name":"Mareep","rank":1,"thumbnailUrl":"https://x/179.png"}]}"""
        )
        assertEquals("catalogue", hour.points.single().source)
        assertEquals("Mareep", hour.species.single().name)
        val alerts = json.decodeFromString<AlertHoursResponse>(
            """{"day":"2026-10-25","timeField":"createdAt","total":2,"byType":{"Hundo":2},
              "hours":[{"hour":2,"start":1,"count":2,"byType":{"Hundo":2}}]}"""
        )
        assertEquals(mapOf("Hundo" to 2), alerts.hours.single().byType)
    }

    @Test
    fun `the repeated hour of the clock change is told apart`() {
        assertEquals(listOf("00", "01", "02", "02′", "03"), hourLabels(listOf(0, 1, 2, 2, 3)))
    }

    @Test
    fun `a finished day opens on its busiest hour, today on its latest`() {
        val day = SpawnDayResponse(day = "2026-10-26", hours = listOf(
            SpawnHourSlot(hour = 0, start = 1, active = 5), SpawnHourSlot(hour = 1, start = 2, active = 9), SpawnHourSlot(hour = 2, start = 3, active = 7)))
        assertEquals(2L, defaultHour(day, isToday = false))
        assertEquals(3L, defaultHour(day, isToday = true))
        assertNull(defaultHour(SpawnDayResponse(day = "2026-10-28"), isToday = true))
    }

    @Test
    fun `column totals are short enough for a narrow column`() {
        assertEquals("950", compactCount(950))
        assertEquals("11.7k", compactCount(11_660))
        assertEquals("10k", compactCount(10_000))
        assertEquals("120k", compactCount(120_400))
    }
}
