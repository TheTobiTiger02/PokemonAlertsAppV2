package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAlertSorterTest {
    private val origin = WidgetAlertFilter.Origin(49.74, 8.62)

    @Test
    fun `newest sorts by descending id and then descending end time`() {
        val olderHighId = alert("older-high-id", id = 20, endTime = "2026-07-14 10:00:00")
        val newerHighId = alert("newer-high-id", id = 20, endTime = "2026-07-14 11:00:00")
        val lowId = alert("low-id", id = 10, endTime = "2026-07-14 12:00:00")
        val missingId = alert("missing-id", id = null, endTime = "2026-07-14 13:00:00")

        val result = WidgetAlertSorter.sort(
            listOf(lowId, missingId, olderHighId, newerHighId),
            SortPreference.POSTED_TIME,
            origin
        )

        assertEquals(listOf(newerHighId, olderHighId, lowId, missingId), result)
    }

    @Test
    fun `time remaining puts earliest valid deadline first and missing time last`() {
        val later = alert("later", endTime = "2026-07-14 12:00:00")
        val missing = alert("missing", endTime = "")
        val earlier = alert("earlier", endTime = "2026-07-14 10:00:00")

        assertEquals(
            listOf(earlier, later, missing),
            WidgetAlertSorter.sort(
                listOf(later, missing, earlier),
                SortPreference.TIME_REMAINING,
                origin
            )
        )
    }

    @Test
    fun `distance sorts nearest first and keeps unknown distances stable at the end`() {
        val far = alert("far")
        val unknownOne = alert("unknown-one")
        val near = alert("near")
        val unknownTwo = alert("unknown-two")

        val result = WidgetAlertSorter.sort(
            alerts = listOf(far, unknownOne, near, unknownTwo),
            preference = SortPreference.DISTANCE,
            origin = origin,
            distanceMeters = { _, candidate ->
                when (candidate.name) {
                    "near" -> 100f
                    "far" -> 2_000f
                    "unknown-one" -> null
                    else -> Float.NaN
                }
            }
        )

        assertEquals(listOf(near, far, unknownOne, unknownTwo), result)
    }

    @Test
    fun `distance preserves source order when location is unavailable`() {
        val source = listOf(alert("zeta"), alert("alpha"), alert("middle"))

        assertEquals(
            source,
            WidgetAlertSorter.sort(source, SortPreference.DISTANCE, origin = null)
        )
    }

    @Test
    fun `distance puts routed alerts before direct fallbacks and uses routed meters`() {
        val directNear = alert("direct-near")
        val routedFar = alert("routed-far")
        val routedNear = alert("routed-near")

        val result = WidgetAlertSorter.sort(
            alerts = listOf(directNear, routedFar, routedNear),
            preference = SortPreference.DISTANCE,
            origin = origin,
            walkingRoutes = mapOf(
                routedFar.uniqueId to WalkingRouteInfo(2_000, 1_200),
                routedNear.uniqueId to WalkingRouteInfo(500, 400)
            ),
            distanceMeters = { _, candidate ->
                if (candidate == directNear) 100f else 50f
            }
        )

        assertEquals(listOf(routedNear, routedFar, directNear), result)
    }

    @Test
    fun `name sorts case insensitively`() {
        val source = listOf(alert("zubat"), alert("Abra"), alert("mew"))

        assertEquals(
            listOf(source[1], source[2], source[0]),
            WidgetAlertSorter.sort(source, SortPreference.NAME, origin)
        )
    }

    private fun alert(
        name: String,
        id: Int? = null,
        endTime: String = name
    ) = PokemonAlert(name = name, id = id, endTime = endTime)
}
