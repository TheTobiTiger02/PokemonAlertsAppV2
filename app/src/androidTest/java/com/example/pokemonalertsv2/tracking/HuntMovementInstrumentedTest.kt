package com.example.pokemonalertsv2.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.hunt.undoLastCatch
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Service-level route playback. All callbacks use the production main-looper contract. */
@RunWith(AndroidJUnit4::class)
class HuntMovementInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val journeys = ArrivalTrackingRepository.getInstance(context)
    private val hunts = HuntRepository.getInstance(context)
    private val preferences = AlertPreferences(context.alertPreferencesDataStore)
    private lateinit var gps: PlaybackSource
    private val target = PokemonAlert(
        name = "Hunt playback Pikachu", pokemon = "Pikachu", type = listOf("Hundo"),
        cp = 938, latitude = 49.738, longitude = 8.603, area = "HuntServicePlaybackOnly"
    )

    @Before fun setup() = runBlocking<Unit> {
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        hunts.stop()
        journeys.stopTracking()
        RaidWatchController.stop(context)
        main { context.stopService(Intent(context, ArrivalTrackingService::class.java)) }
        waitFor("previous service notification removed") { notification() == null }
        instrumentation.waitForIdleSync()
        preferences.updateSpacialRendEnabled(false)
        preferences.removeDismissedAlert(target.uniqueId)
        preferences.forgetCaughtAlert()
        gps = PlaybackSource()
        ArrivalTrackingService.locationSourceFactory = ArrivalLocationSourceFactory { gps }
    }

    @After fun cleanup() = runBlocking<Unit> {
        hunts.stop()
        journeys.stopTracking()
        RaidWatchController.stop(context)
        main { context.stopService(Intent(context, ArrivalTrackingService::class.java)) }
        waitFor("service location subscription released") { !this@HuntMovementInstrumentedTest::gps.isInitialized || gps.callback == null }
        waitFor("service notification removed") { notification() == null }
        ArrivalTrackingService.locationSourceFactory = DefaultArrivalLocationSourceFactory
    }

    @Test fun approachRetreatDetourStopAndResumeKeepTheHuntTarget() = runBlocking<Unit> {
        startHunt()
        // Metres north of the target: far, walking, approach, interaction range, retreat.
        for (metres in listOf(3000.0, 1200.0, 400.0, 150.0, 65.0, 20.0, 150.0, 400.0,
                170.0, 350.0, 100.0, 240.0, 30.0, -100.0, -300.0, 10.0)) {
            emit(fix(metres))
            assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
            assertNotNull(hunts.currentSession())
        }
        emit(fix(500.0))
        val starts = gps.starts
        repeat(20) { emit(fix(500.0)) }
        assertEquals("Stationary callbacks must not restart location requests", starts, gps.starts)
        gps.availability(false)
        SystemClock.sleep(300)
        assertNotNull(journeys.currentDestination())
        gps.availability(true)
        emit(fix(100.0))
        assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
        Log.i(TAG, "movement cadence starts=${gps.starts} stops=${gps.stops} fixes=${gps.fixes}")
    }

    @Test fun reachingPassingAndReturningDoesNotAutomaticallyCatchHuntTarget() = runBlocking<Unit> {
        startHunt()
        emit(fix(0.0))
        SystemClock.sleep(2100)
        emit(fix(0.0))
        waitFor("in range") { body().contains("In range") }
        assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
        emit(fix(-200.0))
        waitFor("out of range after passing") { !body().contains("In range") }
        emit(fix(0.0))
        waitFor("back in range") { body().contains("In range") }
        assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
    }

    @Test fun duplicateFixCannotConfirmArrival() = runBlocking<Unit> {
        journeys.startTracking(target)
        main { ArrivalTrackingService.start(context) }
        waitFor("GPS started") { gps.callback != null }
        val sample = fix(0.0)
        emit(sample)
        SystemClock.sleep(2100)
        emit(Location(sample))
        SystemClock.sleep(300)
        assertNotNull("Replaying one GPS observation must not count as a second arrival fix", journeys.currentDestination())
    }

    @Test fun olderFixCannotMoveAnInRangeHuntBackOutOfRange() = runBlocking<Unit> {
        startHunt()
        val old = fix(500.0)
        SystemClock.sleep(100)
        emit(fix(0.0))
        waitFor("first in-range fix") { body().contains("In range") }
        emit(old)
        SystemClock.sleep(300)
        assertTrue("An older batch must not overwrite the current position: ${body()}", body().contains("In range"))
    }

    @Test fun staleFixesCannotConfirmArrivalAndRecoveryWorks() = runBlocking<Unit> {
        startHunt()
        emit(fix(500.0))
        emit(fix(0.0).apply { elapsedRealtimeNanos -= 31_000_000_000L; time -= 31_000L })
        assertFalse(body().contains("In range"))
        emit(fix(0.0, 250f))
        assertFalse(body().contains("In range"))
        emit(fix(0.0, 150f))
        waitFor("phone-grade position in range") { body().contains("In range") }
        gps.availability(false)
        assertTrue(body().contains("In range"))
        gps.availability(true)
        emit(fix(200.0))
        waitFor("recovered outside radius") { !body().contains("In range") }
    }

    @Test fun notificationStopClearsBothStoresAndAnotherHuntCanStart() = runBlocking<Unit> {
        startHunt()
        emit(fix(400.0))
        val stop = notification()?.actions?.first { it.title.toString() == "Stop hunt" }
        assertNotNull(stop)
        stop!!.actionIntent.send()
        waitFor("hunt stopped") { runBlocking { hunts.currentSession() == null && journeys.currentDestination() == null } }
        waitFor("GPS stopped") { gps.callback == null }
        waitFor("stopped notification removed") { notification() == null }
        startHunt()
        emit(fix(100.0))
        assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
    }

    @Test fun notificationCatchAndUndoKeepHuntAlive() = runBlocking<Unit> {
        PokemonAlertsRepository.create(context).processIncomingAlert(target)
        startHunt()
        emit(fix(0.0))
        waitFor("catch action") { notification()?.actions?.any { it.title.toString() == "Got it" } == true }
        notification()!!.actions.first { it.title.toString() == "Got it" }.actionIntent.send()
        waitFor("caught target cleared") { runBlocking { journeys.currentDestination()?.uniqueId != target.uniqueId } }
        assertNotNull(hunts.currentSession())
        assertTrue(undoLastCatch(context))
        waitFor("undo restores target") { runBlocking { journeys.currentDestination()?.uniqueId == target.uniqueId } }
    }

    @Test fun repeatedStartsAndRapidFixesDoNotMultiplySubscriptions() = runBlocking<Unit> {
        startHunt()
        emit(fix(400.0))
        val starts = gps.starts
        repeat(10) { main { ArrivalTrackingService.startHunt(context) } }
        repeat(100) { emit(fix(400.0 + it % 5), settle = false) }
        assertEquals(starts, gps.starts)
        assertEquals(1, gps.starts - gps.stops)
        assertNotNull(journeys.currentDestination())
        Log.i(TAG, "burst 100 fixes: starts=${gps.starts} stops=${gps.stops}")
    }

    @Test fun standbyAcquiresTargetWithoutOverlayPermission() = runBlocking<Unit> {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW deny")
        try {
            PokemonAlertsRepository.create(context).processIncomingAlert(target)
            hunts.start("Playback", FilterDefinition(areas = FilterSelection.only(listOf("HuntServicePlaybackOnly"))))
            main { ArrivalTrackingService.startHunt(context) }
            waitFor("standby location subscription without overlay") { gps.callback != null }
            emit(fix(0.0))
            waitFor("standby target acquisition") { runBlocking { journeys.currentDestination()?.uniqueId == target.uniqueId } }
        } finally {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        }
    }

    @Test fun prolongedGpsLossMarksReadoutStaleAndRecovers() = runBlocking<Unit> {
        startHunt()
        emit(fix(0.0))
        waitFor("in range before loss") { body().contains("In range") }
        gps.availability(false)
        // Allow two existing 30-second notification ticks; this tests real elapsed time.
        SystemClock.sleep(65_000)
        assertTrue("Old in-range state must not remain current indefinitely: ${body()}", body().contains("Waiting for precise GPS"))
        gps.availability(true)
        emit(fix(0.0))
        waitFor("fresh position after recovery") { body().contains("In range") }
    }

    @Test fun invalidatedHuntTargetIsReleased() = runBlocking<Unit> {
        val alerts = PokemonAlertsRepository.create(context)
        alerts.processIncomingAlert(target)
        startHunt()
        emit(fix(400.0))
        alerts.processIncomingAlert(target.copy(invalidatedAt = java.time.Instant.now().toString()))
        waitFor("invalidated target released") { runBlocking { journeys.currentDestination()?.uniqueId != target.uniqueId } }
        assertNotNull(hunts.currentSession())
    }

    @Test fun futureDatedFixCannotClaimArrivalOrBlockRecovery() = runBlocking<Unit> {
        startHunt()
        emit(fix(400.0))
        emit(fix(0.0).apply { elapsedRealtimeNanos += 60_000_000_000L; time += 60_000 })
        assertFalse("A future observation is not a usable current fix", body().contains("In range"))
        emit(fix(0.0))
        waitFor("normal clock recovers") { body().contains("In range") }
    }

    @Test fun delayedDeliveryUsesObservationTimeForArrivalDwell() = runBlocking<Unit> {
        journeys.startTracking(target)
        main { ArrivalTrackingService.start(context) }
        waitFor("GPS started") { gps.callback != null }
        val first = fix(0.0)
        val second = Location(first).apply { elapsedRealtimeNanos += 100_000_000L; time += 100 }
        emit(first)
        SystemClock.sleep(2100)
        emit(second)
        assertNotNull("100 ms of observations cannot become 2 s of arrival dwell through delivery delay", journeys.currentDestination())
    }

    @Test fun activityRecreationAndBackgroundPreserveHuntOnBothMapProviders() = runBlocking<Unit> {
        startHunt()
        val session = hunts.currentSession()
        for (style in listOf(MapStylePreference.GOOGLE_STANDARD, MapStylePreference.OPENSTREETMAP)) {
            preferences.updateMapStylePreference(style)
            ActivityScenario.launch<MainActivity>(MainActivity.createMapIntent(context)).use { activity ->
                instrumentation.waitForIdleSync()
                emit(fix(300.0))
                activity.recreate()
                instrumentation.waitForIdleSync()
                assertEquals(session?.startedAtMillis, hunts.currentSession()?.startedAtMillis)
                assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
                activity.moveToState(Lifecycle.State.CREATED)
                emit(fix(100.0))
                assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
                activity.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                emit(fix(0.0))
                waitFor("arrival after recreation on $style") { body().contains("In range") }
                var entered = false
                activity.onActivity {
                    entered = it.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9)).build())
                }
                assertTrue("System accepted explicit PiP on $style", entered)
                // Allow the renderer and the system PiP transition to settle before capture.
                SystemClock.sleep(5000)
                activity.onActivity { assertTrue(it.isInPictureInPictureMode) }
                assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                java.io.File(context.getExternalFilesDir(null), "hunt-pip-${style.name}.png")
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            // Closing the pinned activity must leave the service's hunt intact.
            assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
            ActivityScenario.launch<MainActivity>(MainActivity.createMapIntent(context)).use { reopened ->
                reopened.onActivity { assertFalse(it.isInPictureInPictureMode) }
                emit(fix(100.0))
                waitFor("out of range after PiP close and reopen on $style") { !body().contains("In range") }
                assertEquals(session?.startedAtMillis, hunts.currentSession()?.startedAtMillis)
                assertEquals(target.uniqueId, journeys.currentDestination()?.uniqueId)
            }
        }
    }

    @Test fun emptyHuntWaitsForBothFirstMatchAndFirstGpsFix() = runBlocking<Unit> {
        val incoming = target.copy(name = "First incoming playback match", area = "HuntFirstMatchOnly")
        preferences.addDismissedAlert(incoming.uniqueId)
        hunts.start("Waiting", FilterDefinition(areas = FilterSelection.only(listOf("HuntFirstMatchOnly"))))
        main { ArrivalTrackingService.startHunt(context) }
        waitFor("standby subscription") { gps.callback != null }
        assertNull(journeys.currentDestination())
        PokemonAlertsRepository.create(context).processIncomingAlert(incoming)
        preferences.removeDismissedAlert(incoming.uniqueId)
        SystemClock.sleep(300)
        assertNull("A match alone is not a usable GPS origin", journeys.currentDestination())
        emit(fix(100.0))
        waitFor("first match acquired") { runBlocking { journeys.currentDestination()?.uniqueId == incoming.uniqueId } }
    }

    @Test fun expiringTargetEndsOnlyTheLeg() = runBlocking<Unit> {
        val expiring = target.copy(name = "Expiring playback", endTime = java.time.Instant.now().plusSeconds(5).toString())
        startHunt(expiring)
        emit(fix(100.0))
        waitFor("expired leg released", timeout = 8000) { runBlocking { journeys.currentDestination()?.uniqueId != expiring.uniqueId } }
        assertNotNull("Expiry must leave the hunt available for another match", hunts.currentSession())
    }

    @Test fun raidHandoffDoesNotClearAReplacementJourney() = runBlocking<Unit> {
        val raid = target.copy(name = "Playback raid", pokemon = "Mewtwo", type = listOf("Raid"),
            gym = "Playback gym", hundoCP = HundoCP(level20 = 2387, level25 = 2984),
            endTime = java.time.Instant.now().plusSeconds(600).toString())
        startHunt(raid)
        emit(fix(0.0), settle = false)
        val replacement = target.copy(name = "Chosen during raid handoff", latitude = 49.741)
        journeys.startTracking(replacement)
        hunts.setTarget(replacement.uniqueId)
        SystemClock.sleep(750)
        assertEquals("A completed old handoff cannot clear a newly chosen target", replacement.uniqueId,
            journeys.currentDestination()?.uniqueId)
    }

    private suspend fun startHunt(alert: PokemonAlert = target) {
        hunts.start("Playback", FilterDefinition(areas = FilterSelection.only(listOf("HuntServicePlaybackOnly"))))
        journeys.startTracking(alert)
        hunts.setTarget(alert.uniqueId)
        main { ArrivalTrackingService.startHunt(context) }
        waitFor("GPS started") { gps.callback != null }
        waitFor("hunt notification") { notification()?.actions?.any { it.title.toString() == "Got it" } == true }
        assertEquals("Fixture destination", alert.uniqueId, journeys.currentDestination()?.uniqueId)
    }

    private fun fix(northMetres: Double, accuracyMetres: Float = 5f) = Location("hunt-playback").apply {
        latitude = target.latitude!! + northMetres / 111_200.0
        longitude = target.longitude!!
        accuracy = accuracyMetres
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    private fun emit(location: Location, settle: Boolean = true) {
        main { gps.callback?.invoke(location); gps.fixes++ }
        if (settle) SystemClock.sleep(150)
        Log.i(TAG, "fix time=${location.elapsedRealtimeNanos} lat=${location.latitude} accuracy=${location.accuracy} body=${body()}")
    }
    private fun notification(): Notification? = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == ArrivalTrackingNotifications.ONGOING_NOTIFICATION_ID }?.notification
    private fun body() = notification()?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).readBytes()
    }
    private fun waitFor(label: String, timeout: Long = 5000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out: $label; notification=${body()}", predicate())
    }
    private inner class PlaybackSource : ArrivalLocationSource {
        @Volatile var callback: ((Location) -> Unit)? = null
        private var available: ((Boolean) -> Unit)? = null
        var starts = 0
        var stops = 0
        var fixes = 0
        override fun start(cadence: ArrivalCadence, onLocation: (Location) -> Unit, onAvailabilityChanged: (Boolean) -> Unit): Boolean {
            starts++
            callback = onLocation
            available = onAvailabilityChanged
            return true
        }
        override fun stop() { stops++; callback = null; available = null }
        fun availability(value: Boolean) = main { available?.invoke(value) }
    }
    companion object { private const val TAG = "HuntPlayback" }
}
