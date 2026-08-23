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
            zoom = 14.0,
            density = 3f
        )

        assertEquals(1, items.size)
        assertEquals(2, (items.single() as MapMarkerItem.Cluster).alerts.size)
    }

    @Test
    fun separateAlertsRemainIndividualAtMaximumZoom() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.73801, 8.61801)),
            zoom = MAP_CLUSTER_MAX_ZOOM,
            density = 3f
        )

        assertTrue(items.all { it is MapMarkerItem.Alert })
    }

    @Test
    fun coincidentAlertsRemainClusteredAtMaximumZoom() {
        val items = clusterMapAlerts(
            alerts = listOf(alert("a", 49.7380, 8.6180), alert("b", 49.7380, 8.6180)),
            zoom = 20.0,
            density = 3f
        )

        assertTrue(items.single() is MapMarkerItem.Cluster)
    }

    @Test
    fun mixedClusterDoesNotClaimCategory() {
        val items = clusterMapAlerts(
            alerts = listOf(
                alert("a", 49.7380, 8.6180, listOf("Raid")),
                alert("b", 49.7380, 8.6180, listOf("Quest"))
            ),
            zoom = 14.0,
            density = 3f
        )

        assertNull((items.single() as MapMarkerItem.Cluster).sharedCategory)
    }

    @Test
    fun expandedNonCoincidentMembersRenderIndividuallyBelowThreshold() {
        val first = alert("a", 49.7380, 8.6180)
        val second = alert("b", 49.73801, 8.61801)

        val items = clusterMapAlerts(
            alerts = listOf(first, second),
            zoom = 14.0,
            density = 3f,
            expandedAlertIds = setOf(first.uniqueId, second.uniqueId)
        )

        assertEquals(2, items.size)
        assertTrue(items.all { it is MapMarkerItem.Alert })
    }

    @Test
    fun expandedCoincidentMembersRemainASelectableCountCluster() {
        val first = alert("a", 49.7380, 8.6180)
        val second = alert("b", 49.7380, 8.6180)

        val items = clusterMapAlerts(
            alerts = listOf(first, second),
            zoom = 14.0,
            density = 3f,
            expandedAlertIds = setOf(first.uniqueId, second.uniqueId)
        )

        assertTrue(items.single() is MapMarkerItem.Cluster)
        assertTrue(areMapAlertsCoincident(listOf(first, second)))
    }

    @Test
    fun unrelatedAlertsContinueClusteringWhileExpandedMembersStayIndividual() {
        val expandedFirst = alert("expanded-a", 49.7380, 8.6180)
        val expandedSecond = alert("expanded-b", 49.73801, 8.61801)
        val normalFirst = alert("normal-a", 49.7390, 8.6190)
        val normalSecond = alert("normal-b", 49.73901, 8.61901)

        val items = clusterMapAlerts(
            alerts = listOf(expandedFirst, expandedSecond, normalFirst, normalSecond),
            zoom = 14.0,
            density = 3f,
            expandedAlertIds = setOf(expandedFirst.uniqueId, expandedSecond.uniqueId)
        )

        assertEquals(2, items.count { it is MapMarkerItem.Alert })
        assertEquals(1, items.count { it is MapMarkerItem.Cluster })
        assertEquals(
            setOf(normalFirst.uniqueId, normalSecond.uniqueId),
            (items.single { it is MapMarkerItem.Cluster } as MapMarkerItem.Cluster)
                .alerts
                .mapTo(mutableSetOf(), PokemonAlert::uniqueId)
        )
    }

    @Test
    fun expansionStateClearsOnlyAfterZoomingOutAndDropsInactiveMembers() {
        val original = listOf("expired", "active")
        assertFalse(shouldClearExpandedMapCluster(15.0, 15.0))
        assertFalse(shouldClearExpandedMapCluster(15.0, 16.0))
        assertTrue(shouldClearExpandedMapCluster(15.0, 14.8))
        assertEquals(
            listOf("active"),
            retainActiveExpandedAlertIds(
                expandedAlertIds = original,
                activeAlertIds = setOf("active", "other")
            )
        )
        assertEquals(listOf("expired", "active"), original)
    }

    @Test
    fun clusterInteractionExpandsDistinctMembersButListsCoordinateStacks() {
        val first = alert("a", 49.7380, 8.6180)
        val second = alert("b", 49.73801, 8.61801)
        val distinctCluster = clusterMapAlerts(
            alerts = listOf(first, second),
            zoom = 14.0,
            density = 3f
        ).single() as MapMarkerItem.Cluster
        val stackedCluster = clusterMapAlerts(
            alerts = listOf(first, second.copy(latitude = first.latitude, longitude = first.longitude)),
            zoom = 20.0,
            density = 3f
        ).single() as MapMarkerItem.Cluster

        val expansion = resolveMapClusterInteraction(distinctCluster, currentZoom = 14.0)

        assertTrue(expansion is MapClusterInteraction.Expand)
        expansion as MapClusterInteraction.Expand
        assertEquals(14.0, expansion.originZoom, 0.001)
        assertEquals(
            setOf(first.uniqueId, second.uniqueId),
            expansion.alertIds.toSet()
        )
        assertEquals(
            MapClusterInteraction.ShowMembers,
            resolveMapClusterInteraction(stackedCluster, currentZoom = 20.0)
        )
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

        val first = clusterMapAlerts(alerts, zoom = 14.0, density = 3f)
            .single() as MapMarkerItem.Cluster
        val second = clusterMapAlerts(alerts.reversed(), zoom = 14.0, density = 3f)
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
