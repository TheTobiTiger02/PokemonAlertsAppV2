package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterSelectionToggleTest {
    private val universe = listOf("1", "3", "5", "Mega")

    @Test
    fun switchingOneOffFromAllKeepsTheRest() {
        assertEquals(
            FilterSelection.only(listOf("1", "3", "Mega")),
            FilterSelection.All.toggled("5", universe)
        )
    }

    @Test
    fun switchingOneOnFromNoneSelectsOnlyIt() {
        assertEquals(FilterSelection.only(listOf("Mega")), FilterSelection.None.toggled("Mega", universe))
    }

    @Test
    fun onlyFlipsTheTokenEitherWay() {
        val some = FilterSelection.only(listOf("1", "5"))
        assertEquals(FilterSelection.only(listOf("1", "5", "3")), some.toggled("3", universe))
        assertEquals(FilterSelection.only(listOf("1")), some.toggled("5", universe))
    }

    @Test
    fun switchingTheLastOneOffLeavesNone() {
        assertEquals(FilterSelection.None, FilterSelection.only(listOf("5")).toggled("5", universe))
    }
}
