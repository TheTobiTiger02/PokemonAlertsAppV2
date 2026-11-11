package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.R
import androidx.compose.ui.res.stringResource
import com.example.pokemonalertsv2.ui.components.readableWidth
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.events.EventsRepository
import com.example.pokemonalertsv2.events.eventTypeName
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.launch

/**
 * Picks which event types one events widget lists. Shown when the widget is placed and from the
 * home screen's reconfigure action; placing it without choosing keeps every type.
 */
class EventsWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Backing out of a first placement must not place the widget.
        setResult(RESULT_CANCELED, resultIntent())
        val themeRepository = PokemonAlertsRepository.create(applicationContext)

        setContent {
            val dark = com.example.pokemonalertsv2.ui.theme.rememberAppDarkTheme()
            PokemonAlertsV2Theme(darkTheme = dark) {
                EventTypePicker(
                    loadTypes = { knownTypes() },
                    loadShown = {
                        val types = knownTypes()
                        types.toSet() - EventsWidgetSettings.hiddenTypes(applicationContext, appWidgetId) { types }
                    },
                    onCancel = { finish() },
                    onSave = { shown -> save(shown) }
                )
            }
        }
    }

    private suspend fun knownTypes(): List<String> = knownEventTypes(EventsRepository(applicationContext).cached())

    private fun save(shown: Set<String>?) {
        lifecycleScope.launch {
            // Store what was unticked, so types that appear later are shown rather than hidden.
            val hidden = if (shown == null) emptySet() else knownTypes().toSet() - shown
            EventsWidgetSettings.setHiddenTypes(applicationContext, appWidgetId, hidden)
            EventsWidgetProvider.update(applicationContext, appWidgetId)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTypePicker(
    loadTypes: suspend () -> List<String>,
    loadShown: suspend () -> Set<String>?,
    onCancel: () -> Unit,
    onSave: (Set<String>?) -> Unit
) {
    var types by remember { mutableStateOf<List<String>>(emptyList()) }
    // Null until loaded; an absent setting means every type is shown.
    var selected by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(Unit) {
        val all = loadTypes()
        types = all
        selected = loadShown() ?: all.toSet()
    }
    val chosen = selected.orEmpty()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.widget_events_widget)) }) },
        bottomBar = {
            Row(
                Modifier.readableWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.mainscaffoldkt_cancel)) }
                Button(
                    onClick = { scope.launch { onSave(if (chosen.containsAll(types)) null else chosen) } },
                    enabled = selected != null && chosen.isNotEmpty()
                ) { Text(stringResource(R.string.catchroutes_save)) }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).readableWidth()) {
            Text(
                stringResource(R.string.widget_show_these_event_types),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { selected = types.toSet() }) { Text(stringResource(R.string.alerts_all)) }
                TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.alerts_none)) }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(types, key = { it }) { type ->
                    val checked = type in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .toggleable(value = checked, role = Role.Checkbox) {
                                selected = if (checked) chosen - type else chosen + type
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(eventTypeName(type), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}
