@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.settings

import com.example.pokemonalertsv2.util.AppText
import androidx.compose.ui.res.stringResource
import com.example.pokemonalertsv2.ui.theme.Dimens
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
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
import com.example.pokemonalertsv2.widget.*

/**
 * The Filter Studio hub: the active filter set on top, then one card per place
 * that filters (Feed, Map, Notifications, Widgets) in the same layout, then the
 * reusable profiles. Each card says in plain words where its rules come from.
 */
@Composable
internal fun FiltersHubContent(
    viewModel: SettingsViewModel,
    onOpenFullNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val document by viewModel.filterStateDocument.collectAsStateWithLifecycle()
    val counts by viewModel.filterPreviewCounts.collectAsStateWithLifecycle()
    val requestedEditor by viewModel.requestedFilterEditor.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingSet by rememberSaveable { mutableStateOf(false) }
    var managingSets by rememberSaveable { mutableStateOf(false) }
    var profileTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var savingFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestedEditor) {
        requestedEditor?.let { editing = it.name; viewModel.consumeRequestedFilterEditor() }
    }
    val profiles = BuiltInFilterProfiles.all + document.profiles
    val activeSet = document.activeSet
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_filter_studio), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.filter_hub_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ActiveSetCard(
            name = activeSet.name,
            setCount = document.allSets().size,
            onSwitch = { choosingSet = true },
            onManage = { managingSets = true }
        )
        FilterSurface.entries.forEach { surface ->
            val assignment = document.assignment(surface)
            SurfaceCard(
                surface = surface,
                definition = assignment.resolve(document),
                profileName = profiles.firstOrNull { it.id == assignment.profileId }?.name?.takeIf { assignment.mode == FilterAssignmentMode.LINKED },
                matchCount = counts[surface] ?: 0,
                onEdit = { editing = surface.name },
                onCopyFrom = { from -> viewModel.copySurfaceFilter(from, surface) },
                onUseProfile = { profileTarget = surface.name },
                onSaveProfile = { savingFrom = surface.name }
            ) {
                if (surface == FilterSurface.WIDGETS) PlacedWidgets(viewModel)
            }
        }
        ProfilesCard(
            profiles = document.profiles,
            usage = { document.consumerCount(it) },
            onOpen = { showProfiles = true }
        )
        TextButton(onClick = onOpenFullNotificationSettings) {
            Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_notification_delivery_quiet_hours_and_vibration))
        }
    }
    editing?.let { name -> FilterSurface.entries.firstOrNull { it.name == name }?.let { FilterStudioDialog(it, viewModel) { editing = null } } }
    if (choosingSet) SetPickerDialog(
        sets = document.allSets(),
        activeId = activeSet.id,
        onDismiss = { choosingSet = false },
        onChoose = { set ->
            choosingSet = false
            viewModel.activateFilterSet(set.id)
            android.widget.Toast.makeText(context, AppText.get(R.string.filter_sets_switched, set.name), android.widget.Toast.LENGTH_SHORT).show()
        }
    )
    if (managingSets) ManageSetsDialog(
        sets = document.allSets(),
        activeId = activeSet.id,
        onDismiss = { managingSets = false },
        onCreate = viewModel::createFilterSet,
        onRename = viewModel::renameFilterSet,
        onDelete = viewModel::deleteFilterSet
    )
    profileTarget?.let { name ->
        val surface = FilterSurface.valueOf(name)
        ProfilePickerDialog(
            document, { profileTarget = null },
            onChoose = { profile, linked -> viewModel.applyProfile(surface, profile, linked); profileTarget = null },
            onDelete = viewModel::deleteFilterProfile, onSave = viewModel::saveFilterProfile, widgetConsumers = viewModel::linkedWidgetConsumers
        )
    }
    if (showProfiles) ProfilePickerDialog(
        document, { showProfiles = false }, onChoose = null,
        onDelete = viewModel::deleteFilterProfile, onSave = viewModel::saveFilterProfile, widgetConsumers = viewModel::linkedWidgetConsumers
    )
    savingFrom?.let { name ->
        val surface = FilterSurface.valueOf(name)
        NameDialog(stringResource(R.string.settings_save_profile), { savingFrom = null }) {
            viewModel.saveFilterProfile(it, document.assignment(surface).resolve(document)); savingFrom = null
        }
    }
}

