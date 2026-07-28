package com.example.pokemonalertsv2.widget

import android.content.Context
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo

internal enum class RadarViewMode {
    FOCUS,
    OVERVIEW
}

internal data class RadarWidgetState(
    val selectedAlertId: String? = null,
    val viewMode: RadarViewMode = RadarViewMode.FOCUS
)

internal data class RadarSelection(
    val alerts: List<PokemonAlert>,
    val selectedAlert: PokemonAlert?,
    val selectedIndex: Int,
    val state: RadarWidgetState
)

internal object RadarWidgetStateStore {
    private const val PREFS_NAME = "widget_filter_prefs"
    private const val SELECTED_ALERT_PREFIX = "radar_selected_alert_"
    private const val VIEW_MODE_PREFIX = "radar_view_mode_"

    fun get(context: Context, appWidgetId: Int): RadarWidgetState {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val viewMode = preferences.getString("$VIEW_MODE_PREFIX$appWidgetId", null)
            ?.let { runCatching { RadarViewMode.valueOf(it) }.getOrNull() }
            ?: RadarViewMode.FOCUS
        return RadarWidgetState(
            selectedAlertId = preferences.getString("$SELECTED_ALERT_PREFIX$appWidgetId", null),
            viewMode = viewMode
        )
    }

    fun save(context: Context, appWidgetId: Int, state: RadarWidgetState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .apply {
                if (state.selectedAlertId == null) {
                    remove("$SELECTED_ALERT_PREFIX$appWidgetId")
                } else {
                    putString("$SELECTED_ALERT_PREFIX$appWidgetId", state.selectedAlertId)
                }
                putString("$VIEW_MODE_PREFIX$appWidgetId", state.viewMode.name)
            }
            .commit()
    }

    fun remove(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("$SELECTED_ALERT_PREFIX$appWidgetId")
            .remove("$VIEW_MODE_PREFIX$appWidgetId")
            .commit()
    }
}

internal fun orderRadarAlerts(
    alerts: List<PokemonAlert>,
    origin: WidgetAlertFilter.Origin?,
    walkingRoutes: Map<String, WalkingRouteInfo>,
    distanceMeters: (WidgetAlertFilter.Origin, PokemonAlert) -> Float? =
        WidgetAlertFilter::directDistanceMeters
): List<PokemonAlert> =
    if (origin == null) {
        alerts.sortedWith(
            compareBy<PokemonAlert> {
                TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
            }.thenBy { it.uniqueId }
        )
    } else {
        alerts.sortedWith(
            compareBy<PokemonAlert> { alert ->
                walkingRoutes[alert.uniqueId]?.distanceMeters?.toFloat()
                    ?: distanceMeters(origin, alert)
                        ?.takeUnless { it.isNaN() || it.isInfinite() || it < 0f }
                    ?: Float.MAX_VALUE
            }.thenBy {
                TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
            }.thenBy {
                it.uniqueId
            }
        )
    }

internal fun resolveRadarSelection(
    orderedAlerts: List<PokemonAlert>,
    requestedState: RadarWidgetState
): RadarSelection {
    val selectedIndex = orderedAlerts.indexOfFirst {
        it.uniqueId == requestedState.selectedAlertId
    }.takeIf { it >= 0 } ?: if (orderedAlerts.isEmpty()) -1 else 0
    val selectedAlert = orderedAlerts.getOrNull(selectedIndex)
    val resolvedState = requestedState.copy(selectedAlertId = selectedAlert?.uniqueId)
    return RadarSelection(
        alerts = orderedAlerts,
        selectedAlert = selectedAlert,
        selectedIndex = selectedIndex,
        state = resolvedState
    )
}

internal fun advanceRadarSelection(
    orderedAlerts: List<PokemonAlert>,
    currentState: RadarWidgetState
): RadarWidgetState {
    if (orderedAlerts.isEmpty()) return currentState.copy(selectedAlertId = null)
    val currentIndex = orderedAlerts.indexOfFirst {
        it.uniqueId == currentState.selectedAlertId
    }
    val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % orderedAlerts.size
    return currentState.copy(selectedAlertId = orderedAlerts[nextIndex].uniqueId)
}
