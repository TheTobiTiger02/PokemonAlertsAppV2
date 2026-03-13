package com.example.pokemonalertsv2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokemonalertsv2.R

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

    val DarkBg = Color(0xFF0F172A)
    val DarkSurface = Color(0xFF1E293B)
    val TextWhite = Color(0xFFF8FAFC)
    val TextGray = Color(0xFF94A3B8)
    val AccentBlue = Color(0xFF06B6D4)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Title
            Text(
                text = stringResource(R.string.widget_config_title),
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Choose which alert types appear on your widget",
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Scrollable type list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                ALL_FILTER_TYPES.forEach { (key, label) ->
                    val checked = enabledTypes[key] ?: true
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = TextWhite,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabledTypes[key] = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentBlue,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = DarkSurface
                                )
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
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(
                    text = stringResource(R.string.widget_config_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
