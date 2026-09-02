package com.example.pokemonalertsv2.widget

import android.content.Context
import com.example.pokemonalertsv2.ui.alerts.FILTERABLE_ALERT_CATEGORIES
import com.example.pokemonalertsv2.ui.alerts.legacyWidgetTokenToCategory
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterAssignment
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterStateCodec

internal enum class WidgetPriority {
    APP_DEFAULT,
    NEAREST,
    ENDING_SOON,
    NEWEST
}

internal sealed interface WidgetDistanceMode {
    data object InheritApp : WidgetDistanceMode
    data object Unlimited : WidgetDistanceMode
    data class Fixed(val kilometers: Int) : WidgetDistanceMode {
        init {
            require(kilometers in 1..50)
        }
    }
}

internal sealed interface WidgetAreaMode {
    data object InheritApp : WidgetAreaMode
    data class Fixed(val area: String) : WidgetAreaMode
}

internal enum class WidgetLoadState {
    LOADING,
    CONTENT,
    EMPTY,
    LOCATION_UNAVAILABLE,
    ERROR
}

/** @param selectedAlertTypes muted [com.example.pokemonalertsv2.ui.alerts.AlertCategory] names; empty = show all. */
internal data class WidgetConfiguration(
    val selectedAlertTypes: Set<String> = emptySet(),
    val priority: WidgetPriority = WidgetPriority.APP_DEFAULT,
    val distance: WidgetDistanceMode = WidgetDistanceMode.InheritApp,
    val area: WidgetAreaMode = WidgetAreaMode.InheritApp,
    val filterAssignment: FilterAssignment? = null
)

internal fun WidgetConfiguration.legacyFilterDefinition(appArea: String, appDistanceKm: Int): FilterDefinition {
    val effectiveArea = when (val mode = area) { WidgetAreaMode.InheritApp -> appArea; is WidgetAreaMode.Fixed -> mode.area }
    val effectiveDistance = when (val mode = distance) { WidgetDistanceMode.InheritApp -> appDistanceKm; WidgetDistanceMode.Unlimited -> 0; is WidgetDistanceMode.Fixed -> mode.kilometers }
    return FilterDefinition(
        alertTypes = if (selectedAlertTypes.isEmpty()) FilterSelection.All else FilterSelection.only(FilterAlertType.entries.filterNot { it.name in selectedAlertTypes }.map { it.name }),
        areas = if (effectiveArea == "All") FilterSelection.All else FilterSelection.only(listOf(effectiveArea)),
        maxDistanceKm = effectiveDistance
    )
}

internal object WidgetConfigurationStore {
    private const val PREFS_NAME = "widget_filter_prefs"
    private const val FILTER_PREFIX = "widget_filters_"
    private const val PRIORITY_PREFIX = "widget_priority_"
    private const val DISTANCE_PREFIX = "widget_distance_"
    private const val AREA_PREFIX = "widget_area_"
    private const val ASSIGNMENT_PREFIX = "widget_filter_assignment_"

    fun get(context: Context, appWidgetId: Int): WidgetConfiguration {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawFilters = prefs.getStringSet("$FILTER_PREFIX$appWidgetId", emptySet())?.toSet().orEmpty()
        val filters = migrateLegacyFilterTokens(rawFilters)
        val priority = prefs.getString("$PRIORITY_PREFIX$appWidgetId", null)
            ?.let { runCatching { WidgetPriority.valueOf(it) }.getOrNull() }
            ?: WidgetPriority.APP_DEFAULT
        val rawDistance = prefs.getString("$DISTANCE_PREFIX$appWidgetId", null)
        val distance = when {
            rawDistance == null || rawDistance == "INHERIT" -> WidgetDistanceMode.InheritApp
            rawDistance == "UNLIMITED" -> WidgetDistanceMode.Unlimited
            rawDistance.startsWith("FIXED:") -> rawDistance.substringAfter(':').toIntOrNull()
                ?.coerceIn(1, 50)
                ?.let(WidgetDistanceMode::Fixed)
                ?: WidgetDistanceMode.InheritApp
            else -> WidgetDistanceMode.InheritApp
        }
        val rawArea = prefs.getString("$AREA_PREFIX$appWidgetId", null)
        val area = when {
            rawArea == null || rawArea == "INHERIT" -> WidgetAreaMode.InheritApp
            rawArea.startsWith("FIXED:") -> WidgetAreaMode.Fixed(rawArea.substringAfter(':'))
            else -> WidgetAreaMode.InheritApp
        }
        val assignment = FilterStateCodec.decodeAssignment(
            prefs.getString("$ASSIGNMENT_PREFIX$appWidgetId", null)
        )
        return WidgetConfiguration(filters, priority, distance, area, assignment)
    }

    /**
     * Older builds stored display-label tokens ("Hundo", "PvP", …) as an ALLOW list — the
     * widget showed only those types. The new model stores muted categories, so the migration
     * maps the tokens onto categories and stores the complement: a widget that showed only
     * raids keeps showing only raids, now expressed as "everything except raids muted". An
     * empty stored set already meant "show all" and stays empty.
     *
     * Already-migrated sets are detected by case: enum names are upper-snake ("RAID"), legacy
     * tokens were display labels ("Raid"), so a round-trip through [save] never re-migrates.
     */
    private fun migrateLegacyFilterTokens(rawFilters: Set<String>): Set<String> {
        if (rawFilters.isEmpty()) return emptySet()
        val alreadyMigrated = rawFilters.all { token ->
            runCatching { com.example.pokemonalertsv2.ui.alerts.AlertCategory.valueOf(token) }
                .getOrNull() in FILTERABLE_ALERT_CATEGORIES
        }
        if (alreadyMigrated) return rawFilters
        val allowed = rawFilters.mapNotNull { token -> legacyWidgetTokenToCategory(token) }.toSet()
        return FILTERABLE_ALERT_CATEGORIES
            .filterNot { it in allowed }
            .map { it.name }
            .toSet()
    }

    fun save(context: Context, appWidgetId: Int, configuration: WidgetConfiguration) {
        val distance = when (val mode = configuration.distance) {
            WidgetDistanceMode.InheritApp -> "INHERIT"
            WidgetDistanceMode.Unlimited -> "UNLIMITED"
            is WidgetDistanceMode.Fixed -> "FIXED:${mode.kilometers.coerceIn(1, 50)}"
        }
        val area = when (val mode = configuration.area) {
            WidgetAreaMode.InheritApp -> "INHERIT"
            is WidgetAreaMode.Fixed -> "FIXED:${mode.area}"
        }
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("$FILTER_PREFIX$appWidgetId", configuration.selectedAlertTypes)
            .putString("$PRIORITY_PREFIX$appWidgetId", configuration.priority.name)
            .putString("$DISTANCE_PREFIX$appWidgetId", distance)
            .putString("$AREA_PREFIX$appWidgetId", area)
        configuration.filterAssignment?.let { assignment ->
            editor.putString(
                "$ASSIGNMENT_PREFIX$appWidgetId",
                FilterStateCodec.encodeAssignment(assignment)
            )
        }
        editor.apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("$FILTER_PREFIX$appWidgetId")
            .remove("$PRIORITY_PREFIX$appWidgetId")
            .remove("$DISTANCE_PREFIX$appWidgetId")
            .remove("$AREA_PREFIX$appWidgetId")
            .remove("$ASSIGNMENT_PREFIX$appWidgetId")
            .apply()
    }

}
