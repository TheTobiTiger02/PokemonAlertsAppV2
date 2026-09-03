@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.theme.Alphas
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * How much room the rail takes at the top of the map, for the map engines' content insets.
 *
 * The chrome above the map used to be a 56dp bar with the rail on top of it, and the insets
 * hardcoded 72dp to clear that while ignoring the status bar entirely. There is only the rail
 * now, and callers add the real status bar height to this.
 */
internal val MAP_TOP_CHROME_HEIGHT = 52.dp

/**
 * The map's category rail — and, since the header bar was removed, its entire top chrome.
 *
 * Chips read positively: a lit chip means "this is on the map", even though the stored value is
 * the *muted* set every other surface persists. Every filterable category gets one, so the rail
 * doubles as the legend for the marker colours, and the trailing chip opens everything else the
 * map can do.
 *
 * Order is the declared one rather than by count: counts change on every poll, and chips that
 * shuffle under your thumb are worse than chips that are always in the same place. Only the
 * empty categories move, to the end.
 */
@Composable
internal fun MapCategoryRail(
    mutedCategories: Set<AlertCategory>,
    categoryCounts: Map<AlertCategory, Int>,
    visibleAlertCount: Int,
    advancedRuleCount: Int,
    onMutedCategoriesChange: (Set<AlertCategory>) -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.xxs)
) {
    val haptics = LocalHapticFeedback.current
    val soloDescription = stringResource(R.string.map_category_solo)

    // Present categories keep their declared order; empty ones fall to the end so the rail
    // leads with what is actually around without reshuffling every 30s poll.
    val ordered = remember(categoryCounts) {
        val (present, empty) = FILTERABLE_ALERT_CATEGORIES.partition { (categoryCounts[it] ?: 0) > 0 }
        present + empty
    }

    LazyRow(
        modifier = modifier.fillMaxWidth().testTag("map_filter_rail"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Root destinations never show one, but the parameter is still wired, and with the bar
        // gone the rail is the only chrome left that could host it.
        if (showBackButton) {
            item(key = "BACK") {
                MapRailBackButton(onClick = onBack)
            }
        }
        item(key = "ALL") {
            MapFilterPill(
                label = stringResource(R.string.map_filter_all),
                count = visibleAlertCount,
                selected = mutedCategories.isEmpty(),
                accent = MaterialTheme.colorScheme.primary,
                onClick = { onMutedCategoriesChange(emptySet()) },
                modifier = Modifier.testTag("map_filter_all")
            )
        }
        items(ordered, key = { it.name }) { category ->
            val selected = category !in mutedCategories
            val count = categoryCounts[category] ?: 0
            val soloed = selected && mutedCategories.size == FILTERABLE_ALERT_CATEGORIES.size - 1
            MapFilterPill(
                label = category.filterLabel,
                count = count,
                selected = selected,
                accent = Color(category.accentArgb),
                dimmed = count == 0,
                onClick = {
                    val next = if (selected) mutedCategories + category else mutedCategories - category
                    // Turning the last chip off would leave an empty map with no way back on
                    // the rail itself, so it reads as "show everything again" instead.
                    onMutedCategoriesChange(
                        if (next.size >= FILTERABLE_ALERT_CATEGORIES.size) emptySet() else next
                    )
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMutedCategoriesChange(
                        if (soloed) emptySet() else FILTERABLE_ALERT_CATEGORIES.toSet() - category
                    )
                },
                longClickLabel = soloDescription
            )
        }
        item(key = "MORE") {
            MapFiltersPill(activeRuleCount = advancedRuleCount, onClick = onOpenFilters)
        }
    }
}

/**
 * One rail chip. Hand-built rather than a [androidx.compose.material3.FilterChip] for two
 * reasons: it needs its own shadow (nothing sits behind the rail any more) and it needs a
 * long-press, which the Material chip does not expose.
 */
@Composable
private fun MapFilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    // Composited, not translucent: a see-through chip over map tiles reads as muddy rather
    // than as glass, and the label stops being legible over dark satellite imagery.
    val container = if (selected) {
        accent.copy(alpha = Alphas.Tint).compositeOver(scheme.surface)
    } else {
        scheme.surface
    }
    val border = if (selected) accent.copy(alpha = 0.7f) else scheme.outlineVariant
    val labelColor = if (selected) scheme.onSurface else scheme.onSurfaceVariant

    Surface(
        modifier = modifier.alpha(if (dimmed && !selected) Alphas.Muted else 1f),
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = labelColor,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = longClickLabel
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else accent.copy(alpha = Alphas.Disabled))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            if (count > 0) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The rail's trailing chip, and the map's only remaining entry point to everything the ten
 * category chips cannot express: advanced filters, map style, overlays, refresh and
 * picture-in-picture.
 *
 * The badge counts active filter rules only. Layer state is legible from the map itself, so
 * counting it here would make one badge mean two things at once.
 */
@Composable
private fun MapFiltersPill(activeRuleCount: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val active = activeRuleCount > 0
    val container = if (active) {
        scheme.primary.copy(alpha = Alphas.Tint).compositeOver(scheme.surface)
    } else {
        scheme.surface
    }
    Surface(
        modifier = Modifier.testTag("map_open_filters"),
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = if (active) scheme.onSurface else scheme.onSurfaceVariant,
        shadowElevation = 3.dp,
        border = BorderStroke(
            1.dp,
            if (active) scheme.primary.copy(alpha = 0.7f) else scheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = "Filters and map settings" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_filter),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (active) scheme.primary else LocalContentColor.current
            )
            Text(
                text = stringResource(R.string.map_filters_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            if (active) {
                Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                    Text(
                        text = "$activeRuleCount",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/** A round rail chip carrying the back arrow instead of a label. */
@Composable
private fun MapRailBackButton(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .combinedClickable(onClick = onClick)
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * The key to the marker colours, shown in the map panel.
 *
 * The map has always been colour-coded and has never said so anywhere. The rail is the live
 * version of this; this is the version you can read without narrowing anything.
 */
@Composable
internal fun MapCategoryLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FILTERABLE_ALERT_CATEGORIES.forEach { category ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(category.accentArgb))
                )
                Text(
                    text = category.filterLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
