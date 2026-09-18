package com.example.pokemonalertsv2.catchroutes

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.ArrivalCadence
import com.example.pokemonalertsv2.tracking.ArrivalLocationSource
import com.example.pokemonalertsv2.tracking.ArrivalLocationSourceFactory
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.DefaultArrivalLocationSourceFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Deterministic service and persistence coverage for Catch route guidance. */
@RunWith(AndroidJUnit4::class)
class CatchRouteInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store get() = CatchRouteStore.get(context)
    private val controller get() = CatchRouteController.get(context)
    private val hunts = HuntRepository.getInstance(context)
    private val arrivals = ArrivalTrackingRepository.getInstance(context)
    private lateinit var gps: PlaybackSource

    @Before
    fun setUp() = runBlocking<Unit> {
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        controller.stop()
        hunts.stop()
        arrivals.stopTracking()
        main {
            context.stopService(Intent(context, CatchRouteService::class.java))
            context.stopService(Intent(context, com.example.pokemonalertsv2.tracking.ArrivalTrackingService::class.java))
        }
        waitFor("previous Catch route notification removed") { catchNotification() == null }
        gps = PlaybackSource()
        CatchRouteService.locationSourceFactory = ArrivalLocationSourceFactory { gps }
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        controller.stop()
        hunts.stop()
        arrivals.stopTracking()
        main {
            context.stopService(Intent(context, CatchRouteService::class.java))
            context.stopService(Intent(context, com.example.pokemonalertsv2.tracking.ArrivalTrackingService::class.java))
        }
        waitFor("Catch route location source stopped") {
            !this@CatchRouteInstrumentedTest::gps.isInitialized || gps.callback == null
        }
        waitFor("Catch route notification removed") { catchNotification() == null }
        CatchRouteService.locationSourceFactory = DefaultArrivalLocationSourceFactory
    }

    @Test
    fun deadlineEndsGuidanceWithoutAnyGpsFix() = runBlocking<Unit> {
        val plan = fixtureItinerary("No GPS expiry")
        controller.begin(plan.copy(settings = plan.settings.copy(deadlineMillis = System.currentTimeMillis() + 1_000)))
        waitForSession("deadline marked finished") { it?.finished == true && it.paused }
        waitForStored("finished state persisted") { it?.finished == true }
        waitFor("expired service stopped location") { gps.callback == null }
        waitFor("expired foreground notification removed") { catchNotification() == null }
        assertEquals(0, controller.session.value?.visits?.size)
    }

    @Test
    fun rapidRestartKeepsNewestServiceAndSnapshot() = runBlocking<Unit> {
        controller.begin(fixtureItinerary("Old route"))
        controller.stop()
        val latest = fixtureItinerary("Newest route")
        controller.begin(latest)
        waitFor("newest route foreground notification") { catchNotification()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Newest route" }
        waitFor("newest route location source") { gps.callback != null }
        waitForStored("newest route snapshot") { it?.itinerary == latest }
        assertEquals(latest, controller.session.value?.itinerary)
    }

    @Test
    fun concurrentHandoverLeavesOneNavigationOwner() = runBlocking<Unit> {
        coroutineScope {
            launch(Dispatchers.Default) { controller.begin(fixtureItinerary("Concurrent Catch")) }
            launch(Dispatchers.Default) { hunts.start("Concurrent Hunt", FilterDefinition()) }
        }
        assertNotEquals(controller.session.value != null, hunts.currentSession() != null)
        controller.stop()
        hunts.stop()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        assertNull(store.current())
    }

    @Test
    fun rapidStartStopFulfillsForegroundStartAndCleansUp() = runBlocking<Unit> {
        controller.begin(fixtureItinerary("Rapid stop"))
        controller.stop()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        assertNull(controller.session.value)
        assertNull(store.current())
        assertNull(catchNotification())
    }

    @Test
    fun floatingWindowKeepsGuidanceAlive() = runBlocking<Unit> {
        val oldOp = String(shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))
        val oldMode = Regex("SYSTEM_ALERT_WINDOW: (\\w+)").find(oldOp)?.groupValues?.get(1) ?: "default"
        try {
            controller.begin(fixtureItinerary("Route surfaces"))
            waitFor("route notification") { catchNotification() != null }
            ActivityScenario.launch(CatchRoutesActivity::class.java).use { activity ->
                activity.recreate()
                instrumentation.waitForIdleSync()
                SystemClock.sleep(1500)
                capture("catch-route-session.png")
                assertNotNull(catchNotification())
            }
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            catchNotification()!!.actions.first { it.title.toString() == "Map" }.actionIntent.send()
            waitFor("floating application overlay") {
                hasCatchOverlay()
            }
            SystemClock.sleep(2000)
            capture("catch-route-floating.png")
            controller.pause()
            waitForSession("overlay pause") { it?.paused == true }
            assertNotNull(catchNotification())
            controller.stop()
            waitFor("overlay removed") { !hasCatchOverlay() }
        } finally {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $oldMode")
        }
    }

    private fun hasCatchOverlay() = String(shell("dumpsys window windows")).split(Regex("Window #\\d+ ")).any {
        it.contains("package=${context.packageName} ") && it.contains("ty=APPLICATION_OVERLAY")
    }

    private fun capture(name: String) {
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @Test
    fun savedSetupsSupportReadEditDuplicateAndDelete() = runBlocking<Unit> {
        val token = UUID.randomUUID().toString()
        val original = fixtureSettings("Setup $token")
        val id = store.save(original)
        var duplicateId: String? = null
        try {
            val created = store.setups.first { rows -> rows.any { it.id == id } }
                .first { it.id == id }
            assertEquals(original.name, created.name)
            assertEquals(original, store.decodeSetup(created))

            val edited = original.copy(name = "Edited $token")
            store.save(edited, id)
            val updated = store.setups.first { rows -> rows.any { it.id == id && it.name == edited.name } }
                .first { it.id == id }
            assertEquals(edited, store.decodeSetup(updated))

            duplicateId = store.save(store.decodeSetup(updated))
            assertNotEquals(id, duplicateId)
            val rows = store.setups.first { saved -> saved.any { it.id == duplicateId } }
            assertEquals(2, rows.count { it.id == id || it.id == duplicateId })
            assertEquals(edited.name, rows.first { it.id == duplicateId }.name)

            store.delete(id)
            store.delete(duplicateId!!)
            store.setups.first { saved -> saved.none { it.id == id || it.id == duplicateId } }
        } finally {
            store.delete(id)
            duplicateId?.let { store.delete(it) }
        }
    }

    @Test
    fun activeSessionSnapshotPersistsVisitedState() = runBlocking<Unit> {
        val plan = fixtureItinerary()
        controller.begin(plan)
        waitForSession("Catch route session started") { it?.itinerary == plan }
        waitFor("Catch route fake location source started") { gps.callback != null }

        emitTimedFixes(plan.encounters.single().opportunity.point)
        waitForSession("timed fixes record one visit") { it?.visits?.size == 1 }
        waitForStored("visited snapshot persisted") { it?.visits?.size == 1 }

        val restored = store.current()
        assertNotNull(restored)
        assertEquals(plan.encounters.single().opportunity.id, restored!!.visits.single().opportunity.id)
    }

    @Test
    fun catchStartReplacesHuntAndArrivalWithoutAlertFeed() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        hunts.start("Existing hunt", FilterDefinition(), nowMillis = now)
        val existingArrival = PokemonAlert(
            name = "Existing arrival destination",
            latitude = point(300.0).latitude,
            longitude = point(300.0).longitude,
            type = listOf("Spawn")
        )
        arrivals.startTracking(existingArrival, nowMillis = now)
        assertNotNull(hunts.currentSession())
        assertEquals(existingArrival.uniqueId, arrivals.currentDestination()?.uniqueId)

        controller.begin(fixtureItinerary("Handover"))
        waitFor("Catch route handover notification posted") { catchNotification() != null }
        waitForSession("Catch route handover session started") { it != null }
        waitFor("Hunt and arrival stores handed over") {
            runBlocking { hunts.currentSession() == null && arrivals.currentDestination() == null }
        }
        assertNotNull(controller.session.value)
    }

    @Test
    fun freshTimedFixesConfirmTheVisit() = runBlocking<Unit> {
        val plan = fixtureItinerary("Timed fixes")
        controller.begin(plan)
        waitFor("Catch route fake location source started") { gps.callback != null }
        emitTimedFixes(plan.encounters.single().opportunity.point)

        waitForSession("fresh fixes confirm visit") { it?.visits?.size == 1 }
        assertFalse(controller.session.value?.visits?.single()?.skipped == true)
    }

    @Test
    fun fixesNeverReplanAndLeavingTheRouteOnlyAdvises() = runBlocking<Unit> {
        // Guidance used to rebuild the whole route on a fix every two minutes, or 30 s after
        // leaving the path, and blank the stops meanwhile. Now fixes only ever advise.
        val plan = fixtureItinerary("No automatic replans")
        controller.begin(plan)
        waitFor("Catch route fake location source started") { gps.callback != null }

        // Well away from the 0-100 m path, for longer than a stray fix and past the old 30 s trigger.
        val away = point(500.0)
        val started = SystemClock.elapsedRealtime()
        var flaggedAfter: Long? = null
        while (SystemClock.elapsedRealtime() - started < 34_000L) {
            main { gps.callback?.invoke(fix(away)) }
            assertFalse("a fix must never start a replan", controller.recalculating.value)
            if (flaggedAfter == null && controller.outOfDate.value) flaggedAfter = SystemClock.elapsedRealtime() - started
            SystemClock.sleep(2_000L)
        }
        assertSame("the route must stay the one the trainer started", plan, controller.session.value?.itinerary)
        assertEquals(1, controller.session.value?.remaining?.size)
        assertNotNull("sustained time off the route must be flagged", flaggedAfter)
        assertTrue("one stray fix must not flag the route (flagged after ${flaggedAfter}ms)", flaggedAfter!! >= 18_000L)

        // Walking back onto the route clears the advice.
        main { gps.callback?.invoke(fix(point(50.0))) }
        waitFor("back on the route clears out of date") { !controller.outOfDate.value }
    }

    @Test
    fun standingStillOffTheRouteIsStillFlagged() = runBlocking<Unit> {
        // Fixes only arrive after 5 m of movement, so someone standing off the route sends one
        // and then nothing. Waiting for a second fix meant they were never told.
        controller.begin(fixtureItinerary("Standing off route"))
        waitFor("Catch route fake location source started") { gps.callback != null }
        main { gps.callback?.invoke(fix(point(500.0))) }
        SystemClock.sleep(15_000L)
        assertFalse("not flagged before it has lasted", controller.outOfDate.value)
        waitFor("flagged with no further fixes", timeout = 10_000L) { controller.outOfDate.value }
        assertFalse(controller.recalculating.value)
    }

    @Test
    fun skipAndUndoLeaveVisitStateIntact() = runBlocking<Unit> {
        val plan = fixtureItinerary("Skip and undo")
        controller.begin(plan)
        waitFor("Catch route notification posted") { catchNotification() != null }

        // The guidance notification offers only what does not cost play time.
        assertEquals(listOf("Pause", "Map"), catchNotification()!!.actions.map { it.title.toString() })

        controller.skip()
        waitForSession("skip retires the stop") { it?.visits?.size == 1 && it.visits.single().skipped }
        controller.undo()
        waitForSession("undo restores the stop") { it?.visits?.isEmpty() == true }
    }

    @Test
    fun pauseKeepsNotificationAliveAndIgnoresFixes() = runBlocking<Unit> {
        val plan = fixtureItinerary("Paused route")
        controller.begin(plan)
        waitFor("Catch route notification posted") { catchNotification() != null }

        controller.pause()
        waitForSession("Catch route paused") { it?.paused == true }
        waitFor("paused notification remains ongoing") {
            val notification = catchNotification()
            notification != null &&
                (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 &&
                notificationText().contains("Paused")
        }

        emitTimedFixes(plan.encounters.single().opportunity.point)
        SystemClock.sleep(200)
        assertEquals(0, controller.session.value?.visits?.size)
        assertNotNull(catchNotification())
    }

    @Test
    fun stoppingRemovesNotificationAndActiveSnapshot() = runBlocking<Unit> {
        controller.begin(fixtureItinerary("Stop route"))
        waitFor("Catch route notification posted") { catchNotification() != null }
        controller.stop()

        waitForSession("Catch route session stopped") { it == null }
        waitForStored("active Catch route snapshot cleared") { it == null }
        waitFor("Catch route location source stopped") { gps.callback == null }
        waitFor("Catch route notification removed") { catchNotification() == null }
    }

    @Test
    fun huntStartStopsCatchRouteThroughSingleReverseHandover() = runBlocking<Unit> {
        controller.begin(fixtureItinerary("Reverse handover"))
        waitFor("Catch route notification posted") { catchNotification() != null }
        assertNotNull(controller.session.value)

        val hunt = hunts.start("New hunt", FilterDefinition())
        assertEquals("New hunt", hunt.name)
        waitForSession("Hunt start stops Catch route") { it == null }
        waitForStored("reverse handover clears Catch route snapshot") { it == null }
        waitFor("reverse handover removes Catch route notification") { catchNotification() == null }
        waitFor("reverse handover stops Catch route location source") { gps.callback == null }
        assertEquals(hunt, hunts.currentSession())
    }

    private fun fixtureSettings(name: String, startAtMillis: Long = System.currentTimeMillis() - 10_000L) =
        CatchRouteSettings(
            name = name,
            start = point(0.0),
            startAtMillis = startAtMillis,
            durationMinutes = 10,
            finish = CatchFinish.ANYWHERE,
            speedMps = 1.0
        )

    private fun fixtureItinerary(name: String = "Fixture route"): CatchItinerary {
        val startAt = System.currentTimeMillis() - 10_000L
        val settings = fixtureSettings(name, startAt)
        val opportunity = SpawnOpportunity(
            id = "op-${UUID.randomUUID()}",
            pointId = "fixture-point",
            point = point(0.0),
            availableFrom = startAt - 1_000L,
            despawnAt = startAt + 300_000L,
            basis = "observed_encounter"
        )
        return CatchItinerary(
            settings = settings,
            path = listOf(CatchPathPosition(point(0.0), 0.0), CatchPathPosition(point(100.0), 100.0)),
            encounters = listOf(CatchEncounter(opportunity, startAt, 0.0)),
            anchors = listOf(point(0.0))
        )
    }

    private fun point(metersNorth: Double) =
        CatchPoint(49.7408 + metersNorth / 111_195.0, 8.6201)

    private suspend fun emitTimedFixes(point: CatchPoint) {
        main { gps.callback?.invoke(fix(point, ageMillis = 2_500L)) }
        main { gps.callback?.invoke(fix(point)) }
    }

    private fun fix(point: CatchPoint, ageMillis: Long = 0L) = Location("catch-test").apply {
        latitude = point.latitude
        longitude = point.longitude
        accuracy = 5f
        time = System.currentTimeMillis() - ageMillis
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageMillis * 1_000_000L
    }

    private fun catchNotification(): Notification? =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.id == CatchRouteService.NOTIFICATION_ID }
            ?.notification

    private fun notificationText() =
        catchNotification()?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

    private fun waitForSession(label: String, predicate: (CatchSession?) -> Boolean) =
        waitFor(label) { predicate(controller.session.value) }

    private suspend fun waitForStored(
        label: String,
        timeout: Long = 5_000L,
        predicate: (CatchSession?) -> Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        var current = store.current()
        while (!predicate(current) && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
            current = store.current()
        }
        assertTrue("Timed out: $label; stored=$current", predicate(current))
    }

    private fun waitFor(label: String, timeout: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out: $label", predicate())
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).readBytes()
    }

    private inner class PlaybackSource : ArrivalLocationSource {
        @Volatile var callback: ((Location) -> Unit)? = null

        override fun start(
            cadence: ArrivalCadence,
            onLocation: (Location) -> Unit,
            onAvailabilityChanged: (Boolean) -> Unit
        ): Boolean {
            callback = onLocation
            return true
        }

        override fun stop() {
            callback = null
        }
    }
}
