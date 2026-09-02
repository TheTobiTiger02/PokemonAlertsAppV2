@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.alerts.AREA_FILTER_OPTIONS
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.accentColor
import com.example.pokemonalertsv2.util.TravelTime
import com.example.pokemonalertsv2.widget.*

@Composable
internal fun FiltersHubContent(
    viewModel: SettingsViewModel,
    onOpenFullNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val document by viewModel.filterStateDocument.collectAsStateWithLifecycle()
    val counts by viewModel.filterPreviewCounts.collectAsStateWithLifecycle()
    val requestedEditor by viewModel.requestedFilterEditor.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showWidgets by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestedEditor) {
        requestedEditor?.let { editing = it.name; viewModel.consumeRequestedFilterEditor() }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Filter Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Each surface has its own rules. Start simple, then tune only the alert types that matter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterSurface.entries.forEach { surface ->
            val assignment = document.assignment(surface)
            val profileName = (BuiltInFilterProfiles.all + document.profiles).firstOrNull { it.id == assignment.profileId }?.name
            SurfaceSummaryCard(surface, assignment, assignment.resolve(document), counts[surface] ?: 0, profileName) { editing = surface.name }
        }
        SummaryCard("Widgets", "Independent rules per widget • sorting stays with each widget", Icons.Default.Settings, null) { showWidgets = !showWidgets }
        AnimatedVisibility(showWidgets) { WidgetFiltersSection() }
        TextButton(onClick = onOpenFullNotificationSettings) {
            Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text("Notification delivery, quiet hours and vibration")
        }
    }
    editing?.let { name -> FilterSurface.entries.firstOrNull { it.name == name }?.let { FilterStudioDialog(it, viewModel) { editing = null } } }
}

@Composable
private fun SurfaceSummaryCard(surface: FilterSurface, assignment: FilterAssignment, definition: FilterDefinition, matchCount: Int, profileName: String?, onClick: () -> Unit) {
    val typeSummary = when (definition.alertTypes.mode) {
        FilterSelectionMode.ALL -> "All alert types"; FilterSelectionMode.NONE -> "No alert types"; FilterSelectionMode.ONLY -> "${definition.alertTypes.selectedCount} alert types"
    }
    val location = buildList {
        if (definition.areas.mode == FilterSelectionMode.NONE) add("No areas")
        if (definition.areas.mode == FilterSelectionMode.ONLY) add("${definition.areas.selectedCount} areas")
        if (definition.maxDistanceKm > 0) add("${definition.maxDistanceKm} km")
        definition.distanceOverrides.ruleCount.takeIf { it > 0 }?.let { add(if (it == 1) "1 distance override" else "$it distance overrides") }
        if (definition.maxWalkingMinutes > 0) add("${definition.maxWalkingMinutes} min walk")
    }.ifEmpty { listOf("Anywhere") }.joinToString(" • ")
    SummaryCard(
        surface.label, "$typeSummary • $location",
        when (surface) { FilterSurface.FEED -> Icons.Default.List; FilterSurface.MAP -> Icons.Default.LocationOn; FilterSurface.NOTIFICATIONS -> Icons.Default.Notifications },
        "$matchCount live" + (if (definition.advancedRuleCount > 0) " • ${definition.advancedRuleCount} advanced" else "") + (if (assignment.mode == FilterAssignmentMode.LINKED) " • Linked: ${profileName ?: "profile"}" else " • Custom rules"), onClick
    )
}

@Composable
private fun SummaryCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, badge: String?, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                badge?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

private enum class SelectorTarget { SPAWN, RARE, HUNDO, NUNDO, PVP, RAID_SPECIES, RAID_TIERS, ROCKET }

private fun FilterSelection.summary(noun: String): String = when (mode) {
    FilterSelectionMode.ALL -> "All $noun"
    FilterSelectionMode.NONE -> "No $noun"
    FilterSelectionMode.ONLY -> "$selectedCount $noun"
}

