package com.example.pokemonalertsv2.data.insights

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class SpawnActivityTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun day(day: String, active: Int, live: Int = active) = SpawnActivityDay(day = day, active = active, live = live)

    @Test
    fun `usual is the median of finished days and today is never flagged for being low`() {
        val summary = summarizeSpawnActivity(
            SpawnActivityDaysResponse(
                summary = listOf(
                    day("2026-09-26", 4000), day("2026-09-27", 4200), day("2026-09-28", 9000),
                    day("2026-09-29", 4100), day("2026-09-30", 900)
                )
            ),
            berlin
        )
        assertEquals(900, summary.today)
        assertEquals(4150, summary.usual)
        assertEquals(9000, summary.busiest)
        assertEquals(setOf(2), summary.flagged)
    }

    @Test
    fun `days before live scanning neither set the usual day nor get flagged`() {
        val summary = summarizeSpawnActivity(
            SpawnActivityDaysResponse(
                summary = listOf(
                    day("2026-09-01", 5, live = 0), day("2026-09-02", 7, live = 0),
                    day("2026-09-03", 9000), day("2026-09-04", 9400), day("2026-09-05", 3000)
                )
            ),
            berlin
        )
        assertEquals(9200, summary.usual)
        assertEquals(emptySet<Int>(), summary.flagged)
        assertEquals(2, summary.unscanned)
    }

    @Test
    fun `a surge marks the Berlin days it touches`() {
        // 22:00Z on the 27th is already midnight on the 28th in Berlin.
        val summary = summarizeSpawnActivity(
            SpawnActivityDaysResponse(
                summary = listOf(day("2026-09-27", 4000), day("2026-09-28", 4000), day("2026-09-29", 4000)),
                surges = listOf(SpawnSurge(id = "inferred:x", start = "2026-09-27T22:00:00Z", end = "2026-09-28T01:00:00Z"))
            ),
            berlin
        )
        assertEquals(setOf(1), summary.flagged)
    }

    @Test
    fun `an empty response has nothing to say`() {
        val summary = summarizeSpawnActivity(SpawnActivityDaysResponse(), berlin)
        assertNull(summary.today)
        assertNull(summary.usual)
        assertEquals(emptySet<Int>(), summary.flagged)
    }

    @Test
    fun `species response parses the server shape`() {
        val body = """
            {"generatedAt":"2026-09-30T10:00:00Z","days":["2026-09-30"],"area":null,"areas":["Alsbach"],
             "perDay":[{"day":"2026-09-30","total":5}],"totals":{"sightings":5,"species":2},
             "species":[{"pokemonId":19,"formId":45,"count":3,"name":"Rattata Alola","thumbnailUrl":"https://x/19.png"},
                        {"pokemonId":16,"formId":null,"count":2,"name":"Pidgey","thumbnailUrl":null}]}
        """.trimIndent()
        val parsed = json.decodeFromString(SpawnSpeciesResponse.serializer(), body)
        assertEquals(listOf("Rattata Alola", "Pidgey"), parsed.species.map { it.name })
        assertEquals(45, parsed.species.first().formId)
        assertEquals(5, parsed.totals.sightings)
    }

    @Test
    fun `search results carry their rank and per-day counts`() {
        val body = """
            {"days":["2026-09-29","2026-09-30"],"query":"rat","totals":{"sightings":9,"species":4},"perDay":[],
             "species":[{"pokemonId":19,"formId":45,"count":3,"rank":2,"daily":[1,2],"name":"Rattata"}]}
        """.trimIndent()
        val parsed = json.decodeFromString(SpawnSpeciesResponse.serializer(), body)
        assertEquals("rat", parsed.query)
        assertEquals(2, parsed.species.single().rank)
        assertEquals(listOf(1, 2), parsed.species.single().daily)
    }
}
