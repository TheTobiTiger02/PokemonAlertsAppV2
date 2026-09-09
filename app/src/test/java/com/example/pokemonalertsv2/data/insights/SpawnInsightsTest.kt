package com.example.pokemonalertsv2.data.insights

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SpawnInsightsTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `hours are bucketed in the reader's zone, not UTC`() {
        // 22:30Z on a summer day is 00:30 the next morning in Berlin.
        val insights = buildSpawnInsights(
            history(alert(createdAt = "2026-07-01T22:30:00Z")),
            zone = berlin
        )

        assertEquals(1, insights.byHour[0])
        assertEquals(0, insights.byHour[22])
        assertEquals(0, insights.busiestHour)
    }

    @Test
    fun `createdAt wins over endTime, which is only a fallback`() {
        // Posted at 18:00 local, despawning at 18:30: the spawn belongs to 18.
        val both = buildSpawnInsights(
            history(
                alert(
                    createdAt = "2026-07-01T16:00:00Z",
                    endTime = "2026-07-01T16:30:00Z"
                )
            ),
            zone = berlin
        )
        assertEquals(1, both.byHour[18])

        val onlyEnd = buildSpawnInsights(
            history(alert(createdAt = null, endTime = "2026-07-01T16:30:00Z")),
            zone = berlin
        )
        assertEquals(1, onlyEnd.byHour[18])
    }

    @Test
    fun `rows with no usable timestamp are dropped, not guessed at`() {
        val insights = buildSpawnInsights(
            history(
                alert(createdAt = "2026-07-01T10:00:00Z"),
                alert(createdAt = null, endTime = ""),
                alert(createdAt = "not a date", endTime = "also not a date")
            ),
            zone = berlin
        )

        assertEquals(1, insights.totalSightings)
    }

    @Test
    fun `days covered and the per-day average describe the window actually seen`() {
        val insights = buildSpawnInsights(
            history(
                alert(createdAt = "2026-07-01T10:00:00Z"),
                alert(createdAt = "2026-07-01T12:00:00Z"),
                alert(createdAt = "2026-07-02T10:00:00Z"),
                alert(createdAt = "2026-07-03T10:00:00Z")
            ),
            zone = berlin
        )

        assertEquals(4, insights.totalSightings)
        assertEquals(3, insights.daysCovered)
        assertEquals(4f / 3f, insights.perDayAverage, 0.0001f)
    }

    @Test
    fun `areas rank by count, ties broken by name so the order is stable`() {
        val insights = buildSpawnInsights(
            history(
                alert(createdAt = "2026-07-01T10:00:00Z", area = "Bickenbach"),
                alert(createdAt = "2026-07-01T11:00:00Z", area = "Alsbach"),
                alert(createdAt = "2026-07-01T12:00:00Z", area = "Alsbach"),
                alert(createdAt = "2026-07-01T13:00:00Z", area = "Zwingenberg"),
                alert(createdAt = "2026-07-01T14:00:00Z", area = "  ")
            ),
            zone = berlin
        )

        assertEquals(
            listOf(AreaCount("Alsbach", 2), AreaCount("Bickenbach", 1), AreaCount("Zwingenberg", 1)),
            insights.byArea
        )
        assertEquals(AreaCount("Alsbach", 2), insights.topArea)
    }

    @Test
    fun `weekdays are Monday first`() {
        // 2026-07-01 is a Wednesday.
        val insights = buildSpawnInsights(
            history(alert(createdAt = "2026-07-01T10:00:00Z")),
            zone = berlin
        )

        assertEquals(1, insights.byWeekday[2])
        assertEquals(0, insights.byWeekday[0])
    }

    @Test
    fun `hundos are counted separately from the total`() {
        val insights = buildSpawnInsights(
            history(
                alert(createdAt = "2026-07-01T10:00:00Z", perfect = true),
                alert(createdAt = "2026-07-01T11:00:00Z")
            ),
            zone = berlin
        )

        assertEquals(2, insights.totalSightings)
        assertEquals(1, insights.hundoCount)
    }

    @Test
    fun `a capped read says so, rather than presenting a partial count as complete`() {
        val capped = buildSpawnInsights(
            InsightsHistory(listOf(alert(createdAt = "2026-07-01T10:00:00Z")), hitCap = true),
            zone = berlin
        )
        assertTrue(capped.truncated)

        val complete = buildSpawnInsights(
            history(alert(createdAt = "2026-07-01T10:00:00Z")),
            zone = berlin
        )
        assertFalse(complete.truncated)
    }

    @Test
    fun `nothing observed reads as empty rather than as a busiest hour of midnight`() {
        val insights = buildSpawnInsights(InsightsHistory(), zone = berlin)

        assertTrue(insights.isEmpty)
        assertNull(insights.busiestHour)
        assertNull(insights.topArea)
        assertEquals(0f, insights.perDayAverage, 0f)
        assertEquals(24, insights.byHour.size)
        assertEquals(7, insights.byWeekday.size)
    }

    private fun history(vararg alerts: PokemonAlert) = InsightsHistory(alerts.toList())

    private fun alert(
        createdAt: String?,
        endTime: String = "2026-07-01T10:30:00Z",
        area: String? = null,
        perfect: Boolean = false
    ) = PokemonAlert(
        name = "Larvitar",
        pokemon = "Larvitar",
        createdAt = createdAt,
        endTime = endTime,
        area = area,
        ivAttack = if (perfect) 15 else null,
        ivDefense = if (perfect) 15 else null,
        ivStamina = if (perfect) 15 else null
    )
}
