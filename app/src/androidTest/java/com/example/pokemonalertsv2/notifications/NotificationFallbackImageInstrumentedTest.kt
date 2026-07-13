package com.example.pokemonalertsv2.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationFallbackImageInstrumentedTest {

    @Test
    fun thumbnailLessAlertIsFirstPostedWithGeneratedMap() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = PokemonAlertsRepository.create(context).alertPreferences
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alert = PokemonAlert(
            id = 9_900_001,
            name = "Fallback map QA",
            description = "Thumbnail-less notification image verification",
            latitude = 49.8728,
            longitude = 8.6512,
            pokemonLocation = "Wilhelminenstraße Darmstadt"
        )
        val notificationId = alert.uniqueId.hashCode()

        val previousNotificationsEnabled = preferences.notificationsEnabled.first()
        val previousVibration = preferences.notificationVibrate.first()
        val previousSilenceUntil = preferences.silenceUntil.first()
        val previousArea = preferences.selectedArea.first()
        val previousMaxDistance = preferences.maxDistance.first()

        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            preferences.updateNotificationsEnabled(true)
            preferences.updateNotificationVibrate(false)
            preferences.updateSilenceUntil(0L)
            preferences.updateSelectedArea("All")
            preferences.updateMaxDistance(0)
            notificationManager.cancel(notificationId)

            AlertNotifier.notifyAlerts(context, listOf(alert))

            val posted = notificationManager.activeNotifications
                .firstOrNull { it.id == notificationId }
            assertNotNull("Expected the alert notification to be posted", posted)
            assertTrue(
                "Expected the first posted notification to contain its generated map",
                posted!!.notification.extras.containsKey(Notification.EXTRA_PICTURE)
            )
        } finally {
            notificationManager.cancel(notificationId)
            preferences.updateNotificationsEnabled(previousNotificationsEnabled)
            preferences.updateNotificationVibrate(previousVibration)
            preferences.updateSilenceUntil(previousSilenceUntil)
            preferences.updateSelectedArea(previousArea)
            preferences.updateMaxDistance(previousMaxDistance)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
