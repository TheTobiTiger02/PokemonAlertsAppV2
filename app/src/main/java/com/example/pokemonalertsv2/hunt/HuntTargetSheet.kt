package com.example.pokemonalertsv2.hunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
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
 * Saved filter profiles are deliberately absent: a hunt is a decision you make
 * standing on a street corner, not one you set up in Settings beforehand. The
 * controls are the same ones Filter Studio and the map panel use, so a hunt is
 * expressible in exactly the vocabulary the rest of the app already speaks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuntTargetSheet(
    catalog: FilterCatalog,
    artwork: Map<String, String>,
    questRewardThumbnails: Map<String, String>,
    categoryCounts: Map<AlertCategory, Int>,
    onDismiss: () -> Unit,
    onStart: (name: String, definition: FilterDefinition) -> Unit
) {
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
                onClick = { onStart(huntName(hunt, catalog), hunt) },
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
