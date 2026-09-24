package com.example.pokemonalertsv2.navigation

/** Stable destination IDs. Numeric tab values are read only for old PendingIntents. */
internal enum class AppDestination(val id: String, val legacyTabIndex: Int) {
    ALERTS("alerts", 0),
    MAP("map", 2),
    EVENTS("events", 3),
    SETTINGS("settings", 4);

    companion object {
        fun fromId(id: String?): AppDestination? = entries.firstOrNull { it.id == id }
    }
}

internal enum class AlertsView { ACTIVE, HISTORY }

internal data class AppNavigationRequest(
    val destination: AppDestination,
    val alertsView: AlertsView = AlertsView.ACTIVE
)

internal fun legacyNavigationRequestOrNull(index: Int): AppNavigationRequest? = when (index) {
    0 -> AppNavigationRequest(AppDestination.ALERTS)
    1 -> AppNavigationRequest(AppDestination.ALERTS, AlertsView.HISTORY)
    2 -> AppNavigationRequest(AppDestination.MAP)
    3 -> AppNavigationRequest(AppDestination.EVENTS)
    4 -> AppNavigationRequest(AppDestination.SETTINGS)
    else -> null
}
