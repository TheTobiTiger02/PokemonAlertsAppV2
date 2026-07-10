package com.example.pokemonalertsv2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    // State: which types are enabled
    val enabledTypes = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ALL_FILTER_TYPES.forEach { (key, _) ->
                // If initialFilters is empty, all are enabled by default
                put(key, initialFilters.isEmpty() || key in initialFilters)
            }
        }
    }

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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.widget_config_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ALL_FILTER_TYPES.forEach { (key, label) ->
                    val checked = enabledTypes[key] ?: true
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(start = 16.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabledTypes[key] = it }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
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
