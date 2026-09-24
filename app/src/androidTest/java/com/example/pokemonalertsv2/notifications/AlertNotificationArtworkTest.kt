package com.example.pokemonalertsv2.notifications

import android.Manifest
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertNotificationArtworkTest {
    @Test
    fun dismissedNotificationIsNotRepostedAfterArtworkLoads() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS
        )
        AlertNotifier.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val notificationId = 718_091
        val builder = NotificationCompat.Builder(context, AlertNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setContentTitle("Test alert")
            .setContentText("5 min left")
            .setOnlyAlertOnce(true)
        val imageStarted = CompletableDeferred<Unit>()
        val finishImage = CompletableDeferred<Unit>()

        try {
            manager.notify(notificationId, builder.build())
            val enrichment = async {
                AlertNotifier.enrichActiveNotification(
                    context, manager, notificationId, builder, "Test alert", "5 min left"
                ) {
                    imageStarted.complete(Unit)
                    finishImage.await()
                    Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                }
            }
            imageStarted.await()
            manager.cancel(notificationId)
            finishImage.complete(Unit)
            enrichment.await()

            assertFalse(manager.activeNotifications.any { it.id == notificationId })
        } finally {
            manager.cancel(notificationId)
        }
    }
}
