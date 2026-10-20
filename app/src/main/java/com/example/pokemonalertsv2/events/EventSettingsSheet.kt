@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * Everything about which events matter, in one sheet: which types show in the list and which
 * ones remind you before they start. Replaces the separate "Event types" and "Reminders" sheets,
 * which listed the same types twice. [tabs] picks the layout: one list with Show and Remind on
 * every row, or the two as tabs.
 */
@Composable
internal fun EventSettingsSheet(
    tabs: Boolean,
    settings: EventSettings,
    listedTypes: List<String>,
    reminderTypes: List<String>,
    onToggleHidden: (String) -> Unit,
    onShowAllTypes: () -> Unit,
    onToggleReminderType: (String) -> Unit,
    onLeadMinutes: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("events_settings_sheet")
    ) {
        val hidden = listedTypes.count { it in settings.hiddenTypes }
        if (tabs) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            Column {
                PanelHeader(title = "Events", subtitle = "What the list shows and what reminds you")
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = Spacing.sm)) {
                    listOf("Show", "Remind").forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) },
                            modifier = Modifier.testTag("events_settings_tab_${label.lowercase()}"))
                    }
                }
                LazyColumn(Modifier.keepScrollInSheet(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                    if (tab == 0) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(start = PanelGutter, end = Spacing.sm, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (hidden == 0) "Every type shows" else "$hidden hidden",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onShowAllTypes, enabled = hidden > 0, modifier = Modifier.testTag("events_types_all")) { Text("Show all") }
                            }
                        }
                        items(listedTypes, key = { "show-$it" }) { type ->
                            PanelSwitchRow(
                                title = eventTypeName(type),
                                checked = type !in settings.hiddenTypes,
                                onCheckedChange = { onToggleHidden(type) },
                                modifier = Modifier.testTag("events_show_$type")
                            )
                        }
                    } else {
                        item { LeadTime(settings, onLeadMinutes) }
                        item { PanelSectionLabel("Remind me before every") }
                        items(reminderTypes, key = { "remind-$it" }) { type ->
                            PanelSwitchRow(
                                title = eventTypeName(type),
                                checked = type in settings.reminderTypes,
                                onCheckedChange = { onToggleReminderType(type) },
                                modifier = Modifier.testTag("events_remind_$type")
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.keepScrollInSheet(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                item {
                    PanelHeader(
                        title = "Event types",
                        subtitle = "Show them in the list, and get reminded before they start",
                        actions = {
                            TextButton(onClick = onShowAllTypes, enabled = hidden > 0, modifier = Modifier.testTag("events_types_all")) { Text("Show all") }
                        }
                    )
                }
                items((listedTypes + reminderTypes).distinct().sortedBy { eventTypeName(it) }, key = { it }) { type ->
                    TypeRow(
                        name = eventTypeName(type),
                        listed = type in listedTypes,
                        shown = type !in settings.hiddenTypes,
                        reminded = type in settings.reminderTypes,
                        onShow = { onToggleHidden(type) },
                        onRemind = { onToggleReminderType(type) },
                        type = type
                    )
                }
                item { LeadTime(settings, onLeadMinutes) }
            }
        }
    }
}

/** One type with its two answers side by side: in the list, and reminded before it starts. */
@Composable
private fun TypeRow(name: String, listed: Boolean, shown: Boolean, reminded: Boolean, onShow: () -> Unit, onRemind: () -> Unit, type: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = PanelGutter),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(Spacing.sm))
        // Types with nothing on the calendar right now can still be reminded, but there is
        // nothing to hide.
        if (listed) {
            FilterChip(
                selected = shown,
                onClick = onShow,
                label = { Text("Show") },
                leadingIcon = if (shown) { { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                modifier = Modifier.testTag("events_show_$type")
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        FilterChip(
            selected = reminded,
            onClick = onRemind,
            label = { Text("Remind") },
            leadingIcon = if (reminded) { { Icon(Icons.Filled.Notifications, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
            modifier = Modifier.testTag("events_remind_$type")
        )
    }
}

@Composable
private fun LeadTime(settings: EventSettings, onLeadMinutes: (Int) -> Unit) {
    Column {
        PanelSectionLabel("How long before")
        FlowRow(Modifier.panelGutter(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            REMINDER_LEAD_CHOICES.forEach { minutes ->
                FilterChip(
                    selected = settings.leadMinutes == minutes,
                    onClick = { onLeadMinutes(minutes) },
                    label = { Text("$minutes min") },
                    modifier = Modifier.testTag("events_lead_$minutes")
                )
            }
        }
    }
}
