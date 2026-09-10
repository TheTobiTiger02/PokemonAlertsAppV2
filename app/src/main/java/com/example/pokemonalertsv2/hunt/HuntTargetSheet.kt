package com.example.pokemonalertsv2.hunt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.MAX_FILTER_PROFILE_NAME
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.data.QuestFilterRules
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.AlertTypesSection
import com.example.pokemonalertsv2.ui.alerts.MapSelectorTarget
import com.example.pokemonalertsv2.ui.alerts.SpeciesPickerSheet
import com.example.pokemonalertsv2.ui.alerts.TokenSelectionSection
import com.example.pokemonalertsv2.ui.alerts.candidates
import com.example.pokemonalertsv2.ui.alerts.selectionFor
import com.example.pokemonalertsv2.ui.alerts.withSelection
import com.example.pokemonalertsv2.ui.settings.QuestRulesDialog

/**
 * Choose what to hunt, here and now.
 *
 * The controls are the same ones Filter Studio and the map panel use, so a hunt
 * is expressible in exactly the vocabulary the rest of the app already speaks.
 *
 * Hunts you have run before are listed at the top, and starting one is a single
 * tap. They are not Filter Studio profiles: the list is a record of what you
 * have actually hunted, written by starting a hunt rather than set up in
 * Settings beforehand -- see [SavedHunt].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuntTargetSheet(
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    questRewardThumbnails: Map<String, String>,
    categoryCounts: Map<AlertCategory, Int>,
    onDismiss: () -> Unit,
    onStart: (name: String, definition: FilterDefinition, savedHuntId: String?) -> Unit
) {
    val context = LocalContext.current
    val huntRepository = remember(context) { HuntRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val savedHunts by huntRepository.savedHunts.collectAsStateWithLifecycle()
    // Set when a saved hunt is opened for editing, so starting writes back to that
    // row instead of leaving a near-duplicate beside it.
    var editingId by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SavedHunt?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Every selection starts empty, not "all".
    //
    // The shared chip controls treat ALL as "everything is ticked", so the first tap
    // on a chip *removes* one — the right gesture for narrowing a map filter, and the
    // wrong one for a hunt, where tapping Dragon has to mean "I want Dragon". Starting
    // from None makes the first tap additive. Nothing picked is then translated back
    // to "any" by [forHunt] on the way out, so an untouched section still means all.
    var draft by remember { mutableStateOf(EMPTY_HUNT_DRAFT) }
    var speciesTarget by remember { mutableStateOf<MapSelectorTarget?>(null) }
    var questsOpen by remember { mutableStateOf(false) }

    val chosenTypes = remember(draft) { draft.chosenTypes() }
    val ready = chosenTypes.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("What are you hunting?", style = MaterialTheme.typography.titleLarge)

            if (savedHunts.isNotEmpty()) {
                Section("Hunted before") {
                    savedHunts.forEach { saved ->
                        SavedHuntRow(
                            hunt = saved,
                            onStart = { onStart(saved.name, saved.definition, saved.id) },
                            onEdit = {
                                draft = saved.definition.forHuntDraft()
                                editingId = saved.id
                            },
                            onRename = { renaming = saved },
                            onDelete = {
                                scope.launch { huntRepository.deleteSavedHunt(saved.id) }
                                if (editingId == saved.id) editingId = null
                            }
                        )
                    }
                }
            }

            AlertTypesSection(
                definition = draft,
                categoryCounts = categoryCounts,
                onDefinitionChange = { draft = it },
                setAllLabel = "Pick one or more"
            )

            // Only the criteria that belong to the chosen types: a Rocket hunt has
            // no business asking about raid tiers.
            chosenTypes.forEach { type ->
                when (type) {
                    FilterAlertType.ROCKET -> Section("Which grunts?") {
                        TokenSelectionSection(
                            tokens = MapSelectorTarget.ROCKET.candidates(catalog),
                            selection = draft.rocketTypes,
                            onSelectionChange = { draft = draft.copy(rocketTypes = it) }
                        )
                    }

                    FilterAlertType.RAID -> Section("Which raids?") {
                        TokenSelectionSection(
                            tokens = MapSelectorTarget.RAID_TIERS.candidates(catalog),
                            selection = draft.raidTiers,
                            onSelectionChange = { draft = draft.copy(raidTiers = it) }
                        )
                        SpeciesLauncher(
                            label = "Raid bosses",
                            selection = draft.raidSpecies,
                            onClick = { speciesTarget = MapSelectorTarget.RAID_SPECIES }
                        )
                    }

                    FilterAlertType.QUEST -> Section("Which quests?") {
                        OutlinedButton(
                            onClick = { questsOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(draft.quests.summary()) }
                    }

                    FilterAlertType.SPAWN -> SpeciesLauncher(
                        label = "Spawn species",
                        selection = draft.spawnSpecies,
                        onClick = { speciesTarget = MapSelectorTarget.SPAWN }
                    )

                    FilterAlertType.HUNDO -> SpeciesLauncher(
                        label = "Hundo species",
                        selection = draft.hundoSpecies,
                        onClick = { speciesTarget = MapSelectorTarget.HUNDO }
                    )

                    FilterAlertType.NUNDO -> SpeciesLauncher(
                        label = "Nundo species",
                        selection = draft.nundoSpecies,
                        onClick = { speciesTarget = MapSelectorTarget.NUNDO }
                    )

                    FilterAlertType.PVP -> SpeciesLauncher(
                        label = "PvP species",
                        selection = draft.pvpSpecies,
                        onClick = { speciesTarget = MapSelectorTarget.PVP }
                    )

                    FilterAlertType.RARE -> SpeciesLauncher(
                        label = "Rare species",
                        selection = draft.rareSpecies,
                        onClick = { speciesTarget = MapSelectorTarget.RARE }
                    )

                    // Kecleon, weather and "other" have nothing further to narrow.
                    else -> Unit
                }
            }

            val hunt = remember(draft) { draft.forHunt() }
            Button(
                onClick = { onStart(huntName(hunt, catalog), hunt, editingId) },
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ready) "Hunt ${huntName(hunt, catalog)}" else "Pick something to hunt")
            }
        }
    }

    speciesTarget?.let { target ->
        SpeciesPickerSheet(
            initialTarget = target,
            definition = draft,
            catalog = catalog,
            artwork = artwork,
            onDefinitionChange = { draft = it },
            onDismiss = { speciesTarget = null }
        )
    }

    renaming?.let { target ->
        HuntNameDialog(
            initial = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                scope.launch { huntRepository.renameSavedHunt(target.id, name) }
                renaming = null
            }
        )
    }

    if (questsOpen) {
        QuestRulesDialog(
            current = draft.quests,
            catalog = catalog.quests,
            artwork = artwork,
            rewardThumbnails = questRewardThumbnails,
            onDismiss = { questsOpen = false },
            onSave = {
                draft = draft.copy(quests = it)
                questsOpen = false
            }
        )
    }
}

/**
 * One remembered hunt: tap to run it again, overflow for the rest.
 *
 * The name is the whole row's affordance because that is what the trainer is
 * looking for -- the sub-line only says how narrow it is, which matters when two
 * hunts read alike.
 */
@Composable
private fun SavedHuntRow(
    hunt: SavedHunt,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onStart)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hunt.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (val rules = hunt.definition.advancedRuleCount) {
                    0 -> "Anything of that kind"
                    1 -> "1 extra rule"
                    else -> "$rules extra rules"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More for ${hunt.name}"
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/** Renaming a saved hunt. Deliberately local: FiltersHub's dialog belongs to Settings. */
@Composable
private fun HuntNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename hunt") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(MAX_FILTER_PROFILE_NAME) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun SpeciesLauncher(
    label: String,
    selection: FilterSelection,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$label · ${selection.summary()}")
    }
}

private fun FilterSelection.summary(): String = when {
    mode != FilterSelectionMode.ONLY -> "Any"
    selectedCount == 1 -> values.first()
    else -> "$selectedCount chosen"
}

private fun QuestFilterRules.summary(): String = when {
    exactPairs.size == 1 -> "1 exact quest"
    exactPairs.size > 1 -> "${exactPairs.size} exact quests"
    facetEnabled -> "By task and reward"
    else -> "Any quest"
}
