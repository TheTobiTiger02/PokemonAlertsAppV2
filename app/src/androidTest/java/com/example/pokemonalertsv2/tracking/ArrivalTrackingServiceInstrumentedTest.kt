package com.example.pokemonalertsv2.tracking

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ArrivalTrackingServiceInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = ArrivalTrackingRepository.getInstance(context)
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
            context.stopService(Intent(context, ArrivalTrackingService::class.java))
            ArrivalTrackingService.locationSourceFactory = DefaultArrivalLocationSourceFactory
            context.getSystemService(NotificationManager::class.java)?.cancelAll()
        }
    }

    @Test
    fun twoInjectedPreciseFixesClearTripAndPostArrivalNotification() = runBlocking {
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

        fakeLocationSource.emit(location(latitude, longitude))
        SystemClock.sleep(2_100L)
        fakeLocationSource.emit(location(latitude, longitude))

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

    private fun location(latitude: Double, longitude: Double): Location =
        Location("injected").apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = 5f
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
            onAvailabilityChanged(true)
            started.countDown()
            return true
        }

        override fun stop() {
            onLocation = null
        }

        fun emit(location: Location) {
            onLocation?.invoke(location)
        }
    }
}
