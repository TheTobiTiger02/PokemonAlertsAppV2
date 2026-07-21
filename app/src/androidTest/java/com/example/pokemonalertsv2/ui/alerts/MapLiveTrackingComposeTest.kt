package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.location.Location
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MapLiveTrackingComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun grantLocationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("pm grant $packageName ${Manifest.permission.ACCESS_FINE_LOCATION}")
            .close()
    }

    @Test
    fun gpsButtonStartsInjectedTrackerAndShowsFollowingState() {
        var trackerStarted = false
        val pose = MapUserPose(
            location = Location("test").apply {
                latitude = 49.738
                longitude = 8.603
                accuracy = 3f
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
            headingDegrees = 42f,
            headingFromSensor = true
        )
        val trackerFactory: MapPoseTrackerFactory = { _, onPose, onStatus ->
            object : MapPoseTracker {
                override fun start() {
                    trackerStarted = true
                    onPose(pose)
                    onStatus(MapTrackingStatus.ACTIVE)
                }

                override fun stop() = Unit
            }
        }

        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertsMapScreenContent(
                    alerts = emptyList(),
                    onBack = {},
                    onRefresh = {},
                    showBackButton = false,
                    locationTrackerFactory = trackerFactory
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            runCatching {
                composeRule.onNodeWithContentDescription("Start live location tracking")
                    .fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Start live location tracking")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { trackerStarted }
        composeRule.onNodeWithContentDescription("Following your live location")
            .assertIsDisplayed()
        assertTrue(trackerStarted)
    }
}
