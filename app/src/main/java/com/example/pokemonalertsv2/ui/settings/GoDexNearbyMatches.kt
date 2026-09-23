package com.example.pokemonalertsv2.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.ui.alerts.*

@Composable
internal fun GoDexNearbyMatches(alerts: List<PokemonAlert>) {
    val context = LocalContext.current
    val results = rememberGoDexMatchResults(alerts)
    val matching = remember(alerts, results) {
        alerts.filter { results[it.uniqueId]?.status in setOf(GoDexMatchStatus.NEEDED,
            GoDexMatchStatus.EVOLUTION_NEEDED, GoDexMatchStatus.FORM_CHANGE_NEEDED,
            GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED) }
    }
    var expanded by remember { mutableStateOf(false) }
    Text("Matching live alerts (${matching.size})", style = MaterialTheme.typography.titleMedium)
    if (matching.isEmpty()) Text("No known matches right now. Unknown forms are never treated as collected.", style = MaterialTheme.typography.bodySmall)
    (if (expanded) matching else matching.take(3)).forEach { alert ->
        OutlinedCard(onClick = { context.startActivity(AlertDetailActivity.createIntent(context, alert)) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(alert.name, style = MaterialTheme.typography.titleSmall)
                results[alert.uniqueId]?.let { GoDexStatusPill(it) }
                alert.locationDisplay?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (matching.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show fewer" else "Show all matches") }
}
