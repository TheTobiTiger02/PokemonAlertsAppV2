package com.example.pokemonalertsv2.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object FcmTopicSubscriber {
    const val ALERTS_TOPIC = "alerts"
    private const val TAG = "FcmTopicSubscriber"

    fun subscribe(context: Context) {
        runCatching {
            FirebaseApp.initializeApp(context.applicationContext)
            FirebaseMessaging.getInstance().subscribeToTopic(ALERTS_TOPIC)
                .addOnSuccessListener {
                    Log.d(TAG, "Subscribed to FCM topic $ALERTS_TOPIC")
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "Failed to subscribe to FCM topic $ALERTS_TOPIC", exception)
                }
        }.onFailure { exception ->
            Log.w(TAG, "Firebase is not configured; skipping FCM topic subscription", exception)
        }
    }
}
