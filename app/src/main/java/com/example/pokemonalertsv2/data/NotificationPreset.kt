package com.example.pokemonalertsv2.data

data class NotificationCategoryState(
    val raids: Boolean,
    val spawns: Boolean,
    val quests: Boolean,
    val hundos: Boolean,
    val pvp: Boolean,
    val nundos: Boolean,
    val kecleon: Boolean,
    val rocket: Boolean
)

enum class NotificationPreset(val label: String) {
    EVERYTHING("Everything"),
    HIGH_VALUE("High-value catches"),
    QUIET_ESSENTIALS("Quiet essentials"),
    CUSTOM("Custom");

    fun categories(): NotificationCategoryState? = when (this) {
        EVERYTHING -> NotificationCategoryState(true, true, true, true, true, true, true, true)
        HIGH_VALUE -> NotificationCategoryState(false, true, false, true, true, true, true, false)
        QUIET_ESSENTIALS -> NotificationCategoryState(false, false, false, true, false, true, true, false)
        CUSTOM -> null
    }

    companion object {
        fun detect(state: NotificationCategoryState): NotificationPreset =
            entries.firstOrNull { it != CUSTOM && it.categories() == state } ?: CUSTOM
    }
}
