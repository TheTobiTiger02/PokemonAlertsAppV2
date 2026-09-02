package com.example.pokemonalertsv2.widget

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
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
            val themeMode by repository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = 0)
            val showExactAlarmPermission by showExactAlarmDialog.collectAsStateWithLifecycle()
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())
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
                WidgetStudioConfiguration(
                    initialConfiguration = existing,
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

                if (showFilterEditor) com.example.pokemonalertsv2.ui.settings.FilterStudioDialog(
                    surface = com.example.pokemonalertsv2.data.FilterSurface.FEED,
                    viewModel = filterViewModel,
                    widgetId = appWidgetId,
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

    private fun needsExactAlarmAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactWidgetAlarms()

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
    onOpenEditor: () -> Unit,
    onConfirm: (WidgetConfiguration) -> Unit
) {
    var priority by remember { mutableStateOf(initialConfiguration.priority) }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Widget configuration") }) },
        bottomBar = {
            Button(onClick = { onConfirm(initialConfiguration.copy(priority = priority)) }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp)) { Text("Save widget") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("A view of your own", style = MaterialTheme.typography.headlineSmall)
            Text("This widget has independent alert rules. Link a reusable profile or keep a local copy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenEditor, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open Filter Studio") }
            Text("Display priority", style = MaterialTheme.typography.titleMedium)
            Text("Sorting only changes the order, never which alerts qualify.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetPriority.entries.forEach { option ->
                    FilterChip(selected = priority == option, onClick = { priority = option }, label = { Text(when (option) { WidgetPriority.APP_DEFAULT -> "App default"; WidgetPriority.NEAREST -> "Nearest"; WidgetPriority.ENDING_SOON -> "Ending soon"; WidgetPriority.NEWEST -> "Newest" }) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun WidgetConfigScreen(
    initialConfiguration: WidgetConfiguration,
    categoryCounts: Map<AlertCategory, Int> = emptyMap(),
    onConfirm: (WidgetConfiguration) -> Unit
) {
    // [shownCategories] is the source of truth; the stored configuration holds the muted
    // complement, matching the feed and map semantics.
    val muted = initialConfiguration.selectedAlertTypes.toCategorySelection()
    val shownCategories = remember(muted) {
        mutableStateMapOf<AlertCategory, Boolean>().apply {
            FILTERABLE_ALERT_CATEGORIES.forEach { category ->
                put(category, category !in muted)
            }
        }
    }
    val shownCount = shownCategories.count { it.value }
    val allShown = shownCount == FILTERABLE_ALERT_CATEGORIES.size
    var priority by remember { mutableStateOf(initialConfiguration.priority) }
    var distanceMode by remember { mutableStateOf(initialConfiguration.distance) }
    var fixedDistance by remember {
        mutableStateOf((initialConfiguration.distance as? WidgetDistanceMode.Fixed)?.kilometers ?: 10)
    }
    var areaMode by remember { mutableStateOf(initialConfiguration.area) }

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
                            value = allShown,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                FILTERABLE_ALERT_CATEGORIES.forEach { category ->
                                    shownCategories[category] = enabled
                                }
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
                        checked = allShown,
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
                    text = pluralStringResource(
                        R.plurals.widget_config_selected_count,
                        shownCount,
                        shownCount,
                        FILTERABLE_ALERT_CATEGORIES.size
                    ),
                    color = if (shownCount == 0) {
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryFilterGrid(
                    selection = FILTERABLE_ALERT_CATEGORIES
                        .filterNot { shownCategories[it] == true }
                        .toSet(),
                    counts = categoryCounts,
                    onToggle = { category, shownAfter ->
                        shownCategories[category] = shownAfter
                    }
                )

                Text(
                    text = "Area",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = areaMode is WidgetAreaMode.InheritApp,
                        onClick = { areaMode = WidgetAreaMode.InheritApp },
                        label = { Text("Follow app") }
                    )
                    WIDGET_AREA_OPTIONS.forEach { area ->
                        val selected = (areaMode as? WidgetAreaMode.Fixed)?.area == area
                        FilterChip(
                            selected = selected,
                            onClick = { areaMode = WidgetAreaMode.Fixed(area) },
                            label = { Text(area) }
                        )
                    }
                }

                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetPriority.values().forEach { option ->
                        FilterChip(
                            selected = priority == option,
                            onClick = { priority = option },
                            label = {
                                Text(
                                    when (option) {
                                        WidgetPriority.APP_DEFAULT -> "App default"
                                        WidgetPriority.NEAREST -> "Nearest"
                                        WidgetPriority.ENDING_SOON -> "Ending soon"
                                        WidgetPriority.NEWEST -> "Newest"
                                    }
                                )
                            }
                        )
                    }
                }
                Text(
                    text = "Distance",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Inherit", "Unlimited", "Fixed").forEach { label ->
                        val selected = when (label) {
                            "Inherit" -> distanceMode is WidgetDistanceMode.InheritApp
                            "Unlimited" -> distanceMode is WidgetDistanceMode.Unlimited
                            else -> distanceMode is WidgetDistanceMode.Fixed
                        }
                        FilterChip(
                            selected = selected,
                            onClick = {
                                distanceMode = when (label) {
                                    "Inherit" -> WidgetDistanceMode.InheritApp
                                    "Unlimited" -> WidgetDistanceMode.Unlimited
                                    else -> WidgetDistanceMode.Fixed(fixedDistance)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                if (distanceMode is WidgetDistanceMode.Fixed) {
                    Text("$fixedDistance km", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = fixedDistance.toFloat(),
                        onValueChange = {
                            fixedDistance = it.toInt().coerceIn(1, 50)
                            distanceMode = WidgetDistanceMode.Fixed(fixedDistance)
                        },
                        valueRange = 1f..50f,
                        steps = 48
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm button
            Button(
                onClick = {
                    val mutedNames = FILTERABLE_ALERT_CATEGORIES
                        .filter { shownCategories[it] != true }
                        .map { it.name }
                        .toSet()
                    onConfirm(
                        WidgetConfiguration(
                            selectedAlertTypes = mutedNames,
                            priority = priority,
                            distance = distanceMode,
                            area = areaMode
                        )
                    )
                },
                enabled = shownCount > 0,
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
