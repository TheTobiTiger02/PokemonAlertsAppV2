package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.CounterMetric
import com.example.pokemonalertsv2.data.counters.DecoratedCounter
import com.example.pokemonalertsv2.data.counters.PersonalCounter
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounter
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidBossMoveset
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import java.util.Locale
import com.example.pokemonalertsv2.data.counters.toCounterMetric

@Composable
internal fun BattleSetupSummary(state: RaidCountersUiState, onOpen: () -> Unit) {
    val options = state.options
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "Open battle setup",
                onClick = onOpen
            )
            .semantics {
                contentDescription = "Battle setup: level ${options.attackerLevel}, " +
                    "${options.weather.label}, ${options.friendship.label}, ${options.sort.label}"
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Battle setup", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = buildString {
                        append("L${options.attackerLevel} · ${options.weather.label} · ")
                        append(options.friendship.label)
                        append(" · ${options.sort.label}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpen) { Text("Tune") }
        }
    }
}

@Composable
internal fun BattleSetupSheet(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    onDismiss: () -> Unit
) {
    val options = state.options
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
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

