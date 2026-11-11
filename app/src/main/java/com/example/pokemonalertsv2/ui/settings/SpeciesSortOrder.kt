package com.example.pokemonalertsv2.ui.settings
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.util.AppText



/** How the species pickers order their artwork grids. */
enum class SpeciesSortOrder(val label: String) {
    DEX_NUMBER(AppText.get(R.string.settings_dex)),
    NAME_AZ("A–Z")
}