@Composable
private fun ActiveSetCard(name: String, setCount: Int, onSwitch: () -> Unit, onManage: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Star, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_sets_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("filter_set_active_name"))
                    Text(stringResource(R.string.filter_sets_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSwitch, enabled = setCount > 1, modifier = Modifier.testTag("filter_set_switch")) { Text(stringResource(R.string.filter_sets_switch)) }
                OutlinedButton(onClick = onManage, modifier = Modifier.testTag("filter_set_manage")) { Text(stringResource(R.string.filter_sets_manage)) }
            }
        }
    }
}

/** What a definition does, as a few short chips: types, place and extra rules. */
@Composable
private fun definitionChips(definition: FilterDefinition): List<String> {
    val types = when (definition.alertTypes.mode) {
        FilterSelectionMode.ALL -> stringResource(R.string.alerts_all)
        FilterSelectionMode.NONE -> stringResource(R.string.alerts_none)
        FilterSelectionMode.ONLY -> definition.alertTypes.selectedCount.let { if (it == 1) stringResource(R.string.settings_1_alert_type) else stringResource(R.string.hunt_x_alert_types, it) }
    }
    return buildList {
        add(types)
        add(definition.areaSummary())
        if (definition.maxDistanceMeters > 0) add(distanceLabel(definition.maxDistanceMeters))
        if (definition.maxWalkingMinutes > 0) add(AppText.get(R.string.walk_minutes, definition.maxWalkingMinutes))
        definition.distanceOverrides.ruleCount.takeIf { it > 0 }?.let { add(if (it == 1) stringResource(R.string.settings_1_distance_override) else stringResource(R.string.settings_x_distance_overrides, it)) }
        if (definition.advancedRuleCount > 0) add(stringResource(R.string.settings_x_advanced, definition.advancedRuleCount).removePrefix(" • ").trim())
    }
}

@Composable
private fun SurfaceCard(
    surface: FilterSurface,
    definition: FilterDefinition,
    profileName: String?,
    matchCount: Int,
    onEdit: () -> Unit,
    onCopyFrom: (FilterSurface) -> Unit,
    onUseProfile: () -> Unit,
    onSaveProfile: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("filter_surface_${surface.name}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(surface.icon(), null, Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(surface.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        profileName?.let { stringResource(R.string.filter_surface_uses_profile, it) } ?: stringResource(R.string.filter_surface_own),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profileName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.filter_surface_more, surface.label)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        FilterSurface.entries.filter { it != surface }.forEach { other ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.filter_surface_copy_from, other.label)) }, onClick = { menu = false; onCopyFrom(other) })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_surface_use_profile)) }, onClick = { menu = false; onUseProfile() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_surface_save_profile)) }, onClick = { menu = false; onSaveProfile() })
                    }
                }
            }
            if (surface == FilterSurface.WIDGETS) {
                Text(stringResource(R.string.filter_surface_widgets_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                definitionChips(definition).forEach { chip ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(chip, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_surface_live, matchCount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onEdit, modifier = Modifier.testTag("filter_surface_edit_${surface.name}")) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.filter_surface_edit))
                }
            }
            extra()
        }
    }
}

private fun FilterSurface.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    FilterSurface.FEED -> Icons.Default.List
    FilterSurface.MAP -> Icons.Default.LocationOn
    FilterSurface.NOTIFICATIONS -> Icons.Default.Notifications
    FilterSurface.WIDGETS -> Icons.Default.Home
}

