package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AreaAtLocationTest {

    @Test
    fun standingInAnAreaNamesIt() {
        assertEquals("Alsbach", areaAtLocation(ALSBACH_LATITUDE, ALSBACH_LONGITUDE))
        assertEquals("Darmstadt", areaAtLocation(DARMSTADT_LATITUDE, DARMSTADT_LONGITUDE))
    }

    @Test
    fun aShortWalkAwayIsStillTheSameArea() {
        // ~1.1 km north of the centre.
        assertEquals("Alsbach", areaAtLocation(ALSBACH_LATITUDE + 0.01, ALSBACH_LONGITUDE))
    }

    @Test
    fun theNearestOfTwoOverlappingAreasWins() {
        // Alsbach and Darmstadt sit ~14 km apart, so their radii overlap in between.
        val betweenButCloserToDarmstadt = (ALSBACH_LATITUDE + DARMSTADT_LATITUDE) / 2 + 0.02
        assertEquals("Darmstadt", areaAtLocation(betweenButCloserToDarmstadt, ALSBACH_LONGITUDE))
    }

    @Test
    fun somewhereElseEntirelyIsNoArea() {
        // Berlin: no badge should be offered there.
        assertNull(areaAtLocation(52.52000, 13.40500))
        // Frankfurt, ~40 km north: outside the radius but the same region.
        assertNull(areaAtLocation(50.11090, 8.68210))
    }
}
