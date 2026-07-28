package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRadarImageRendererTest {
    @Test
    fun fittedViewportKeepsAllPointsInsideOverlaySafeArea() {
        val insets = RadarRenderInsets(leftPx = 40, topPx = 90, rightPx = 30, bottomPx = 150)
        val points = listOf(
            RadarGeoPoint(49.8728, 8.6512),
            RadarGeoPoint(49.8580, 8.6290),
            RadarGeoPoint(49.8910, 8.6740)
        )

        val viewport = fitRadarViewport(points, 800, 480, insets)
        val projected = points.map { projectToRadar(it, viewport, 800, 480) }

        assertTrue(projected.all { it.first in insets.leftPx.toFloat()..(800 - insets.rightPx).toFloat() })
        assertTrue(projected.all { it.second in insets.topPx.toFloat()..(480 - insets.bottomPx).toFloat() })
    }

    @Test
    fun singlePointUsesReadableNeighborhoodZoom() {
        val viewport = fitRadarViewport(
            listOf(RadarGeoPoint(49.8728, 8.6512)),
            widthPx = 800,
            heightPx = 480
        )

        assertEquals(16, viewport.zoom)
        assertEquals(49.8728, viewport.center.latitude, 0.000001)
        assertEquals(8.6512, viewport.center.longitude, 0.000001)
    }

    @Test
    fun onlyScreenSpaceOverlapsBecomeClusters() {
        val groups = clusterRadarPoints(
            points = listOf(100f to 100f, 112f to 108f, 260f to 100f),
            cellSizePx = 48f
        )

        assertEquals(listOf(1, 2), groups.map { it.size }.sorted())
    }

    @Test
    fun focusFitsOnlySelectedAlertAndLocationWhileOverviewFitsAllAlerts() {
        val selected = RadarVisualMarker("selected", RadarGeoPoint(49.8728, 8.6512))
        val farAway = RadarVisualMarker("far", RadarGeoPoint(50.1109, 8.6821))
        val location = RadarGeoPoint(49.8710, 8.6500)

        val focusPoints = resolveRadarViewportPoints(
            alertMarkers = listOf(selected, farAway),
            selectedAlertId = selected.alertId,
            viewMode = RadarViewMode.FOCUS,
            locationPoint = location
        )
        val overviewPoints = resolveRadarViewportPoints(
            alertMarkers = listOf(selected, farAway),
            selectedAlertId = selected.alertId,
            viewMode = RadarViewMode.OVERVIEW,
            locationPoint = location
        )

        assertEquals(listOf(selected.point, location), focusPoints)
        assertEquals(listOf(selected.point, farAway.point, location), overviewPoints)
    }

    @Test
    fun selectedAlertIsAlwaysRenderedAndNeverIncludedInClusters() {
        val alerts = (1..10).map { index ->
            PokemonAlert(name = "Alert $index", endTime = "2099-01-01T10:00:00Z")
        }
        val selected = alerts.last()

        val renderable = radarRenderableAlerts(
            alerts = alerts,
            selectedAlertId = selected.uniqueId,
            limit = 8
        )
        val partition = partitionRadarMarkers(
            alertIds = renderable.map(PokemonAlert::uniqueId),
            selectedAlertId = selected.uniqueId
        )

        assertEquals(8, renderable.size)
        assertEquals(selected, renderable.first())
        assertEquals(0, partition.selectedIndex)
        assertFalse(partition.clusterableIndices.contains(partition.selectedIndex))
        assertEquals(7, partition.clusterableIndices.size)
    }

    @Test
    fun focusModeCanClipOffscreenNonSelectedMarkers() {
        assertTrue(isRadarPointVisible(100f, 100f, 400, 240, 20f))
        assertTrue(isRadarPointVisible(-10f, 100f, 400, 240, 20f))
        assertFalse(isRadarPointVisible(-40f, 100f, 400, 240, 20f))
        assertFalse(isRadarPointVisible(100f, 300f, 400, 240, 20f))
    }

    @Test
    fun radarMarkerMetricsUseCompactIndependentCircleSizes() {
        val metrics = radarMarkerVisualMetrics(density = 2f)

        assertEquals(64f, metrics.alertDiameterPx, 0.001f)
        assertEquals(76f, metrics.selectedOuterDiameterPx, 0.001f)
        assertEquals(72f, metrics.clusterDiameterPx, 0.001f)
        assertEquals(72f, metrics.collisionDistancePx, 0.001f)
        assertEquals(38f, metrics.clipMarginPx, 0.001f)
    }

    @Test
    fun normalAndSelectedRadarMarkersStayCenteredOnExactProjectedCoordinate() {
        val metrics = radarMarkerVisualMetrics(density = 2f)
        val normal = radarAlertMarkerVisual(
            centerX = 123f,
            centerY = 87f,
            selected = false,
            metrics = metrics
        )
        val selected = radarAlertMarkerVisual(
            centerX = 123f,
            centerY = 87f,
            selected = true,
            metrics = metrics
        )

        assertEquals(123f, normal.centerX, 0.001f)
        assertEquals(87f, normal.centerY, 0.001f)
        assertEquals(91f, normal.outerLeft, 0.001f)
        assertEquals(55f, normal.outerTop, 0.001f)
        assertEquals(155f, normal.outerRight, 0.001f)
        assertEquals(119f, normal.outerBottom, 0.001f)
        assertEquals(64f, selected.contentDiameterPx, 0.001f)
        assertEquals(76f, selected.outerDiameterPx, 0.001f)
        assertEquals(123f, (selected.outerLeft + selected.outerRight) / 2f, 0.001f)
        assertEquals(87f, (selected.outerTop + selected.outerBottom) / 2f, 0.001f)
    }

    @Test
    fun accuracyRadiusGrowsWithReportedAccuracy() {
        val viewport = RadarViewport(RadarGeoPoint(49.8728, 8.6512), 15)
        val precise = radarAccuracyRadiusPx(49.8728, 8.6512, 10f, viewport, 800, 480)
        val coarse = radarAccuracyRadiusPx(49.8728, 8.6512, 200f, viewport, 800, 480)

        assertTrue(coarse > precise * 10f)
    }

    @Test
    fun locationFreshnessIsHonestAboutAgeAndAccuracy() {
        val now = 10 * 60_000L

        assertEquals("Location unavailable", formatRadarLocationAge(null, null, now))
        assertEquals("Location · now", formatRadarLocationAge(now - 10_000L, 15f, now))
        assertEquals("Approximate · 3m ago", formatRadarLocationAge(now - 180_000L, 250f, now))
    }
}
