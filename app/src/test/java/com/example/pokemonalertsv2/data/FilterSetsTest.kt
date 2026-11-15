package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.widget.WidgetConfiguration
import com.example.pokemonalertsv2.widget.effectiveDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSetsTest {
    private val hundos = FilterDefinition(alertTypes = FilterSelection.only(listOf(FilterAlertType.HUNDO.name)))
    private val raids = FilterDefinition(alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name)))

    private fun before() = FilterStateDocument(
        feed = FilterAssignment.local(hundos),
        map = FilterAssignment.local(raids),
        notifications = FilterAssignment.local(hundos)
    )

    @Test
    fun aDocumentFromBeforeSetsBecomesTheFirstSetUnchanged() {
        val decoded = FilterStateCodec.decode(FilterStateCodec.encode(before()))!!
        assertEquals(1, decoded.allSets().size)
        assertEquals("Standard", decoded.activeSet.name)
        assertEquals(hundos, decoded.feed.resolve(decoded))
        assertEquals(raids, decoded.map.resolve(decoded))
        assertEquals(hundos, decoded.notifications.resolve(decoded))
        // New widgets start from what the feed shows.
        assertEquals(hundos, decoded.widgets.resolve(decoded))
    }

    @Test
    fun switchingSetsSwapsEverySurfaceAndKeepsEditsToTheOldOne() {
        val start = before().withSetsInitialized("Standard")
        val withWork = start.createSet("Work", "set-work", from = null).activateSet("set-work")
        assertEquals("Work", withWork.activeSet.name)
        assertEquals(FilterDefinition(), withWork.feed.resolve(withWork))

        val edited = withWork.withAssignment(FilterSurface.NOTIFICATIONS, FilterAssignment.local(raids))
        val back = edited.activateSet(DEFAULT_FILTER_SET_ID)
        assertEquals(hundos, back.notifications.resolve(back))
        val again = back.activateSet("set-work")
        assertEquals(raids, again.notifications.resolve(again))
        assertEquals(again, FilterStateCodec.decode(FilterStateCodec.encode(again)))
    }

    @Test
    fun setsHaveUniqueNamesALimitAndAlwaysAtLeastOne() {
        var document = before().withSetsInitialized("Standard")
        assertEquals(document, document.deleteSet(DEFAULT_FILTER_SET_ID))
        document = document.createSet("Standard", "set-2")
        assertEquals("Standard 2", document.allSets().last().name)
        repeat(MAX_FILTER_SETS + 2) { document = document.createSet("Extra", "extra-$it") }
        assertEquals(MAX_FILTER_SETS, document.allSets().size)

        val active = document.activateSet("set-2")
        val removed = active.deleteSet("set-2")
        assertNotEquals("set-2", removed.activeSet.id)
        assertEquals(hundos, removed.feed.resolve(removed))
        assertEquals("Renamed", removed.renameSet(DEFAULT_FILTER_SET_ID, "  Renamed ").activeSet.name)
    }

    @Test
    fun deletingAProfileKeepsItsRulesInEverySet() {
        val profile = FilterProfile("p1", "Mine", raids)
        val document = before().copy(profiles = listOf(profile))
            .withSetsInitialized("Standard")
            .createSet("Other", "other", from = null)
            .activateSet("other")
            .withAssignment(FilterSurface.FEED, FilterAssignment.linked(profile))
            .activateSet(DEFAULT_FILTER_SET_ID)
            .withAssignment(FilterSurface.MAP, FilterAssignment.linked(profile))
        assertEquals(2, document.consumerCount("p1"))

        val deleted = document.deleteProfilePreservingConsumers("p1")
        assertTrue(deleted.profiles.isEmpty())
        assertEquals(FilterAssignment.local(raids), deleted.map)
        val other = deleted.activateSet("other")
        assertEquals(FilterAssignment.local(raids), other.feed)
    }

    @Test
    fun theSetsWidgetFilterOnlyReachesWidgetsThatFollowIt() {
        val document = before().withSetsInitialized("Standard")
            .withAssignment(FilterSurface.WIDGETS, FilterAssignment.local(raids))
        assertEquals(3, document.surfaceDefinitions().size)

        val follows = WidgetConfiguration(followSet = true, filterAssignment = FilterAssignment.local(hundos))
        val own = follows.copy(followSet = false)
        assertEquals(raids, follows.effectiveDefinition(document, "All", 0))
        assertEquals(hundos, own.effectiveDefinition(document, "All", 0))
    }
}
