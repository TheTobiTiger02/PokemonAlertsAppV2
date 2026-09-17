package com.example.pokemonalertsv2.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class UnifiedFilterNotificationTest {
    @Test fun rareDistanceSettingsRemainIndependentWhileHunting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AlertPreferences(context.alertPreferencesDataStore)
        val hunts = com.example.pokemonalertsv2.hunt.HuntRepository.getInstance(context)
        org.junit.Assume.assumeTrue(hunts.currentSession() == null)
        val previous = preferences.filterStateDocument.first()
        val hunt = FilterDefinition(alertTypes = FilterSelection.only(listOf("Common")), commonSpecies = FilterSelection.only(listOf("Flamigo")))
        val notifications = hunt.copy(distanceOverrides = DistanceOverrides(perType = mapOf(FilterAlertType.COMMON.name to 300)))
        val alert = PokemonAlert(name = "Flamigo", pokemon = "Flamigo", type = listOf("Common"))
        try {
            preferences.updateFilterStateDocument { it.copy(notifications = FilterAssignment.local(notifications)) }
            val before = AlertNotifier.NotificationSettings.load(preferences)
                .copy(notificationsEnabled = true, silenceUntil = 0, quietHoursEnabled = false)
            hunts.start("Flamigo QA", hunt)
            val during = AlertNotifier.NotificationSettings.load(preferences)
                .copy(notificationsEnabled = true, silenceUntil = 0, quietHoursEnabled = false)
            assertEquals(before.filterDefinition, during.filterDefinition)
            for (settings in listOf(before, during)) {
                assertTrue(settings.shouldNotify(alert, matchContext = FilterMatchContext(effectiveDistanceMeters = 299f)))
                assertTrue(settings.shouldNotify(alert, matchContext = FilterMatchContext(effectiveDistanceMeters = 300f)))
                assertFalse(settings.shouldNotify(alert, matchContext = FilterMatchContext(effectiveDistanceMeters = 301f)))
                assertTrue(settings.shouldNotify(alert, matchContext = FilterMatchContext()))
            }
            assertEquals(previous.feed, preferences.filterStateDocument.first().feed)
            assertEquals(previous.map, preferences.filterStateDocument.first().map)
        } finally {
            hunts.stop()
            preferences.updateFilterStateDocument { previous }
        }
    }

    @Test fun postsOnlyAllowedFutureAlertsAndPreservesOtherSurfaces() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = AlertPreferences(context.alertPreferencesDataStore)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val previous = preferences.filterStateDocument.first()
        val enabled = preferences.notificationsEnabled.first()
        val quiet = preferences.quietHoursEnabled.first()
        val silence = preferences.silenceUntil.first()
        val future = Instant.now().plusSeconds(1800).toString()
        val allowed = PokemonAlert(id = 9_940_001, name = "Filter QA allowed Pikachu", pokemon = "Pikachu", type = listOf("Spawn"), endTime = future)
        val rejected = allowed.copy(id = 9_940_002, name = "Filter QA rejected Eevee", pokemon = "Eevee")
        val expired = allowed.copy(id = 9_940_003, name = "Filter QA expired", endTime = "2000-01-01T00:00:00Z")
        val invalidated = allowed.copy(id = 9_940_004, name = "Filter QA invalidated", invalidatedAt = Instant.now().toString())
        val alerts = listOf(allowed, rejected, expired, invalidated)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            preferences.updateNotificationsEnabled(true)
            preferences.updateQuietHoursEnabled(false)
            preferences.updateSilenceUntil(0)
            preferences.updateFilterStateDocument {
                it.copy(notifications = FilterAssignment.local(FilterDefinition(
                    alertTypes = FilterSelection.only(listOf("Spawn")),
                    spawnSpecies = FilterSelection.only(listOf("Pikachu"))
                )))
            }
            alerts.forEach { manager.cancel(AlertNotificationIds.forAlert(it.uniqueId)) }
            AlertNotifier.notifyAlerts(context, alerts)
            val posted = manager.activeNotifications.map { it.id }.toSet()
            assertTrue(posted.contains(AlertNotificationIds.forAlert(allowed.uniqueId)))
            listOf(rejected, expired, invalidated).forEach { assertFalse(posted.contains(AlertNotificationIds.forAlert(it.uniqueId))) }
            assertEquals(previous.feed, preferences.filterStateDocument.first().feed)
            assertEquals(previous.map, preferences.filterStateDocument.first().map)
        } finally {
            alerts.forEach { manager.cancel(AlertNotificationIds.forAlert(it.uniqueId)) }
            preferences.updateFilterStateDocument { previous }
            preferences.updateNotificationsEnabled(enabled)
            preferences.updateQuietHoursEnabled(quiet)
            preferences.updateSilenceUntil(silence)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
