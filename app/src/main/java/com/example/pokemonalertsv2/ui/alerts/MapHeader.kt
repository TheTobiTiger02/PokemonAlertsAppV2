@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.theme.Alphas
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * The map's single floating header.
 *
 * There is deliberately no title: the bottom navigation already says "Map", so the words
 * "Alerts Map" only cost map. What is left is the one number that changes — how many alerts
 * survive the current filters — and the three view actions.
 *
 * Unlike the bar it replaces, this one is inset-aware: it pads itself clear of the status bar
 * rather than starting 8dp from the top edge of the screen.
 */
@Composable
internal fun MapHeaderBar(
    visibleAlertCount: Int,
    showBackButton: Boolean,
    refreshing: Boolean,
    activeLayerCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLayers: () -> Unit,
    modifier: Modifier = Modifier,
    onEnterPictureInPicture: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            AnimatedContent(
                targetState = visibleAlertCount,
                transitionSpec = { appFadeThrough() },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (showBackButton) Spacing.xxs else Spacing.lg),
                label = "map_alert_count"
            ) { count ->
                Text(
                    text = pluralStringResource(R.plurals.map_alerts_visible, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                AnimatedRefreshIcon(
                    refreshing = refreshing,
                    contentDescription = stringResource(R.string.refresh_alerts)
                )
            }
            if (onEnterPictureInPicture != null) {
                IconButton(onClick = onEnterPictureInPicture, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pip),
                        contentDescription = "Open map in picture-in-picture",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Box {
                IconButton(onClick = onOpenLayers, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_layers),
                        contentDescription = stringResource(R.string.map_layers_title),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                AnimatedContent(
                    targetState = activeLayerCount,
                    transitionSpec = { appFadeThrough() },
                    modifier = Modifier.align(Alignment.TopEnd),
                    label = "map_layer_count"
                ) { count ->
                    if (count > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The map's category rail.
 *
 * Chips read positively — a lit chip means "this is on the map" — even though the stored value
 * is still the *muted* set every other surface persists. The old rail inverted that and showed
 * nothing selected in the default state, where in fact everything was showing.
 *
 * Every filterable category gets a chip, not the six that used to fit, so the rail doubles as
 * the legend for the marker colours. Order is the declared one rather than by count: counts
 * change on every poll, and chips that shuffle under your thumb are worse than chips that are
 * always in the same place. Only the empty categories move, to the end.
 */
@Composable
internal fun MapCategoryRail(
    mutedCategories: Set<AlertCategory>,
    categoryCounts: Map<AlertCategory, Int>,
    advancedRuleCount: Int,
    onMutedCategoriesChange: (Set<AlertCategory>) -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
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
        item(key = "ALL") {
            // No count: the header bar directly above already states how many alerts are
            // showing, and repeating it here made the rail read as though the per-category
            // counts ought to sum to it. They overlap, so they never will.
            MapFilterPill(
                label = stringResource(R.string.map_filter_all),
                count = 0,
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

/** The rail's trailing chip: everything the six categories cannot express. */
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
                .semantics { contentDescription = "Advanced filters" },
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

/**
 * The key to the marker colours, shown in the layers sheet.
 *
 * The map has always been colour-coded and has never said so anywhere. The rail is the live
 * version of this; this is the version you can read without narrowing anything.
 */
@Composable
internal fun MapCategoryLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = stringResource(R.string.map_legend_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
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
}
