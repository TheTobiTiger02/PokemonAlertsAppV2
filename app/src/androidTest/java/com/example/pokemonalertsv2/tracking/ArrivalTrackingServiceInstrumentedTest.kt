package com.example.pokemonalertsv2.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.raidwatch.RaidWatchNotifications
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ArrivalTrackingServiceInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = ArrivalTrackingRepository.getInstance(context)
    private val alertPreferences = AlertPreferences(context.alertPreferencesDataStore)
    private lateinit var fakeLocationSource: FakeArrivalLocationSource

    @Before
    fun setUp() = runBlocking {
        val packageName = context.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand(
            "pm grant $packageName ${Manifest.permission.ACCESS_FINE_LOCATION}"
        ).close()
        uiAutomation.executeShellCommand(
            "pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}"
        ).close()
        repository.stopTracking()
        alertPreferences.updateSpacialRendEnabled(false)
        RaidWatchController.stop(context)
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
        fakeLocationSource = FakeArrivalLocationSource()
        ArrivalTrackingService.locationSourceFactory = ArrivalLocationSourceFactory {
            fakeLocationSource
        }
    }

    @After
    fun cleanUp() {
        runBlocking {
            repository.stopTracking()
            alertPreferences.updateSpacialRendEnabled(false)
            RaidWatchController.stop(context)
            context.stopService(Intent(context, ArrivalTrackingService::class.java))
            ArrivalTrackingService.locationSourceFactory = DefaultArrivalLocationSourceFactory
            context.getSystemService(NotificationManager::class.java)?.cancelAll()
        }
    }

    @Test
    fun twoInjectedPhoneGradeFixesClearTripAndPostArrivalNotification() = runBlocking {
        val latitude = 49.86
        val longitude = 8.65
        repository.startTracking(
            PokemonAlert(
                name = "Injected Hundo",
                pokemon = "Pikachu",
                type = listOf("Hundo"),
                cp = 938,
                latitude = latitude,
                longitude = longitude
            )
        )

        ArrivalTrackingService.start(context)
        assertTrue(fakeLocationSource.started.await(5, TimeUnit.SECONDS))

        fakeLocationSource.emit(location(latitude, longitude, accuracyMeters = 150f))
        SystemClock.sleep(2_100L)
        fakeLocationSource.emit(location(latitude, longitude, accuracyMeters = 150f))

        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (repository.currentDestination() != null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50L)
        }
        assertNull(repository.currentDestination())

        val notifications = context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            .orEmpty()
        assertTrue(
            notifications.any { status ->
                status.id != ArrivalTrackingNotifications.ONGOING_NOTIFICATION_ID &&
                    status.notification.extras
                        .getCharSequence(android.app.Notification.EXTRA_TITLE)
                        ?.contains("in range") == true
            }
        )
    }

    @Test
    fun ongoingNotificationUsesAndroid16LiveProgressContract() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        assertEquals(
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(
                "android.permission.POST_PROMOTED_NOTIFICATIONS",
                context.packageName
            )
        )
        val notification = ArrivalTrackingNotifications.ongoing(
            context = context,
            destination = TrackedDestination(
                alert = PokemonAlert(
                    name = "Hundo Pikachu",
                    pokemon = "Pikachu",
                    type = listOf("Hundo"),
                    cp = 938,
                    latitude = 49.86,
                    longitude = 8.65,
                    endTime = (System.currentTimeMillis() + 60_000L).toString()
                ),
                radiusMeters = 40,
                startedAtMillis = System.currentTimeMillis()
            ),
            distanceMeters = 275f,
            walkingRoute = WalkingRouteInfo(distanceMeters = 420, durationSeconds = 300)
        )

        assertEquals("420 m", notification.shortCriticalText)
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.contains("min walk") == true
        )
        assertTrue(notification.hasPromotableCharacteristics())
        assertTrue(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue(
            notification.extras
                .getString(Notification.EXTRA_TEMPLATE)
                ?.contains("ProgressStyle") == true
        )
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.contains("CP 938") == true
        )
    }

    @Test
    fun transientLocationAvailabilityKeepsLastKnownRangeState() = runBlocking {
        repository.startTracking(
            PokemonAlert(
                name = "Hundo Pikachu",
                pokemon = "Pikachu",
                type = listOf("Hundo"),
                cp = 938,
                latitude = 49.86,
                longitude = 8.65
            )
        )

        ArrivalTrackingService.start(context)
        assertTrue(fakeLocationSource.started.await(5, TimeUnit.SECONDS))
        fakeLocationSource.emit(location(49.86, 8.65, accuracyMeters = 150f))
        SystemClock.sleep(100L)
        fakeLocationSource.emitAvailability(false)
        SystemClock.sleep(100L)

        val notification = context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            .orEmpty()
            .firstOrNull { it.id == ArrivalTrackingNotifications.ONGOING_NOTIFICATION_ID }
            ?.notification
        val text = notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(text.contains("In range"))
        assertTrue(!text.contains("Waiting for precise GPS"))
    }

    @Test
    fun spawnRadiusFollowsLiveSpacialRendPreference() = runBlocking {
        val destination = repository.startTracking(
            PokemonAlert(
                name = "Hundo Pikachu",
                pokemon = "Pikachu",
                type = listOf("Hundo"),
                latitude = 49.86,
                longitude = 8.65
            )
        )
        assertEquals(ArrivalTrackingRepository.SPAWN_RADIUS_METERS, destination.radiusMeters)

        alertPreferences.updateSpacialRendEnabled(true)
        assertEquals(
            ArrivalTrackingRepository.SPACIAL_REND_RADIUS_METERS,
            withTimeout(5_000L) {
                repository.destinationFlow.first {
                    it?.radiusMeters == ArrivalTrackingRepository.SPACIAL_REND_RADIUS_METERS
                }
            }?.radiusMeters
        )

        alertPreferences.updateSpacialRendEnabled(false)
        assertEquals(
            ArrivalTrackingRepository.SPAWN_RADIUS_METERS,
            withTimeout(5_000L) {
                repository.destinationFlow.first {
                    it?.radiusMeters == ArrivalTrackingRepository.SPAWN_RADIUS_METERS
                }
            }?.radiusMeters
        )
    }

    @Test
    fun raidUsesEightyMetersAndHandsJourneyOffToHundoLiveUpdate() = runBlocking {
        val latitude = 49.86
        val longitude = 8.65
        repository.updateArrivalRadius(35)
        val destination = repository.startTracking(
            PokemonAlert(
                name = "Legendary Raid",
                pokemon = "Mewtwo",
                type = listOf("Raid"),
                gym = "Central Gym",
                hundoCP = HundoCP(level20 = 2387, level25 = 2984),
                latitude = latitude,
                longitude = longitude,
                endTime = (System.currentTimeMillis() + 10 * 60_000L).toString()
            )
        )
        assertEquals(ArrivalTrackingRepository.POI_RADIUS_METERS, destination.radiusMeters)

        ArrivalTrackingService.start(context)
        assertTrue(fakeLocationSource.started.await(5, TimeUnit.SECONDS))

        // Roughly 111 m north: outside the 80 m boundary plus maximum GPS tolerance.
        fakeLocationSource.emit(location(latitude + 0.001, longitude))
        SystemClock.sleep(250L)
        assertTrue(repository.currentDestination() != null)

        // A raid hands off on the first trustworthy in-range fix so its hundo CPs replace
        // the generic "In range" journey chip immediately.
        fakeLocationSource.emit(location(latitude, longitude))

        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (repository.currentDestination() != null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50L)
        }
        assertNull(repository.currentDestination())

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        var raidNotification = notificationManager
            ?.activeNotifications
            .orEmpty()
            .firstOrNull { it.id == RaidWatchNotifications.NOTIFICATION_ID }
        val notificationDeadline = SystemClock.elapsedRealtime() + 5_000L
        while (raidNotification == null && SystemClock.elapsedRealtime() < notificationDeadline) {
            SystemClock.sleep(50L)
            raidNotification = notificationManager
                ?.activeNotifications
                .orEmpty()
                .firstOrNull { it.id == RaidWatchNotifications.NOTIFICATION_ID }
        }
        assertTrue(raidNotification != null)
        if (Build.VERSION.SDK_INT >= 36) {
            assertEquals("2387/2984", raidNotification?.notification?.shortCriticalText)
        }
        val title = raidNotification?.notification?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
        assertEquals("Mewtwo", title)
    }

    private fun location(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float = 5f
    ): Location =
        Location("injected").apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = accuracyMeters
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

    private class FakeArrivalLocationSource : ArrivalLocationSource {
        val started = CountDownLatch(1)
        private var onLocation: ((Location) -> Unit)? = null

        override fun start(
            onLocation: (Location) -> Unit,
            onAvailabilityChanged: (Boolean) -> Unit
        ): Boolean {
            this.onLocation = onLocation
            this.onAvailabilityChanged = onAvailabilityChanged
            onAvailabilityChanged(true)
            started.countDown()
            return true
        }

        override fun stop() {
            onLocation = null
            onAvailabilityChanged = null
        }

        fun emit(location: Location) {
            onLocation?.invoke(location)
        }

        fun emitAvailability(available: Boolean) {
            onAvailabilityChanged?.invoke(available)
        }

        private var onAvailabilityChanged: ((Boolean) -> Unit)? = null
    }
}
