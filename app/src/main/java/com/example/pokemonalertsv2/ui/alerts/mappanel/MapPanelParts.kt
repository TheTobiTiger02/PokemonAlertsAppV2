@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.alerts.MAP_TOP_CHROME_HEIGHT
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * Which sheet the map's top bar opened: [FILTERS] answers "what is on the map", [LAYERS] answers
 * "how does it look".
 */
internal enum class MapPanelScope { FILTERS, LAYERS }

/**
 * Hosts one of the map's sheets: a bottom sheet on phones, a panel beside the map from 840dp.
 *
 * The sheet opens fully expanded. At partial expansion its own drag handling took the first
 * vertical gesture to grow itself, so the first swipe over the content scrolled nothing.
 */
@Composable
internal fun MapPanelContainer(
    useSidePanel: Boolean,
    testTag: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (useSidePanel) {
        Surface(
            modifier = modifier
                .width(380.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = MAP_TOP_CHROME_HEIGHT, end = 16.dp, bottom = 24.dp)
                .testTag(testTag),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.lg), content = content)
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(testTag)
        ) {
            Column(content = content)
        }
    }
}

/**
 * A sheet's pages: [root] is the list, every other page is one editor reached from it. Back
 * returns to the list before it closes the sheet, and the pages slide like screens do.
 */
@Composable
internal fun <P : Enum<P>> MapPanelPages(
    page: P,
    root: P,
    onBack: () -> Unit,
    content: @Composable (P) -> Unit
) {
    BackHandler(enabled = page != root, onBack = onBack)
    AnimatedContent(
        targetState = page,
        transitionSpec = { appSharedAxisX(forward = targetState != root) },
        label = "map_panel_page"
    ) { shown -> content(shown) }
}

/** Horizontal margin every row in the map's sheets shares. */
internal val PanelGutter = 20.dp

/**
 * A page's title. On an editor page it carries the back arrow, and [actions] holds that
 * page's own commands such as All and None.
 */
@Composable
internal fun PanelHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) Spacing.xs else PanelGutter, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

/** Names the group of rows below it, in the blue the Settings screen uses for the same job. */
@Composable
internal fun PanelSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = PanelGutter, end = PanelGutter, top = Spacing.xl, bottom = Spacing.xs)
    )
}

/** The round, tinted badge that leads every row. */
@Composable
internal fun PanelRowIcon(active: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * A row that opens an editor. The [summary] says what is set without opening it, and turns blue
 * when it narrows the map, so the list reads as a statement of the current filters.
 */
@Composable
internal fun PanelNavRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PanelGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelRowIcon(active = active, content = icon)
        Spacer(Modifier.width(Spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** An on/off setting; the whole row is the switch, so it need not be hit exactly. */
@Composable
internal fun PanelSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = PanelGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            PanelRowIcon(active = checked, content = icon)
        } else {
            // Nested under the row above: keeps its text on the same line as that row's.
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.width(Spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The sheet's one primary action, full width under the list. */
@Composable
internal fun PanelFooterButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PanelGutter, vertical = Spacing.sm)
            .height(52.dp)
            .testTag("map_panel_done")
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** A small painted icon for [PanelRowIcon]. */
@Composable
internal fun PanelIcon(id: Int) {
    Icon(painter = painterResource(id), contentDescription = null, modifier = Modifier.size(20.dp))
}

internal fun Modifier.panelGutter(): Modifier = padding(horizontal = PanelGutter)
