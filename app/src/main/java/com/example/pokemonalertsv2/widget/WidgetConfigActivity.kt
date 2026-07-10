package com.example.pokemonalertsv2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme

/**
 * Configuration activity shown when a widget is first placed.
 * Lets the user pick which alert types to display in this widget instance.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result canceled so if the user backs out, the widget won't be placed
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Load existing prefs for this widget (if reconfiguring)
        val existing = WidgetFilterPrefs.getFilters(this, appWidgetId)

        setContent {
            PokemonAlertsV2Theme {
                WidgetConfigScreen(
                    initialFilters = existing,
                    onConfirm = { filters ->
                        WidgetFilterPrefs.saveFilters(this@WidgetConfigActivity, appWidgetId, filters)
                        // Trigger widget update
                        AlertsWidgetProvider.requestUpdate(this@WidgetConfigActivity)
                        // Return success
                        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(RESULT_OK, resultValue)
                        finish()
                    }
                )
            }
        }
    }
}

/**
 * All supported alert type filter options.
 */
data class WidgetTypeFilter(
    val key: String,
    val label: String,
    val enabled: Boolean = true
)

val ALL_FILTER_TYPES = listOf(
    "Hundo" to "Hundos (100% IV)",
    "Nundo" to "Nundos (0% IV)",
    "PvP" to "PvP Ranked",
    "Spawn" to "Wild Spawns",
    "Raid" to "Raids",
    "Rocket" to "Team Rocket",
    "Quest" to "Quests",
    "Kecleon" to "Kecleon"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    initialFilters: Set<String>,
    onConfirm: (Set<String>) -> Unit
) {
    val enabledTypes = remember(initialFilters) {
        mutableStateMapOf<String, Boolean>().apply {
            ALL_FILTER_TYPES.forEach { (key, _) ->
                put(key, initialFilters.isEmpty() || key in initialFilters)
            }
        }
    }
    val selectedCount = enabledTypes.count { it.value }
    val allSelected = selectedCount == ALL_FILTER_TYPES.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.widget_config_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.widget_config_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .toggleable(
                            value = allSelected,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                ALL_FILTER_TYPES.forEach { (key, _) -> enabledTypes[key] = enabled }
                            }
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_config_show_all),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.widget_config_show_all_subtitle),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = allSelected,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.widget_config_types_heading),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.widget_config_selected_count,
                        selectedCount,
                        ALL_FILTER_TYPES.size
                    ),
                    color = if (selectedCount == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ALL_FILTER_TYPES.forEach { (key, _) ->
                    val checked = enabledTypes[key] ?: true
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .toggleable(
                                    value = checked,
                                    role = Role.Switch,
                                    onValueChange = { enabledTypes[key] = it }
                                )
                                .semantics(mergeDescendants = true) {}
                                .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(widgetFilterLabelResource(key)),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm button
            Button(
                onClick = {
                    val selected = enabledTypes.filter { it.value }.keys
                    onConfirm(selected)
                },
                enabled = selectedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = stringResource(R.string.widget_config_confirm),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun widgetFilterLabelResource(key: String): Int = when (key) {
    "Hundo" -> R.string.widget_filter_hundo
    "Nundo" -> R.string.widget_filter_nundo
    "PvP" -> R.string.widget_filter_pvp
    "Spawn" -> R.string.widget_filter_spawn
    "Raid" -> R.string.widget_filter_raid
    "Rocket" -> R.string.widget_filter_rocket
    "Quest" -> R.string.widget_filter_quest
    "Kecleon" -> R.string.widget_filter_kecleon
    else -> R.string.widget_filter_other
}

/**
 * Utility to read/write per-widget type filter preferences.
 */
object WidgetFilterPrefs {
    private const val PREFS_NAME = "widget_filter_prefs"
    private const val KEY_PREFIX = "widget_filters_"

    fun saveFilters(context: Context, appWidgetId: Int, enabledTypes: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("$KEY_PREFIX$appWidgetId", enabledTypes)
            .apply()
    }

    fun getFilters(context: Context, appWidgetId: Int): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet("$KEY_PREFIX$appWidgetId", emptySet()) ?: emptySet()
    }

    fun removeFilters(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PREFIX$appWidgetId")
            .apply()
    }
}
