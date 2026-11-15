package com.example.pokemonalertsv2.fcm

import android.util.Log
import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.work.DeviceSyncWorker
import com.example.pokemonalertsv2.work.FcmAlertWorker
import com.example.pokemonalertsv2.work.PushTopicSyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PokemonFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // A device sync ping is not an alert: it only says the group changed (DEVICE_SYNC.md).
        when (DeviceSync.pingAction(remoteMessage.data, DeviceSync.deviceId(applicationContext))) {
            DeviceSync.PingAction.SYNC -> {
                DeviceSyncWorker.trigger(applicationContext)
                return
            }
            DeviceSync.PingAction.OWN_WRITE -> return
            DeviceSync.PingAction.NOT_A_PING -> Unit
        }
        FcmAlertWorker.enqueue(
            context = applicationContext,
            messageId = remoteMessage.messageId,
            data = remoteMessage.data
        )
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        FcmAlertWorker.enqueueAuthoritativeSync(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM registration token changed; renewing topic subscriptions")
        // Topic subscriptions are per-token, so the new token is on none of them.
        FcmTopicSubscriptionManager.forgetAppliedTopics(applicationContext)
        PushTopicSyncWorker.triggerSync(applicationContext, delaySeconds = 0)
        DeviceSync.onNewToken(applicationContext, scope)
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    companion object {
        private const val TAG = "PokemonFcmService"
    }
}
