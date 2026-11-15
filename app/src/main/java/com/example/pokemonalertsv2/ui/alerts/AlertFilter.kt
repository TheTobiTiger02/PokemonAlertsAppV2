package com.example.pokemonalertsv2.ui.alerts
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.util.AppText



enum class AlertFilter(private val labelRes: Int) {
    ALL(R.string.alerts_all),
    RAIDS(R.string.data_raids),
    QUESTS(R.string.data_quests),
    RARES(R.string.alerts_common),
    HUNDOS(R.string.data_hundos),
    PVP(R.string.alerts_pvp),
    NUNDOS(R.string.data_nundos),
    ROCKET(R.string.alerts_rocket),
    KECLEON(R.string.alerts_kecleon),
    WEATHER_CHANGE(R.string.alerts_weather);

    /** Read on every access, so a language switch shows at once. */
    val label: String get() = AppText.get(labelRes)
}
