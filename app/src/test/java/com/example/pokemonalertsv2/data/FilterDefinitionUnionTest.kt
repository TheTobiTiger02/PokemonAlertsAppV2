package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterDefinitionUnionTest {

    @Test
    fun union_allAbsorbsEverything() {
        val union = FilterSelection.only(listOf("HUNDO")).union(FilterSelection.All)

        assertEquals(FilterSelectionMode.ALL, union.mode)
    }

    @Test
    fun union_noneOnlySurvivesWhenBothAreNone() {
        assertEquals(FilterSelectionMode.NONE, FilterSelection.None.union(FilterSelection.None).mode)
        assertEquals(
            FilterSelection.only(listOf("RAID")).normalizedValues,
            FilterSelection.None.union(FilterSelection.only(listOf("RAID"))).normalizedValues
        )
    }

    @Test
    fun union_mergesOnlyValues() {
        val union = FilterSelection.only(listOf("HUNDO")).union(FilterSelection.only(listOf("RAID")))

        assertEquals(FilterSelectionMode.ONLY, union.mode)
        assertEquals(setOf("hundo", "raid"), union.normalizedValues)
    }

    /**
     * Axis-wise union is a superset of the true union — "Hundos in Alsbach" ∪ "Raids in
     * Darmstadt" becomes "Hundos or Raids in Alsbach or Darmstadt". Over-delivery is the safe
     * direction, and this pins that it is what happens.
     */
    @Test
    fun unionOf_widensEachAxisIndependently() {
        val hundosInAlsbach = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.HUNDO.name)),
            areas = FilterSelection.only(listOf("Alsbach"))
        )
        val raidsInDarmstadt = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name)),
            areas = FilterSelection.only(listOf("Darmstadt"))
        )

        val union = unionOf(listOf(hundosInAlsbach, raidsInDarmstadt))

        assertEquals(setOf("hundo", "raid"), union.alertTypes.normalizedValues)
        assertEquals(setOf("alsbach", "darmstadt"), union.areas.normalizedValues)
    }

    @Test
    fun unionOf_dropsDistanceBecauseItIsNotATopic() {
        val union = unionOf(
            listOf(
                FilterDefinition(maxDistanceKm = 5, maxWalkingMinutes = 10),
                FilterDefinition(maxDistanceKm = 20, maxWalkingMinutes = 30)
            )
        )

        assertEquals(0, union.maxDistanceKm)
        assertEquals(0, union.maxWalkingMinutes)
    }

    @Test
    fun unionOf_emptyIsTheUnfilteredDefault() {
        assertEquals(FilterDefinition(), unionOf(emptyList()))
    }

    @Test
    fun surfaceDefinitions_resolvesLinkedProfiles() {
        val profile = FilterProfile(
            id = "p1",
            name = "Raids",
            definition = FilterDefinition(alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name)))
        )
        val document = FilterStateDocument(
            profiles = listOf(profile),
            feed = FilterAssignment.linked(profile),
            map = FilterAssignment.local(FilterDefinition(areas = FilterSelection.only(listOf("Alsbach")))),
            notifications = FilterAssignment.local()
        )

        val union = unionOf(document.surfaceDefinitions())

        // The notifications surface is unfiltered, so nothing narrows.
        assertEquals(FilterSelectionMode.ALL, union.alertTypes.mode)
        assertEquals(FilterSelectionMode.ALL, union.areas.mode)
    }
}
