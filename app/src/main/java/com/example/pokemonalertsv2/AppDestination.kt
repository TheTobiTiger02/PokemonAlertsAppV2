package com.example.pokemonalertsv2

/** Stable UI identity. Legacy intent integers remain a separate compatibility contract. */
internal enum class AppDestination(val title: String) {
    ALERTS("Alerts"), HISTORY("History"), MAP("Map"), EVENTS("Events"), TOOLS("Tools"), SETTINGS("Settings");
    val root: AppDestination get() = if (this == HISTORY) ALERTS else this
    companion object {
        fun fromLegacy(index: Int): AppDestination? = when (index) {
            0 -> ALERTS; 1 -> HISTORY; 2 -> MAP; 3 -> EVENTS; 4 -> SETTINGS; else -> null
        }
    }
}
