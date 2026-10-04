package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.hunt.HuntControls
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon

/**
 * Things the map *does*, as opposed to what it shows: hunts, route planning, the Mega
 * helper, the floating window and a manual refresh.
 *
 * These used to be split between two unlabelled text bubbles on the map ("Route", "Mega")
 * and the top of the filter sheet, where they pushed the actual filters below the fold.
 */
@Composable
internal fun MapToolsContent(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onEnterPictureInPicture: (() -> Unit)?,
    onOpenHuntSetup: () -> Unit,
    userLocation: android.location.Location? = null,
    onOpenCatchRoutes: () -> Unit = {},
    onOpenMegaBoost: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Every device can hunt: the hunt's floating map is an overlay window, not
        // picture-in-picture, so it no longer waits on PiP support.
        Text(
            text = "Hunt",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        HuntControls(
            onOpenHuntSetup = onOpenHuntSetup,
            userLocation = userLocation
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ToolRow(
            title = "Catch route",
            subtitle = "Plan a walk past the most spawnpoints",
            icon = { Icon(painterResource(R.drawable.ic_navigate), contentDescription = null) },
            onClick = onOpenCatchRoutes,
            modifier = Modifier.testTag("open_catch_routes")
        )
        ToolRow(
            title = "Mega boost",
            subtitle = "Which Mega boosts the most live spawns",
            icon = { Icon(painterResource(R.drawable.ic_insights), contentDescription = null) },
            onClick = onOpenMegaBoost,
            modifier = Modifier.testTag("open_mega_boost")
        )
        if (onEnterPictureInPicture != null) {
            ToolRow(
                title = "Floating map",
                subtitle = "Keep the map on top of Pokémon GO",
                icon = { Icon(painterResource(R.drawable.ic_pip), contentDescription = null) },
                onClick = onEnterPictureInPicture,
                modifier = Modifier.semantics { contentDescription = "Open map in picture-in-picture" }
            )
        }
        ToolRow(
            title = "Refresh alerts",
            subtitle = "The map also refreshes every 30 seconds",
            icon = { AnimatedRefreshIcon(refreshing = refreshing, contentDescription = "") },
            onClick = onRefresh
        )
        if (onOpenSettings != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ToolRow(
                title = "Settings",
                subtitle = "Filters, notifications, appearance and more",
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = onOpenSettings,
                modifier = Modifier.testTag("open_settings_from_map_tools")
            )
        }
    }
}

@Composable
private fun ToolRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        // A fixed slot: the drawables differ in intrinsic size, which shifted each row's text.
        leadingContent = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapToolsSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Map tools",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}
