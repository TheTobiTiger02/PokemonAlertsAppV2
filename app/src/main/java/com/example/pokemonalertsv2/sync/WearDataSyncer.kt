package com.example.pokemonalertsv2.sync

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.data.PokemonAlert
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WearDataSyncer(private val context: Context) {

    private val dataClient by lazy { Wearable.getDataClient(context) }
    
    // We only need to serialize a subset of data to avoid exceeding the data item size limit (100KB)
    fun syncAlerts(alerts: List<PokemonAlert>) {
        // Only send the top 20 alerts to keep payload small
        val topAlerts = alerts.take(20)
        
        try {
            val jsonString = Json.encodeToString(topAlerts)
            
            val putDataMapReq = PutDataMapRequest.create("/alerts").apply {
                dataMap.putString("alerts_json", jsonString)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            
            val putDataReq = putDataMapReq.asPutDataRequest()
            putDataReq.setUrgent()
            
            dataClient.putDataItem(putDataReq)
                .addOnSuccessListener {
                    Log.d("WearDataSyncer", "Successfully synced ${topAlerts.size} alerts to Wear OS")
                }
                .addOnFailureListener { e ->
                    Log.e("WearDataSyncer", "Failed to sync alerts to Wear OS", e)
                }
        } catch (e: Exception) {
            Log.e("WearDataSyncer", "Error serializing alerts for Wear OS", e)
        }
    }
}
