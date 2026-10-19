@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterCatalogQuest
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.QuestFilterRules
import com.example.pokemonalertsv2.data.QuestPairRule
import com.example.pokemonalertsv2.ui.settings.QuestRewardIcon
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * Which side of a quest the tab is organised by. Each side's detail sheet lists the other one:
 * a reward's tasks, or a task's rewards.
 */
private enum class QuestView(val label: String, val unit: String, val other: String) {
    REWARDS("Rewards", "reward", "task"),
    TASKS("Tasks", "task", "reward")
}

/** One reward or one task, with every quest pairing it with the other side, index for index. */
private class QuestGroup(
    val key: String,
    val label: String,
    val active: Boolean,
    val members: List<FilterCatalogQuest>,
    val rules: List<QuestPairRule>
)

private fun groupQuests(quests: List<FilterCatalogQuest>, view: QuestView): List<QuestGroup> =
    quests.groupBy { if (view == QuestView.REWARDS) it.rewardKey else it.taskKey }
        .map { (key, members) ->
            val first = members.first()
            QuestGroup(
                key = key,
                label = (if (view == QuestView.REWARDS) first.reward else first.task).ifBlank { key },
                active = members.any { it.active },
                members = members,
                rules = members.map { QuestPairRule(it.taskKey, it.rewardKey) }
            )
        }
        .sortedWith(compareByDescending<QuestGroup> { it.active }.thenBy { it.label.lowercase() })

/** "3 tasks", "1 of 3 rewards" or "Not live": how much of a group is on the map. */
private fun countLabel(group: QuestGroup, selectedCount: Int, noun: String): String {
    val total = group.rules.size
    return when {
        !group.active -> "Not live"
        selectedCount in 1 until total -> "$selectedCount of $total ${noun}s"
        total == 1 -> "1 $noun"
        else -> "$total ${noun}s"
    }
}

/**
 * Field research chosen in place, by reward or by task, in one grid of tiles. By reward: you
 * usually want a reward and rarely care which task yields it. By task: each task with the rewards
 * it can give, for the tasks quick enough to be worth any reward. Tapping a tile
 * shows or hides every quest in it; holding it, or its tune button, picks the other side one by
 * one. Task/reward rules stay in the full editor behind [onOpenRules].
 */
@Composable
internal fun QuestRewardGrid(
    rules: QuestFilterRules,
    quests: List<FilterCatalogQuest>,
    rewardThumbnails: Map<String, String>,
    artwork: Map<String, String>,
    onRulesChange: (QuestFilterRules) -> Unit,
    onOpenRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    var view by rememberSaveable { mutableStateOf(QuestView.REWARDS) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickFor by rememberSaveable { mutableStateOf<String?>(null) }
    val knownSpecies = remember(artwork) { artwork.keys }
    val groups = remember(quests, view) { groupQuests(quests, view) }
    val universe = remember(quests) { quests.map { QuestPairRule(it.taskKey, it.rewardKey) } }
    val selected = rules.selectedPairs(universe)
    val shown = remember(groups, query) {
        if (query.isBlank()) groups
        else groups.filter { group ->
            group.label.contains(query, ignoreCase = true) ||
                group.members.any { it.task.contains(query, ignoreCase = true) || it.reward.contains(query, ignoreCase = true) }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize().testTag("map_panel_list"),
        contentPadding = PaddingValues(start = PanelGutter, end = PanelGutter, bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
                QuestView.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = view == entry,
                        onClick = {
                            view = entry
                            pickFor = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = QuestView.entries.size),
                        modifier = Modifier.testTag("quest_view_${entry.name.lowercase()}")
                    ) { Text("By ${entry.unit}") }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search rewards or tasks") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (rules.exactMode) {
                        FilterSelectionMode.ALL -> if (rules.facetEnabled) "Rule-based selection" else "All quests shown"
                        FilterSelectionMode.NONE -> "No quests picked"
                        FilterSelectionMode.ONLY -> "${selected.size} of ${universe.size} quests"
                    } + " · hold a ${view.unit} for its ${view.other}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onRulesChange(rules.copy(exactPairs = emptySet(), exactMode = FilterSelectionMode.ALL)) }) {
                    Text("All")
                }
                TextButton(onClick = { onRulesChange(rules.copy(exactPairs = emptySet(), exactMode = FilterSelectionMode.NONE)) }) {
                    Text("None")
                }
            }
        }
        if (groups.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Quests load with the next refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.lg)
                )
            }
        }
        items(shown, key = { "${view.name}:${it.key}" }) { group ->
            QuestTile(
                group = group,
                view = view,
                selectedCount = group.rules.count { it in selected },
                rewardThumbnails = rewardThumbnails,
                artwork = artwork,
                knownSpecies = knownSpecies,
                onClick = { onRulesChange(rules.withGroupToggled(group.rules, universe)) },
                onOpenPairs = { pickFor = group.key }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Text("Task & reward rules…")
            }
        }
    }

    groups.firstOrNull { it.key == pickFor }?.let { group ->
        QuestPairsSheet(
            group = group,
            view = view,
            selected = selected,
            rewardThumbnails = rewardThumbnails,
            artwork = artwork,
            knownSpecies = knownSpecies,
            onSetPairs = { pairs, show -> onRulesChange(rules.withPairs(pairs, show, universe)) },
            onDismiss = { pickFor = null }
        )
    }
}

