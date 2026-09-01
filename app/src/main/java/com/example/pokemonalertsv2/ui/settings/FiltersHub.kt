@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.ui.alerts.AREA_FILTER_OPTIONS
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.CategoryFilterGrid
import com.example.pokemonalertsv2.ui.alerts.FILTERABLE_ALERT_CATEGORIES
import com.example.pokemonalertsv2.ui.alerts.filterLabel
import com.example.pokemonalertsv2.ui.alerts.toCategorySelection
import com.example.pokemonalertsv2.util.TravelTime
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import com.example.pokemonalertsv2.widget.NearbyRadarWidgetProvider
import com.example.pokemonalertsv2.widget.WidgetConfigActivity
import com.example.pokemonalertsv2.widget.WidgetConfigurationStore

internal enum class FiltersHubTab(val label: String) {
    FEED("Feed"),
    MAP("Map"),
    WIDGETS("Widgets"),
    NOTIFICATIONS("Notify")
}

/**
 * One place that answers "what am I seeing, where?".
 *
 * Every surface keeps its own category selection — muting quests in the feed leaves them on
 * the map, in the widget and in notifications until muted there too. The shared tally of
 * live alerts sits under every grid so the effect of a mute is visible before tapping.
 */
@Composable
internal fun FiltersHubContent(
    viewModel: SettingsViewModel,
    onOpenFullNotificationSettings: () -> Unit,
    arrivalTrackingSection: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var tabName by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(FiltersHubTab.FEED.name)
    }
    val tab = FiltersHubTab.entries.firstOrNull { it.name == tabName } ?: FiltersHubTab.FEED

    // No verticalScroll here: the settings host already scrolls its destination content,
    // and a nested scroll container would be measured with infinite height and crash.
    Column(modifier = modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            FiltersHubTab.entries.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = tab == candidate,
                    onClick = { tabName = candidate.name },
                    shape = SegmentedButtonDefaults.itemShape(index, FiltersHubTab.entries.size)
                ) {
                    Text(candidate.label)
                }
            }
        }

        when (tab) {
            FiltersHubTab.FEED -> FeedFiltersSection(viewModel)
            FiltersHubTab.MAP -> MapFiltersSection(viewModel)
            FiltersHubTab.WIDGETS -> WidgetFiltersSection()
            FiltersHubTab.NOTIFICATIONS -> NotificationFiltersSection(
                viewModel = viewModel,
                onOpenFullSettings = onOpenFullNotificationSettings
            )
        }

        // Arrival tracking lives with the feed filters: it gates which alerts are worth the
        // walk, not what a notification may say.
        if (tab == FiltersHubTab.FEED) {
            arrivalTrackingSection()
        }
    }
}

@Composable
private fun FeedFiltersSection(viewModel: SettingsViewModel) {
    val mutedCategories by viewModel.feedFilterCategories.collectAsStateWithLifecycle()
    val counts by viewModel.filterCategoryCounts.collectAsStateWithLifecycle()
    val selectedArea by viewModel.selectedArea.collectAsStateWithLifecycle()
    val maxDistance by viewModel.maxDistance.collectAsStateWithLifecycle()
    val maxWalkingMinutes by viewModel.maxWalkingMinutes.collectAsStateWithLifecycle()

    FiltersHubSection(
        title = "Alert types",
        subtitle = "Everything is shown unless you hide it. These filters apply to the feed only."
    ) {
        CategoryFilterGrid(
            selection = mutedCategories,
            counts = counts,
            onToggle = { category, shownAfter ->
                viewModel.updateFeedFilterCategories(
                    if (shownAfter) mutedCategories - category else mutedCategories + category
                )
            }
        )
    }

    FiltersHubSection(title = "Location filters", subtitle = "Shared by the feed and notifications.") {
        Text(
            text = "Area",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AREA_FILTER_OPTIONS.forEach { area ->
                FilterChip(
                    selected = selectedArea == area,
                    onClick = { viewModel.updateSelectedArea(area) },
                    label = { Text(area) }
                )
            }
        }

        Text(
            text = "Maximum distance — ${if (maxDistance == 0) "Unlimited" else "$maxDistance km"}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )
        Slider(
            value = maxDistance.toFloat(),
            onValueChange = { viewModel.updateMaxDistance(kotlin.math.round(it).toInt()) },
            valueRange = 0f..50f
        )

        Text(
            text = "Reachable on foot — ${TravelTime.label(maxWalkingMinutes)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "Uses real walking routes. Alerts with no route available are always shown.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TravelTime.PRESET_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = maxWalkingMinutes == minutes,
                    onClick = { viewModel.updateMaxWalkingMinutes(minutes) },
                    label = { Text(TravelTime.label(minutes)) }
                )
            }
        }
    }
}

