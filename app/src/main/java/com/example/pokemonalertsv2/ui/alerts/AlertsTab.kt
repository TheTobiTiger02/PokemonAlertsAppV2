package com.example.pokemonalertsv2.ui.alerts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.components.SegmentedChoice
import com.example.pokemonalertsv2.ui.components.SpringSegmentedRow
import com.example.pokemonalertsv2.ui.motion.AppMotion
import androidx.compose.animation.core.tween

/**
 * The views that share the Alerts tab. [INSIGHTS] is not a segment of the switch: it opens from
 * a button on the history screen and has its own back arrow.
 */
enum class AlertsSection(val label: String) {
    LIVE("Live"),
    HISTORY("History"),
    INSIGHTS("Insights")
}

/** The two sections the Live | History switch offers, in display order. */
private val SWITCH_SECTIONS = listOf(AlertsSection.LIVE, AlertsSection.HISTORY)

/**
 * The Alerts tab: live feed and history behind one segmented switch, with spawn insights one
 * tap further inside history.
 *
 * One header row carries the switch, a single refresh that acts on whichever section is
 * showing, and the gear that opens Settings.
 */
@Composable
fun AlertsTab(
    section: AlertsSection,
    onSectionChange: (AlertsSection) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    live: @Composable () -> Unit,
    history: @Composable () -> Unit,
    insights: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = section == AlertsSection.INSIGHTS) { onSectionChange(AlertsSection.HISTORY) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (section == AlertsSection.INSIGHTS) {
                IconButton(onClick = { onSectionChange(AlertsSection.HISTORY) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to history")
                }
                Text(
                    text = "Spawn insights",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SpringSegmentedRow(
                    selectedIndex = SWITCH_SECTIONS.indexOf(section),
                    segmentCount = SWITCH_SECTIONS.size,
                    modifier = Modifier.weight(1f)
                ) {
                    SWITCH_SECTIONS.forEach { entry ->
                        SegmentedChoice(
                            label = entry.label,
                            selected = entry == section,
                            transparent = true,
                            modifier = Modifier.weight(1f),
                            onClick = { onSectionChange(entry) }
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    AnimatedRefreshIcon(
                        refreshing = refreshing,
                        contentDescription = stringResource(id = R.string.refresh_alerts)
                    )
                }
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
        Crossfade(
            targetState = section,
            animationSpec = tween(AppMotion.Quick),
            label = "alerts_section"
        ) { shown ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (shown) {
                    AlertsSection.LIVE -> live()
                    AlertsSection.HISTORY -> history()
                    AlertsSection.INSIGHTS -> insights()
                }
            }
        }
    }
}
