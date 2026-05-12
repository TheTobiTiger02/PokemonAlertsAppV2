package com.example.pokemonalertsv2.wear

import android.content.Intent
import android.util.Log
import com.example.pokemonalertsv2.MainActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path == "/open_alert") {
            val pokemonName = String(messageEvent.data)
            Log.d("WearMessageListener", "Received request to open alert for $pokemonName")
            
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                // Optionally pass the pokemon name to scroll to it or open details
                putExtra("pokemon_name", pokemonName)
            }
            startActivity(intent)
        }
    }
}