@Composable
private fun ProfilesCard(profiles: List<FilterProfile>, usage: (String) -> Int, onOpen: () -> Unit) {
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("filter_profiles")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${stringResource(R.string.settings_profiles)} · ${profiles.size + BuiltInFilterProfiles.all.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.filter_profiles_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                profiles.take(3).forEach { profile ->
                    val used = usage(profile.id)
                    Text(
                        "${profile.name} · ${if (used > 0) stringResource(R.string.filter_profile_used_by, used) else stringResource(R.string.filter_profile_unused)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetPickerDialog(sets: List<FilterSet>, activeId: String, onDismiss: () -> Unit, onChoose: (FilterSet) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_sets_choose)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sets.forEach { set ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChoose(set) }.testTag("filter_set_option_${set.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = set.id == activeId, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(set.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catchroutes_close)) } }
    )
}

@Composable
private fun ManageSetsDialog(
    sets: List<FilterSet>,
    activeId: String,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FilterSet?>(null) }
    var deleting by remember { mutableStateOf<FilterSet?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_sets_manage_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sets.forEach { set ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(set.name, style = MaterialTheme.typography.bodyLarge)
                            if (set.id == activeId) Text(stringResource(R.string.filter_sets_active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { renaming = set }) { Icon(Icons.Default.Edit, stringResource(R.string.filter_sets_rename)) }
                        IconButton(onClick = { deleting = set }, enabled = sets.size > 1) { Icon(Icons.Default.Delete, stringResource(R.string.filter_sets_delete)) }
                    }
                }
                Text(stringResource(R.string.filter_sets_limit, MAX_FILTER_SETS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catchroutes_close)) } },
        confirmButton = {
            Button(onClick = { creating = true }, enabled = sets.size < MAX_FILTER_SETS, modifier = Modifier.testTag("filter_set_new")) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.filter_sets_new))
            }
        }
    )
    if (creating) NewSetDialog(onDismiss = { creating = false }) { name, copy -> onCreate(name, copy); creating = false; onDismiss() }
    renaming?.let { set -> NameDialog(stringResource(R.string.filter_sets_rename), { renaming = null }, initialValue = set.name, maxLength = MAX_FILTER_SET_NAME) { onRename(set.id, it); renaming = null } }
    deleting?.let { set ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.filter_sets_delete)) },
            text = { Text(stringResource(R.string.filter_sets_delete_body, set.name)) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } },
            confirmButton = { Button(onClick = { onDelete(set.id); deleting = null }) { Text(stringResource(R.string.filter_sets_delete)) } }
        )
    }
}

@Composable
private fun NewSetDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var copyActive by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_sets_new)) },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it.take(MAX_FILTER_SET_NAME) },
                    label = { Text(stringResource(R.string.filter_sets_name)) },
                    supportingText = { Text("${name.length}/$MAX_FILTER_SET_NAME") },
                    singleLine = true,
                    modifier = Modifier.testTag("filter_set_name_field")
                )
                listOf(true to R.string.filter_sets_new_copy, false to R.string.filter_sets_new_empty).forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { copyActive = value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = copyActive == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } },
        confirmButton = { Button(onClick = { onCreate(name.trim(), copyActive) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("filter_set_create")) { Text(stringResource(R.string.catchroutes_save)) } }
    )
}

