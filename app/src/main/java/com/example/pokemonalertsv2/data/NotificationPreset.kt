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

enum class NotificationPreset(private val labelRes: Int) {
    EVERYTHING(R.string.data_everything),
    HIGH_VALUE(R.string.data_high_value_catches),
    QUIET_ESSENTIALS(R.string.data_quiet_essentials),
    CUSTOM(R.string.alerts_custom);

    /** Read on every access, so a language switch shows at once. */
    val label: String get() = AppText.get(labelRes)

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
