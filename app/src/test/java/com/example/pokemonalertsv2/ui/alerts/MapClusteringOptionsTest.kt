package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MapClusteringOptionsTest {
    private fun alert(id: Int, type: String, latitude: Double = 49.87) = PokemonAlert(
        id = id, name = type, pokemon = if (type == "Kecleon") "Kecleon" else null,
        type = listOf(type), latitude = latitude, longitude = 8.65)

    @Test fun temporaryAlertsAlwaysBeatQuestEvenWhenQuestHasEarlierId() {
        val quest = alert(1, "Quest")
        listOf("Rocket", "Kecleon", "Spawn", "Weather").forEach { type ->
            val other = alert(2, type)
            assertTrue(type, compareAlertPriority(other, quest) < 0)
            assertEquals(other, listOf(quest, other).sortedWith(::compareAlertPriority).first())
        }
    }

    @Test fun stacksBecomeNormalMarkersAndCompanionsStayAccessible() = runBlocking {
        val quest = alert(1, "Quest")
        for (type in listOf("Rocket", "Kecleon")) {
            val primary = alert(2, type)
            for (preset in MapClusteringPreset.entries) {
                for (zoom in listOf(10.0, 16.0)) {
                    val prepared = prepareMapMarkers(listOf(quest, primary), null, zoom, null, emptySet(), preset.config)
                    assertEquals(primary, (prepared.items.single() as MapMarkerItem.Alert).alert)
                    assertEquals(listOf(quest), sameStopCompanions(primary, prepared.alerts))
                }
            }
        }
        assertTrue(sameStopCompanions(quest, listOf(quest, alert(3, "Rocket", 49.870002))).isEmpty())
    }

    @Test fun everyPresetPreservesEveryMemberAndHonorsBudget() {
        val alerts = List(3000) { alert(it, "Spawn", 49.8 + it * 0.00001) }
        for (preset in MapClusteringPreset.entries) {
            for (zoom in listOf(9.0, 20.0)) {
                val items = clusterMapAlerts(alerts, zoom, config = preset.config)
                val members = items.flatMap { when (it) {
                    is MapMarkerItem.Alert -> listOf(it.alert)
                    is MapMarkerItem.Cluster -> it.alerts
                } }
                assertEquals(alerts.map { it.uniqueId }.toSet(), members.map { it.uniqueId }.toSet())
                val cap = if (zoom >= preset.config.zoomCutoff) preset.config.closeLimit else preset.config.overviewLimit
                assertTrue(items.size <= cap)
                assertEquals(items, clusterMapAlerts(alerts.reversed(), zoom, config = preset.config))
            }
        }
    }

    @Test fun cutoffSeparatesNearbyDistinctLocations() {
        val tuning = MapClusteringPreset.LESS.config
        val alerts = listOf(alert(1, "Rocket"), alert(2, "Quest", 49.87001))
        assertEquals(1, clusterMapAlerts(alerts, 10.9, config = tuning).size)
        assertEquals(2, clusterMapAlerts(alerts, 11.0, config = tuning).size)
    }

    @Test fun questOnlyStacksAndFilteredCompanionsKeepDeterministicAccess() = runBlocking {
        val first = alert(1, "Quest")
        val second = alert(2, "Quest")
        val rocket = alert(3, "Rocket")
        for (preset in MapClusteringPreset.entries) {
            val forward = prepareMapMarkers(listOf(first, second), null, 16.0, null, emptySet(), preset.config)
            val reverse = prepareMapMarkers(listOf(second, first), null, 16.0, null, emptySet(), preset.config)
            assertEquals(forward.items, reverse.items)
            val representative = (forward.items.single() as MapMarkerItem.Alert).alert
            assertEquals(1, sameStopCompanions(representative, forward.alerts).size)
            // Marker preparation receives only eligible alerts from map filtering/dismissal.
            val filtered = prepareMapMarkers(listOf(rocket, second), null, 16.0, null, emptySet(), preset.config)
            assertEquals(listOf(second), sameStopCompanions(rocket, filtered.alerts))
            assertFalse(first in sameStopCompanions(rocket, filtered.alerts))
        }
    }

    @Test fun customCloseLimitCanBeLowerThanOverviewAndSignalsOverflow() {
        val alerts = List(1000) { alert(it + 1, "Spawn", 49.8 + it * 0.0001) }
        val config = MapClusteringConfig(overviewLimit = 1000, closeLimit = 350)
        val items = clusterMapAlerts(alerts, 20.0, config = config)
        assertTrue(items.size <= 350)
        assertTrue(items.filterIsInstance<MapMarkerItem.Cluster>().any { it.markerLimitActive })
    }

    @Test fun defaultsAndMalformedValuesAreSafe() {
        assertEquals(MapClusteringConfig(), MapClusteringPreferences.decode(null).config)
        assertEquals(MapClusteringConfig(), MapClusteringPreferences.decode("broken").config)
        assertEquals(1200, MapClusteringConfig(closeLimit = Int.MAX_VALUE).normalized().closeLimit)
        assertEquals(50, MapClusteringConfig(closeLimit = 0).normalized().closeLimit)
        assertEquals(MapClusteringConfig(), MapClusteringSettings(preset = "future").config)
    }

    @Test fun numericCoordinateKeysPreserveSixDecimalRoundingAndSignedZero() {
        val random = kotlin.random.Random(41)
        val values = listOf(0.0, -0.0, 0.0000005, -0.0000005, 49.8700005, -49.8700005,
            0.00000049, -0.00000049) + List(20000) { random.nextDouble(-85.0, 85.0) }
        values.forEach { value ->
            val formatted = "%.6f".format(java.util.Locale.ROOT, value)
            val expected = if (formatted == "-0.000000") Long.MIN_VALUE else
                java.math.BigDecimal(formatted).movePointRight(6).toLong()
            assertEquals("Coordinate $value", MapCoordinateKey(expected, expected), exactCoordinateKey(value, value))
        }
    }

    @Test fun representativeOrderingMatchesPublicPriorityAndRefreshesOnRemoval() = runBlocking {
        val alerts = listOf("Quest", "Rocket", "Kecleon", "Spawn", "Raid", "Hundo", "Rare", "PvP")
            .mapIndexed { index, type -> alert(index + 1, type) }
        for (remaining in alerts.indices) {
            val eligible = alerts.drop(remaining)
            val result = prepareMapMarkers(eligible, null, 16.0, null, emptySet())
            val marker = result.items.single() as MapMarkerItem.Alert
            assertEquals(eligible.minWith(::compareAlertPriority), marker.alert)
            assertEquals(eligible.size - 1, sameStopCompanions(marker.alert, result.alerts).size)
        }
        val tracked = alerts[1]
        val protected = prepareMapMarkers(alerts, null, 16.0, null, setOf(tracked.uniqueId))
        assertTrue(protected.items.any { it is MapMarkerItem.Alert && it.alert == tracked })
        assertEquals(alerts.size - 1, sameStopCompanions(tracked, protected.alerts).size)
        assertTrue(prepareMapMarkers(emptyList(), null, 16.0, null, emptySet()).items.isEmpty())
    }
}
