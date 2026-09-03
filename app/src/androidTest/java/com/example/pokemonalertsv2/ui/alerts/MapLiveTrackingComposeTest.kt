package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.location.Location
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
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

    @Test
    fun compactPictureInPictureStartsTrackingAndSuppressesFullMapControls() {
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
                    presentationMode = MapPresentationMode.COMPACT_PICTURE_IN_PICTURE,
                    initialZoom = 12.5,
                    onEnterPictureInPicture = {},
                    locationTrackerFactory = trackerFactory
                )
            }
        }

        composeRule.onNodeWithTag("map_pip_content").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000L) { trackerStarted }
        composeRule.onNodeWithContentDescription("Open map in picture-in-picture")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Map layers").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Following your live location")
            .assertDoesNotExist()
        assertTrue(trackerStarted)
    }

    @Test
    fun pictureInPictureBrowseCommandSelectsAnAlertAndShowsItsChip() {
        val commands = MutableSharedFlow<MapPipCommand>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val reportedStates = mutableListOf<Pair<MapPipMode, Boolean>>()
        // Larvitar sits on the map's default centre and Chespin well outside it, so the
        // nearest-first cursor has an unambiguous first stop.
        val alerts = listOf(
            PokemonAlert(name = "Larvitar", latitude = ALSBACH_LATITUDE, longitude = ALSBACH_LONGITUDE),
            PokemonAlert(name = "Chespin", latitude = 49.8000, longitude = 8.7000)
        )

        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertsMapScreenContent(
                    alerts = alerts,
                    onBack = {},
                    onRefresh = {},
                    presentationMode = MapPresentationMode.COMPACT_PICTURE_IN_PICTURE,
                    initialZoom = 15.0,
                    onEnterPictureInPicture = {},
                    pipCommands = commands,
                    onPipStateChanged = { mode, canStep -> reportedStates += mode to canStep }
                )
            }
        }

        composeRule.onNodeWithTag("map_pip_content").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000L) { reportedStates.isNotEmpty() }
        assertEquals(MapPipMode.FOLLOW to true, reportedStates.first())
        composeRule.onNodeWithTag("map_pip_browse_chip").assertDoesNotExist()

        composeRule.runOnIdle { commands.tryEmit(MapPipCommand.TOGGLE_MODE) }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            reportedStates.lastOrNull()?.first == MapPipMode.BROWSE
        }
        composeRule.onNodeWithTag("map_pip_browse_chip").assertIsDisplayed()
        // Scoped to the chip: the map's own marker also carries the species name.
        composeRule.onNode(
            hasAnyAncestor(hasTestTag("map_pip_browse_chip")) and
                hasText("Larvitar", substring = true)
        ).assertExists()
    }

    @Test
    fun mapToolbarExposesPictureInPictureAction() {
        var selected = false
        composeRule.setContent {
            PokemonAlertsV2Theme {
                MapHeaderBar(
                    visibleAlertCount = 3,
                    showBackButton = false,
                    refreshing = false,
                    activeLayerCount = 0,
                    onBack = {},
                    onRefresh = {},
                    onOpenLayers = {},
                    onEnterPictureInPicture = { selected = true }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open map in picture-in-picture")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(selected) }
    }
}
