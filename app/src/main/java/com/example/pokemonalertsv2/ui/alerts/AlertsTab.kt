package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.components.AnimatedRefreshIcon
import com.example.pokemonalertsv2.ui.components.SegmentedChoice
import com.example.pokemonalertsv2.ui.components.SpringSegmentedRow
import com.example.pokemonalertsv2.ui.motion.AppMotion
import androidx.compose.animation.core.tween

/** The three views that share the Alerts tab. */
enum class AlertsSection(val label: String) {
    LIVE("Live"),
    HISTORY("History"),
    INSIGHTS("Insights")
}

/**
 * The Alerts tab: live feed, history and spawn insights behind one segmented switch.
 *
 * History used to be its own bottom-bar destination with its own title bar and refresh
 * button, which left five tabs and two nearly identical headers. One header row now carries
 * the switch and a single refresh that acts on whichever section is showing.
 */
@Composable
fun AlertsTab(
    section: AlertsSection,
    onSectionChange: (AlertsSection) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    live: @Composable () -> Unit,
    history: @Composable () -> Unit,
    insights: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpringSegmentedRow(
                selectedIndex = section.ordinal,
                segmentCount = AlertsSection.entries.size,
                modifier = Modifier.weight(1f)
            ) {
                AlertsSection.entries.forEach { entry ->
                    SegmentedChoice(
                        label = entry.label,
                        selected = entry == section,
                        transparent = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onSectionChange(entry) }
                    )
                }
            }
            // Insights runs on demand, so there is nothing for a refresh to act on there.
            IconButton(
                onClick = onRefresh,
                enabled = section != AlertsSection.INSIGHTS,
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
