package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.util.AppText
import com.example.pokemonalertsv2.R
import androidx.compose.ui.res.stringResource
import com.example.pokemonalertsv2.ui.theme.Dimens
import com.example.pokemonalertsv2.ui.components.AdaptiveCardColumns
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
            PokemonAlertsV2Theme(darkTheme = com.example.pokemonalertsv2.ui.theme.rememberAppDarkTheme()) {
                // Null until the first read, so "no routes yet" does not flash before the list arrives.
                val loaded by store.history.collectAsStateWithLifecycle<List<CatchRouteSummary>?>(initialValue = null)
                val history = loaded.orEmpty()
                val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
                var open by remember { mutableStateOf<CatchRouteSummary?>(null) }
                var clearing by remember { mutableStateOf(false) }
                Scaffold(snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) }, topBar = {
                    TopAppBar(title = { Text(stringResource(R.string.catchroutes_route_history)) }, navigationIcon = {
                        IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.catchroutes_back)) }
                    }, actions = { if (history.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text(stringResource(R.string.catchroutes_clear)) } })
                }) { padding ->
                    LazyVerticalGrid(columns = AdaptiveCardColumns, modifier = Modifier.fillMaxSize().padding(padding).testTag("catch_history"),
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (loaded == null) Unit
                        else if (history.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.catchroutes_finished_routes_appear_here_with_what),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.widthIn(max = Dimens.ReadableContentWidth), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(history.size.toString(), "Routes", Modifier.weight(1f))
                                StatTile(history.sumOf { it.caught }.toString(), "Caught", Modifier.weight(1f))
                                StatTile(formatCatchKm(history.sumOf { it.walkedMeters }), "Walked", Modifier.weight(1f))
                            }
                        }
                        items(history, key = { it.id }) { summary ->
                            HistoryRow(summary, onDelete = {
                                lifecycleScope.launch {
                                    store.deleteHistory(summary.id)
                                    val result = snackbar.showSnackbar("Route deleted", actionLabel = AppText.get(R.string.mainscaffoldkt_undo),
                                        duration = androidx.compose.material3.SnackbarDuration.Short)
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) store.addHistory(summary)
                                }
                            }) { open = summary }
                        }
                    }
                }
                open?.let { summary ->
                    CatchRouteSummarySheet(summary, onDismiss = { open = null }, onWalkAgain = {
                        open = null
                        startActivity(walkAgainIntent(this, summary.id))
                    })
                }
                if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text(stringResource(R.string.catchroutes_clear_route_history)) },
                    confirmButton = { TextButton(onClick = { lifecycleScope.launch { store.clearHistory() }; clearing = false }) { Text(stringResource(R.string.catchroutes_clear)) } },
                    dismissButton = { TextButton(onClick = { clearing = false }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } })
            }
        }
    }

    @Composable private fun HistoryRow(summary: CatchRouteSummary, onDelete: () -> Unit, onClick: () -> Unit) {
        Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(summary.name, style = MaterialTheme.typography.titleMedium)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(summary.startedAtMillis)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${summary.headline()} · ${formatCatchSpeed(summary.averageKmh)}", style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("catch_history_delete_${summary.id}")) {
                    Icon(Icons.Filled.Delete, contentDescription = AppText.get(R.string.catchroutes_delete_x_2, summary.name))
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
