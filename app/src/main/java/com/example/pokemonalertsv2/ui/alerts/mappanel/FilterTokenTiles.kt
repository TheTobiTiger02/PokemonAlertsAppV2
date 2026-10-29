@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.RaidTier
import com.example.pokemonalertsv2.data.normalizeFilterToken
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.ui.theme.typeColor
import com.example.pokemonalertsv2.ui.theme.typeLabel

/** How a token is drawn: the badge's colour and glyph, and the tile's label. */
internal class TokenLook(
    val label: String,
    val badge: Color,
    val glyph: String,
    val tint: Color? = null,
    /** Artwork shown in place of the glyph badge, such as a Tappable's reward icon. */
    val imageUrl: String? = null
)

/**
 * Tokens (raid tiers, grunt types) as a grid of tiles with a coloured badge, rather than a run of
 * text chips. [look] decides how each one is drawn; selection is the app's blue border and check.
 */
@Composable
internal fun TokenTileGrid(
    tokens: List<String>,
    selection: FilterSelection,
    onSelectionChange: (FilterSelection) -> Unit,
    perRow: Int,
    look: (String) -> TokenLook,
    modifier: Modifier = Modifier,
    /** Everything the selection ranges over, when [tokens] is only one section of it. */
    universe: List<String> = tokens
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        maxItemsInEachRow = perRow,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        tokens.forEach { token ->
            TokenTile(
                look = look(token),
                selected = selection.contains(token),
                onClick = { onSelectionChange(selection.toggled(token, universe)) },
                modifier = Modifier.weight(1f).testTag("token_tile_${normalizeFilterToken(token)}")
            )
        }
        // Keeps a part-filled last row aligned with the rows above it.
        repeat((perRow - tokens.size % perRow) % perRow) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TokenTile(look: TokenLook, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val wash = look.tint?.copy(alpha = 0.12f)?.compositeOver(scheme.surface) ?: scheme.surface
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 84.dp)
            .semantics { contentDescription = "${look.label}, ${if (selected) "shown" else "hidden"}" },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.35f).compositeOver(wash) else wash,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant)
    ) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm, horizontal = Spacing.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (look.imageUrl != null) {
                    AsyncImage(
                        model = look.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).alpha(if (selected) 1f else 0.7f)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = look.badge.copy(alpha = if (selected) 1f else 0.55f),
                        contentColor = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(look.glyph, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = look.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                )
            }
        }
    }
}

private val RaidPink = Color(0xFFE0609A)
private val RaidAmber = Color(0xFFE0A030)
private val RaidSlate = Color(0xFF5E6B7E)
private val RaidMega = Color(0xFFE8710A)
private val RaidPrimal = Color(0xFFC0392B)
private val RaidShadow = Color(0xFF7B4FB0)
private val RaidSpecial = Color(0xFF2E7D8A)
private val Neutral = Color(0xFF6C7480)

/** A raid tier's egg colour and glyph: the number for plain and shadow tiers, a letter otherwise. */
internal fun raidTierLook(token: String): TokenLook {
    val tier = RaidTier.entries.firstOrNull { normalizeFilterToken(it.displayLabel) == normalizeFilterToken(token) }
    return when (tier) {
        RaidTier.TIER_1, RaidTier.TIER_2 -> TokenLook("Tier ${tier.displayLabel}", RaidPink, tier.displayLabel)
        RaidTier.TIER_3, RaidTier.TIER_4 -> TokenLook("Tier ${tier.displayLabel}", RaidAmber, tier.displayLabel)
        RaidTier.TIER_5, RaidTier.TIER_6 -> TokenLook("Tier ${tier.displayLabel}", RaidSlate, tier.displayLabel)
        RaidTier.MEGA -> TokenLook("Mega", RaidMega, "M")
        RaidTier.MEGA_LEGENDARY -> TokenLook("Mega Legendary", RaidMega, "M★")
        RaidTier.PRIMAL -> TokenLook("Primal", RaidPrimal, "P")
        RaidTier.SHADOW_1, RaidTier.SHADOW_3, RaidTier.SHADOW_5 ->
            TokenLook(tier.displayLabel, RaidShadow, tier.displayLabel.substringAfter(' '))
        RaidTier.ELITE -> TokenLook("Elite", RaidSpecial, "★")
        RaidTier.ULTRA_BEAST -> TokenLook("Ultra Beast", RaidSpecial, "UB")
        null -> TokenLook(token, Neutral, token.take(1).uppercase())
    }
}

/** A grunt type in its type colour with its initial; "Mixed", leaders and the unknown get Rocket's R. */
internal fun rocketTypeLook(token: String): TokenLook {
    val hue = typeColor(token)
    return if (hue != null) {
        TokenLook(typeLabel(token) ?: token, hue, token.trim().take(1).uppercase(), tint = hue)
    } else {
        TokenLook(token, Neutral, "R")
    }
}
