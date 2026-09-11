package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPresetsTest {

    private fun preset(name: String, filter: String = "RAIDS") =
        FilterPreset(name = name, filter = filter, sort = "DISTANCE", area = "Alsbach", maxDistanceMeters = 5_000)

    @Test
    fun `presets survive a round trip`() {
        val presets = listOf(preset("Near home"), preset("Everything", filter = "ALL"))
        assertEquals(presets, FilterPresets.decode(FilterPresets.encode(presets)))
    }

    @Test
    fun `a corrupt value yields no presets rather than breaking the feed`() {
        assertEquals(emptyList<FilterPreset>(), FilterPresets.decode("{not json"))
        assertEquals(emptyList<FilterPreset>(), FilterPresets.decode(""))
        assertEquals(emptyList<FilterPreset>(), FilterPresets.decode(null))
    }

    @Test
    fun `saving over an existing name replaces it instead of duplicating`() {
        val existing = listOf(preset("Near home", filter = "RAIDS"))
        val updated = FilterPresets.upsert(existing, preset("Near home", filter = "HUNDOS"))

        assertEquals(1, updated.size)
        assertEquals("HUNDOS", updated.single().filter)
    }

    @Test
    fun `name matching ignores case and surrounding space`() {
        val existing = listOf(preset("Near home"))
        val updated = FilterPresets.upsert(existing, preset("  near HOME  "))
        assertEquals(1, updated.size)
        assertEquals("near HOME", updated.single().name)
    }

    @Test
    fun `the list is capped so the chip row cannot grow without bound`() {
        var presets = emptyList<FilterPreset>()
        repeat(FilterPresets.MAX_PRESETS + 3) { index ->
            presets = FilterPresets.upsert(presets, preset("preset $index"))
        }
        assertEquals(FilterPresets.MAX_PRESETS, presets.size)
        // The oldest are the ones dropped.
        assertEquals("preset 3", presets.first().name)
    }

    @Test
    fun `a blank name is not saved`() {
        val existing = listOf(preset("Near home"))
        assertEquals(existing, FilterPresets.upsert(existing, preset("   ")))
    }

    @Test
    fun `long names are truncated rather than rejected`() {
        val long = "x".repeat(FilterPresets.MAX_NAME_LENGTH + 10)
        val saved = FilterPresets.upsert(emptyList(), preset(long)).single()
        assertEquals(FilterPresets.MAX_NAME_LENGTH, saved.name.length)
    }

    @Test
    fun `remove ignores case and space too`() {
        val existing = listOf(preset("Near home"), preset("Raids only"))
        assertEquals(1, FilterPresets.remove(existing, " near home ").size)
    }

    @Test
    fun `describe summarises only the parts that are actually set`() {
        val described = FilterPresets.describe(
            FilterPreset(name = "n", filter = "RAIDS", area = "Alsbach", maxDistanceMeters = 5_000)
        )
        assertTrue(described.contains("Raids"))
        assertTrue(described.contains("Alsbach"))
        assertTrue(described.contains("5 km"))

        val subKilometer = FilterPresets.describe(
            FilterPreset(name = "n", filter = "RAIDS", maxDistanceMeters = 500)
        )
        assertTrue(subKilometer.contains("500 m"))

        val minimal = FilterPresets.describe(FilterPreset(name = "n", filter = "ALL"))
        assertEquals("All", minimal)
    }

    @Test
    fun `stored kilometer distances migrate to meters on decode`() {
        val legacy = """[{"name":"Near home","filter":"RAIDS","sort":"DISTANCE","area":"Alsbach","maxDistance":5}]"""
        val decoded = FilterPresets.decode(legacy).single()
        assertEquals(5_000, decoded.maxDistanceMeters)
        assertEquals(listOf(decoded), FilterPresets.decode(FilterPresets.encode(decoded.let(::listOf))))
    }
}
