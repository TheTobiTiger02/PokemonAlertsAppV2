package com.example.pokemonalertsv2.notifications

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The notification action buttons persist their effect, so a dismissed alert does not come back on the next sync. */
@RunWith(AndroidJUnit4::class)
class NotificationActionReceiverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = AlertPreferences(context.alertPreferencesDataStore)
    private val ids = listOf("receiver-test-1", "receiver-test-2", "receiver-test-3")

    @After
    fun clean() = runBlocking {
        ids.forEach { preferences.removeDismissedAlert(it) }
    }

    @Test
    fun dismissRecordsTheAlert() = runBlocking {
        send(Intent(NotificationActionReceiver.ACTION_DISMISS)
            .putExtra(NotificationActionReceiver.EXTRA_ALERT_UNIQUE_ID, ids[0])
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 4242))
        awaitDismissed(setOf(ids[0]))
    }

    @Test
    fun dismissGroupRecordsEveryAlertInIt() = runBlocking {
        send(Intent(NotificationActionReceiver.ACTION_DISMISS_GROUP)
            .putExtra(NotificationActionReceiver.EXTRA_ALERT_UNIQUE_IDS, arrayOf(ids[1], ids[2]))
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 4243))
        awaitDismissed(setOf(ids[1], ids[2]))
    }

    private fun send(intent: Intent) {
        context.sendBroadcast(intent.setClass(context, NotificationActionReceiver::class.java))
    }

    private suspend fun awaitDismissed(expected: Set<String>) {
        withTimeout(10_000) {
            preferences.dismissedAlertIds.first { it.containsAll(expected) }
        }
        assertTrue(preferences.dismissedAlertIds.first().containsAll(expected))
    }
}
