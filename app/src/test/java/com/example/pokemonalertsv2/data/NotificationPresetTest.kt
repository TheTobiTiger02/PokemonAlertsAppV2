package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPresetTest {
    @Test
    fun mappingsMatchTheProductPresets() {
        val highValue = NotificationPreset.HIGH_VALUE.categories()!!
        assertFalse(highValue.raids)
        assertTrue(highValue.spawns)
        assertTrue(highValue.hundos)
        assertTrue(highValue.pvp)
        assertTrue(highValue.nundos)
        assertTrue(highValue.kecleon)
        assertFalse(highValue.quests)
        assertFalse(highValue.rocket)

        assertEquals(NotificationPreset.HIGH_VALUE, NotificationPreset.detect(highValue))
        assertEquals(NotificationPreset.EVERYTHING, NotificationPreset.detect(NotificationPreset.EVERYTHING.categories()!!))
    }

    @Test
    fun unmatchedSwitchesAreCustom() {
        assertEquals(
            NotificationPreset.CUSTOM,
            NotificationPreset.detect(NotificationCategoryState(true, false, true, false, true, false, true, false))
        )
    }
}