@Composable
private fun MapFiltersSection(viewModel: SettingsViewModel) {
    val mutedCategories by viewModel.mapFilterCategories.collectAsStateWithLifecycle()
    val counts by viewModel.filterCategoryCounts.collectAsStateWithLifecycle()
    val showDismissed by viewModel.mapShowDismissed.collectAsStateWithLifecycle()
    val showCountdowns by viewModel.showMapCountdowns.collectAsStateWithLifecycle()
    val showSpawnRadius by viewModel.showSpawnRadius.collectAsStateWithLifecycle()
    val spacialRend by viewModel.spacialRendEnabled.collectAsStateWithLifecycle()

    FiltersHubSection(
        title = "Alert types",
        subtitle = "The map keeps its own selection, independent from the feed."
    ) {
        CategoryFilterGrid(
            selection = mutedCategories,
            counts = counts,
            onToggle = { category, shownAfter ->
                viewModel.updateMapFilterCategories(
                    if (shownAfter) mutedCategories - category else mutedCategories + category
                )
            }
        )
    }

    FiltersHubSection(title = "Map layers") {
        SwitchSetting(
            title = "Show dismissed alerts",
            subtitle = "Bring dismissed alerts back onto the map. The feed has its own toggle.",
            checked = showDismissed,
            onCheckedChange = viewModel::updateMapShowDismissed
        )
        SwitchSetting(
            title = "Countdown labels",
            subtitle = "Show time remaining on markers. Labels switch to minute precision on busy maps to stay smooth.",
            checked = showCountdowns,
            onCheckedChange = viewModel::updateShowMapCountdowns
        )
        SwitchSetting(
            title = "Spawn radius",
            subtitle = "Draw the catch circle around spawns. Circles appear once you zoom in.",
            checked = showSpawnRadius,
            onCheckedChange = viewModel::updateShowSpawnRadius
        )
        SwitchSetting(
            title = "Spacial Rend",
            subtitle = "Draw the larger 80 m circles while Spacial Rend is active in game.",
            checked = spacialRend,
            enabled = showSpawnRadius,
            onCheckedChange = viewModel::updateSpacialRendEnabled
        )
    }
}

