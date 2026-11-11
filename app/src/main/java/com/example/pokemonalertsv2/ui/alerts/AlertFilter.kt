package com.example.pokemonalertsv2.ui.alerts
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.util.AppText



enum class AlertFilter(val label: String) {
    ALL(AppText.get(R.string.alerts_all)),
    RAIDS(AppText.get(R.string.data_raids)),
    QUESTS(AppText.get(R.string.data_quests)),
    RARES(AppText.get(R.string.alerts_common)),
    HUNDOS(AppText.get(R.string.data_hundos)),
    PVP(AppText.get(R.string.alerts_pvp)),
    NUNDOS(AppText.get(R.string.data_nundos)),
    ROCKET(AppText.get(R.string.alerts_rocket)),
    KECLEON(AppText.get(R.string.alerts_kecleon)),
    WEATHER_CHANGE(AppText.get(R.string.alerts_weather))
}
