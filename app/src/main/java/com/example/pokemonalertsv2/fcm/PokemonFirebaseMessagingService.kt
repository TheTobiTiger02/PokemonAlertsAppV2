package com.example.pokemonalertsv2.fcm

import android.util.Log
import com.example.pokemonalertsv2.work.AlertWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PokemonFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        scope.launch {
            runCatching {
                FcmAlertHandler.handle(applicationContext, remoteMessage.data)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to handle FCM alert message", exception)
            }
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        AlertWorker.triggerImmediateSync(applicationContext)
    }

    companion object {
        private const val TAG = "PokemonFcmService"
    }
}