@Composable
private fun WidgetFiltersSection() {
    val context = LocalContext.current
    val placedWidgets = remember {
        val manager = AppWidgetManager.getInstance(context)
        buildList {
            manager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
                .forEach { add(PlacedWidget(id = it, isAlertsWidget = true)) }
            manager.getAppWidgetIds(ComponentName(context, NearbyRadarWidgetProvider::class.java))
                .forEach { add(PlacedWidget(id = it, isAlertsWidget = false)) }
        }
    }

    FiltersHubSection(
        title = "Your widgets",
        subtitle = "Every widget instance carries its own type filter, area, distance and sort."
    ) {
        if (placedWidgets.isEmpty()) {
            Text(
                text = "No widgets placed yet. Long-press your home screen, pick Widgets and add the Pokemon Alerts list or radar to configure it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                placedWidgets.forEach { widget ->
                    val configuration = remember(widget.id) {
                        WidgetConfigurationStore.get(context, widget.id)
                    }
                    val muted = configuration.selectedAlertTypes.toCategorySelection()
                    FiltersHubWidgetCard(
                        title = if (widget.isAlertsWidget) "Alerts widget" else "Nearby radar",
                        subtitle = widgetSummary(configuration, muted),
                        onCustomize = {
                            val intent = Intent(context, WidgetConfigActivity::class.java).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.id)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

private data class PlacedWidget(val id: Int, val isAlertsWidget: Boolean)

private fun widgetSummary(
    configuration: com.example.pokemonalertsv2.widget.WidgetConfiguration,
    muted: Set<AlertCategory>
): String = buildList {
    add(
        if (muted.isEmpty()) {
            "All types"
        } else {
            "Hiding " + FILTERABLE_ALERT_CATEGORIES
                .filter { it in muted }
                .joinToString(", ") { it.filterLabel.lowercase() }
        }
    )
    add(
        when (val area = configuration.area) {
            is com.example.pokemonalertsv2.widget.WidgetAreaMode.Fixed -> "Area: ${area.area}"
            else -> "Area: follows app"
        }
    )
    add(
        when (val distance = configuration.distance) {
            is com.example.pokemonalertsv2.widget.WidgetDistanceMode.Fixed -> "Within ${distance.kilometers} km"
            com.example.pokemonalertsv2.widget.WidgetDistanceMode.Unlimited -> "Any distance"
            com.example.pokemonalertsv2.widget.WidgetDistanceMode.InheritApp -> "App distance"
        }
    )
}.joinToString(" • ")

@Composable
private fun NotificationFiltersSection(
    viewModel: SettingsViewModel,
    onOpenFullSettings: () -> Unit
) {
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val raids by viewModel.raidsNotifications.collectAsStateWithLifecycle()
    val spawns by viewModel.spawnsNotifications.collectAsStateWithLifecycle()
    val quests by viewModel.questsNotifications.collectAsStateWithLifecycle()
    val hundos by viewModel.hundosNotifications.collectAsStateWithLifecycle()
    val nundos by viewModel.nundosNotifications.collectAsStateWithLifecycle()
    val pvp by viewModel.pvpNotifications.collectAsStateWithLifecycle()
    val kecleon by viewModel.kecleonNotifications.collectAsStateWithLifecycle()
    val rocket by viewModel.rocketNotifications.collectAsStateWithLifecycle()

    FiltersHubSection(
        title = "What may buzz you",
        subtitle = "Notifications have their own switches, separate from what the feed, map and widgets show. A long wave posts the most urgent alerts first and folds the rest into a summary."
    ) {
        SwitchSetting(
            title = "Notifications",
            subtitle = "Master switch for every alert notification.",
            checked = notificationsEnabled,
            onCheckedChange = viewModel::updateNotificationsEnabled
        )
        NotificationCategoryToggle("Raids", raids, viewModel::updateRaidsNotifications)
        NotificationCategoryToggle("Spawns", spawns, viewModel::updateSpawnsNotifications)
        NotificationCategoryToggle("Quests", quests, viewModel::updateQuestsNotifications)
        NotificationCategoryToggle("Hundos", hundos, viewModel::updateHundosNotifications)
        NotificationCategoryToggle("Nundos", nundos, viewModel::updateNundosNotifications)
        NotificationCategoryToggle("PvP", pvp, viewModel::updatePvpNotifications)
        NotificationCategoryToggle("Kecleon", kecleon, viewModel::updateKecleonNotifications)
        NotificationCategoryToggle("Rocket", rocket, viewModel::updateRocketNotifications)
        TextButton(onClick = onOpenFullSettings) {
            Text("Species filters, raid tiers, quiet hours and more")
        }
    }
}

@Composable
private fun NotificationCategoryToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchSetting(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun FiltersHubSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    SettingsSection(title = title) {
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        content()
    }
}

@Composable
private fun FiltersHubWidgetCard(
    title: String,
    subtitle: String,
    onCustomize: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCustomize) { Text("Customize") }
        }
    }
}
