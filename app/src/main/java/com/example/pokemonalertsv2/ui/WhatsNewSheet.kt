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

/** The release the notes below describe. Bump it together with the notes. */
internal const val WHATS_NEW_RELEASE = "1.16"

private val WHATS_NEW_POINTS = listOf(
    "Compact alert rows fit about six alerts on screen. Prefer the map cards? Settings › Appearance.",
    "History and Insights now live inside the Alerts tab: Live, History, Insights.",
    "The feed and map filter buttons edit in place and bring you straight back.",
    "The map’s new tools button holds hunts, catch routes, Mega boost and the floating map.",
    "Alert pages keep only I’m going and Navigate at the bottom; snooze and share moved to the top.",
    "Settings are regrouped, and missing permissions show as one card instead of pop-ups."
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
