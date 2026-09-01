package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Accent color for a filter category, used consistently across chips, cards and markers. */
@Composable
fun AlertCategory.accentColor(): Color = Color(accentArgb)

/**
 * The shared multi-select category editor.
 *
 * Every surface (feed sheet, map sheet, Settings hub) renders the same grid so "what am I
 * filtering out?" always looks and behaves identically: tap a card to hide that category,
 * tap again to bring it back. Cards show how many live alerts they currently hold, so the
 * Darmstadt flood can be reasoned about before muting anything.
 *
 * [selection] holds the *muted* categories; [onToggle] reports whether the category should
 * be shown after the tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryFilterGrid(
    selection: Set<AlertCategory>,
    counts: Map<AlertCategory, Int>,
    onToggle: (category: AlertCategory, shownAfter: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = columns
    ) {
        FILTERABLE_ALERT_CATEGORIES.forEach { category ->
            Box(Modifier.weight(1f)) {
                CategoryFilterCard(
                    category = category,
                    count = counts[category] ?: 0,
                    shown = category !in selection,
                    onToggle = { onToggle(category, category in selection) }
                )
            }
        }
    }
}

/**
 * One category inside [CategoryFilterGrid].
 *
 * Semantics: [selected] means "this category is currently shown" — the default for every
 * category. Deselecting mutes it; the card dims and loses its accent so the muted state is
 * readable at a glance rather than looking like a disabled control.
 */
@Composable
private fun CategoryFilterCard(
    category: AlertCategory,
    count: Int,
    shown: Boolean,
    onToggle: () -> Unit
) {
    val accent by animateColorAsState(
        targetValue = if (shown) category.accentColor() else MaterialTheme.colorScheme.outlineVariant,
        label = "categoryAccent"
    )
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Checkbox },
        shape = MaterialTheme.shapes.medium,
        color = if (shown) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(0.dp)
        },
        contentColor = if (shown) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (shown) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (shown) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = accent, shape = CircleShape)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = category.filterLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = countText(count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(color = accent, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun countText(count: Int): String = when {
    count <= 0 -> "none active"
    count == 1 -> "1 active"
    else -> "$count active"
}

/**
 * Compact chip used where a full grid will not fit (the feed's active-filter row). Shows a
 * currently-visible category; tapping mutes it.
 */
@Composable
fun ActiveCategoryFilterChip(
    category: AlertCategory,
    onRemove: () -> Unit
) {
    val accent = category.accentColor()
    Surface(
        onClick = onRemove,
        shape = CircleShape,
        color = accent.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = accent, shape = CircleShape)
            )
            Text(
                text = category.filterLabel,
                style = MaterialTheme.typography.labelLarge
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Hide ${category.filterLabel}",
                modifier = Modifier.size(16.dp),
                tint = accent
            )
        }
    }
}
