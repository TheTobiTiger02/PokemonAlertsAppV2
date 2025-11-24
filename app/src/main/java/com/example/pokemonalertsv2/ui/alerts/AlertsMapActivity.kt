package com.example.pokemonalertsv2.ui.alerts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.ui.alerts.AlertsMapRoute

class AlertsMapActivity : ComponentActivity() {
    private val alertsViewModel: PokemonAlertsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PokemonAlertsV2Theme {
                AlertsMapRoute(viewModel = alertsViewModel, onBack = { finish() })
            }
        }
    }
}
