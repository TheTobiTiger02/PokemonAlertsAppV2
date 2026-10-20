package com.example.pokemonalertsv2.ui.counters

import com.example.pokemonalertsv2.ui.components.SegmentedChoice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun BattleSetupSheet(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onDismiss: () -> Unit
) {
    val options = state.options
    // Taller than a short phone, so it scrolls and Save stays reachable.
    Column(
        modifier = Modifier
            .keepScrollInSheet()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Battle setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            "These controls match the Pokébattler raid defaults. Results stay visible while a new query loads.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OptionDropdown(
            label = state.selectedBossMoveset?.displayName ?: "Average moveset",
            entries = buildList {
                add(null to "Average moveset")
                state.bossMovesets.filterNot { it.isRandom }.forEach { add(it to it.displayName) }
            }.distinctBy { it.first?.cacheToken() ?: "AVERAGE" },
            onSelect = actions.onBossMovesetChanged
        )
        OptionDropdown(
            label = "Level ${options.attackerLevel}",
            entries = RaidCounterOptions.ATTACKER_LEVELS.map { it to "Level $it" },
            onSelect = { actions.onOptionsChanged(options.copy(attackerLevel = it)) },
            enabled = !state.showingPersonal
        )
        OptionDropdown(
            label = options.weather.label,
            entries = PokebattlerWeather.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(weather = it)) }
        )
        OptionDropdown(
            label = options.friendship.label,
            entries = PokebattlerFriendship.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(friendship = it)) }
        )
        OptionDropdown(
            label = options.attackStrategy.label,
            entries = com.example.pokemonalertsv2.data.counters.PokebattlerAttackStrategy.entries
                .map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(attackStrategy = it)) }
        )
        OptionDropdown(
            label = options.sort.label,
            entries = PokebattlerSort.entries.map { it to it.label },
            onSelect = { actions.onOptionsChanged(options.copy(sort = it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        SheetSwitchRow("Party Power", options.partyPower) {
            actions.onOptionsChanged(options.copy(partyPower = it))
        }
        SheetSwitchRow("Include megas", options.includeMegas) {
            actions.onOptionsChanged(options.copy(includeMegas = it))
        }
        SheetSwitchRow("Include shadows", options.includeShadow) {
            actions.onOptionsChanged(options.copy(includeShadow = it))
        }
        SheetSwitchRow("Include legendaries", options.includeLegendary) {
            actions.onOptionsChanged(options.copy(includeLegendary = it))
        }
        SheetSwitchRow("Owned only", state.ownedOnly, actions.onOwnedOnlyChanged)

        if (state.showingPersonal) {
            Text("My Pokémon moves", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PersonalMovesMode.entries.forEach { mode ->
                    SegmentedChoice(
                        label = mode.label,
                        selected = state.personalMovesMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { actions.onPersonalMovesModeChanged(mode) }
                    )
                }
            }
            Text(
                if (state.personalMovesMode == PersonalMovesMode.CURRENT) {
                    "Only imports with a recorded fast move and at least one charged move are ranked."
                } else {
                    "Known moves are kept; only missing components use the best legal move."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = actions.onSaveAsDefault) { Text("Save as default") }
        TextButton(onClick = onDismiss) { Text("Done") }
    }
}

@Composable
internal fun SheetSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun CounterChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
internal fun <T> OptionDropdown(
    label: String,
    entries: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CounterChip(label = label, selected = selected, enabled = enabled) { expanded = true }
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

