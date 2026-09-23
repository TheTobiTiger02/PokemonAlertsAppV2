package com.example.pokemonalertsv2.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.catchroutes.*
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.*
import kotlinx.coroutines.launch

/** A readout of existing sessions; this bar never creates a second session owner. */
@Composable
fun ActiveActivityBar(onOpenMap: () -> Unit, onOpenHunt: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hunt by remember(context) { HuntRepository.getInstance(context).activeHunt }.collectAsStateWithLifecycle()
    val destination by remember(context) { ArrivalTrackingRepository.getInstance(context).destinationFlow }.collectAsStateWithLifecycle(null)
    val route by remember(context) { CatchRouteStore.get(context).session }.collectAsStateWithLifecycle(null)
    val walkingRoute = route?.takeUnless { it.finished }
    val title = when {
        walkingRoute != null -> "Catch route in progress"
        hunt != null -> "Hunt: ${hunt!!.name}"
        destination != null -> "Going to ${destination!!.alert.name}"
        else -> return
    }
    var expanded by remember(title) { mutableStateOf(false) }
    val openActivity = {
        when {
            walkingRoute != null -> context.startActivity(Intent(context, CatchRoutesActivity::class.java))
            hunt != null -> onOpenHunt()
            destination != null -> context.startActivity(
                com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity.createIntent(context, destination!!.alert)
            )
            else -> onOpenMap()
        }
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = openActivity, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "Controls") }
            }
            androidx.compose.animation.AnimatedVisibility(expanded) {
                TextButton(onClick = { scope.launch {
                    if (walkingRoute != null) CatchRouteController.get(context).stop() else ArrivalTrackingService.stopEverything(context)
                } }) { Text("Stop activity") }
            }
        }
    }
}
