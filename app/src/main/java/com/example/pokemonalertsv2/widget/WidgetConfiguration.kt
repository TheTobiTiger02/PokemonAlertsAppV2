package com.example.pokemonalertsv2.widget

import android.content.Context

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

internal enum class WidgetLoadState {
    LOADING,
    CONTENT,
    EMPTY,
    LOCATION_UNAVAILABLE,
    ERROR
}

internal data class WidgetConfiguration(
    val selectedAlertTypes: Set<String> = emptySet(),
    val priority: WidgetPriority = WidgetPriority.APP_DEFAULT,
    val distance: WidgetDistanceMode = WidgetDistanceMode.InheritApp
)

internal object WidgetConfigurationStore {
    private const val PREFS_NAME = "widget_filter_prefs"
    private const val FILTER_PREFIX = "widget_filters_"
    private const val PRIORITY_PREFIX = "widget_priority_"
    private const val DISTANCE_PREFIX = "widget_distance_"

    fun get(context: Context, appWidgetId: Int): WidgetConfiguration {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val filters = prefs.getStringSet("$FILTER_PREFIX$appWidgetId", emptySet())?.toSet().orEmpty()
        val migratedFilters = if (filters.containsAll(LEGACY_ALL_FILTER_TYPES)) {
            filters + NEW_DEFAULT_FILTER_TYPES
        } else {
            filters
        }
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
        return WidgetConfiguration(migratedFilters, priority, distance)
    }

    fun save(context: Context, appWidgetId: Int, configuration: WidgetConfiguration) {
        val distance = when (val mode = configuration.distance) {
            WidgetDistanceMode.InheritApp -> "INHERIT"
            WidgetDistanceMode.Unlimited -> "UNLIMITED"
            is WidgetDistanceMode.Fixed -> "FIXED:${mode.kilometers.coerceIn(1, 50)}"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("$FILTER_PREFIX$appWidgetId", configuration.selectedAlertTypes)
            .putString("$PRIORITY_PREFIX$appWidgetId", configuration.priority.name)
            .putString("$DISTANCE_PREFIX$appWidgetId", distance)
            .apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("$FILTER_PREFIX$appWidgetId")
            .remove("$PRIORITY_PREFIX$appWidgetId")
            .remove("$DISTANCE_PREFIX$appWidgetId")
            .apply()
    }

    private val LEGACY_ALL_FILTER_TYPES = setOf(
        "Hundo", "Nundo", "PvP", "Spawn", "Raid", "Rocket", "Quest", "Kecleon"
    )
    private val NEW_DEFAULT_FILTER_TYPES = setOf("Rare", "Weather")
}
