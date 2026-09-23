package com.example.pokemonalertsv2.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.*
import kotlinx.coroutines.launch

@Composable
fun FeedLayoutPicker(preview: Boolean = false) {
    val context = LocalContext.current
    val preferences = remember(context) { PresentationPreferences(context.alertPreferencesDataStore) }
    val layout by preferences.feedLayout.collectAsStateWithLifecycle(FeedLayout.VISUAL)
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedLayout.entries.forEach { choice ->
                FilterChip(selected = layout == choice, onClick = { scope.launch { preferences.setFeedLayout(choice) } }, label = { Text(choice.label) })
            }
        }
        if (preview) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (layout == FeedLayout.VISUAL) Surface(Modifier.fillMaxWidth().height(90.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Icon(androidx.compose.ui.res.painterResource(com.example.pokemonalertsv2.R.drawable.ic_widget_pokeball), null, Modifier.size(56.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (layout == FeedLayout.COMPACT) Icon(androidx.compose.ui.res.painterResource(com.example.pokemonalertsv2.R.drawable.ic_widget_pokeball), null, Modifier.size(40.dp))
                        Column {
                            Text("Pikachu · 12 min left", style = MaterialTheme.typography.titleMedium)
                            Text("250 m away · IV 15/15/15", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(if (layout == FeedLayout.COMPACT) "Smaller artwork, more alerts at a glance" else "Larger cards with more room for artwork", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
