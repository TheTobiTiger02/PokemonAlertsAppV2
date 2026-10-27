package com.example.pokemonalertsv2.ui.alerts.mapchrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.alerts.MapCategoryRail
import com.example.pokemonalertsv2.ui.motion.appFadeIn
import com.example.pokemonalertsv2.ui.motion.appFadeOut
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * The controls drawn over the map. One bar across the top says how many alerts are on the map
 * and whether they are current, and holds the three things that change what the map shows:
 * filters, layers and Settings. The chip rail sits under it. The bottom keeps a labelled Tools
 * button on the left and the camera on the right.
 */
@Composable
internal fun BoxScope.MapChrome(state: MapChromeState, actions: MapChromeActions) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        MapTopBar(state, actions, Modifier.padding(start = Spacing.lg, end = state.controlsEndPadding))
        // No hunt banner: the Tools pill below already names the target, and the fit button
        // frames it.
        MapCategoryRail(
            mutedCategories = state.mutedCategories,
            categoryCounts = state.categoryCounts,
            visibleAlertCount = state.visibleCount,
            showBackButton = state.showBackButton,
            onBack = actions.onBack,
            onMutedCategoriesChange = actions.onCategoriesChange,
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = 2.dp, bottom = Spacing.xxs)
        )
    }

    AnimatedVisibility(
        visible = state.floatingControlsVisible,
        enter = appFadeIn(),
        exit = appFadeOut(),
        modifier = Modifier.align(Alignment.BottomStart)
    ) {
        MapToolsPill(
            state = state,
            onClick = actions.onOpenTools,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = Spacing.lg, bottom = Spacing.xxl)
        )
    }

    AnimatedVisibility(
        visible = state.floatingControlsVisible,
        enter = appFadeIn(),
        exit = appFadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd)
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = state.controlsEndPadding, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.End
        ) {
            MapFitButton(contentDescription = fitDescription(state.hunt), onClick = actions.onFit)
            MapLocateButton(state = state.locate, onClick = actions.onLocate)
        }
    }
}

@Composable
private fun MapTopBar(state: MapChromeState, actions: MapChromeActions, modifier: Modifier = Modifier) {
    MapPillBar(modifier = modifier.testTag("map_top_bar")) {
        // The status half doubles as the retry button when the alerts are not live.
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    enabled = state.syncStatus.isProblem,
                    role = Role.Button,
                    onClickLabel = "Retry loading alerts",
                    onClick = actions.onRefresh
                )
                .padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            MapSyncDot(state.syncStatus)
            Column {
                Text(
                    text = state.countLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.syncStatus.chromeSummary(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = actions.onOpenFilters, modifier = Modifier.testTag("map_open_filters")) {
            BadgedIcon(
                icon = painterResource(R.drawable.ic_filter),
                contentDescription = "Filters",
                count = state.advancedFilterRuleCount
            )
        }
        IconButton(onClick = actions.onOpenLayers, modifier = Modifier.testTag("map_open_layers")) {
            Icon(painterResource(R.drawable.ic_layers), contentDescription = "Map layers")
        }
        actions.onOpenSettings?.let { openSettings ->
            IconButton(
                onClick = openSettings,
                modifier = Modifier.padding(end = Spacing.xxs).testTag("open_settings")
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
    }
}

/** Floating controls give way to an alert's detail sheet on phones, and wait for the map. */
private val MapChromeState.floatingControlsVisible: Boolean
    get() = mapLoaded && (useSidePanel || !alertSelected)

/** Leaves room for the wide-screen side panel on the right. */
private val MapChromeState.controlsEndPadding: Dp
    get() = if (useSidePanel) 392.dp else Spacing.lg

/**
 * "Tools" at rest. During a hunt, the same guidance card a catch route walks with: the target
 * and how far away it is, opening the hunt's controls. Uses the content-slot FAB overload: the
 * text-slot one clears its label's semantics, which left TalkBack with nothing.
 */
@Composable
private fun MapToolsPill(state: MapChromeState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hunt = state.hunt
    if (hunt != null) {
        RouteGuidanceCard(
            badge = RouteBadge.Glyph(painterResource(R.drawable.ic_my_location)),
            title = hunt.title(),
            detail = hunt.detail(),
            onClick = onClick,
            modifier = modifier.widthIn(max = 300.dp).testTag("open_map_tools"),
            trailing = {
                Icon(painterResource(R.drawable.ic_tools), contentDescription = "Hunt controls",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )
        return
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.widthIn(max = 260.dp).testTag("open_map_tools"),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Icon(painterResource(R.drawable.ic_tools), contentDescription = null)
        Spacer(Modifier.width(Spacing.md))
        Text("Tools")
    }
}
