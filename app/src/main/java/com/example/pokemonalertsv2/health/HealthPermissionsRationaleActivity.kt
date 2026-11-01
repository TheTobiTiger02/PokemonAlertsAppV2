package com.example.pokemonalertsv2.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme

/** Health Connect's required "why does this app want this" page. */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by remember { AlertPreferences(alertPreferencesDataStore).themeMode }
                .collectAsStateWithLifecycle(initialValue = 0)
            PokemonAlertsV2Theme(darkTheme = AppThemeMode.fromStored(themeMode).resolveDark(isSystemInDarkTheme())) {
                Scaffold { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Watch data in Pokémon Alerts", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "The app reads your heart rate, steps and calories from Health Connect only for the time a " +
                                "hunt or catch route was running, and shows them in that walk's summary. A Galaxy Watch " +
                                "gets there through Samsung Health.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Nothing is written to Health Connect, nothing is read in the background, and the numbers " +
                                "stay on your phone with the hunt history. Clearing the history removes them. You can " +
                                "revoke access any time in Health Connect.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = ::finish) { Text("OK") }
                    }
                }
            }
        }
    }
}
