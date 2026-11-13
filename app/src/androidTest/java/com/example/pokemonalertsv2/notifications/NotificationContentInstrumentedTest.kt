package com.example.pokemonalertsv2.notifications

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.raidwatch.RaidTeamMember
import com.example.pokemonalertsv2.raidwatch.RaidTeamSnapshot
import com.example.pokemonalertsv2.raidwatch.RaidWatchNotifications
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.raidwatch.RaidWatchStore
import com.example.pokemonalertsv2.raidwatch.WatchedRaid
import com.example.pokemonalertsv2.tracking.ArrivalTrackingNotifications
import com.example.pokemonalertsv2.tracking.TrackedDestination
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.ui.settings.SettingsViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import java.io.File

class NotificationContentInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferences = AlertPreferences(context.alertPreferencesDataStore)

    @Before
    fun prepareProof() {
        if (InstrumentationRegistry.getArguments().getString("notificationProof") == "true") {
            manager.cancelAll()
            shell("cmd statusbar collapse")
            val info = instrumentation.uiAutomation.serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            instrumentation.uiAutomation.serviceInfo = info
        }
    }

    @Test
    fun largePictureExpandsToTheMapAndNoneLeavesThePictureOut() {
        val alert = raid().copy(id = 9_910_009)
        val content = renderNotificationContent(NotificationContentInput(alert, "320 m", "4 min walk"))
        val picture = android.graphics.Bitmap.createBitmap(64, 32, android.graphics.Bitmap.Config.ARGB_8888)
        val large = AlertNotifier.buildAlertNotification(
            context, alert, content, picture,
            com.example.pokemonalertsv2.data.NotificationImageStyle.LARGE, AlertNotifier.CHANNEL_RAIDS
        ).build()
        assertTrue(large.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("BigPictureStyle"))
        assertTrue(large.extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT).toString().contains("Central Gym"))
        assertEquals("Raid · Mewtwo", large.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val none = AlertNotifier.buildAlertNotification(
            context, alert, content, picture,
            com.example.pokemonalertsv2.data.NotificationImageStyle.NONE, AlertNotifier.CHANNEL_RAIDS
        ).build()
        assertTrue(none.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("BigTextStyle"))
        assertNull(none.getLargeIcon())
    }

    @Test
    fun incomingAlertUsesSelectedTextWithExpiryAndSummary() = runBlocking {
        val previousContent = preferences.notificationContent.first()
        val previousFilter = preferences.filterStateDocument.first()
        val previousEnabled = preferences.notificationsEnabled.first()
        val previousQuiet = preferences.quietHoursEnabled.first()
        val previousSilence = preferences.silenceUntil.first()
        val alert = raid().copy(id = 9_910_001)
        val id = AlertNotificationIds.forAlert(alert.uniqueId)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            preferences.updateNotificationsEnabled(true)
            preferences.updateQuietHoursEnabled(false)
            preferences.updateSilenceUntil(0)
            preferences.updateFilterStateDocument {
                it.copy(notifications = FilterAssignment.local(FilterDefinition(alertTypes = FilterSelection.only(listOf("RAID")))))
            }
            preferences.updateNotificationContent {
                NotificationContentPreferences().withField(FilterAlertType.RAID, true, NotificationField.TIME_LEFT, false)
                    .withImageStyle(FilterAlertType.RAID, com.example.pokemonalertsv2.data.NotificationImageStyle.THUMBNAIL)
            }
            AlertNotifier.notifyAlerts(context, listOf(alert))
            val posted = waitForNotification(id)
            assertTrue(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("💨 Windy • Boosted"))
            assertFalse(posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("min left"))
            assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertTrue(posted.timeoutAfter > 0)
            assertTrue(posted.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("BigTextStyle"))
            assertTrue(posted.actions.any { it.title == "Directions" || it.title == "Open Maps" })
            val summary = waitForNotification(AlertNotificationIds.forSummary(AlertNotifier.CHANNEL_RAIDS)) { notification ->
                notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().any { it.contains("Windy") }
            }
            assertTrue(summary.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().any { it.contains("Windy") })
            captureShade("incoming-raid")
        } finally {
            shell("cmd statusbar collapse")
            manager.cancel(id)
            manager.cancel(AlertNotificationIds.forSummary(AlertNotifier.CHANNEL_RAIDS))
            preferences.updateNotificationContent { previousContent }
            preferences.updateFilterStateDocument { previousFilter }
            preferences.updateNotificationsEnabled(previousEnabled)
            preferences.updateQuietHoursEnabled(previousQuiet)
            preferences.updateSilenceUntil(previousSilence)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun settingsChangesRefreshRaidWatchWithoutDroppingItsTeam() = runBlocking {
        val store = RaidWatchStore(context)
        org.junit.Assume.assumeTrue(store.current() == null)
        val previousContent = preferences.notificationContent.first()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            val now = System.currentTimeMillis()
            val alert = raid()
            val team = RaidTeamSnapshot(
                alertUniqueId = alert.uniqueId,
                members = listOf(RaidTeamMember("Tyranitar", 2, "Bite", "Brutal Swing")), goQuery = "248"
            )
            preferences.updateNotificationContent { NotificationContentPreferences() }
            store.save(alert, now, now + 720_000L)
            store.saveTeam(team)
            RaidWatchController.refresh(context)
            val before = store.current()
            assertTrue(waitForNotification(RaidWatchNotifications.NOTIFICATION_ID).extras
                .getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Tyranitar"))
            instrumentation.runOnMainSync {
                SettingsViewModel(context.applicationContext as android.app.Application, SavedStateHandle())
                    .updateNotificationField(FilterAlertType.RAID, true, NotificationField.TEAM, false)
            }
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            var posted = waitForNotification(RaidWatchNotifications.NOTIFICATION_ID)
            while (posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Tyranitar") && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
                posted = waitForNotification(RaidWatchNotifications.NOTIFICATION_ID)
            }
            assertFalse(posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Tyranitar"))
            assertEquals(before, store.current())
            assertEquals(team, store.currentTeam())
            assertTrue(posted.actions.any { it.title == "Copy team" })
        } finally {
            RaidWatchController.stop(context)
            preferences.updateNotificationContent { previousContent }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun liveTextCardsHideTravelAndRetainChipTeamAndActions() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        val id = 9_910_010
        try {
            val alert = raid()
            ArrivalTrackingNotifications.ensureChannels(context)
            val journey = ArrivalTrackingNotifications.ongoing(
                context, TrackedDestination(alert, 80, System.currentTimeMillis()),
                distanceMeters = 75f,
                walkingRoute = WalkingRouteInfo(320, 240),
                inRange = true,
                huntActive = true
            )
            assertTrue(journey.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("In range • 💨 Windy • Boosted"))
            assertFalse(journey.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("320 m"))
            assertFalse(journey.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("min walk"))
            assertTrue(journey.actions.any { it.title == "Got it" })
            assertTrue(journey.actions.any { it.title == "Stop hunt" })
            manager.notify(id, journey)
            waitForNotification(id)
            if (Build.VERSION.SDK_INT >= 36) {
                assertTrue(journey.hasPromotableCharacteristics())
                assertEquals("2387/2984", journey.shortCriticalText)
                val deadline = SystemClock.elapsedRealtime() + 5_000L
                while (waitForNotification(id).flags and Notification.FLAG_PROMOTED_ONGOING == 0 && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(50)
                }
                assertTrue(waitForNotification(id).flags and Notification.FLAG_PROMOTED_ONGOING != 0)
            }
            captureShade("in-range-journey")
            manager.cancel(id)

            val team = RaidTeamSnapshot(
                alertUniqueId = alert.uniqueId,
                members = listOf(RaidTeamMember("Tyranitar", 2, "Bite", "Brutal Swing")),
                goQuery = "248"
            )
            val hiddenCatchCp = NotificationContentPreferences().withField(FilterAlertType.RAID, false, NotificationField.CATCH_CP, false)
            RaidWatchNotifications.ensureChannel(context)
            val watched = WatchedRaid(alert, System.currentTimeMillis(), System.currentTimeMillis() + 12 * 60_000)
            val raidWatch = RaidWatchNotifications.build(context, watched, team, contentPreferences = hiddenCatchCp)
            assertTrue(raidWatch.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Windy"))
            assertFalse(raidWatch.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("In range"))
            assertFalse(raidWatch.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("2387"))
            assertTrue(raidWatch.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().startsWith("1. Tyranitar"))
            assertTrue(raidWatch.actions.any { it.title == "Copy team" })
            assertTrue(raidWatch.actions.any { it.title == "Dismiss" })
            if (Build.VERSION.SDK_INT >= 36) assertFalse(raidWatch.shortCriticalText.toString().contains("2387"))
            manager.notify(id, raidWatch)
            waitForNotification(id)
            captureShade("raid-watch")
        } finally {
            shell("cmd statusbar collapse")
            manager.cancel(id)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun raid() = PokemonAlert(
        name = "Legendary Raid", pokemon = "Mewtwo", type = listOf("Raid"),
        currentWeather = "Windy", isWeatherBoosted = true,
        hundoCP = HundoCP(2387, 2984), gym = "Central Gym",
        moves = PokemonMoves("Confusion", "Psystrike"),
        endTime = (System.currentTimeMillis() + 12 * 60_000L).toString()
    )

    private fun waitForNotification(id: Int, ready: (Notification) -> Boolean = { true }): Notification {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            manager.activeNotifications.firstOrNull { it.id == id && ready(it.notification) }?.let { return it.notification }
            SystemClock.sleep(50)
        }
        throw AssertionError("Notification $id was not posted")
    }

    private fun captureShade(name: String) {
        if (InstrumentationRegistry.getArguments().getString("notificationProof") != "true") return
        shell("cmd statusbar collapse")
        shell("input keyevent KEYCODE_HOME")
        SystemClock.sleep(1_000)
        captureNotificationProof(context, "$name-chip")
        if (Build.VERSION.SDK_INT >= 36 && name != "incoming-raid") {
            shell("input tap 330 80")
            SystemClock.sleep(800)
            captureNotificationProof(context, "$name-expanded")
            shell("input keyevent KEYCODE_HOME")
            SystemClock.sleep(600)
        }
        shell("input swipe 500 10 500 2200 600")
        SystemClock.sleep(800)
        captureNotificationProof(context, name)
        var parent = notificationRoots().flatMap { it.findAccessibilityNodeInfosByText("Mewtwo") }
            .firstOrNull { it.isVisibleToUser }
        for (attempt in 0..7) {
            val expand = parent?.findAccessibilityNodeInfosByViewId("android:id/expand_button")
                ?.firstOrNull { it.isVisibleToUser }
            if (expand != null) {
                expand.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                SystemClock.sleep(500)
                captureNotificationProof(context, "$name-expanded")
                break
            }
            parent = parent?.parent
        }
        shell("cmd statusbar collapse")
        SystemClock.sleep(300)
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun notificationRoots(): List<AccessibilityNodeInfo> =
        instrumentation.uiAutomation.windows.mapNotNull { it.root }
}

internal fun captureNotificationProof(context: Context, name: String) {
    if (InstrumentationRegistry.getArguments().getString("notificationProof") != "true") return
    val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ?: throw AssertionError("No screenshot")
    val directory = File(context.getExternalFilesDir(null), "notification-proof").apply { mkdirs() }
    File(directory, "$name-api${Build.VERSION.SDK_INT}.png").outputStream().use {
        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    screenshot.recycle()
}
