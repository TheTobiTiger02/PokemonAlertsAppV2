package com.example.pokemonalertsv2.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.ToolPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.ui.settings.SettingsOverviewGroup
import com.example.pokemonalertsv2.ui.settings.SettingsOverviewRow
import kotlinx.coroutines.launch

/** What a tool is, independent of how the tab draws it. Settings pages are not tools. */
internal data class ToolEntry(
    val id: String,
    val title: String,
    val summary: String,
    val group: String,
    val keywords: String = ""
)

internal val TOOL_ENTRIES = listOf(
    ToolEntry("hunt", "Hunt", "Follow matching live targets on the map", "Hunt & routes", "walking target battery saver"),
    ToolEntry("hunthistory", "Hunt history", "Past hunts, catches and distance walked", "Hunt & routes", "summary stats walked caught"),
    ToolEntry("routes", "Catch routes", "Plan a walk past the most spawnpoints", "Hunt & routes", "walk spawnpoints prediction"),
    ToolEntry("raids", "Raid counters", "Pick a boss and see the best counters", "Raids", "boss pokebattler estimator team"),
    ToolEntry("roster", "My Pokémon", "Browse your imported Poké Genie roster", "Raids", "csv import pokegenie collection"),
    ToolEntry("godex", "GoDex checklist", "Missing Pokémon and nearby matches", "Collection & research", "hundo collection sync"),
    ToolEntry("insights", "Spawn insights", "When and where a species turns up", "Collection & research", "history species research")
)

internal fun findTools(query: String): List<ToolEntry> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return TOOL_ENTRIES.filter { entry ->
        words.all { "${entry.title} ${entry.summary} ${entry.keywords}".contains(it, ignoreCase = true) }
    }
}

@Composable
private fun toolIcon(id: String): Painter = when (id) {
    "hunt" -> painterResource(R.drawable.ic_my_location)
    "routes" -> painterResource(R.drawable.ic_navigate)
    "raids" -> painterResource(R.drawable.ic_timer)
    "roster" -> rememberVectorPainter(Icons.Filled.Person)
    "godex" -> rememberVectorPainter(Icons.Filled.CheckCircle)
    "hunthistory" -> rememberVectorPainter(Icons.Filled.DateRange)
    else -> painterResource(R.drawable.ic_insights)
}

/**
 * The Tools tab: the things the app does beyond the alert feed, grouped by what they are for.
 * Starring a tool (up to four) puts it in a quick-access row at the top.
 */
@Composable
internal fun ToolsScreen(onOpen: (String) -> Unit, onOpenSettings: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val open: (String) -> Unit = { id -> focusManager.clearFocus(); onOpen(id) }
    val context = LocalContext.current
    val prefs = remember(context) { ToolPreferences(context.alertPreferencesDataStore) }
    val pins by prefs.pinnedTools.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { findTools(query) }
    val pinned = remember(pins) { pins.mapNotNull { id -> TOOL_ENTRIES.firstOrNull { it.id == id } } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("tools_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search tools") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().testTag("tools_search")
            )
        }
        if (query.isBlank() && pinned.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quick access",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pinned.forEach { tool ->
                            QuickAccessTile(tool, onClick = { open(tool.id) }, modifier = Modifier.weight(1f))
                        }
                        repeat(ToolPreferences.MAX_PINS - pinned.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            item {
                Text(
                    text = "No tool matches “${query.trim()}”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        matches.groupBy { it.group }.forEach { (group, entries) ->
            item(key = group) {
                SettingsOverviewGroup(title = group) {
                    entries.forEachIndexed { index, tool ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                        val isPinned = tool.id in pins
                        SettingsOverviewRow(
                            icon = toolIcon(tool.id),
                            title = tool.title,
                            summary = tool.summary,
                            trailing = {
                                IconButton(
                                    onClick = { scope.launch { prefs.togglePin(tool.id) } },
                                    enabled = isPinned || pins.size < ToolPreferences.MAX_PINS,
                                    modifier = Modifier.testTag("pin_${tool.id}")
                                ) {
                                    Icon(
                                        painter = if (isPinned) {
                                            rememberVectorPainter(Icons.Filled.Star)
                                        } else {
                                            painterResource(R.drawable.ic_star_border)
                                        },
                                        contentDescription = if (isPinned) {
                                            "Remove ${tool.title} from quick access"
                                        } else {
                                            "Add ${tool.title} to quick access"
                                        },
                                        tint = if (isPinned) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            },
                            onClick = { open(tool.id) }
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "Star up to four tools to keep them one tap away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun QuickAccessTile(tool: ToolEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .testTag("quick_${tool.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape
        ) {
            Icon(
                painter = toolIcon(tool.id),
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp)
            )
        }
        Text(
            text = tool.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
