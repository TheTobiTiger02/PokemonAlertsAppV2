package com.example.pokemonalertsv2.widget

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
            val themeMode by themeRepository.observeThemeMode().collectAsStateWithLifecycle(initialValue = 0)
            val dark = AppThemeMode.fromStored(themeMode).resolveDark(isSystemInDarkTheme())
            PokemonAlertsV2Theme(darkTheme = dark) {
                EventTypePicker(
                    loadTypes = { knownEventTypes() },
                    loadShown = { EventsWidgetSettings.shownTypes(applicationContext, appWidgetId) },
                    onCancel = { finish() },
                    onSave = { shown -> save(shown) }
                )
            }
        }
    }

    /** Types seen in the calendar plus the common ones, so a quiet week still offers them. */
    private suspend fun knownEventTypes(): List<String> {
        val seen = EventsRepository(applicationContext).cached().map { it.eventType }
        return (seen + COMMON_EVENT_TYPES).distinct().sortedBy(::eventTypeName)
    }

    private fun save(shown: Set<String>?) {
        lifecycleScope.launch {
            EventsWidgetSettings.setShownTypes(applicationContext, appWidgetId, shown)
            EventsWidgetProvider.update(applicationContext, appWidgetId)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private companion object {
        val COMMON_EVENT_TYPES = listOf(
            "community-day", "pokemon-spotlight-hour", "raid-hour", "raid-day", "raid-battles",
            "max-battles", "max-mondays", "go-battle-league", "event", "season"
        )
    }
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
        topBar = { CenterAlignedTopAppBar(title = { Text("Events widget") }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    onClick = { scope.launch { onSave(if (chosen.containsAll(types)) null else chosen) } },
                    enabled = selected != null && chosen.isNotEmpty()
                ) { Text("Save") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Show these event types",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { selected = types.toSet() }) { Text("All") }
                TextButton(onClick = { selected = emptySet() }) { Text("None") }
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
