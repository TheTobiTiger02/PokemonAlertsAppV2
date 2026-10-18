package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.hunt.StatTile
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Every guided route that ended with something to show: totals, and each one's summary and map a tap away. */
class CatchRouteHistoryActivity : ComponentActivity() {
    private val store by lazy { CatchRouteStore.get(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by remember { AlertPreferences(alertPreferencesDataStore).themeMode }.collectAsStateWithLifecycle(initialValue = 0)
            PokemonAlertsV2Theme(darkTheme = AppThemeMode.fromStored(themeMode).resolveDark(isSystemInDarkTheme())) {
                val history by store.history.collectAsStateWithLifecycle(initialValue = emptyList())
                var open by remember { mutableStateOf<CatchRouteSummary?>(null) }
                var clearing by remember { mutableStateOf(false) }
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Route history") }, navigationIcon = {
                        IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }, actions = { if (history.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text("Clear") } })
                }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("catch_history"), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (history.isEmpty()) item {
                            Text("Finished routes appear here, with what you caught and where you walked.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(history.size.toString(), "Routes", Modifier.weight(1f))
                                StatTile(history.sumOf { it.caught }.toString(), "Caught", Modifier.weight(1f))
                                StatTile(formatCatchKm(history.sumOf { it.walkedMeters }), "Walked", Modifier.weight(1f))
                            }
                        }
                        items(history, key = { it.id }) { summary -> HistoryRow(summary) { open = summary } }
                    }
                }
                open?.let { summary ->
                    CatchRouteSummarySheet(summary, onDismiss = { open = null }, onWalkAgain = {
                        open = null
                        startActivity(walkAgainIntent(this, summary.id))
                    })
                }
                if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("Clear route history?") },
                    confirmButton = { TextButton(onClick = { lifecycleScope.launch { store.clearHistory() }; clearing = false }) { Text("Clear") } },
                    dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } })
            }
        }
    }

    @Composable private fun HistoryRow(summary: CatchRouteSummary, onClick: () -> Unit) {
        Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(summary.name, style = MaterialTheme.typography.titleMedium)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(summary.startedAtMillis)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${summary.headline()} · ${formatCatchSpeed(summary.averageKmh)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    companion object {
        /** Opens the planner with a past route's settings, ready to plan it again from now. */
        internal const val EXTRA_WALK_AGAIN_ID = "catch_route_walk_again_id"
        internal fun walkAgainIntent(context: Context, summaryId: String): Intent =
            Intent(context, CatchRoutesActivity::class.java).putExtra(EXTRA_WALK_AGAIN_ID, summaryId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
