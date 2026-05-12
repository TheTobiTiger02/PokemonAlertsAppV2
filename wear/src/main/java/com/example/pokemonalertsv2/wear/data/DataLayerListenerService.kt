package com.example.pokemonalertsv2.wear.data

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class DataLayerListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                if (uri.path == "/alerts") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val jsonString = dataMap.getString("alerts_json")
                    if (jsonString != null) {
                        Log.d("DataLayerListener", "Received alerts: $jsonString")
                        
                        // Save to SharedPreferences so MainActivity can read it
                        val prefs = getSharedPreferences("wear_prefs", MODE_PRIVATE)
                        prefs.edit().putString("alerts_json", jsonString).apply()
                        
                        // Notify UI if open
                        val intent = Intent("com.example.pokemonalertsv2.wear.ALERTS_UPDATED")
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    }
                }
            }
        }
    }
}
