package com.example.pokemonalertsv2.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.counters.PokebattlerAuthRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerLoginProvider
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterSettings
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportCandidate
import com.example.pokemonalertsv2.ui.auth.PokebattlerLoginActivity
import java.text.DateFormat
import java.util.Date

/**
 * Defaults for the raid counters card, plus the Poke Genie import.
 *
 * Split out of `SettingsScreen.kt`, which is already very large, following the same
 * pattern as the GoDex collection screen.
 */
@Composable
fun RaidCountersSettingsContent(
    settings: RaidCounterSettings,
    onOptionsChanged: (RaidCounterOptions) -> Unit,
    onPrepareCsv: (android.net.Uri) -> Unit,
    pendingImport: PokeGenieImportCandidate?,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onClearPokeGenie: () -> Unit,
    importStatus: String?
) {
    val options = settings.options
    val context = LocalContext.current
    val auth = remember(context) { PokebattlerAuthRepository.getInstance(context) }
    val account by auth.account.collectAsStateWithLifecycle()
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The activity stores the session itself; the account flow delivers the result.
    }

    // The picker grants temporary access; prepare the bytes while that grant is valid, then
    // hold only the parsed candidate until the user confirms replacement.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPrepareCsv)
    }

    SettingsSection(title = "Counter defaults") {
        Text(
            text = "Used when a raid opens. You can still change them on the raid itself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingDropdown(
            label = "Attacker level",
            current = "Level ${options.attackerLevel}",
            entries = RaidCounterOptions.ATTACKER_LEVELS.map { it to "Level $it" },
            onSelect = { onOptionsChanged(options.copy(attackerLevel = it)) }
        )
        SettingDropdown(
            label = "Weather",
            current = options.weather.label,
            entries = PokebattlerWeather.entries.map { it to it.label },
            onSelect = { onOptionsChanged(options.copy(weather = it)) }
        )
        SettingDropdown(
            label = "Friendship",
            current = options.friendship.label,
            entries = PokebattlerFriendship.entries.map { it to it.label },
            onSelect = { onOptionsChanged(options.copy(friendship = it)) }
        )
        SettingDropdown(
            label = "Rank by",
            current = options.sort.label,
            entries = PokebattlerSort.entries.map { it to it.label },
            onSelect = { onOptionsChanged(options.copy(sort = it)) }
        )

        SwitchSetting(
            title = "Include megas",
            checked = options.includeMegas,
            onCheckedChange = { onOptionsChanged(options.copy(includeMegas = it)) }
        )
        SwitchSetting(
            title = "Include shadows",
            checked = options.includeShadow,
            onCheckedChange = { onOptionsChanged(options.copy(includeShadow = it)) }
        )
        SwitchSetting(
            title = "Include legendaries",
            checked = options.includeLegendary,
            onCheckedChange = { onOptionsChanged(options.copy(includeLegendary = it)) }
        )
    }

    SettingsSection(title = "My Pokémon") {
        Text(
            text = if (settings.pokeGenieCount > 0) {
                "Pokébattler scores each imported Pokémon at its recorded level and moves. " +
                    "IVs are intentionally ignored because anonymous Pokébattler by-level " +
                    "results use perfect IVs."
            } else {
                "Import your Poké Genie scan history to rank your Pokémon with Pokébattler."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (settings.pokeGenieCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("${settings.pokeGenieCount} Pokémon") }
                )
                settings.pokeGenieImportedAtMillis.takeIf { it > 0 }?.let { millis ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
                            )
                        }
                    )
                }
            }
            settings.pokeGenieFileName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        importStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { picker.launch(POKE_GENIE_MIME_TYPES) }) {
                Text(if (settings.pokeGenieCount > 0) "Re-import CSV" else "Import CSV")
            }
            if (settings.pokeGenieCount > 0) {
                OutlinedButton(onClick = onClearPokeGenie) { Text("Clear") }
            }
        }

        Text(
            text = "In Poké Genie: Menu → Export/Backup → Export to CSV, then pick the file here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingImport?.let { candidate ->
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text("Review CSV import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.fileName ?: "Poké Genie export")
                    Text("${candidate.summary.importedCount} Pokémon rows are ready to import.")
                    if (candidate.summary.skippedCount > 0) {
                        Text("${candidate.summary.skippedCount} rows will be skipped.")
                    }
                    if (candidate.summary.ignoredColumnCount > 0) {
                        Text("${candidate.summary.ignoredColumnCount} columns are not used.")
                    }
                    if (settings.pokeGenieCount > 0) {
                        Text(
                            "Confirming replaces the current ${settings.pokeGenieCount}-Pokémon roster.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirmImport) { Text("Replace roster") }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) { Text("Cancel") }
            }
        )

    }

    Text(
        text = "Pokébattler Pokébox (optional)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    Text(
        text = if (account == null) {
            "Sign in to rank the Pokémon saved in your Pokébattler Pokébox, using their real " +
                "IVs and movesets. There is no public Pokébox API, so this needs an account."
        } else {
            "Ranked from your Pokébox with its exact IVs and movesets."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    account?.let { linked ->
        Text(
            text = "Signed in as ${linked.displayName ?: linked.userId}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (account == null) {
            PokebattlerLoginProvider.entries.forEach { provider ->
                TextButton(
                    onClick = { signIn.launch(PokebattlerLoginActivity.intent(context, provider)) }
                ) { Text(provider.label) }
            }
        } else {
            TextButton(
                onClick = {
                    auth.signOut()
                    // Without this the WebView would silently sign straight back in.
                    PokebattlerLoginActivity.clearWebSession()
                }
            ) { Text("Sign out") }
        }
    }
}

@Composable
private fun <T> SettingDropdown(
    label: String,
    current: String,
    entries: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(current) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        }
                    )
                }
            }
        }
    }
}

/**
 * The in-app picker stays broad for mislabeled exports. The external ACTION_VIEW filter is
 * intentionally narrower so unrelated text files do not offer this app.
 */
private val POKE_GENIE_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "text/plain",
    "*/*"
)
