package com.example.pokemonalertsv2.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.pokemonalertsv2.data.checklists.ChecklistRepository
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.PokemonSpeciesEntity
import com.example.pokemonalertsv2.data.gametext.GameNames
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.checklists.ChecklistKind
import com.example.pokemonalertsv2.data.database.ChecklistEntity

@Composable
internal fun AddGoDexDialog(busy: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checklists_add_godex)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.checklists_add_godex_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.checklists_link_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(url.trim()) }, enabled = url.isNotBlank() && !busy) {
                Text(stringResource(R.string.checklists_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
    )
}

/** Kind, alert badges, refresh and removal for one checklist. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChecklistSettingsSheet(
    checklist: ChecklistEntity,
    busy: Boolean,
    onDismiss: () -> Unit,
    onChange: (ChecklistEntity) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit
) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(checklist.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.checklists_kind), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChecklistKind.entries.forEach { kind ->
                    FilterChip(
                        selected = checklist.kind == kind.name,
                        onClick = { onChange(checklist.copy(kind = kind.name)) },
                        label = { Text(kindLabel(kind)) }
                    )
                }
            }
            Text(
                stringResource(R.string.checklists_kind_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow(
                title = stringResource(R.string.checklists_use_for_alerts),
                subtitle = stringResource(R.string.checklists_use_for_alerts_hint),
                checked = checklist.useForAlerts,
                onChange = { onChange(checklist.copy(useForAlerts = it)) }
            )
            SwitchRow(
                title = stringResource(R.string.checklists_include_evolutions),
                subtitle = stringResource(R.string.checklists_include_evolutions_hint),
                checked = checklist.includeEvolutions,
                enabled = checklist.useForAlerts,
                onChange = { onChange(checklist.copy(includeEvolutions = it)) }
            )
            if (checklist.source == ChecklistEntity.SOURCE_GODEX) {
                OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.checklists_refresh))
                }
            }
            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.checklists_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.checklists_remove)) },
            text = {
                Text(
                    stringResource(
                        if (checklist.source == ChecklistEntity.SOURCE_GODEX) R.string.checklists_remove_godex_message
                        else R.string.checklists_remove_local_message,
                        checklist.title
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.checklists_remove_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Name, kind and starting Pokémon for a checklist made in the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewChecklistDialog(busy: Boolean, onDismiss: () -> Unit, onCreate: (String, ChecklistKind, IntRange?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(ChecklistKind.SHINY.name) }
    // 0: every Pokémon, 1..9: that generation, -1: empty.
    var start by rememberSaveable { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checklists_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.checklists_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.checklists_kind), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChecklistKind.entries.forEach { kind ->
                        FilterChip(selected = kindName == kind.name, onClick = { kindName = kind.name }, label = { Text(kindLabel(kind)) })
                    }
                }
                Text(stringResource(R.string.checklists_start_with), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = start == 0, onClick = { start = 0 }, label = { Text(stringResource(R.string.checklists_template_all)) })
                    FilterChip(selected = start > 0, onClick = { if (start <= 0) start = 1 }, label = { Text(stringResource(R.string.checklists_template_generation)) })
                    FilterChip(selected = start == -1, onClick = { start = -1 }, label = { Text(stringResource(R.string.checklists_template_empty)) })
                }
                if (start > 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GENERATION_RANGES.indices.forEach { index ->
                            FilterChip(
                                selected = start == index + 1,
                                onClick = { start = index + 1 },
                                label = { Text(stringResource(R.string.checklists_generation, index + 1)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val range = when {
                        start == 0 -> 1..ChecklistRepository.MAX_DEX
                        start > 0 -> GENERATION_RANGES[start - 1]
                        else -> null
                    }
                    onCreate(title, ChecklistKind.fromName(kindName), range)
                },
                enabled = title.isNotBlank() && !busy
            ) { Text(stringResource(R.string.checklists_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
    )
}

/** Picks species to add to a checklist made in the app; ones already in it are not offered. */
@Composable
internal fun AddSpeciesDialog(present: Set<Int>, onDismiss: () -> Unit, onAdd: (Set<Int>) -> Unit) {
    val context = LocalContext.current
    var species by remember { mutableStateOf<List<PokemonSpeciesEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        com.example.pokemonalertsv2.data.PokemonSpeciesRepository.getInstance(context).syncIfNeeded()
        species = AppDatabase.getDatabase(context).pokemonSpeciesDao().getAllSpeciesList().filter { it.id <= ChecklistRepository.MAX_DEX }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val shown = remember(species, query, present) {
        val q = query.trim().lowercase(Locale.ROOT)
        species.filter { it.id !in present }.filter { entry ->
            q.isEmpty() || entry.id.toString() == q ||
                entry.name.lowercase(Locale.ROOT).contains(q) ||
                (GameNames.pokemon(entry.name, entry.id) ?: "").lowercase(Locale.ROOT).contains(q)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checklists_add_pokemon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.settings_search_name_or_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { entry ->
                        val checked = entry.id in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (checked) picked - entry.id else picked + entry.id }
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text("#${entry.id.toString().padStart(4, '0')}  ${GameNames.pokemon(entry.name, entry.id) ?: entry.name}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(picked) }, enabled = picked.isNotEmpty()) {
                Text(stringResource(R.string.checklists_add_selected, picked.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
    )
}
