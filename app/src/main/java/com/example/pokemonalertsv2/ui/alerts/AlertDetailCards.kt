@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import java.util.Locale

@Composable
internal fun StatsCard(alert: PokemonAlert) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        StatsCardContent(alert = alert, modifier = Modifier.padding(16.dp))
    }
}

/** The stats card's body, also drawn flat (without its own title) by the redesigned detail. */
@Composable
internal fun StatsCardContent(alert: PokemonAlert, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val isReplacement = alert.isSpeciesReplacement
    val isChanged = alert.isWeatherChange  // includes both weather-only and replacement
    val accentColor = Color(resolveAlertVisualStyle(alert).category.accentArgb)

    Column(modifier = modifier) {
        // Section title
        if (showTitle) {
            Text(
                text = when {
                    isReplacement -> "New species"
                    isChanged -> "Updated stats"
                    else -> "Stats"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isChanged) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Species replacement banner — shows old species info
        if (isReplacement) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = accentColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replacing ${alert.oldSpecies}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val oldDetails = listOfNotNull(
                            alert.oldIv?.let { "IV: $it" },
                            alert.oldCp?.let { "CP: $it" }
                        )
                        if (oldDetails.isNotEmpty()) {
                            Text(
                                text = oldDetails.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // For weather change / replacement alerts, display the new stats as primary values
        val displayIv = if (isChanged && alert.newIv != null) alert.newIv else alert.formattedIv
        val displayCp = if (isChanged && alert.newCp != null) alert.newCp else alert.cp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // IVs
            displayIv?.let { iv ->
                val isNewIv = isChanged && alert.newIv != null
                StatItem(
                    label = if (isNewIv) "New IV" else "IV",
                    value = iv,
                    subValue = if (isNewIv && alert.formattedIv != null) "was ${alert.formattedIv}" else alert.ivPercentage?.let { "$it%" },
                    highlight = alert.isPerfect,
                    highlightColor = if (isNewIv) accentColor else if (alert.isPerfect) MaterialTheme.colorScheme.primary else if (alert.isNundo) MaterialTheme.colorScheme.onSurfaceVariant else null
                )
            }

            // CP
            displayCp?.let { cp ->
                val isNewCp = isChanged && alert.newCp != null
                StatItem(
                    label = if (isNewCp) "New CP" else "CP",
                    value = cp.toString(),
                    subValue = if (isNewCp && alert.cp != null) "was ${alert.cp}" else alert.hundoCP?.formatted(),
                    highlightColor = if (isNewCp) accentColor else null
                )
            }

            // Hundo CP (standalone for raids when no individual CP)
            if (displayCp == null && alert.hundoCP != null) {
                alert.hundoCP.level20?.let { l20 ->
                    StatItem(
                        label = "100% L20",
                        value = l20.toString(),
                        highlightColor = MaterialTheme.colorScheme.primary
                    )
                }
                alert.hundoCP.level25?.let { l25 ->
                    StatItem(
                        label = "100% L25",
                        value = l25.toString(),
                        highlightColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Level — show for species replacement (it's the new species' level), hide for weather-only change
            if (!isChanged || isReplacement) {
                alert.level?.let { level ->
                    StatItem(
                        label = if (isReplacement) "New Level" else "Level",
                        value = if (level == level.toLong().toDouble()) level.toLong().toString() else level.toString(),
                        highlightColor = if (isReplacement) accentColor else null
                    )
                }
            }
        }

        // Individual IVs breakdown — hide for weather change alerts (old breakdown is no longer relevant)
        if (!isChanged && alert.ivAttack != null && alert.ivDefense != null && alert.ivStamina != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IvBar(label = "ATK", value = alert.ivAttack, maxValue = 15)
                IvBar(label = "DEF", value = alert.ivDefense, maxValue = 15)
                IvBar(label = "STA", value = alert.ivStamina, maxValue = 15)
            }
        }
    }
}

@Composable
internal fun StatItem(
    label: String,
    value: String,
    subValue: String? = null,
    highlight: Boolean = false,
    highlightColor: Color? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface
        )
        subValue?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun IvBar(label: String, value: Int, maxValue: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.toFloat() / maxValue)
                    .height(8.dp)
                    .background(
                        when {
                            value == 15 -> MaterialTheme.colorScheme.primary
                            value >= 13 -> MaterialTheme.colorScheme.primary
                            value >= 10 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun WeatherAndGenderCard(alert: PokemonAlert) {
    val accent = Color(resolveAlertVisualStyle(alert).category.accentArgb)
    val weather = currentWeatherDisplay(alert)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        weather?.let {
            AlertPill(
                // Unconfirmed weather is still used as reported; it is only labelled.
                text = if (it.confirmed) it.labelWithGlyph else "${it.labelWithGlyph} · $UNCONFIRMED_WEATHER_NOTE"
            )
        }
        if (alert.isWeatherBoosted == true) {
            AlertPill(
                text = "Weather boosted",
                icon = Icons.Filled.Star,
                containerColor = accent.copy(alpha = 0.16f),
                contentColor = accent
            )
        }
        alert.gender?.takeIf { it.isNotBlank() }?.let { gender ->
            AlertPill(text = gender.replaceFirstChar { it.uppercase() })
        }
    }
}

@Composable
internal fun MovesCard(moves: PokemonMoves) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚔️", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Moves",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Fast Move
            moves.fast?.let { fast ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "FAST",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = fast,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Charged Move
            moves.charged?.let { charged ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "CHARGED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = charged,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun LocationCard(alert: PokemonAlert) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // PokéStop / Gym venue and street address
            val venue = alert.venueName
            val venueType = alert.venueTypeLabel
            val address = alert.pokemonLocation

            if (venue != null) {
                Text(
                    text = if (venueType != null) "$venueType: $venue" else venue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!address.isNullOrBlank() && address != venue) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!address.isNullOrBlank()) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else alert.locationDisplay?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Coordinates are for pasting elsewhere, not for reading.
            if (alert.latitude != null && alert.longitude != null) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        clipboard.setText(
                            androidx.compose.ui.text.AnnotatedString(
                                String.format(java.util.Locale.ROOT, "%.6f, %.6f", alert.latitude, alert.longitude)
                            )
                        )
                        android.widget.Toast.makeText(context, "Coordinates copied", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy coordinates", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun QuestCard(alert: PokemonAlert) {
    val questPresentation = remember(alert) { questAlertPresentation(alert) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "📜", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Quest",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (alert.requiresAR == true) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "AR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            questPresentation?.task?.let { task ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Task: $task",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            questPresentation?.reward?.let { reward ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reward: $reward",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun RocketCard(
    gruntType: String? = null,
    pokemonRewards: List<PokemonReward>? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "🚀", style = MaterialTheme.typography.headlineMedium)
                Column {
                    Text(
                        text = "Team GO Rocket",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (gruntType?.isNotBlank() == true) {
                        Text(
                            text = gruntType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pokemon Rewards
            if (!pokemonRewards.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                Text(
                    text = "Possible Rewards",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                pokemonRewards.forEach { reward ->
                    val rarityColor = when (reward.rarity?.lowercase()) {
                        "common" -> MaterialTheme.colorScheme.primary
                        "rare" -> MaterialTheme.colorScheme.primary
                        "legendary", "ultra rare" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Rarity dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(rarityColor, CircleShape)
                        )

                        // Pokemon name
                        Text(
                            text = reward.pokemon ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        // Rarity label
                        reward.rarity?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = rarityColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Percentage
                        reward.percentage?.let { pct ->
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Percentage bar
                    reward.percentage?.let { pct ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    MaterialTheme.shapes.extraSmall
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(rarityColor, MaterialTheme.shapes.extraSmall)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PvpRankingsCard(rankings: List<com.example.pokemonalertsv2.data.PvpRanking>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚔️", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "PvP Rankings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            rankings.forEachIndexed { index, ranking ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                PvpRankingItem(ranking = ranking)
            }
        }
    }
}

@Composable
internal fun PvpRankingItem(ranking: com.example.pokemonalertsv2.data.PvpRanking) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // League name with badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val leagueColor = when {
                ranking.league?.contains("Great", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                ranking.league?.contains("Ultra", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                ranking.league?.contains("Master", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                ranking.league?.contains("Little", ignoreCase = true) == true -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = leagueColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = ranking.league ?: "League",
                    style = MaterialTheme.typography.labelMedium,
                    color = leagueColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Rank badge
            ranking.rank?.let { rank ->
                val rankEmoji = when (rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "#$rank"
                }
                val rankColor = when (rank) {
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.onSurfaceVariant
                    3 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                if (rank <= 3) {
                    Text(
                        text = rankEmoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Pokemon name if different from main alert
        ranking.pokemon?.let { pokemon ->
            Text(
                text = pokemon,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Stats row: CP and Level. Wraps rather than squeezing Stat Product on a narrow phone.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            ranking.cp?.let { cp ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "CP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = cp.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            ranking.level?.let { level ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Level",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (level == level.toLong().toDouble()) level.toLong().toString() else level.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            ranking.percentage?.let { percentage ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Stat Product",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%.1f", percentage)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertMetaRow(
    alert: PokemonAlert,
    distanceInfo: AlertDistanceInfo,
    countdownClock: State<Long> = rememberCountdownClock()
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val typeLabel = alert.typeDisplay?.uppercase(Locale.getDefault())
        val distanceLabel = distanceInfo.distanceText
        val walkingLabel = distanceInfo.walkingText

        val typeIcon = when {
            alert.hasType("Hundo") -> Icons.Filled.Star
            alert.hasType("PvP") -> Icons.Filled.Star
            alert.hasType("Nundo") -> Icons.Filled.Close
            alert.hasType("Raid") -> Icons.Filled.Warning
            alert.hasType("Quest") -> Icons.Filled.Star
            alert.hasType("Rocket") -> Icons.Filled.Warning
            alert.hasType("Kecleon") -> Icons.Filled.LocationOn
            else -> Icons.Filled.LocationOn
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!typeLabel.isNullOrBlank()) {
                AlertTag(text = typeLabel, icon = typeIcon)
            }
            if (!distanceLabel.isNullOrBlank()) {
                AlertTag(text = distanceLabel, icon = Icons.Filled.LocationOn)
            }
            if (!walkingLabel.isNullOrBlank()) {
                AlertTag(text = walkingLabel, icon = null)
            }
        }
        CountdownAndEndTimeRow(alert = alert, countdownClock = countdownClock)
    }
}

@Composable
fun AlertTag(text: String, icon: ImageVector? = null) {
    AlertPill(text = text, icon = icon)
}

@Composable
fun CountdownAndEndTimeRow(
    alert: PokemonAlert,
    countdownClock: State<Long> = rememberCountdownClock()
) {
    val nowMillis = countdownClock.value
    val endMillis = remember(alert.endTime) { TimeUtils.parseEndTimeToMillis(alert.endTime) }
    val remaining = endMillis?.let { it - nowMillis } ?: -1
    val expiredLabel = if (endMillis != null && remaining <= 0) {
        "Expired ${TimeUtils.formatTimeAgo(endMillis)}"
    } else {
        stringResource(id = R.string.alert_expired)
    }
    val remainingText = if (endMillis != null) {
        if (remaining > 0) TimeUtils.formatDurationShort(remaining) else expiredLabel
    } else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!remainingText.isNullOrBlank()) {
            val countdownColor = if (remainingText == expiredLabel)
                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.secondaryContainer
            val countdownContentColor = if (remainingText == expiredLabel)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSecondaryContainer
            AlertPill(
                text = remainingText,
                painter = painterResource(id = R.drawable.ic_timer),
                containerColor = countdownColor,
                contentColor = countdownContentColor,
                rolling = remainingText != expiredLabel
            )
        }
        Text(
            text = (endMillis?.let { TimeUtils.formatAlertEndTime(it, nowMillis) }
                ?: stringResource(id = R.string.alert_end_time, alert.endTime)) +
                // The map scanner could not confirm this despawn; it is its best guess.
                if (alert.expiryVerified == false) " (estimated)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DistanceChip(text: String) {
    AlertPill(
        text = text,
        painter = painterResource(id = R.drawable.ic_map),
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

fun openMapForAlert(context: Context, alert: PokemonAlert) {
    val mapsIntent = Intent(Intent.ACTION_VIEW, alert.googleMapsUri)
    try {
        context.startActivity(mapsIntent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_maps_app), Toast.LENGTH_SHORT).show()
    }
}

internal fun openAlertInPictureInPicture(context: Context, alert: PokemonAlert) {
    val intent = AlertDetailActivity.createIntent(context, alert).apply {
        putExtra(AlertDetailActivity.EXTRA_LAUNCH_PIP, true)
    }
    context.startActivity(intent)
}

fun getLastKnownLocation(context: Context): Location? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) return null
        val providers = lm.getProviders(true)
        var best: Location? = null
        for (p in providers) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
            if (l != null && (best == null || (l.accuracy < best!!.accuracy))) {
                best = l
            }
        }
        best
    } catch (_: Throwable) { null }
}

fun formatDistance(meters: Float): String {
    return WalkingRouteUtils.formatDistanceMeters(meters)
}
