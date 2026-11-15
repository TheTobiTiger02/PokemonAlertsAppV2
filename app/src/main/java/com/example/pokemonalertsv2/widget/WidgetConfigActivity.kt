package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.ui.components.readableWidth
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.ALERT_DISTANCE_STEPS_METERS
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.data.distanceStepIndex
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.CategoryFilterGrid
import com.example.pokemonalertsv2.ui.alerts.FILTERABLE_ALERT_CATEGORIES
import com.example.pokemonalertsv2.ui.alerts.countAlertsByCategory
import com.example.pokemonalertsv2.ui.alerts.toCategorySelection
import com.example.pokemonalertsv2.ui.alerts.toStoredNames
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Configuration activity shown when a widget is first placed.
 * Lets the user pick which alert categories this widget instance shows, along with its own
 * priority, distance and area overrides.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val repository by lazy { PokemonAlertsRepository.create(applicationContext) }
    private val filterViewModel: com.example.pokemonalertsv2.ui.settings.SettingsViewModel by viewModels()
    private val showExactAlarmDialog = MutableStateFlow(false)
    private val exactAlarmSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (canScheduleExactWidgetAlarms()) {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.exact_alarm_permission_granted),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            completeWidgetConfiguration()
        }

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
        val existing = WidgetConfigurationStore.get(this, appWidgetId)

        setContent {
            val showExactAlarmPermission by showExactAlarmDialog.collectAsStateWithLifecycle()
            val darkTheme = com.example.pokemonalertsv2.ui.theme.rememberAppDarkTheme()
            // Live counts next to each category switch, mirroring the feed and map sheets.
            val categoryCounts by remember {
                repository.alerts.map { alerts ->
                    val now = System.currentTimeMillis()
                    countAlertsByCategory(
                        alerts.filter { alert ->
                            !alert.isInvalidated && (alert.expiresAfter(now))
                        }
                    )
                }
            }.collectAsStateWithLifecycle(initialValue = emptyMap())
            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                var showFilterEditor by remember { mutableStateOf(false) }
                var followSet by remember { mutableStateOf(existing.followSet) }
                WidgetStudioConfiguration(
                    initialConfiguration = existing,
                    followSet = followSet,
                    onFollowChange = { follow ->
                        followSet = follow
                        filterViewModel.setWidgetFollowsSet(appWidgetId, follow)
                    },
                    onOpenEditor = { showFilterEditor = true },
                    onConfirm = { configuration ->
                        val latest = WidgetConfigurationStore.get(this@WidgetConfigActivity, appWidgetId)
                        WidgetConfigurationStore.save(this@WidgetConfigActivity, appWidgetId, latest.copy(priority = configuration.priority))
                        if (needsExactAlarmAccess()) {
                            showExactAlarmDialog.value = true
                        } else {
                            completeWidgetConfiguration()
                        }
                    }
                )

                // A widget that follows the set edits the set's widget filter; one with its
                // own filter edits that.
                if (showFilterEditor) com.example.pokemonalertsv2.ui.settings.FilterStudioDialog(
                    surface = if (followSet) com.example.pokemonalertsv2.data.FilterSurface.WIDGETS else com.example.pokemonalertsv2.data.FilterSurface.FEED,
                    viewModel = filterViewModel,
                    widgetId = appWidgetId.takeUnless { followSet },
                    onDismiss = { showFilterEditor = false }
                )

                if (showExactAlarmPermission) {
                    AlertDialog(
                        onDismissRequest = {
                            showExactAlarmDialog.value = false
                            completeWidgetConfiguration()
                        },
                        title = { Text(stringResource(R.string.exact_alarm_permission_title)) },
                        text = { Text(stringResource(R.string.exact_alarm_permission_message)) },
                        confirmButton = {
                            TextButton(onClick = ::openExactAlarmSettings) {
                                Text(stringResource(R.string.exact_alarm_permission_positive))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showExactAlarmDialog.value = false
                                    rememberExactAlarmDeclined()
                                    completeWidgetConfiguration()
                                }
                            ) {
                                Text(stringResource(R.string.exact_alarm_permission_negative))
                            }
                        }
                    )
                }
            }
        }
    }

    // "Maybe later" is remembered: adding a second widget asked the same question again.
    private fun needsExactAlarmAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactWidgetAlarms() &&
            !getSharedPreferences(PROMPT_PREFS, MODE_PRIVATE).getBoolean(KEY_EXACT_ALARM_DECLINED, false)

    private fun rememberExactAlarmDeclined() {
        getSharedPreferences(PROMPT_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_EXACT_ALARM_DECLINED, true).apply()
    }

    private fun canScheduleExactWidgetAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    private fun openExactAlarmSettings() {
        showExactAlarmDialog.value = false
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { exactAlarmSettingsLauncher.launch(intent) }
            .onFailure { completeWidgetConfiguration() }
    }

    private fun completeWidgetConfiguration() {
        AlertsWidgetProvider.requestUpdate(this)
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

private fun com.example.pokemonalertsv2.data.PokemonAlert.expiresAfter(nowMillis: Long): Boolean =
    (com.example.pokemonalertsv2.util.TimeUtils.parseEndTimeToMillis(endTime) ?: Long.MAX_VALUE) > nowMillis

/** Areas a widget can be pinned to, mirroring the app-level area filter options. */
private val WIDGET_AREA_OPTIONS = listOf("All", "Alsbach", "Darmstadt")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WidgetStudioConfiguration(
    initialConfiguration: WidgetConfiguration,
    followSet: Boolean,
    onFollowChange: (Boolean) -> Unit,
    onOpenEditor: () -> Unit,
    onConfirm: (WidgetConfiguration) -> Unit
) {
    var priority by remember { mutableStateOf(initialConfiguration.priority) }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.widget_widget_configuration)) }) },
        bottomBar = {
            Button(onClick = { onConfirm(initialConfiguration.copy(priority = priority)) }, modifier = Modifier.navigationBarsPadding().padding(16.dp).readableWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.widget_save_widget)) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).readableWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.widget_a_view_of_your_own), style = MaterialTheme.typography.headlineSmall)
            Row(
                Modifier.fillMaxWidth().toggleable(value = followSet, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onFollowChange).testTag("widget_follow_set"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_widgets_follow_switch), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.filter_widgets_follow_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = followSet, onCheckedChange = null)
            }
            Button(onClick = onOpenEditor, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(if (followSet) R.string.filter_widgets_edit_set else R.string.filter_widgets_edit_own))
            }
            Text(stringResource(R.string.widget_display_priority), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.widget_sorting_only_changes_the_order_never), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetPriority.entries.forEach { option ->
                    FilterChip(selected = priority == option, onClick = { priority = option }, label = { Text(when (option) { WidgetPriority.APP_DEFAULT -> "App default"; WidgetPriority.NEAREST -> "Nearest"; WidgetPriority.ENDING_SOON -> "Ending soon"; WidgetPriority.NEWEST -> stringResource(R.string.alerts_newest) }) })
                }
            }
        }
    }
}

/**
 * Reads and writes the raw per-widget filter set. Unlike older builds the set holds muted
 * category names (empty = show all); values are persisted as given, no migration applied.
 */
object WidgetFilterPrefs {
    fun saveFilters(context: Context, appWidgetId: Int, mutedTypes: Set<String>) {
        val existing = WidgetConfigurationStore.get(context, appWidgetId)
        WidgetConfigurationStore.save(
            context,
            appWidgetId,
            existing.copy(selectedAlertTypes = mutedTypes)
        )
    }

    fun getFilters(context: Context, appWidgetId: Int): Set<String> =
        WidgetConfigurationStore.get(context, appWidgetId).selectedAlertTypes

    fun removeFilters(context: Context, appWidgetId: Int) {
        WidgetConfigurationStore.remove(context, appWidgetId)
    }
}

private const val PROMPT_PREFS = "widget_config_prompts"
private const val KEY_EXACT_ALARM_DECLINED = "exact_alarm_declined"
