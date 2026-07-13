package com.example.pokemonalertsv2.ui.theme

enum class AppThemeMode(val storedValue: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: Int): AppThemeMode = entries.firstOrNull {
            it.storedValue == value
        } ?: SYSTEM
    }
}
