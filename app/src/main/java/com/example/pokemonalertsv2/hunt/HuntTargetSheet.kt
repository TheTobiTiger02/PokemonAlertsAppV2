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
import com.example.pokemonalertsv2.catchroutes.CatchAreaPicker
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.catchroutes.isArea
import com.example.pokemonalertsv2.catchroutes.SavedAreaChips
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.ui.alerts.ALSBACH_LATITUDE
import com.example.pokemonalertsv2.ui.alerts.ALSBACH_LONGITUDE
import com.example.pokemonalertsv2.ui.alerts.getLastKnownLocation
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
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import com.example.pokemonalertsv2.ui.components.DesignCompareStrip
import com.example.pokemonalertsv2.ui.components.DesignScreen
import com.example.pokemonalertsv2.ui.components.rememberDesignOption
import com.example.pokemonalertsv2.ui.components.PanelFooterButton
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelPages
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.alerts.mappanel.FiltersState
import com.example.pokemonalertsv2.ui.alerts.mappanel.TypeEditor
import com.example.pokemonalertsv2.ui.alerts.mappanel.TypeEditorBody
import com.example.pokemonalertsv2.ui.alerts.mappanel.editor
import com.example.pokemonalertsv2.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

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
    onStart: (name: String, definition: FilterDefinition, savedHuntId: String?, area: List<CatchPoint>) -> Unit,
    /**
     * The running hunt, when the sheet is opened to change it rather than to start one.
     * Its choices are filled in, the saved-hunt list is left out (picking one of those would
     * be a different hunt), and the button updates the hunt in place.
     */
    editing: HuntSession? = null
) {
    val context = LocalContext.current
    val huntRepository = remember(context) { HuntRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val savedHunts by huntRepository.savedHunts.collectAsStateWithLifecycle()
    // Set when a saved hunt is opened for editing, so starting writes back to that
    // row instead of leaving a near-duplicate beside it.
    var editingId by remember { mutableStateOf(editing?.savedHuntId) }
    var renaming by remember { mutableStateOf<SavedHunt?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Every selection starts empty, not "all".
    //
    // The shared chip controls treat ALL as "everything is ticked", so the first tap
    // on a chip *removes* one — the right gesture for narrowing a map filter, and the
    // wrong one for a hunt, where tapping Dragon has to mean "I want Dragon". Starting
    // from None makes the first tap additive. Nothing picked is then translated back
    // to "any" by [forHunt] on the way out, so an untouched section still means all.
    var draft by remember { mutableStateOf(editing?.definition?.forHuntDraft() ?: EMPTY_HUNT_DRAFT) }
    var speciesTarget by remember { mutableStateOf<MapSelectorTarget?>(null) }
    var questsOpen by remember { mutableStateOf(false) }
    // Optional walking area: matches outside it are never suggested.
    var area by remember { mutableStateOf(editing?.area.orEmpty()) }
    var areaOpen by remember { mutableStateOf(false) }

    val chosenTypes = remember(draft) { draft.chosenTypes() }
    val ready = chosenTypes.isNotEmpty()

    // The new layouts are compared alongside Filter Studio's, since they share its editors.
    if (rememberDesignOption(DesignScreen.FILTER_STUDIO) != 0) {
        val hunt = remember(draft) { draft.forHunt() }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("hunt_target_pages")
        ) {
            HuntPages(
                editing = editing != null,
                savedHunts = if (editing == null) savedHunts else emptyList(),
                chosenTypes = chosenTypes,
                filters = FiltersState(
                    definition = draft,
                    catalog = catalog,
                    artwork = artwork,
                    rewardThumbnails = questRewardThumbnails,
                    categoryCounts = categoryCounts,
                    visibleCount = 0,
                    totalCount = 0,
                    onDefinitionChange = { draft = it },
                    openQuests = { questsOpen = true },
                    openDistanceOverrides = {}
                ),
                areaSummary = if (area.isArea()) "Only inside your area (${area.size} corners)" else "Anywhere nearby",
                area = area,
                onAreaChange = { area = it },
                onOpenArea = { areaOpen = true },
                onStartSaved = { saved -> onStart(saved.name, saved.definition, saved.id, saved.area) },
                onEditSaved = { saved ->
                    draft = saved.definition.forHuntDraft()
                    editingId = saved.id
                    area = saved.area
                },
                onRenameSaved = { renaming = it },
                onDeleteSaved = { saved ->
                    scope.launch { huntRepository.deleteSavedHunt(saved.id) }
                    if (editingId == saved.id) editingId = null
                },
                startLabel = when {
                    !ready -> "Pick something to hunt"
                    editing != null -> "Update hunt"
                    else -> "Hunt ${huntName(hunt, catalog)}"
                },
                ready = ready,
                onStart = { onStart(huntName(hunt, catalog), hunt, editingId, area) }
            )
        }
    } else
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DesignCompareStrip(DesignScreen.FILTER_STUDIO)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A fling back to the top must not close the sheet and lose the draft.
                .keepScrollInSheet()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (editing != null) "Edit hunt targets" else "What are you hunting?",
                style = MaterialTheme.typography.titleLarge
            )
            if (editing != null) {
                Text(
                    "The hunt keeps running. Your catches and the target you are walking to stay, " +
                        "unless it no longer matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (editing == null && savedHunts.isNotEmpty()) {
                Section("Hunted before") {
                    savedHunts.forEach { saved ->
                        SavedHuntRow(
                            hunt = saved,
                            onStart = { onStart(saved.name, saved.definition, saved.id, saved.area) },
                            onEdit = {
                                draft = saved.definition.forHuntDraft()
                                editingId = saved.id
                                area = saved.area
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

                    FilterAlertType.COMMON -> SpeciesLauncher(
                        label = "Common species",
                        selection = draft.commonSpecies,
                        onClick = { speciesTarget = MapSelectorTarget.COMMON }
                    )

                    // Kecleon, weather and "other" have nothing further to narrow.
                    else -> Unit
                }
            }

            Section("Where?") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (area.isArea()) "Only inside your area (${area.size} corners)" else "Anywhere nearby",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (area.isArea()) TextButton(onClick = { area = emptyList() }) { Text("Remove") }
                    OutlinedButton(onClick = { areaOpen = true }) { Text(if (area.isArea()) "Edit area" else "Limit to area") }
                }
                // The areas saved in the picker, shared with catch routes, one tap each.
                SavedAreaChips(area) { area = it }
            }

            val hunt = remember(draft) { draft.forHunt() }
            Button(
                onClick = { onStart(huntName(hunt, catalog), hunt, editingId, area) },
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        !ready -> "Pick something to hunt"
                        editing != null -> "Update hunt"
                        else -> "Hunt ${huntName(hunt, catalog)}"
                    }
                )
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

    if (areaOpen) {
        val center = remember {
            getLastKnownLocation(context)?.let { CatchPoint(it.latitude, it.longitude) } ?: CatchPoint(ALSBACH_LATITUDE, ALSBACH_LONGITUDE)
        }
        CatchAreaPicker(
            initial = area,
            center = center,
            title = "Hunt area",
            onDismiss = { areaOpen = false },
            onDone = { area = it; areaOpen = false }
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

private enum class HuntPage { ROOT, SPAWN, HUNDO, PVP, NUNDO, COMMON, RAIDS, ROCKET, QUESTS }

/** What a hunt narrows a type to, where "nothing picked" means "any of them". */
private fun FilterAlertType.huntSummary(definition: FilterDefinition): String = when (this) {
    FilterAlertType.SPAWN -> definition.spawnSpecies.summary()
    FilterAlertType.HUNDO -> definition.hundoSpecies.summary()
    FilterAlertType.PVP -> definition.pvpSpecies.summary()
    FilterAlertType.NUNDO -> definition.nundoSpecies.summary()
    FilterAlertType.COMMON -> definition.commonSpecies.summary()
    FilterAlertType.RAID -> if (definition.raidTiers.mode != FilterSelectionMode.ONLY && definition.raidSpecies.mode != FilterSelectionMode.ONLY) "Any"
        else "Tiers: ${definition.raidTiers.summary()} · bosses: ${definition.raidSpecies.summary()}"
    FilterAlertType.ROCKET -> definition.rocketTypes.summary()
    FilterAlertType.QUEST -> definition.quests.summary()
    else -> "Any"
}

/**
 * The hunt picker as pages in one sheet: what (type tiles, then one row per chosen type that
 * opens its editor), where, and a Start button that stays put at the bottom.
 */
@Composable
private fun HuntPages(
    editing: Boolean,
    savedHunts: List<SavedHunt>,
    chosenTypes: List<FilterAlertType>,
    filters: FiltersState,
    areaSummary: String,
    area: List<CatchPoint>,
    onAreaChange: (List<CatchPoint>) -> Unit,
    onOpenArea: () -> Unit,
    onStartSaved: (SavedHunt) -> Unit,
    onEditSaved: (SavedHunt) -> Unit,
    onRenameSaved: (SavedHunt) -> Unit,
    onDeleteSaved: (SavedHunt) -> Unit,
    startLabel: String,
    ready: Boolean,
    onStart: () -> Unit
) {
    var page by remember { mutableStateOf(HuntPage.ROOT) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).keepScrollInSheet()) {
        DesignCompareStrip(DesignScreen.FILTER_STUDIO)
        Box(Modifier.weight(1f)) {
            PanelPages(page = page, root = HuntPage.ROOT, onBack = { page = HuntPage.ROOT }) { shown ->
                if (shown == HuntPage.ROOT) {
                    LazyColumn(Modifier.fillMaxSize().testTag("hunt_root")) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            PanelHeader(
                                title = if (editing) "Edit hunt targets" else "What are you hunting?",
                                subtitle = if (editing) "The hunt keeps running while you change it" else null
                            )
                        }
                        if (savedHunts.isNotEmpty()) {
                            item { PanelSectionLabel("Hunted before") }
                            items(savedHunts, key = { it.id }) { saved ->
                                Box(Modifier.padding(horizontal = PanelGutter)) {
                                    SavedHuntRow(
                                        hunt = saved,
                                        onStart = { onStartSaved(saved) },
                                        onEdit = { onEditSaved(saved) },
                                        onRename = { onRenameSaved(saved) },
                                        onDelete = { onDeleteSaved(saved) }
                                    )
                                }
                            }
                        }
                        item { PanelSectionLabel("What") }
                        item {
                            Box(Modifier.padding(horizontal = PanelGutter)) {
                                AlertTypesSection(
                                    definition = filters.definition,
                                    categoryCounts = filters.categoryCounts,
                                    onDefinitionChange = filters.onDefinitionChange,
                                    setAllLabel = "Pick one or more"
                                )
                            }
                        }
                        // Only the criteria that belong to the chosen types: a Rocket hunt has
                        // no business asking about raid tiers.
                        val editable = chosenTypes.mapNotNull { type -> type.editor()?.let { type to it } }
                        if (editable.isNotEmpty()) {
                            item { PanelSectionLabel("Narrow it down") }
                            items(editable, key = { it.first.name }) { (type, editor) ->
                                PanelNavRow(
                                    title = type.label,
                                    summary = type.huntSummary(filters.definition),
                                    active = type.huntSummary(filters.definition) != "Any",
                                    onClick = { page = HuntPage.valueOf(editor.name) },
                                    modifier = Modifier.testTag("hunt_row_${type.name.lowercase()}"),
                                    icon = { PanelIcon(R.drawable.ic_tune) }
                                )
                            }
                        }
                        item { PanelSectionLabel("Where") }
                        item {
                            PanelNavRow(
                                title = "Area",
                                summary = areaSummary,
                                active = area.isArea(),
                                onClick = onOpenArea,
                                modifier = Modifier.testTag("hunt_row_area"),
                                icon = { PanelIcon(R.drawable.ic_map) }
                            )
                        }
                        item {
                            // The areas saved in the picker, shared with catch routes, one tap each.
                            Box(Modifier.padding(horizontal = PanelGutter, vertical = 4.dp)) { SavedAreaChips(area) { onAreaChange(it) } }
                        }
                    }
                } else {
                    val editor = TypeEditor.valueOf(shown.name)
                    Column(Modifier.fillMaxSize()) {
                        PanelHeader(
                            title = editor.type.label,
                            subtitle = editor.type.huntSummary(filters.definition),
                            onBack = { page = HuntPage.ROOT }
                        )
                        TypeEditorBody(editor, filters, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
        PanelFooterButton(
            text = startLabel,
            onClick = onStart,
            enabled = ready,
            modifier = Modifier.navigationBarsPadding().testTag("hunt_start")
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
