package com.example.pokemonalertsv2.data
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.util.AppText



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
    EVERYTHING(AppText.get(R.string.data_everything)),
    HIGH_VALUE(AppText.get(R.string.data_high_value_catches)),
    QUIET_ESSENTIALS(AppText.get(R.string.data_quiet_essentials)),
    CUSTOM(AppText.get(R.string.alerts_custom));

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