@Composable
internal fun FilterStudioDialog(surface: FilterSurface, viewModel: SettingsViewModel, widgetId: Int? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val document by viewModel.filterStateDocument.collectAsStateWithLifecycle()
    val catalog by viewModel.filterCatalog.collectAsStateWithLifecycle()
    val species by viewModel.filterSpecies.collectAsStateWithLifecycle()
    val artwork = remember(species) { speciesArtwork(species) }
    val alerts by viewModel.filterableAlerts.collectAsStateWithLifecycle()
    val rewardThumbnails by viewModel.questRewardThumbnails.collectAsStateWithLifecycle()
    val contexts by viewModel.filterPreviewContexts.collectAsStateWithLifecycle()
    val legacyArea by viewModel.selectedArea.collectAsStateWithLifecycle()
    val legacyDistance by viewModel.maxDistance.collectAsStateWithLifecycle()
    val widgetConfiguration = remember(widgetId) { widgetId?.let { WidgetConfigurationStore.get(context, it) } }
    val assignment = widgetConfiguration?.let {
        when {
            it.followSet -> document.widgets
            else -> it.filterAssignment ?: FilterAssignment.local(it.legacyFilterDefinition(legacyArea, legacyDistance))
        }
    } ?: document.assignment(surface)
    val editorTitle = widgetId?.let { "Widget #$it" }
        ?: stringResource(R.string.filter_studio_title, surface.label, stringResource(R.string.filter_sets_chip, document.activeSet.name))
    var draft by remember(surface, assignment) { mutableStateOf(assignment.resolve(document)) }
    var linkedProfileId by remember(surface, assignment) { mutableStateOf(assignment.profileId.takeIf { assignment.mode == FilterAssignmentMode.LINKED }) }
    var showLinkedWarning by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showQuests by remember { mutableStateOf(false) }
    var showDistanceOverrides by remember { mutableStateOf(false) }
    val matchCount = remember(alerts, draft, contexts) { alerts.count { AlertFilterMatcher.matches(it, draft, contexts[it.uniqueId] ?: FilterMatchContext()) } }
    val linkedProfile = (BuiltInFilterProfiles.all + document.profiles).firstOrNull { it.id == linkedProfileId }
    val applyLocal: () -> Unit = {
        if (widgetId != null && widgetConfiguration != null) {
            WidgetConfigurationStore.save(context, widgetId, widgetConfiguration.copy(filterAssignment = FilterAssignment.local(draft), followSet = false))
            AlertsWidgetProvider.requestUpdate(context)
        } else viewModel.applySurfaceFilter(surface, draft)
        onDismiss()
    }
    val applyLink: (FilterProfile) -> Unit = { profile ->
        if (widgetId != null && widgetConfiguration != null) {
            WidgetConfigurationStore.save(context, widgetId, widgetConfiguration.copy(filterAssignment = FilterAssignment.linked(profile), followSet = false))
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
                topBar = { TopAppBar(windowInsets = WindowInsets(0), title = { Column { Text(editorTitle); Text(stringResource(R.string.settings_filter_studio), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } }, actions = { TextButton(onClick = { showProfiles = true }) { Text(stringResource(R.string.settings_copy_from)) } }) },
                bottomBar = { Surface(shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.settings_x_of_x_live_alerts_match, matchCount, alerts.size), style = MaterialTheme.typography.labelLarge); Text(stringResource(R.string.settings_draft_changes_apply_together), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button(onClick = applyDraft) { Text(stringResource(R.string.settings_apply)) } } } }
            ) { padding ->
                val body = StudioBodyState(
                    draft = draft,
                    onDraftChange = { draft = it },
                    catalog = catalog,
                    artwork = artwork,
                    rewardThumbnails = rewardThumbnails,
                    matchCount = matchCount,
                    totalCount = alerts.size,
                    areas = catalog.areas.ifEmpty { AREA_FILTER_OPTIONS.filterNot { it == "All" } },
                    profileSummary = linkedProfile?.let { stringResource(R.string.filter_studio_profile_banner, it.name, document.consumerCount(it.id).coerceAtLeast(1)) }
                        ?: stringResource(R.string.filter_surface_own),
                    openProfiles = { showProfiles = true },
                    saveProfile = { showSave = true },
                    openQuestRules = { showQuests = true },
                    openDistanceOverrides = { showDistanceOverrides = true }
                )
                Box(Modifier.padding(padding).fillMaxSize()) { StudioTabsBody(body) }
            }
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
    if (showSave) NameDialog(stringResource(R.string.settings_save_profile), { showSave = false }) { viewModel.saveFilterProfile(it, draft); showSave = false }
    if (showLinkedWarning && linkedProfile != null) AlertDialog(
        onDismissRequest = { showLinkedWarning = false },
        title = { Text(stringResource(R.string.filter_update_profile_title)) },
        text = { Text(stringResource(R.string.settings_editing_x_also_changes_x_these, linkedProfile.name, (document.consumersOf(linkedProfile.id).map { it.label } + viewModel.linkedWidgetConsumers(linkedProfile.id) + editorTitle).distinct().joinToString())) },
        confirmButton = { Button(onClick = {
            if (widgetId != null) { viewModel.saveFilterProfile(linkedProfile.name, draft, linkedProfile.id); applyLink(linkedProfile.copy(definition = draft)) }
            else { viewModel.applyLinkedFilter(surface, linkedProfile, draft); onDismiss() }
        }) { Text(stringResource(R.string.filter_update_profile_all)) } },
        dismissButton = { TextButton(onClick = applyLocal) { Text(stringResource(R.string.filter_update_profile_only)) } }
    )
}

