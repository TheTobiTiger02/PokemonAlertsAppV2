package com.example.pokemonalertsv2.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.PresentationPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.launch

internal data class ToolEntry(val id: String, val title: String, val description: String, val group: String, val keywords: String = "")
internal val TOOL_ENTRIES = listOf(
    ToolEntry("hunt", "Hunt", "Follow matching live targets", "Hunting", "walking target battery saver"),
    ToolEntry("routes", "Catch routes", "Plan a walk through spawn opportunities", "Hunting", "walk spawnpoints prediction"),
    ToolEntry("raids", "Raid counters", "Choose a boss and find your team", "Raids", "boss pokebattler estimator"),
    ToolEntry("roster", "My Pokémon", "Browse your imported Poké Genie roster", "Raids", "csv import pokegenie collection"),
    ToolEntry("godex", "GoDex checklist", "Find missing Pokémon and nearby matches", "Collection & research", "hundo collection sync"),
    ToolEntry("insights", "Spawn insights", "Explore patterns in alert history", "Collection & research", "history species research"),
    ToolEntry("filters", "Filters", "Choose alerts for each part of the app", "Settings", "species distance area walking profile"),
    ToolEntry("appearance", "Appearance & behavior", "Theme, feed layout and display preferences", "Settings", "dark light compact visual sorting"),
    ToolEntry("notifications", "Notifications", "Delivery, permissions and quiet hours", "Settings", "sound vibration silence location"),
    ToolEntry("permissions", "Permissions", "Android notifications, location and background access", "Settings", "gps permission denied blocked allow access"),
    ToolEntry("raid_defaults", "Counter defaults", "Weather, friendship and ranking options", "Settings", "level estimator party power"),
    ToolEntry("about", "Backup & updates", "Save settings and check for updates", "Settings", "restore export import version")
)

internal fun findTools(query: String) = TOOL_ENTRIES.filter { entry ->
    query.trim().split(Regex("\\s+")).all { word ->
        "${entry.title} ${entry.description} ${entry.keywords}".contains(word, ignoreCase = true)
    }
}

@Composable
internal fun ToolsScreen(onOpen: (String) -> Unit) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val openDestination: (String) -> Unit = { id -> focusManager.clearFocus(); onOpen(id) }
    val context = LocalContext.current
    val prefs = remember(context) { PresentationPreferences(context.alertPreferencesDataStore) }
    val pins by prefs.pinnedTools.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { findTools(query) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, { query = it }, label = { Text("Search tools and settings") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (query.isBlank() && pins.isNotEmpty()) {
            item { Text("Pinned shortcuts", style = MaterialTheme.typography.titleMedium) }
            items(pins, key = { "pin_$it" }) { id ->
                val entry = TOOL_ENTRIES.first { it.id == id }
                FilledTonalButton(onClick = { openDestination(id) }, modifier = Modifier.fillMaxWidth()) { Text(entry.title) }
            }
        }
        if (matches.isEmpty()) item { Text("No tools found. Try a feature name such as Hunt, filters or backup.") }
        matches.groupBy { it.group }.forEach { (group, entries) ->
            item(key = group) { Text(group, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { openDestination(entry.id) }.padding(16.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (entry.id in PresentationPreferences.TOOL_IDS) TextButton(
                            onClick = { scope.launch { prefs.togglePin(entry.id) } },
                            enabled = entry.id in pins || pins.size < 4
                        ) { Text(if (entry.id in pins) "Unpin" else "Pin") }
                    }
                }
            }
        }
        item { Text("Pin up to four tools for quick access.", style = MaterialTheme.typography.bodySmall) }
    }
}
