package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapClusteringTest {
    @Test
    fun nearbyAlertsClusterBelowThreshold() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.73801, 8.61801)),
            zoom = 12.0
        )

        assertEquals(1, items.size)
        assertEquals(2, (items.single() as MapMarkerItem.Cluster).alerts.size)
    }

    @Test
    fun separateAlertsRemainIndividualAtMaximumZoom() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.73801, 8.61801)),
            zoom = MAP_CLUSTER_MAX_ZOOM
        )

        assertTrue(items.all { it is MapMarkerItem.Alert })
    }

    @Test
    fun coincidentAlertsRemainClusteredAtMaximumZoom() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.7380, 8.6180)),
            zoom = 20.0
        )

        assertTrue(items.single() is MapMarkerItem.Cluster)
    }

    @Test
    fun protectedAlertRemainsIndividualWhenNearbyAlertsCluster() {
        val tracked = alert("tracked", 49.7380, 8.6180)
        val items = clusterMapAlerts(
            alerts = listOf(
                tracked,
                alert("near-a", 49.73801, 8.61801),
                alert("near-b", 49.73802, 8.61802)
            ),
            zoom = 12.0,
            protectedAlertIds = setOf(tracked.uniqueId)
        )

        assertEquals(2, items.size)
        assertEquals(
            tracked.uniqueId,
            (items.single { it is MapMarkerItem.Alert } as MapMarkerItem.Alert).alert.uniqueId
        )
        assertEquals(2, (items.single { it is MapMarkerItem.Cluster } as MapMarkerItem.Cluster).alerts.size)
    }

    @Test
    fun protectedAlertRemainsIndividualAtExactlyTheSameCoordinates() {
        val tracked = alert("tracked", 49.7380, 8.6180)
        val stacked = alert("stacked", 49.7380, 8.6180)

        val items = clusterMapAlerts(
            alerts = listOf(tracked, stacked),
            zoom = 20.0,
            protectedAlertIds = setOf(tracked.uniqueId)
        )

        assertEquals(2, items.size)
        assertTrue(items.all { it is MapMarkerItem.Alert })
        assertEquals(
            setOf(tracked.uniqueId, stacked.uniqueId),
            items.mapTo(mutableSetOf()) { (it as MapMarkerItem.Alert).alert.uniqueId }
        )
    }

    @Test
    fun protectedAlertDoesNotChangeUnrelatedDeterministicCluster() {
        val tracked = alert("tracked", 49.7400, 8.6200)
        val nearby = listOf(
            alert("b", 49.73801, 8.61801),
            alert("a", 49.73800, 8.61800)
        )

        val first = clusterMapAlerts(
            alerts = nearby + tracked,
            zoom = 12.0,
            protectedAlertIds = setOf(tracked.uniqueId)
        ).single { it is MapMarkerItem.Cluster } as MapMarkerItem.Cluster
        val second = clusterMapAlerts(
            alerts = (nearby + tracked).reversed(),
            zoom = 12.0,
            protectedAlertIds = setOf(tracked.uniqueId)
        ).single { it is MapMarkerItem.Cluster } as MapMarkerItem.Cluster

        assertEquals(first.id, second.id)
        assertEquals(first.alerts.map(PokemonAlert::uniqueId), second.alerts.map(PokemonAlert::uniqueId))
    }

    @Test
    fun mixedClusterDoesNotClaimCategory() {
        val items = clusterMapAlerts(
            alerts = listOf(
                alert("a", 49.7380, 8.6180, listOf("Raid")),
                alert("b", 49.7380, 8.6180, listOf("Quest"))
            ),
            zoom = 12.0
        )

        assertNull((items.single() as MapMarkerItem.Cluster).sharedCategory)
    }

    @Test
    fun clusterTapZoomsTwoLevelsWithoutExpandingMembers() {
        val alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.73801, 8.61801))
        val cluster = clusterMapAlerts(alerts, zoom = 12.0).single() as MapMarkerItem.Cluster
        val interaction = resolveMapClusterInteraction(cluster, currentZoom = 12.0, maximumZoom = 20.0)
        assertEquals(
            MapClusterInteraction.ZoomTo(MapCameraSnapshot(cluster.latitude, cluster.longitude, 14.0)),
            interaction
        )
        assertEquals(cluster, clusterMapAlerts(alerts, zoom = 12.0).single())
        assertEquals(
            MapClusterInteraction.ZoomTo(MapCameraSnapshot(cluster.latitude, cluster.longitude, 20.0)),
            resolveMapClusterInteraction(cluster, 19.0, 20.0)
        )
        assertEquals(MapClusterInteraction.ShowMembers, resolveMapClusterInteraction(cluster, 20.0, 20.0))
    }

    @Test
    fun coincidentMembersOpenListAtAnyZoom() {
        val alerts = listOf(alert("a", 49.738, 8.618), alert("b", 49.738, 8.618))
        val cluster = clusterMapAlerts(alerts, zoom = 12.0).single() as MapMarkerItem.Cluster
        assertEquals(MapClusterInteraction.ShowMembers, resolveMapClusterInteraction(cluster, 12.0, 20.0))
    }

    @Test
    fun screenDistanceClusteringJoinsMarkersAcrossFormerCellBoundaries() {
        val groups = connectedMapScreenComponents(
            points = listOf(
                MapScreenPoint(54.0, 100.0),
                MapScreenPoint(58.0, 100.0),
                MapScreenPoint(62.0, 104.0),
                MapScreenPoint(66.0, 98.0)
            ),
            thresholdPx = 56.0
        )

        assertEquals(listOf(listOf(0, 1, 2, 3)), groups)
    }

    @Test
    fun screenDistanceClusteringIsTransitiveButKeepsDistantMarkersSeparate() {
        val groups = connectedMapScreenComponents(
            points = listOf(
                MapScreenPoint(0.0, 0.0),
                MapScreenPoint(50.0, 0.0),
                MapScreenPoint(100.0, 0.0),
                MapScreenPoint(220.0, 0.0)
            ),
            thresholdPx = 56.0
        )

        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), groups)
    }

    @Test
    fun clusterIdsAndMemberOrderingStayStableWhenInputOrderChanges() {
        val alerts = listOf(
            alert("d", 49.73803, 8.61803),
            alert("b", 49.73801, 8.61801),
            alert("a", 49.73800, 8.61800),
            alert("c", 49.73802, 8.61802)
        )

        val first = clusterMapAlerts(alerts, zoom = 12.0)
            .single() as MapMarkerItem.Cluster
        val second = clusterMapAlerts(alerts.reversed(), zoom = 12.0)
            .single() as MapMarkerItem.Cluster

        assertEquals(first.id, second.id)
        assertEquals(
            first.alerts.map(PokemonAlert::uniqueId),
            second.alerts.map(PokemonAlert::uniqueId)
        )
        assertEquals(
            alerts.map(PokemonAlert::uniqueId).sorted(),
            first.alerts.map(PokemonAlert::uniqueId)
        )
    }

    @Test
    fun defaultNeighborhoodZoomKeepsNearbyAlertsIndividual() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.73980, 8.6180)),
            zoom = 13.0
        )

        assertEquals(2, items.size)
        assertTrue(items.all { it is MapMarkerItem.Alert })
    }

    @Test
    fun neighborhoodZoomIgnoresOrdinaryClusteringWithOrWithoutSpawnCircles() {
        val alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.73890, 8.6180))

        val guarded = clusterMapAlerts(alerts = alerts, zoom = 13.0, spawnRadiusMeters = 40.0)
        val unguarded = clusterMapAlerts(alerts = alerts, zoom = 13.0)

        assertEquals(2, guarded.size)
        assertTrue(guarded.all { it is MapMarkerItem.Alert })
        assertEquals(2, unguarded.size)
        assertTrue(unguarded.all { it is MapMarkerItem.Alert })
    }

    @Test
    fun neighborhoodZoomKeepsOverlappingButNonCoincidentAlertsIndividual() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.738450, 8.6180)),
            zoom = 13.0,
            spawnRadiusMeters = 40.0
        )

        assertEquals(2, items.size)
        assertTrue(items.all { it is MapMarkerItem.Alert })
    }

    @Test
    fun spawnCircleGuardIsInertWhenCirclesAreTooSmallToSee() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.73890, 8.6180)),
            zoom = 12.0,
            spawnRadiusMeters = 40.0
        )

        assertTrue(items.single() is MapMarkerItem.Cluster)
    }

    @Test
    fun spawnCircleGuardDoesNotApplyToAlertsWithoutACircle() {
        val items = clusterMapAlerts(
            alerts = listOf(
                alert("a", 49.7380, 8.6180, listOf("Raid")),
                alert("b", 49.73890, 8.6180)
            ),
            zoom = 12.0,
            spawnRadiusMeters = 40.0
        )

        assertTrue(items.single() is MapMarkerItem.Cluster)
    }

    private fun alert(
        name: String,
        latitude: Double,
        longitude: Double,
        type: List<String> = listOf("Spawn")
    ) = PokemonAlert(
        name = name,
        latitude = latitude,
        longitude = longitude,
        endTime = "2099-01-01T00:00:00Z",
        type = type
    )
}