@Composable
internal fun FilterStudioDialog(surface: FilterSurface, viewModel: SettingsViewModel, widgetId: Int? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val document by viewModel.filterStateDocument.collectAsStateWithLifecycle()
    val catalog by viewModel.filterCatalog.collectAsStateWithLifecycle()
    val species by viewModel.filterSpecies.collectAsStateWithLifecycle()
    val artwork = remember(species) { species.associate { normalizeFilterToken(it.name) to it.imageUrl } }
    val alerts by viewModel.filterableAlerts.collectAsStateWithLifecycle()
    val rewardThumbnails by viewModel.questRewardThumbnails.collectAsStateWithLifecycle()
    val contexts by viewModel.filterPreviewContexts.collectAsStateWithLifecycle()
    val legacyArea by viewModel.selectedArea.collectAsStateWithLifecycle()
    val legacyDistance by viewModel.maxDistance.collectAsStateWithLifecycle()
    val widgetConfiguration = remember(widgetId) { widgetId?.let { WidgetConfigurationStore.get(context, it) } }
    val assignment = widgetConfiguration?.let {
        it.filterAssignment ?: FilterAssignment.local(it.legacyFilterDefinition(legacyArea, legacyDistance))
    } ?: document.assignment(surface)
    val editorTitle = widgetId?.let { "Widget #$it" } ?: surface.label
    var draft by remember(surface, assignment) { mutableStateOf(assignment.resolve(document)) }
    var linkedProfileId by remember(surface, assignment) { mutableStateOf(assignment.profileId.takeIf { assignment.mode == FilterAssignmentMode.LINKED }) }
    var showLinkedWarning by remember { mutableStateOf(false) }
    var selector by remember { mutableStateOf<SelectorTarget?>(null) }
    var selectorQueries by remember { mutableStateOf(emptyMap<SelectorTarget, String>()) }
    var showProfiles by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showQuests by remember { mutableStateOf(false) }
    var showDistanceOverrides by remember { mutableStateOf(false) }
    val matchCount = remember(alerts, draft, contexts) { alerts.count { AlertFilterMatcher.matches(it, draft, contexts[it.uniqueId] ?: FilterMatchContext()) } }
    val linkedProfile = (BuiltInFilterProfiles.all + document.profiles).firstOrNull { it.id == linkedProfileId }
    val applyLocal: () -> Unit = {
        if (widgetId != null && widgetConfiguration != null) {
            WidgetConfigurationStore.save(context, widgetId, widgetConfiguration.copy(filterAssignment = FilterAssignment.local(draft)))
            AlertsWidgetProvider.requestUpdate(context)
        } else viewModel.applySurfaceFilter(surface, draft)
        onDismiss()
    }
    val applyLink: (FilterProfile) -> Unit = { profile ->
        if (widgetId != null && widgetConfiguration != null) {
            WidgetConfigurationStore.save(context, widgetId, widgetConfiguration.copy(filterAssignment = FilterAssignment.linked(profile)))
            AlertsWidgetProvider.requestUpdate(context)
        } else viewModel.applyProfile(surface, profile, true)
        onDismiss()
    }
    val applyDraft: () -> Unit = {
        when {
            linkedProfile == null -> applyLocal()
            draft == linkedProfile.definition -> applyLink(linkedProfile)
            BuiltInFilterProfiles.all.any { it.id == linkedProfile.id } -> applyLocal()
            else -> showLinkedWarning = true
        }
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // Compose 1.7 measures a full-height dialog using screenHeightDp. On Android
    // 16 that includes system bars even when the native window is inset. Bound
    // the editor to the actual usable window, including in split-screen/rotation.
    val viewportHeight = remember(configuration, density) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val metrics = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            with(density) { (metrics.bounds.height() - insets.top - insets.bottom).toDp() }
        } else configuration.screenHeightDp.dp
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(Modifier.fillMaxWidth().height(viewportHeight), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = { TopAppBar(windowInsets = WindowInsets(0), title = { Column { Text(editorTitle); Text("Filter Studio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } }, actions = { TextButton(onClick = { showProfiles = true }) { Text("Copy from") } }) },
                bottomBar = { Surface(shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f)) { Text("$matchCount of ${alerts.size} live alerts match", style = MaterialTheme.typography.labelLarge); Text("Draft changes apply together", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button(onClick = applyDraft) { Text("Apply") } } } }
            ) { padding ->
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showProfiles = true }, modifier = Modifier.weight(1f)) { Text(if (assignment.mode == FilterAssignmentMode.LINKED) "Linked profile" else "Profiles") }
                        OutlinedButton(onClick = { showSave = true }, modifier = Modifier.weight(1f)) { Text("Save as profile") }
                    }
                    BasicRules(draft, catalog.areas.ifEmpty { AREA_FILTER_OPTIONS.filterNot { it == "All" } }, onEditDistanceOverrides = { showDistanceOverrides = true }) { draft = it }
                    Text("Advanced by alert type", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Enabled branches are alternatives. Rules inside a branch all need to match.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterAlertType.entries.forEach { type ->
                        TypeRuleRow(type, draft, onToggle = { enabled ->
                            val values = draft.alertTypes.normalizedValues.toMutableSet()
                            if (draft.alertTypes.mode == FilterSelectionMode.ALL) values += FilterAlertType.entries.map { normalizeFilterToken(it.name) }
                            if (enabled) values += normalizeFilterToken(type.name) else values -= normalizeFilterToken(type.name)
                            draft = draft.copy(alertTypes = FilterSelection.only(values))
                        }, onAdvanced = {
                            selector = when (type) {
                                FilterAlertType.SPAWN -> SelectorTarget.SPAWN; FilterAlertType.RARE -> SelectorTarget.RARE; FilterAlertType.HUNDO -> SelectorTarget.HUNDO; FilterAlertType.NUNDO -> SelectorTarget.NUNDO; FilterAlertType.PVP -> SelectorTarget.PVP; FilterAlertType.RAID -> SelectorTarget.RAID_SPECIES; FilterAlertType.ROCKET -> SelectorTarget.ROCKET
                                FilterAlertType.QUEST -> { showQuests = true; null }; else -> null
                            }
                        }, onSecondaryAdvanced = if (type == FilterAlertType.RAID) ({ selector = SelectorTarget.RAID_TIERS }) else null)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
    selector?.let { target ->
        val triple = when (target) {
            SelectorTarget.SPAWN -> Triple("Spawn species", draft.spawnSpecies, catalog.spawnSpecies); SelectorTarget.RARE -> Triple("Rare species", draft.rareSpecies, catalog.spawnSpecies); SelectorTarget.HUNDO -> Triple("Hundo species", draft.hundoSpecies, catalog.spawnSpecies); SelectorTarget.NUNDO -> Triple("Nundo species", draft.nundoSpecies, catalog.spawnSpecies); SelectorTarget.PVP -> Triple("PvP species", draft.pvpSpecies, catalog.spawnSpecies); SelectorTarget.RAID_SPECIES -> Triple("Raid species", draft.raidSpecies, catalog.raidSpecies); SelectorTarget.RAID_TIERS -> Triple("Raid tiers", draft.raidTiers, catalog.raidTiers); SelectorTarget.ROCKET -> Triple("Rocket types", draft.rocketTypes, catalog.rocketTypes)
        }
        SelectionDialog(triple.first, triple.third, triple.second, { selector = null }, artwork = artwork,
            initialQuery = selectorQueries[target].orEmpty(), onQueryChanged = { selectorQueries = selectorQueries + (target to it) }) { selection ->
            draft = when (target) { SelectorTarget.SPAWN -> draft.copy(spawnSpecies = selection); SelectorTarget.RARE -> draft.copy(rareSpecies = selection); SelectorTarget.HUNDO -> draft.copy(hundoSpecies = selection); SelectorTarget.NUNDO -> draft.copy(nundoSpecies = selection); SelectorTarget.PVP -> draft.copy(pvpSpecies = selection); SelectorTarget.RAID_SPECIES -> draft.copy(raidSpecies = selection); SelectorTarget.RAID_TIERS -> draft.copy(raidTiers = selection); SelectorTarget.ROCKET -> draft.copy(rocketTypes = selection) }; selector = null
        }
    }
    if (showQuests) QuestRulesDialog(draft.quests, catalog.quests, artwork, rewardThumbnails, { showQuests = false }) { draft = draft.copy(quests = it); showQuests = false }
    if (showDistanceOverrides) DistanceOverridesDialog(
        definition = draft,
        speciesCandidates = remember(catalog, artwork) { catalog.speciesLimitCandidates(artwork.keys) },
        artwork = artwork,
        onDismiss = { showDistanceOverrides = false }
    ) { draft = draft.copy(distanceOverrides = it); showDistanceOverrides = false }
    if (showProfiles) ProfilePickerDialog(document, { showProfiles = false }, onChoose = { profile, linked -> draft = profile.definition; linkedProfileId = profile.id.takeIf { linked }; showProfiles = false }, onDelete = viewModel::deleteFilterProfile, onSave = viewModel::saveFilterProfile, widgetConsumers = viewModel::linkedWidgetConsumers)
    if (showSave) NameDialog("Save profile", { showSave = false }) { viewModel.saveFilterProfile(it, draft); showSave = false }
    if (showLinkedWarning && linkedProfile != null) AlertDialog(
        onDismissRequest = { showLinkedWarning = false },
        title = { Text("Update linked profile?") },
        text = { Text("Editing ${linkedProfile.name} also changes: ${(document.consumersOf(linkedProfile.id).map { it.label } + viewModel.linkedWidgetConsumers(linkedProfile.id) + editorTitle).distinct().joinToString()}. These consumers stay linked.") },
        confirmButton = { Button(onClick = {
            if (widgetId != null) { viewModel.saveFilterProfile(linkedProfile.name, draft, linkedProfile.id); applyLink(linkedProfile.copy(definition = draft)) }
            else { viewModel.applyLinkedFilter(surface, linkedProfile, draft); onDismiss() }
        }) { Text("Apply to all") } },
        dismissButton = { TextButton(onClick = applyLocal) { Text("Only this surface") } }
    )
}

@Composable
private fun BasicRules(definition: FilterDefinition, areas: List<String>, onEditDistanceOverrides: () -> Unit, onChange: (FilterDefinition) -> Unit) {
    SettingsSection("Basic rules") {
        Text("Areas", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(definition.areas.mode == FilterSelectionMode.ALL, { onChange(definition.copy(areas = FilterSelection.All)) }, label = { Text("All") })
            FilterChip(definition.areas.mode == FilterSelectionMode.NONE, { onChange(definition.copy(areas = FilterSelection.None)) }, label = { Text("None") })
            (areas + definition.areas.values).distinctBy(::normalizeFilterToken).forEach { area -> FilterChip(definition.areas.mode == FilterSelectionMode.ONLY && definition.areas.contains(area), { val set = definition.areas.normalizedValues.toMutableSet(); val key = normalizeFilterToken(area); if (!set.add(key)) set.remove(key); onChange(definition.copy(areas = if (set.isEmpty()) FilterSelection.None else FilterSelection.only(set))) }, label = { Text(area) }) }
        }
        Text("Default distance — ${distanceLabel(definition.maxDistanceKm)}", style = MaterialTheme.typography.titleSmall)
        Slider(definition.maxDistanceKm.toFloat(), { onChange(definition.copy(maxDistanceKm = kotlin.math.round(it).toInt())) }, valueRange = 0f..MAX_FILTER_DISTANCE_KM.toFloat())
        val overrideCount = definition.distanceOverrides.ruleCount
        OutlinedButton(onClick = onEditDistanceOverrides, modifier = Modifier.fillMaxWidth()) {
            Text(if (overrideCount == 0) "Per-type and per-species limits" else "Per-type and per-species limits ($overrideCount)")
        }
        Text("Reachable on foot — ${TravelTime.label(definition.maxWalkingMinutes)}", style = MaterialTheme.typography.titleSmall)
        Text("Missing route or coordinates never hide an alert.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TravelTime.PRESET_MINUTES.forEach { minutes -> FilterChip(definition.maxWalkingMinutes == minutes, { onChange(definition.copy(maxWalkingMinutes = minutes)) }, label = { Text(TravelTime.label(minutes)) }) } }
    }
}

@Composable
private fun TypeRuleRow(type: FilterAlertType, definition: FilterDefinition, onToggle: (Boolean) -> Unit, onAdvanced: () -> Unit, onSecondaryAdvanced: (() -> Unit)?) {
    val enabled = definition.alertTypes.contains(type.name)
    val summary = when (type) {
        FilterAlertType.SPAWN -> definition.spawnSpecies.summary("species")
        FilterAlertType.RARE -> definition.rareSpecies.summary("species")
        FilterAlertType.HUNDO -> definition.hundoSpecies.summary("species")
        FilterAlertType.NUNDO -> definition.nundoSpecies.summary("species")
        FilterAlertType.PVP -> definition.pvpSpecies.summary("species")
        FilterAlertType.RAID -> "${definition.raidSpecies.summary("species")} • ${definition.raidTiers.summary("tiers") }"
        FilterAlertType.ROCKET -> definition.rocketTypes.summary("Rocket types")
        FilterAlertType.QUEST -> if (definition.quests.exactMode == FilterSelectionMode.ALL) "All quests" else "${definition.quests.exactPairs.size} exact • ${if (definition.quests.facetEnabled) "task + reward rule" else "no facets"}"
        else -> "All in this type"
    }
    Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.width(5.dp).height(52.dp), color = type.category().accentColor()) {}
            Switch(enabled, onToggle, modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = "Enable ${type.label}" })
            val typeLimit = definition.distanceOverrides.perType[type.name]
            Column(Modifier.weight(1f)) {
                Text(type.label, fontWeight = FontWeight.SemiBold)
                Text(
                    if (!enabled) "Disabled" else if (typeLimit != null) "$summary • ${distanceLabel(typeLimit)}" else summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (type in setOf(FilterAlertType.SPAWN, FilterAlertType.RARE, FilterAlertType.HUNDO, FilterAlertType.NUNDO, FilterAlertType.PVP, FilterAlertType.RAID, FilterAlertType.ROCKET, FilterAlertType.QUEST)) {
                TextButton(onClick = onAdvanced, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Edit ${type.label} filters" }) { Text(if (type == FilterAlertType.RAID) "Species" else "Edit") }
                onSecondaryAdvanced?.let { TextButton(onClick = it, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Raid tiers" }) { Text("Tiers") } }
            }
        }
    }
}

private fun FilterAlertType.category(): AlertCategory = when (this) { FilterAlertType.SPAWN -> AlertCategory.SPAWN; FilterAlertType.RAID -> AlertCategory.RAID; FilterAlertType.QUEST -> AlertCategory.QUEST; FilterAlertType.ROCKET -> AlertCategory.ROCKET; FilterAlertType.KECLEON -> AlertCategory.KECLEON; FilterAlertType.HUNDO -> AlertCategory.HUNDO; FilterAlertType.NUNDO -> AlertCategory.NUNDO; FilterAlertType.PVP -> AlertCategory.PVP; FilterAlertType.RARE -> AlertCategory.RARE; FilterAlertType.WEATHER -> AlertCategory.WEATHER; FilterAlertType.OTHER -> AlertCategory.GENERIC }

@Composable
internal fun SelectionDialog(title: String, candidates: List<String>, current: FilterSelection, onDismiss: () -> Unit, artwork: Map<String, String> = emptyMap(), initialQuery: String = "", onQueryChanged: (String) -> Unit = {}, onSave: (FilterSelection) -> Unit) {
    var mode by remember { mutableStateOf(current.mode) }; var selected by remember { mutableStateOf(current.normalizedValues) }; var query by rememberSaveable { mutableStateOf(initialQuery) }
    val availableKeys = remember(candidates) { candidates.map(::normalizeFilterToken).toSet() }
    val display = remember(candidates, current.values, query) { candidates.associateBy(::normalizeFilterToken).toMutableMap().apply { current.values.forEach { putIfAbsent(normalizeFilterToken(it), it) } }.values.filter { normalizeFilterToken(it).contains(normalizeFilterToken(query)) }.sortedWith(compareByDescending<String> { normalizeFilterToken(it) in current.normalizedValues }.thenBy { it }) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(12.dp), shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSelectionMode.entries.forEach { candidate ->
                    FilterChip(selected = mode == candidate, onClick = { mode = candidate }, label = {
                        Text(when (candidate) { FilterSelectionMode.ALL -> "All"; FilterSelectionMode.NONE -> "None"; FilterSelectionMode.ONLY -> "Selected (${selected.size})" })
                    })
                }
            }
            OutlinedTextField(query, { query = it; onQueryChanged(it) }, Modifier.fillMaxWidth(), label = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Text(if (mode == FilterSelectionMode.ONLY) "${selected.size} selected • selected entries first" else if (mode == FilterSelectionMode.NONE) "Nothing in this selector will match" else "Every value, including newly discovered entries", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (current.mode == FilterSelectionMode.ONLY && current.values.any { key -> candidates.none { normalizeFilterToken(it) == normalizeFilterToken(key) } }) Text("Unavailable selections are preserved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp))
            if (title.endsWith("species")) {
                // Compact cells fit roughly twice as many species per screen. Selection reads from
                // the border plus a corner badge instead of a full-size checkbox.
                LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    gridItems(display, key = { normalizeFilterToken(it) }) { value ->
                        val key = normalizeFilterToken(value)
                        val checked = mode == FilterSelectionMode.ALL || (mode == FilterSelectionMode.ONLY && key in selected)
                        val unavailable = key !in availableKeys
                        OutlinedCard(onClick = { selected = if (key in selected) selected - key else selected + key }, enabled = mode == FilterSelectionMode.ONLY, colors = CardDefaults.outlinedCardColors(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant), border = BorderStroke(if (checked) 2.dp else 1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.semantics { contentDescription = if (checked) "$value, selected" else value }) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    AsyncImage(artwork[key], contentDescription = null, modifier = Modifier.size(40.dp))
                                    if (checked) Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(value, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (unavailable) Text("Unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { items(display, key = { normalizeFilterToken(it) }) { value -> val key = normalizeFilterToken(value); val checked = mode == FilterSelectionMode.ALL || (mode == FilterSelectionMode.ONLY && key in selected); Row(Modifier.fillMaxWidth().clickable(enabled = mode == FilterSelectionMode.ONLY) { selected = if (key in selected) selected - key else selected + key }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, null); Text(value, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Cancel") }; Button(onClick = { onSave(when (mode) { FilterSelectionMode.ALL -> FilterSelection.All; FilterSelectionMode.NONE -> FilterSelection.None; FilterSelectionMode.ONLY -> FilterSelection.only(selected) }) }) { Text("Done") } }
        } }
    }
}

@Composable
internal fun QuestRulesDialog(current: QuestFilterRules, catalog: List<FilterCatalogQuest>, artwork: Map<String, String> = emptyMap(), rewardThumbnails: Map<String, String> = emptyMap(), onDismiss: () -> Unit, onSave: (QuestFilterRules) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }; var exact by remember { mutableStateOf(current.exactPairs) }; var tasks by remember { mutableStateOf(current.tasks.normalizedValues) }; var rewards by remember { mutableStateOf(current.rewards.normalizedValues) }; var query by rememberSaveable { mutableStateOf("") }
    var facetEnabled by remember { mutableStateOf(current.facetEnabled) }
    var exactMode by remember { mutableStateOf(current.exactMode) }
    var taskMode by remember { mutableStateOf(current.tasks.mode) }
    var rewardMode by remember { mutableStateOf(current.rewards.mode) }
    val unavailable = current.exactPairs.filter { pair -> catalog.none { it.taskKey == pair.taskKey && it.rewardKey == pair.rewardKey } }
        .map { FilterCatalogQuest("${it.taskKey}::${it.rewardKey}", it.taskKey, it.rewardKey, it.taskKey, it.rewardKey, false) }
    val allQuests = unavailable + catalog
    val speciesKeys = remember(artwork) { artwork.keys }
    val taskOptions = (catalog.map { it.task } + current.tasks.values).distinctBy(::normalizeFilterToken)
    val rewardOptions = (catalog.map { it.reward } + current.rewards.values).distinctBy(::normalizeFilterToken)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(10.dp), shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.padding(16.dp)) {
            Text("Quest rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            PrimaryTabRow(tab) { listOf("Exact quests", "Tasks", "Rewards").forEachIndexed { index, label -> Tab(tab == index, { tab = index }, text = { Text(label) }) } }
            if (tab == 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSelectionMode.entries.forEach { mode ->
                    FilterChip(exactMode == mode, { exactMode = mode }, label = { Text(when (mode) { FilterSelectionMode.ALL -> "All quests"; FilterSelectionMode.NONE -> "No exact pairs"; FilterSelectionMode.ONLY -> "Selected (${exact.size})" }) })
                }
            }
            if (tab != 0) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Use task + reward rule", Modifier.weight(1f)); Switch(facetEnabled, { facetEnabled = it; if (it && exactMode == FilterSelectionMode.ALL) exactMode = FilterSelectionMode.NONE }, modifier = Modifier.semantics { contentDescription = "Use task and reward facets" }) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterSelectionMode.entries.forEach { mode ->
                        FilterChip(selected = (if (tab == 1) taskMode else rewardMode) == mode, onClick = { facetEnabled = true; if (exactMode == FilterSelectionMode.ALL) exactMode = FilterSelectionMode.NONE; if (tab == 1) taskMode = mode else rewardMode = mode }, label = { Text(when (mode) { FilterSelectionMode.ALL -> "All"; FilterSelectionMode.NONE -> "None"; FilterSelectionMode.ONLY -> "Selected" }) })
                    }
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 10.dp), label = { Text("Search task or reward") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(Modifier.weight(1f)) { when (tab) {
                0 -> {
                    val groups = allQuests
                        .filter { it.task.contains(query, true) || it.reward.contains(query, true) }
                        .groupBy { it.rewardKey }
                        .entries
                        .sortedBy { it.value.firstOrNull()?.reward.orEmpty() }
                    items(groups, key = { it.key }) { (rewardKey, quests) ->
                        QuestRewardGroup(
                            rewardLabel = quests.firstOrNull()?.reward.orEmpty().ifBlank { rewardKey },
                            rewardKey = rewardKey,
                            quests = quests,
                            expandedByDefault = query.isNotBlank(),
                            rewardThumbnails = rewardThumbnails,
                            artwork = artwork,
                            knownSpecies = speciesKeys,
                            isSelected = { rule -> exactMode == FilterSelectionMode.ALL || (exactMode == FilterSelectionMode.ONLY && rule in exact) },
                            onToggle = { rule -> exactMode = FilterSelectionMode.ONLY; exact = if (rule in exact) exact - rule else exact + rule },
                            onToggleAll = { rules, select ->
                                exactMode = FilterSelectionMode.ONLY
                                exact = if (select) exact + rules else exact - rules.toSet()
                            }
                        )
                    }
                }
                1 -> items(taskOptions.filter { it.contains(query, true) }) { task -> val key = normalizeFilterToken(task); ChoiceRow(task, taskMode == FilterSelectionMode.ALL || (taskMode == FilterSelectionMode.ONLY && key in tasks)) { facetEnabled = true; if (exactMode == FilterSelectionMode.ALL) exactMode = FilterSelectionMode.NONE; taskMode = FilterSelectionMode.ONLY; tasks = if (key in tasks) tasks - key else tasks + key } }
                else -> items(rewardOptions.filter { it.contains(query, true) }) { reward -> val key = normalizeFilterToken(reward); ChoiceRow(reward, rewardMode == FilterSelectionMode.ALL || (rewardMode == FilterSelectionMode.ONLY && key in rewards)) { facetEnabled = true; if (exactMode == FilterSelectionMode.ALL) exactMode = FilterSelectionMode.NONE; rewardMode = FilterSelectionMode.ONLY; rewards = if (key in rewards) rewards - key else rewards + key } }
            } }
            Text("Exact pairs are alternatives to the task + reward facet rule.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Cancel") }; Button(onClick = { onSave(QuestFilterRules(exact, facetEnabled, FilterSelection(taskMode, tasks), FilterSelection(rewardMode, rewards), exactMode)) }) { Text("Done") } }
        } }
    }
}

/**
 * One reward with the tasks that grant it folded underneath. Grouping by reward matches how
 * these are actually chosen — you want a specific reward and do not care which task yields it.
 * The row icon is the same artwork the alert carries, so a reward looks identical here and in
 * the feed.
 */
@Composable
private fun QuestRewardGroup(
    rewardLabel: String,
    rewardKey: String,
    quests: List<FilterCatalogQuest>,
    expandedByDefault: Boolean,
    rewardThumbnails: Map<String, String>,
    artwork: Map<String, String>,
    knownSpecies: Set<String>,
    isSelected: (QuestPairRule) -> Boolean,
    onToggle: (QuestPairRule) -> Unit,
    onToggleAll: (List<QuestPairRule>, Boolean) -> Unit
) {
    var expanded by remember(rewardKey, expandedByDefault) { mutableStateOf(expandedByDefault) }
    val rules = remember(quests) { quests.map { QuestPairRule(it.taskKey, it.rewardKey) } }
    val selectedCount = rules.count(isSelected)
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuestRewardIcon(rewardLabel, rewardKey, rewardThumbnails, artwork, knownSpecies, size = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(rewardLabel, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "$selectedCount of ${rules.size} ${if (rules.size == 1) "task" else "tasks"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onToggleAll(rules, selectedCount < rules.size) }) {
                    Text(if (selectedCount < rules.size) "All" else "None")
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $rewardLabel" else "Expand $rewardLabel"
                )
            }
            AnimatedVisibility(expanded) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quests.forEachIndexed { index, quest ->
                        val rule = rules[index]
                        FilterChip(
                            selected = isSelected(rule),
                            onClick = { onToggle(rule) },
                            label = {
                                Column {
                                    Text(quest.task.ifBlank { quest.taskKey }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (!quest.active) Text("Previously seen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Prefers the artwork the alert itself carries (UICONS item/stardust/pokemon icon), falls back
 * to the species sprite, then to a generic icon for rewards that are not currently live.
 */
@Composable
private fun QuestRewardIcon(
    rewardLabel: String,
    rewardKey: String,
    rewardThumbnails: Map<String, String>,
    artwork: Map<String, String>,
    knownSpecies: Set<String>,
    size: androidx.compose.ui.unit.Dp
) {
    val thumbnail = rewardThumbnails[rewardKey]
        ?: questRewardSpeciesKey(rewardLabel, knownSpecies)?.let { artwork[it] }
    if (thumbnail != null) {
        AsyncImage(thumbnail, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(
            painterResource(questRewardIconRes(rewardLabel, knownSpecies)),
            contentDescription = null,
            Modifier.size(size * 0.7f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun questRewardIconRes(reward: String?, knownSpecies: Set<String>): Int = when (classifyQuestReward(reward, knownSpecies)) {
    QuestRewardKind.STARDUST -> R.drawable.ic_reward_stardust
    QuestRewardKind.MEGA_ENERGY -> R.drawable.ic_reward_mega_energy
    QuestRewardKind.CANDY -> R.drawable.ic_reward_candy
    QuestRewardKind.POKEMON -> R.drawable.ic_widget_pokeball
    QuestRewardKind.ITEM -> R.drawable.ic_reward_item
}

@Composable private fun ChoiceRow(label: String, checked: Boolean, active: Boolean = true, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, null); Column(Modifier.weight(1f)) { Text(label); if (!active) Text("Previously seen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) } } }

@Composable
internal fun ProfilePickerDialog(
    document: FilterStateDocument,
    onDismiss: () -> Unit,
    onChoose: (FilterProfile, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (String, FilterDefinition, String?) -> Unit,
    widgetConsumers: (String) -> List<String>
) {
    val profiles = BuiltInFilterProfiles.all + document.profiles
    var renaming by remember { mutableStateOf<FilterProfile?>(null) }
    var deleting by remember { mutableStateOf<FilterProfile?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profiles") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    val builtIn = BuiltInFilterProfiles.all.any { it.id == profile.id }
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        Text(if (builtIn) "Read-only starter • duplicate to edit" else "${profile.definition.advancedRuleCount} advanced rules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { onChoose(profile, true) }) { Text("Link") }
                            TextButton(onClick = { onChoose(profile, false) }) { Text("Copy") }
                            TextButton(onClick = { onSave("${profile.name} copy", profile.definition, null) }) { Text("Duplicate") }
                        }
                        if (!builtIn) Row {
                            TextButton(onClick = { renaming = profile }) { Text("Rename") }
                            TextButton(onClick = { deleting = profile }) { Text("Delete") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
    renaming?.let { profile -> NameDialog("Rename profile", { renaming = null }, initialValue = profile.name) { name -> onSave(name, profile.definition, profile.id); renaming = null } }
    deleting?.let { profile ->
        val consumers = document.consumersOf(profile.id).map { it.label } + widgetConsumers(profile.id)
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${profile.name}?") }, text = { Text(if (consumers.isEmpty()) "This profile is not linked to any surface." else "${consumers.joinToString()} will keep independent copies of these rules before the profile is deleted.") }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }, confirmButton = { Button(onClick = { onDelete(profile.id); deleting = null }) { Text("Delete profile") } })
    }
}

@Composable private fun NameDialog(title: String, onDismiss: () -> Unit, initialValue: String = "", onSave: (String) -> Unit) { var value by rememberSaveable { mutableStateOf(initialValue) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it.take(MAX_FILTER_PROFILE_NAME) }, label = { Text("Unique name") }, supportingText = { Text("${value.length}/$MAX_FILTER_PROFILE_NAME") }, singleLine = true) }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { Button(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("Save") } }) }

@Composable
private fun WidgetFiltersSection() {
    val context = LocalContext.current
    // getAppWidgetIds and the configuration store are binder/disk calls; keep them off the
    // composition thread so opening the hub never waits on them.
    val placed by produceState(initialValue = emptyList<Triple<Int, String, WidgetConfiguration>>(), context) {
        value = withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            buildList {
                manager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java)).forEach { add(Triple(it, "Alerts widget", WidgetConfigurationStore.get(context, it))) }
                manager.getAppWidgetIds(ComponentName(context, NearbyRadarWidgetProvider::class.java)).forEach { add(Triple(it, "Nearby radar", WidgetConfigurationStore.get(context, it))) }
            }
        }
    }
    SettingsSection("Widget instances") {
        if (placed.isEmpty()) Text("No widgets placed. Add one from the home screen to give it an independent filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        placed.forEach { (id, label, configuration) -> OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold); Text(if (configuration.filterAssignment == null) "Legacy rules • migrates when edited" else "Unified filter • instance #$id", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Customize") } } } }
    }
}
