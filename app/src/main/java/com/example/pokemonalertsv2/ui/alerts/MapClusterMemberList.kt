@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.util.haversineMeters
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterMatchContext
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet

/**
 * The markers the map should draw: mappable, still valid and unexpired alerts, minus the ones
 * the user dismissed unless [showDismissed] opts them back in, narrowed to the map's own
 * [selectedCategories] (independent of the feed's selection).
 */
internal fun visibleMapAlerts(
    alerts: List<PokemonAlert>,
    selectedCategories: Set<AlertCategory>,
    filterDefinition: FilterDefinition? = null,
    dismissedAlertIds: Set<String>,
    showDismissed: Boolean,
    nowMillis: Long,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    walkingRoutes: Map<String, WalkingRouteInfo> = emptyMap()
): List<PokemonAlert> =
    alerts.filter { alert ->
        val coordinates = alert.mapCoordinatesOrNull() ?: return@filter false
        if (alert.isInvalidated) return@filter false
        if (!showDismissed && alert.uniqueId in dismissedAlertIds) return@filter false
        val end = TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE
        if (end <= nowMillis) return@filter false
        if (!matchesCategorySelection(alert, selectedCategories)) return@filter false
        if (filterDefinition == null) return@filter true

        val distance = if (userLatitude != null && userLongitude != null) {
            haversineMeters(userLatitude, userLongitude, coordinates.latitude, coordinates.longitude)
                .toFloat().takeUnless(Float::isNaN)
        } else null
        val route = walkingRoutes[alert.uniqueId]
        AlertFilterMatcher.matches(
            alert,
            filterDefinition,
            FilterMatchContext(
                effectiveDistanceMeters = route?.distanceMeters?.toFloat() ?: distance,
                walkingDurationSeconds = route?.durationSeconds,
                directDistanceMeters = distance
            )
        )
    }

internal enum class MapLoadState {
    LOADING,
    READY,
    ERROR
}

internal fun resolveMapLoadState(loaded: Boolean, failed: Boolean): MapLoadState = when {
    loaded -> MapLoadState.READY
    failed -> MapLoadState.ERROR
    else -> MapLoadState.LOADING
}

/** A virtualized list displaying rich rows for every member of an overlapping cluster or stack. */
@Composable
internal fun MapClusterMemberList(
    alerts: List<PokemonAlert>,
    countdownClock: State<Long>,
    onSelect: (PokemonAlert) -> Unit
) {
    val sortedAlerts = remember(alerts) {
        alerts.sortedWith(::compareAlertPriority)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .keepScrollInSheet()
            .testTag("map_cluster_members"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "heading") {
            Text(
                stringResource(R.string.alerts_x_alerts_at_this_location, sortedAlerts.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(sortedAlerts, key = { it.uniqueId }) { alert ->
            val visualStyle = resolveAlertVisualStyle(alert)
            Surface(
                onClick = { onSelect(alert) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().testTag("map_cluster_member_${alert.uniqueId}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val imageUrl = alert.thumbnailUrl ?: alert.imageUrl
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = alert.name,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.5.dp,
                                    Color(visualStyle.category.accentArgb.toInt()),
                                    CircleShape
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(visualStyle.category.accentArgb.toInt())),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = alert.name.take(2).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = alert.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            alert.displayCp?.let { cp ->
                                Text(
                                    text = stringResource(R.string.alerts_cp_x, cp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            alert.ivPercentage?.let { iv ->
                                Text(
                                    text = "$iv%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (iv == 100) FontWeight.Bold else FontWeight.Normal,
                                    color = if (iv == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = visualStyle.shortCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(visualStyle.category.accentArgb.toInt()),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    MapCountdownText(alert.endTime, countdownClock)
                }
            }
        }
    }
}
