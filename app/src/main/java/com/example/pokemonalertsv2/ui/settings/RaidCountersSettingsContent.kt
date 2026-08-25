package com.example.pokemonalertsv2.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.counters.PokebattlerDodge
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterSettings
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
    onImportCsv: (android.net.Uri) -> Unit,
    onClearPokeGenie: () -> Unit,
    importStatus: String?
) {
    val options = settings.options

    // ACTION_OPEN_DOCUMENT grants access for this activity only, so the import must run
    // straight away rather than being handed to a worker.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportCsv)
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
            label = "Dodging",
            current = options.dodge.label,
            entries = PokebattlerDodge.entries.map { it to it.label },
            onSelect = { onOptionsChanged(options.copy(dodge = it)) }
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
                "Raids show which counters you own, rank them by their real level, IVs and " +
                    "moveset, and suggest a team of six."
            } else {
                "Import your Poké Genie scan history to rank counters by the Pokémon you " +
                    "actually own, using their real level, IVs and moveset."
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
 * Poke Genie exports frequently arrive as `application/octet-stream`, so the wildcard has
 * to be present or the file is greyed out in the picker.
 */
private val POKE_GENIE_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "text/plain",
    "*/*"
)
