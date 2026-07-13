package com.example.pokemonalertsv2.ui.alerts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.ui.alerts.AlertsMapRoute

class AlertsMapActivity : ComponentActivity() {
    private val alertsViewModel: PokemonAlertsViewModel by viewModels()
    private val repository by lazy { PokemonAlertsRepository.create(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by repository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = 0)
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())
            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                AlertsMapRoute(viewModel = alertsViewModel, onBack = { finish() })
            }
        }
    }
}
