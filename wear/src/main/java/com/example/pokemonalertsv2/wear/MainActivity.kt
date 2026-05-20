package com.example.pokemonalertsv2.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.example.pokemonalertsv2.wear.data.PokemonAlertWearModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private val json = Json { ignoreUnknownKeys = true }
    
    private val _alerts = mutableStateOf<List<PokemonAlertWearModel>>(emptyList())

    private val alertsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            loadAlerts()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        loadAlerts()
        fetchAlertsFromPhone()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            alertsReceiver,
            IntentFilter("com.example.pokemonalertsv2.wear.ALERTS_UPDATED")
        )

        setContent {
            MaterialTheme {
                val listState = rememberScalingLazyListState()
                
                Scaffold(
                    positionIndicator = {
                        PositionIndicator(scalingLazyListState = listState)
                    },
                    vignette = {
                        Vignette(vignettePosition = VignettePosition.TopAndBottom)
                    }
                ) {
                    val alerts by remember { _alerts }

                    if (alerts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No active alerts", color = Color.Gray)
                        }
                    } else {
                        ScalingLazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp, start = 8.dp, end = 8.dp)
                        ) {
                            item {
                                Text(
                                    text = "Current Alerts",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(alerts) { alert ->
                                AlertItem(
                                    alert = alert,
                                    onOpen = { sendAlertAction("/open_alert", alert) },
                                    onNavigate = { sendAlertAction("/navigate_alert", alert) },
                                    onSnooze = { sendAlertAction("/snooze_alert", alert) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alertsReceiver)
    }

    private fun loadAlerts() {
        val prefs = getSharedPreferences("wear_prefs", MODE_PRIVATE)
        val jsonString = prefs.getString("alerts_json", null)
        
        if (jsonString != null) {
            try {
                val parsedAlerts = json.decodeFromString<List<PokemonAlertWearModel>>(jsonString)
                _alerts.value = parsedAlerts
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun fetchAlertsFromPhone() {
        // We can request the phone to send us data by sending a message, 
        // but for now relying on DataLayer cache is usually sufficient.
        val dataClient = Wearable.getDataClient(this)
        dataClient.dataItems.addOnSuccessListener { dataItemBuffer ->
            for (dataItem in dataItemBuffer) {
                if (dataItem.uri.path == "/alerts") {
                    val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(dataItem).dataMap
                    val jsonString = dataMap.getString("alerts_json")
                    if (jsonString != null) {
                        try {
                            val parsedAlerts = json.decodeFromString<List<PokemonAlertWearModel>>(jsonString)
                            _alerts.value = parsedAlerts
                            
                            val prefs = getSharedPreferences("wear_prefs", MODE_PRIVATE)
                            prefs.edit().putString("alerts_json", jsonString).apply()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            dataItemBuffer.release()
        }
    }
    
    private fun sendAlertAction(path: String, alert: PokemonAlertWearModel) {
        val messageClient = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val payload = alert.uniqueId.toByteArray()
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, payload)
            }
        }
    }
}

@Composable
fun AlertItem(
    alert: PokemonAlertWearModel,
    onOpen: () -> Unit,
    onNavigate: () -> Unit,
    onSnooze: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundPainter = androidx.wear.compose.material.CardDefaults.cardBackgroundPainter(
            startBackgroundColor = Color(0xFF1E1E2E),
            endBackgroundColor = Color(0xFF1E1E2E)
        )
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = alert.cleanPokemonName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                if (alert.iv != null || alert.cp != null) {
                    val stats = buildString {
                        if (alert.iv != null) append("${alert.iv}%")
                        if (alert.iv != null && alert.cp != null) append(" | ")
                        if (alert.cp != null) append("CP ${alert.cp}")
                    }
                    Text(
                        text = stats,
                        fontSize = 12.sp,
                        color = Color(0xFF89B4FA),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = alert.description.takeIf { it.isNotBlank() } ?: alert.area ?: "Unknown location",
                fontSize = 12.sp,
                color = Color.LightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Despawns: ${alert.endTime.substringAfter(" ").take(5)}",
                fontSize = 10.sp,
                color = Color(0xFFF38BA8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WearActionChip(label = "Open", modifier = Modifier.weight(1f), onClick = onOpen)
                WearActionChip(label = "Nav", modifier = Modifier.weight(1f), onClick = onNavigate)
                WearActionChip(label = "Snooze", modifier = Modifier.weight(1f), onClick = onSnooze)
            }
        }
    }
}

@Composable
private fun WearActionChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .background(Color(0xFF313244), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
