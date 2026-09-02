package com.example.pokemonalertsv2.ui.alerts

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapMarkerPreparationComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun supersededBatchesCannotRestoreRemovedMarkers() {
        val target = mutableStateOf((0 until MAX_RENDERED_MAP_MARKERS).map { "old-$it" })
        var visible = emptyList<String>()
        composeRule.setContent {
            val result by rememberBatchedMapItems(target.value) { it }
            androidx.compose.material3.Text("${result.size} markers")
            SideEffect { visible = result }
        }
        composeRule.waitUntil(5_000) { visible == target.value }
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { target.value = (0 until MAX_RENDERED_MAP_MARKERS).map { "new-$it" } }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnUiThread { target.value = listOf("tracked") }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(5_000) { visible == listOf("tracked") }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(listOf("tracked"), visible) }
    }

    @Test fun rapidCameraFilterAndExpirationUpdatesOnlyPublishLatestMarkers() {
        val dense = List(10_000) { index ->
            PokemonAlert(id = index + 1, name = "a$index", latitude = 49.87, longitude = 8.65)
        }
        val input = mutableStateOf(dense)
        val bounds = mutableStateOf<MapGeoBounds?>(null)
        val zoom = mutableStateOf(12.0)
        var published = PreparedMapMarkers()
        composeRule.setContent {
            val result by rememberPreparedMapMarkers(input.value, bounds.value, zoom.value, 40.0, emptySet())
            androidx.compose.material3.Text("${result.alerts.size} prepared alerts")
            SideEffect { published = result }
        }
        composeRule.waitUntil(10_000) { published.alerts.size == 10_000 }
        repeat(8) { iteration ->
            composeRule.runOnIdle {
                zoom.value = 12.0 + iteration
                input.value = if (iteration % 2 == 0) dense.reversed() else dense
            }
        }
        val finalAlert = dense.first().copy(latitude = 49.9, longitude = 8.7)
        composeRule.runOnIdle {
            input.value = listOf(finalAlert)
            bounds.value = MapGeoBounds(49.89, 8.69, 49.91, 8.71)
            zoom.value = 20.0
        }
        composeRule.waitUntil(10_000) { published.alerts == listOf(finalAlert) }
        composeRule.runOnIdle {
            assertEquals(finalAlert, (published.items.single() as MapMarkerItem.Alert).alert)
            input.value = emptyList() // expiration/removal
        }
        composeRule.waitUntil(5_000) { published.items.isEmpty() && published.alerts.isEmpty() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertTrue(published.items.isEmpty()) }
    }

    @Test fun lastMemberOfTenThousandCanBeScrolledToAndSelected() {
        val alerts = List(10_000) { index ->
            PokemonAlert(id = index + 1, name = "Stack member $index", endTime = "2099-01-01T00:00:00Z")
        }
        var selected: PokemonAlert? = null
        composeRule.setContent {
            PokemonAlertsV2Theme {
                MapClusterMemberList(alerts, remember { mutableStateOf(System.currentTimeMillis()) }) { selected = it }
            }
        }
        composeRule.onNodeWithText("Stack member 9999").assertDoesNotExist()
        composeRule.onNodeWithTag("map_cluster_members").performScrollToIndex(10_000)
        composeRule.onNodeWithText("Stack member 9999").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(alerts.last(), selected) }
    }
}