/**
 * One reward or one task as a tile, so both views share the grid. A reward shows its icon; a task
 * shows the first rewards it can give. Tap shows or hides every quest in it; holding it, or the
 * tune button, picks the other side one by one.
 */
@Composable
private fun QuestTile(
    group: QuestGroup,
    view: QuestView,
    selectedCount: Int,
    rewardThumbnails: Map<String, String>,
    artwork: Map<String, String>,
    knownSpecies: Set<String>,
    onClick: () -> Unit,
    onOpenPairs: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val byReward = view == QuestView.REWARDS
    val all = selectedCount == group.rules.size
    val some = selectedCount > 0
    Surface(
        modifier = Modifier
            .heightIn(min = 112.dp)
            .alpha(if (group.active) 1f else 0.6f)
            .testTag(if (byReward) "quest_reward_${group.key}" else "quest_task_tile_${group.key}"),
        shape = RoundedCornerShape(14.dp),
        color = if (some) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surface,
        border = BorderStroke(if (some) 2.dp else 1.dp, if (some) scheme.primary else scheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = if (all) "Hide this ${view.unit}" else "Show this ${view.unit}",
                    onLongClickLabel = "Pick ${view.other}s",
                    onLongClick = onOpenPairs,
                    onClick = onClick
                )
                .semantics {
                    contentDescription = "${group.label}, ${if (all) "shown" else if (some) "partly shown" else "hidden"}"
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                    if (byReward) {
                        QuestRewardIcon(group.label, group.key, rewardThumbnails, artwork, knownSpecies, size = 40.dp)
                    } else {
                        // A task has no picture of its own, so it shows the first rewards it gives.
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            group.members.take(MaxRewardPreview).forEach { quest ->
                                QuestRewardIcon(quest.reward.ifBlank { quest.rewardKey }, quest.rewardKey, rewardThumbnails, artwork, knownSpecies, size = 28.dp)
                            }
                        }
                    }
                }
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = if (byReward) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = countLabel(group, selectedCount, view.other),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (some) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (some) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                )
            }
            // The visible way into the other side, for anyone who does not think to hold the tile.
            IconButton(
                onClick = onOpenPairs,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(36.dp)
                    .testTag(if (byReward) "quest_tasks_${group.key}" else "quest_rewards_${group.key}")
            ) {
                Icon(
                    painterResource(R.drawable.ic_tune),
                    contentDescription = "Pick ${view.other}s for ${group.label}",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Reward icons a task tile previews, two so they clear the corner buttons; its count says the rest. */
private const val MaxRewardPreview = 2

/**
 * One group's other side, each with its own box: a reward's tasks, for when only some are worth
 * the walk, or a task's rewards, for when only some are worth doing it for.
 */
@Composable
private fun QuestPairsSheet(
    group: QuestGroup,
    view: QuestView,
    selected: Set<QuestPairRule>,
    rewardThumbnails: Map<String, String>,
    artwork: Map<String, String>,
    knownSpecies: Set<String>,
    onSetPairs: (List<QuestPairRule>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val byReward = view == QuestView.REWARDS
    val chosen = group.rules.count { it in selected }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag(if (byReward) "quest_tasks_sheet" else "quest_rewards_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = PanelGutter, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (byReward) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    QuestRewardIcon(group.label, group.key, rewardThumbnails, artwork, knownSpecies, size = 44.dp)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = if (byReward) Spacing.md else 0.dp)) {
                Text(group.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    text = "$chosen of ${group.rules.size} ${view.other}s shown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onSetPairs(group.rules, true) }) { Text("All") }
            TextButton(onClick = { onSetPairs(group.rules, false) }) { Text("None") }
        }
        LazyColumn(
            modifier = Modifier.keepScrollInSheet(),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl)
        ) {
            listItems(group.members.indices.toList(), key = { index -> group.rules[index].let { "${it.taskKey}|${it.rewardKey}" } }) { index ->
                val quest = group.members[index]
                val rule = group.rules[index]
                val shown = rule in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = shown, role = Role.Checkbox, onValueChange = { onSetPairs(listOf(rule), it) })
                        .padding(horizontal = PanelGutter, vertical = Spacing.xs)
                        .testTag(if (byReward) "quest_task_${rule.taskKey}" else "quest_pick_reward_${rule.rewardKey}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = shown, onCheckedChange = null)
                    if (!byReward) {
                        Box(Modifier.padding(start = Spacing.md).size(36.dp), contentAlignment = Alignment.Center) {
                            QuestRewardIcon(quest.reward.ifBlank { quest.rewardKey }, quest.rewardKey, rewardThumbnails, artwork, knownSpecies, size = 32.dp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                        Text(
                            text = if (byReward) quest.task.ifBlank { quest.taskKey } else quest.reward.ifBlank { quest.rewardKey },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!quest.active) {
                            Text(
                                "Not live right now",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
