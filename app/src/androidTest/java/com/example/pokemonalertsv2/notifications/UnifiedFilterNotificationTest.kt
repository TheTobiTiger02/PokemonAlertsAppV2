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
