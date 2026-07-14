package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MapStylePreferenceTest {
    @Test
    fun `stored map style values restore each supported style`() {
        assertEquals(
            MapStylePreference.GOOGLE_STANDARD,
            MapStylePreference.fromStoredValue("GOOGLE_STANDARD")
        )
        assertEquals(
            MapStylePreference.GOOGLE_SATELLITE,
            MapStylePreference.fromStoredValue("GOOGLE_SATELLITE")
        )
        assertEquals(
            MapStylePreference.OPENSTREETMAP,
            MapStylePreference.fromStoredValue("OPENSTREETMAP")
        )
    }

    @Test
    fun `missing or unknown map style values default to google standard`() {
        assertEquals(MapStylePreference.GOOGLE_STANDARD, MapStylePreference.fromStoredValue(null))
        assertEquals(MapStylePreference.GOOGLE_STANDARD, MapStylePreference.fromStoredValue("old_value"))
    }
}
