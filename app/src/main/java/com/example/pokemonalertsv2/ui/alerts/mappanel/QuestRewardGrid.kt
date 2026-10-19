@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.example.pokemonalertsv2.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.FilterCatalogQuest
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.QuestFilterRules
import com.example.pokemonalertsv2.data.QuestPairRule
import com.example.pokemonalertsv2.ui.settings.QuestRewardIcon
import com.example.pokemonalertsv2.ui.theme.Spacing

/** One reward and every task that grants it; [rules] and [tasks] line up index for index. */
private class RewardGroup(
    val key: String,
    val label: String,
    val active: Boolean,
    val tasks: List<FilterCatalogQuest>,
    val rules: List<QuestPairRule>
)

/**
 * Field research as a grid of rewards, chosen in place: tap a reward to show or hide every quest
 * that grants it. You want a reward and rarely care which task yields it, so the reward is the
 * unit here. Holding a reward, or its tune button, opens its tasks one by one; task/reward rules
 * stay in the full editor behind [onOpenRules].
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
    var query by rememberSaveable { mutableStateOf("") }
    var tasksFor by rememberSaveable { mutableStateOf<String?>(null) }
    val knownSpecies = remember(artwork) { artwork.keys }
    val groups = remember(quests) {
        quests.groupBy { it.rewardKey }.map { (key, members) ->
            RewardGroup(
                key = key,
                label = members.first().reward.ifBlank { key },
                active = members.any { it.active },
                tasks = members,
                rules = members.map { QuestPairRule(it.taskKey, it.rewardKey) }
            )
        }.sortedWith(compareByDescending<RewardGroup> { it.active }.thenBy { it.label.lowercase() })
    }
    val universe = remember(groups) { groups.flatMap { it.rules } }
    val selected = rules.selectedPairs(universe)
    val shown = remember(groups, query) {
        if (query.isBlank()) groups
        else groups.filter { group ->
            group.label.contains(query, ignoreCase = true) ||
                quests.any { it.rewardKey == group.key && it.task.contains(query, ignoreCase = true) }
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
                    } + " · hold a reward for its tasks",
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
        items(shown, key = { it.key }) { group ->
            RewardTile(
                group = group,
                selectedCount = group.rules.count { it in selected },
                rewardThumbnails = rewardThumbnails,
                artwork = artwork,
                knownSpecies = knownSpecies,
                onClick = { onRulesChange(rules.withRewardToggled(group.rules, universe)) },
                onOpenTasks = { tasksFor = group.key }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Text("Task & reward rules…")
            }
        }
    }

    groups.firstOrNull { it.key == tasksFor }?.let { group ->
        RewardTasksSheet(
            group = group,
            selected = selected,
            rewardThumbnails = rewardThumbnails,
            artwork = artwork,
            knownSpecies = knownSpecies,
            onSetPairs = { pairs, show -> onRulesChange(rules.withPairs(pairs, show, universe)) },
            onDismiss = { tasksFor = null }
        )
    }
}

@Composable
private fun RewardTile(
    group: RewardGroup,
    selectedCount: Int,
    rewardThumbnails: Map<String, String>,
    artwork: Map<String, String>,
    knownSpecies: Set<String>,
    onClick: () -> Unit,
    onOpenTasks: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val all = selectedCount == group.rules.size
    val some = selectedCount > 0
    Surface(
        modifier = Modifier
            .heightIn(min = 112.dp)
            .alpha(if (group.active) 1f else 0.6f)
            .testTag("quest_reward_${group.key}"),
        shape = RoundedCornerShape(14.dp),
        color = if (some) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surface,
        border = BorderStroke(if (some) 2.dp else 1.dp, if (some) scheme.primary else scheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = if (all) "Hide this reward" else "Show this reward",
                    onLongClickLabel = "Pick tasks",
                    onLongClick = onOpenTasks,
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
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    QuestRewardIcon(group.label, group.key, rewardThumbnails, artwork, knownSpecies, size = 40.dp)
                }
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        !group.active -> "Not live"
                        some && !all -> "$selectedCount of ${group.rules.size} tasks"
                        group.rules.size == 1 -> "1 task"
                        else -> "${group.rules.size} tasks"
                    },
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
            // The visible way into a reward's tasks, for anyone who does not think to hold it.
            IconButton(
                onClick = onOpenTasks,
                modifier = Modifier.align(Alignment.TopStart).size(36.dp).testTag("quest_tasks_${group.key}")
            ) {
                Icon(
                    painterResource(R.drawable.ic_tune),
                    contentDescription = "Pick tasks for ${group.label}",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** One reward's tasks, each with its own box, for when only some of them are worth the walk. */
@Composable
private fun RewardTasksSheet(
    group: RewardGroup,
    selected: Set<QuestPairRule>,
    rewardThumbnails: Map<String, String>,
    artwork: Map<String, String>,
    knownSpecies: Set<String>,
    onSetPairs: (List<QuestPairRule>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val chosen = group.rules.count { it in selected }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("quest_tasks_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = PanelGutter, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                QuestRewardIcon(group.label, group.key, rewardThumbnails, artwork, knownSpecies, size = 44.dp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(group.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    text = "$chosen of ${group.rules.size} tasks shown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onSetPairs(group.rules, true) }) { Text("All") }
            TextButton(onClick = { onSetPairs(group.rules, false) }) { Text("None") }
        }
        LazyColumn(contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl)) {
            listItems(group.tasks.indices.toList(), key = { group.rules[it].taskKey }) { index ->
                val task = group.tasks[index]
                val rule = group.rules[index]
                val shown = rule in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = shown, role = Role.Checkbox, onValueChange = { onSetPairs(listOf(rule), it) })
                        .padding(horizontal = PanelGutter, vertical = Spacing.xs)
                        .testTag("quest_task_${rule.taskKey}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = shown, onCheckedChange = null)
                    Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                        Text(task.task.ifBlank { task.taskKey }, style = MaterialTheme.typography.bodyLarge)
                        if (!task.active) {
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
