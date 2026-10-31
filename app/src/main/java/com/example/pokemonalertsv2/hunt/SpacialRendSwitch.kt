package com.example.pokemonalertsv2.hunt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import kotlinx.coroutines.launch

/**
 * The app's one Spacial Rend setting, which the map's spawn circles, the floating map's ⋯ panel and
 * the arrival check all read: with it on, a spawn counts as in range from 80 m instead of 40 m.
 */
@Composable
internal fun SpacialRendSwitch(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { AlertPreferences(context.alertPreferencesDataStore) }
    val enabled by preferences.spacialRendEnabled.collectAsStateWithLifecycle(initialValue = false)
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spacial Rend", modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { on -> scope.launch { preferences.updateSpacialRendEnabled(on) } },
                modifier = Modifier.semantics { contentDescription = "Spacial Rend active" }
            )
        }
        Text(
            "Spawns count as in range from ${
                if (enabled) ArrivalTrackingRepository.SPACIAL_REND_RADIUS_METERS else ArrivalTrackingRepository.SPAWN_RADIUS_METERS
            } m.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
