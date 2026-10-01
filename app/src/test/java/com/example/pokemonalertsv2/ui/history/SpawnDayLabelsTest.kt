package com.example.pokemonalertsv2.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpawnDayLabelsTest {

    private fun days(count: Int) = (0 until count).map { LocalDate.of(2026, 9, 1).plusDays(it.toLong()).toString() }

    @Test
    fun `each label starts at the day its equal share of the width begins`() {
        assertEquals(listOf("1.9.", "3.9.", "5.9.", "7.9.", "9.9.", "11.9.", "13.9."), dayLabels(days(14)))
        assertEquals(listOf("1.9.", "6.9.", "11.9.", "16.9.", "21.9.", "26.9."), dayLabels(days(30)))
    }

    @Test
    fun `a week or less labels every day`() {
        assertEquals(7, dayLabels(days(7)).size)
    }
}