/** Which areas an alert must be in: All, None, or the areas picked. */
@Composable
internal fun AreaChipsEditor(definition: FilterDefinition, areas: List<String>, onChange: (FilterDefinition) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(definition.areas.mode == FilterSelectionMode.ALL, { onChange(definition.copy(areas = FilterSelection.All)) }, label = { Text(stringResource(R.string.alerts_all)) })
        FilterChip(definition.areas.mode == FilterSelectionMode.NONE, { onChange(definition.copy(areas = FilterSelection.None)) }, label = { Text(stringResource(R.string.alerts_none)) })
        (areas + definition.areas.values).distinctBy(::normalizeFilterToken).forEach { area ->
            val only = definition.areas.mode == FilterSelectionMode.ONLY
            val picked = only && definition.areas.contains(area)
            // A zone is already covered when its city is picked ("Darmstadt" includes
            // "Darmstadt-North"), so it shows as included and cannot be toggled on its own.
            val impliedByCity = only && !picked && AlertFilterMatcher.matchesArea(definition.areas, area)
            FilterChip(
                selected = picked || impliedByCity,
                enabled = !impliedByCity,
                onClick = { val set = definition.areas.normalizedValues.toMutableSet(); val key = normalizeFilterToken(area); if (!set.add(key)) set.remove(key); onChange(definition.copy(areas = if (set.isEmpty()) FilterSelection.None else FilterSelection.only(set))) },
                label = { Text(area) },
                leadingIcon = if (picked || impliedByCity) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}

/** "All areas", "Darmstadt", "3 areas" or "No areas". */
internal fun FilterDefinition.areaSummary(): String = when (areas.mode) {
    FilterSelectionMode.ALL -> AppText.get(R.string.history_all_areas)
    FilterSelectionMode.NONE -> AppText.get(R.string.settings_no_areas)
    FilterSelectionMode.ONLY -> if (areas.selectedCount == 1) areas.values.first() else "${areas.selectedCount} areas"
}

@Composable
internal fun SelectionDialog(title: String, candidates: List<String>, current: FilterSelection, onDismiss: () -> Unit, artwork: Map<String, String> = emptyMap(), initialQuery: String = "", onQueryChanged: (String) -> Unit = {}, onSave: (FilterSelection) -> Unit) {
    var mode by remember { mutableStateOf(current.mode) }
    var selected by remember { mutableStateOf(current.normalizedValues) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val isSpecies = remember(title) { title.contains("species", ignoreCase = true) }
    var sortOrder by rememberSaveable { mutableStateOf(SpeciesSortOrder.DEX_NUMBER) }

    fun extractDex(key: String): Int {
        val url = artwork[key] ?: return Int.MAX_VALUE
        val match = Regex("""/(\d+)\.png""").find(url) ?: return Int.MAX_VALUE
        return match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
    }

    // Normalized keys are computed once per candidate list; filtering and sorting for the
    // search query then reuse them instead of re-normalizing hundreds of species per keystroke.
    val normalizedCandidates = remember(candidates, current.values) {
        candidates.associateBy(::normalizeFilterToken).toMutableMap().apply {
            current.values.forEach { key -> putIfAbsent(normalizeFilterToken(key), key) }
        }.map { (key, value) -> key to value }
    }
    val availableKeys = remember(normalizedCandidates) { normalizedCandidates.mapTo(mutableSetOf()) { it.first } }
    val queryKey = remember(query) { normalizeFilterToken(query) }
    val display = remember(normalizedCandidates, current.normalizedValues, queryKey, isSpecies, sortOrder, artwork) {
        normalizedCandidates
            .filter { (key, _) ->
                key.contains(queryKey) || (isSpecies && extractDex(key).let { it != Int.MAX_VALUE && it.toString().contains(queryKey) })
            }
            .sortedWith(
                compareByDescending<Pair<String, String>> { (key, _) -> key in current.normalizedValues }
                    .thenComparing { (key, _) ->
                        if (isSpecies && sortOrder == SpeciesSortOrder.DEX_NUMBER) {
                            extractDex(key)
                        } else {
                            Int.MAX_VALUE
                        }
                    }
                    .thenBy { (_, value) -> value.lowercase() }
            )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = Dimens.ReadableContentWidth).fillMaxWidth().fillMaxHeight(.9f).padding(12.dp), shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (isSpecies) {
                    FilterChip(
                        selected = true,
                        onClick = {
                            sortOrder = if (sortOrder == SpeciesSortOrder.DEX_NUMBER) SpeciesSortOrder.NAME_AZ else SpeciesSortOrder.DEX_NUMBER
                        },
                        label = { Text(stringResource(R.string.alerts_sort_x, sortOrder.label)) }
                    )
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSelectionMode.entries.forEach { candidate ->
                    FilterChip(selected = mode == candidate, onClick = { mode = candidate }, label = {
                        Text(when (candidate) { FilterSelectionMode.ALL -> "All"; FilterSelectionMode.NONE -> "None"; FilterSelectionMode.ONLY -> stringResource(R.string.settings_selected_x, selected.size) })
                    })
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isSpecies) stringResource(R.string.alerts_search_name_or_dex) else stringResource(R.string.alerts_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            Text(if (mode == FilterSelectionMode.ONLY) stringResource(R.string.settings_x_selected_selected_entries_first, selected.size) else if (mode == FilterSelectionMode.NONE) stringResource(R.string.settings_nothing_in_this_selector_will_match) else stringResource(R.string.settings_every_value_including_newly_discovered_entries), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (current.mode == FilterSelectionMode.ONLY && current.values.any { key -> candidates.none { normalizeFilterToken(it) == normalizeFilterToken(key) } }) Text(stringResource(R.string.settings_unavailable_selections_are_preserved), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp))
            if (isSpecies) {
                // Compact cells fit roughly twice as many species per screen. Selection reads from
                // the border plus a corner badge instead of a full-size checkbox.
                LazyVerticalGrid(columns = GridCells.Adaptive(72.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    gridItems(display, key = { it.first }) { (key, value) ->
                        val checked = mode == FilterSelectionMode.ALL || (mode == FilterSelectionMode.ONLY && key in selected)
                        val unavailable = key !in availableKeys
                        val dexNum = extractDex(key)
                        OutlinedCard(onClick = { selected = if (key in selected) selected - key else selected + key }, enabled = mode == FilterSelectionMode.ONLY, colors = CardDefaults.outlinedCardColors(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant), border = BorderStroke(if (checked) 2.dp else 1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.semantics { contentDescription = if (checked) AppText.get(R.string.settings_x_selected, value) else value }) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    AsyncImage(artwork[key], contentDescription = null, modifier = Modifier.size(40.dp))
                                    if (checked) Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                if (dexNum != Int.MAX_VALUE) {
                                    Text("#${dexNum.toString().padStart(3, '0')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Text(value, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (unavailable) Text(stringResource(R.string.alerts_unavailable), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { items(display, key = { it.first }) { (key, value) -> val checked = mode == FilterSelectionMode.ALL || (mode == FilterSelectionMode.ONLY && key in selected); Row(Modifier.fillMaxWidth().clickable(enabled = mode == FilterSelectionMode.ONLY) { selected = if (key in selected) selected - key else selected + key }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, null); Text(value, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) }; Button(onClick = { onSave(when (mode) { FilterSelectionMode.ALL -> FilterSelection.All; FilterSelectionMode.NONE -> FilterSelection.None; FilterSelectionMode.ONLY -> FilterSelection.only(selected) }) }) { Text(stringResource(R.string.catchroutes_done)) } }
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
        Surface(Modifier.widthIn(max = Dimens.ReadableContentWidth).fillMaxWidth().fillMaxHeight(.92f).padding(10.dp), shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_quest_rules), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            PrimaryTabRow(tab) { listOf(com.example.pokemonalertsv2.util.AppText.get(com.example.pokemonalertsv2.R.string.i18n_quest_tab_exact), com.example.pokemonalertsv2.util.AppText.get(com.example.pokemonalertsv2.R.string.i18n_quest_tab_tasks), com.example.pokemonalertsv2.util.AppText.get(com.example.pokemonalertsv2.R.string.i18n_quest_tab_rewards)).forEachIndexed { index, label -> Tab(tab == index, { tab = index }, text = { Text(label) }) } }
            if (tab == 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSelectionMode.entries.forEach { mode ->
                    FilterChip(exactMode == mode, { exactMode = mode }, label = { Text(when (mode) { FilterSelectionMode.ALL -> "All quests"; FilterSelectionMode.NONE -> "No exact pairs"; FilterSelectionMode.ONLY -> stringResource(R.string.settings_selected_x, exact.size) }) })
                }
            }
            if (tab != 0) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.settings_use_task_reward_rule), Modifier.weight(1f)); Switch(facetEnabled, { facetEnabled = it; if (it && exactMode == FilterSelectionMode.ALL) exactMode = FilterSelectionMode.NONE }, modifier = Modifier.semantics { contentDescription = com.example.pokemonalertsv2.util.AppText.get(com.example.pokemonalertsv2.R.string.i18n_quest_use_facets) }) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterSelectionMode.entries.forEach { mode ->
                        FilterChip(selected = (if (tab == 1) taskMode else rewardMode) == mode, onClick = { facetEnabled = true; if (exactMode == FilterSelectionMode.ALL) exactMode = FilterSelectionMode.NONE; if (tab == 1) taskMode = mode else rewardMode = mode }, label = { Text(when (mode) { FilterSelectionMode.ALL -> "All"; FilterSelectionMode.NONE -> "None"; FilterSelectionMode.ONLY -> stringResource(R.string.alerts_selected) }) })
                    }
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 10.dp), label = { Text(stringResource(R.string.settings_search_task_or_reward)) }, leadingIcon = { Icon(Icons.Default.Search, null) })
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
            Text(stringResource(R.string.settings_exact_pairs_are_alternatives_to_the), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) }; Button(onClick = { onSave(QuestFilterRules(exact, facetEnabled, FilterSelection(taskMode, tasks), FilterSelection(rewardMode, rewards), exactMode)) }) { Text(stringResource(R.string.catchroutes_done)) } }
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
                    Text(if (selectedCount < rules.size) stringResource(R.string.alerts_all) else stringResource(R.string.alerts_none))
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(R.string.settings_collapse_x, rewardLabel) else stringResource(R.string.settings_expand_x, rewardLabel)
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
                                    if (!quest.active) Text(stringResource(R.string.settings_previously_seen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
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
internal fun QuestRewardIcon(
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

@Composable private fun ChoiceRow(label: String, checked: Boolean, active: Boolean = true, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, null); Column(Modifier.weight(1f)) { Text(label); if (!active) Text(stringResource(R.string.settings_previously_seen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) } } }

@Composable
internal fun ProfilePickerDialog(
    document: FilterStateDocument,
    onDismiss: () -> Unit,
    onChoose: ((FilterProfile, Boolean) -> Unit)?,
    onDelete: (String) -> Unit,
    onSave: (String, FilterDefinition, String?) -> Unit,
    widgetConsumers: (String) -> List<String>
) {
    val profiles = BuiltInFilterProfiles.all + document.profiles
    var renaming by remember { mutableStateOf<FilterProfile?>(null) }
    var deleting by remember { mutableStateOf<FilterProfile?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profiles)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    val builtIn = BuiltInFilterProfiles.all.any { it.id == profile.id }
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        Text(if (builtIn) stringResource(R.string.settings_read_only_starter_duplicate_to_edit) else stringResource(R.string.settings_x_advanced_rules, profile.definition.advancedRuleCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            if (onChoose != null) {
                                TextButton(onClick = { onChoose(profile, true) }) { Text(stringResource(R.string.filter_profile_use)) }
                                TextButton(onClick = { onChoose(profile, false) }) { Text(stringResource(R.string.filter_profile_copy_rules)) }
                            }
                            TextButton(onClick = { onSave("${profile.name} copy", profile.definition, null) }) { Text(stringResource(R.string.settings_duplicate)) }
                        }
                        if (!builtIn) Row {
                            TextButton(onClick = { renaming = profile }) { Text(stringResource(R.string.hunt_rename)) }
                            TextButton(onClick = { deleting = profile }) { Text(AppText.get(R.string.catchroutes_delete)) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catchroutes_close)) } }
    )
    renaming?.let { profile -> NameDialog(stringResource(R.string.settings_rename_profile), { renaming = null }, initialValue = profile.name) { name -> onSave(name, profile.definition, profile.id); renaming = null } }
    deleting?.let { profile ->
        val consumers = document.consumersOf(profile.id).map { it.label } + widgetConsumers(profile.id)
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(AppText.get(R.string.catchroutes_delete_x, profile.name)) }, text = { Text(if (consumers.isEmpty()) stringResource(R.string.settings_this_profile_is_not_linked_to) else stringResource(R.string.settings_x_will_keep_independent_copies_of, consumers.joinToString())) }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }, confirmButton = { Button(onClick = { onDelete(profile.id); deleting = null }) { Text(AppText.get(R.string.settings_delete_profile)) } })
    }
}

@Composable private fun NameDialog(title: String, onDismiss: () -> Unit, initialValue: String = "", maxLength: Int = MAX_FILTER_PROFILE_NAME, onSave: (String) -> Unit) { var value by rememberSaveable { mutableStateOf(initialValue) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it.take(maxLength) }, label = { Text(stringResource(R.string.settings_unique_name)) }, supportingText = { Text("${value.length}/$maxLength") }, singleLine = true) }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }, confirmButton = { Button(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.catchroutes_save)) } }) }

