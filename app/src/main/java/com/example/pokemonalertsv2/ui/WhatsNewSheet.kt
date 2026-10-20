package com.example.pokemonalertsv2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet

/** The release the notes below describe. Bump it together with the notes. */
internal const val WHATS_NEW_RELEASE = "1.20"

private val WHATS_NEW_POINTS = listOf(
    "Spawnpoints on the map: switch them on in Layers on the OpenStreetMap style. Green is up now, amber spawns within 10 minutes. Tap one for its hourly timer, next spawns and recent Pokémon.",
    "\"I'm going\" can show a map: tap Map on the alert to see yourself with a direction arrow, the target and the walking path. Show map in the live notification opens it over other apps.",
    "New Events widget for your home screen: what is running now and what is coming up, for the event types you choose. Tap an event to open it.",
    "Smoother everywhere: filtering the live feed and the map no longer blocks scrolling, and countdowns no longer redraw every card each second."
)

/**
 * A one-time summary of what moved in this release, so existing users are not left hunting
 * for History or the Route button. New users skip it: onboarding marks it seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatsNewSheetHost() {
    val context = LocalContext.current
    val preferences = remember(context) { AlertPreferences(context.alertPreferencesDataStore) }
    val seen by preferences.lastSeenWhatsNew.collectAsStateWithLifecycle(initialValue = WHATS_NEW_RELEASE)
    val scope = rememberCoroutineScope()
    if (seen == WHATS_NEW_RELEASE) return
    val dismiss: () -> Unit = { scope.launch { preferences.markWhatsNewSeen(WHATS_NEW_RELEASE) } }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .keepScrollInSheet()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "What’s new in ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            WHATS_NEW_POINTS.forEach { point ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Text(point, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("Got it") }
        }
    }
}