/** Every placed widget, with whether it follows the active set or keeps its own filter. */
@Composable
private fun PlacedWidgets(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    // getAppWidgetIds and the configuration store are binder/disk calls; keep them off the
    // composition thread so opening the hub never waits on them.
    val placed by produceState(initialValue = emptyList<Triple<Int, String, WidgetConfiguration>>(), context) {
        value = withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            buildList {
                manager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java)).forEach { add(Triple(it, AppText.get(R.string.filter_widgets_alerts), WidgetConfigurationStore.get(context, it))) }
                manager.getAppWidgetIds(ComponentName(context, NearbyRadarWidgetProvider::class.java)).forEach { add(Triple(it, AppText.get(R.string.filter_widgets_radar), WidgetConfigurationStore.get(context, it))) }
            }
        }
    }
    // The switch answers at once; the store write lands a moment later.
    var follows by remember { mutableStateOf(mapOf<Int, Boolean>()) }
    HorizontalDivider()
    Text(stringResource(R.string.filter_widgets_placed), style = MaterialTheme.typography.labelLarge)
    if (placed.isEmpty()) Text(stringResource(R.string.filter_widgets_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    placed.forEach { (id, label, configuration) ->
        val follow = follows[id] ?: configuration.followSet
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$label #$id", style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (follow) R.string.filter_widgets_follows else R.string.filter_surface_own),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(stringResource(R.string.settings_customize)) }
            Switch(
                checked = follow,
                onCheckedChange = { follows = follows + (id to it); viewModel.setWidgetFollowsSet(id, it) },
                modifier = Modifier.testTag("filter_widget_follow_$id").semantics { contentDescription = AppText.get(R.string.filter_widgets_follow_switch) }
            )
        }
    }
}
