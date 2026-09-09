package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.alertCategories
import com.example.pokemonalertsv2.ui.alerts.displayCp

/**
 * The one fact worth reading from the status bar once you are close enough to
 * see the alert in the game.
 *
 * Up to this point the chip counts down the distance, which is the only useful
 * thing while walking. Once in range the distance stops mattering — you are
 * looking at the thing — and what you actually need is whatever decides your
 * next tap: the CP of a spawn, the stop a grunt is sitting on.
 *
 * Raids are deliberately absent. Arriving at a raid hands off to the raid Live
 * Update, whose own chip already carries the hundo CPs, so returning anything
 * here would only fight it for the same slot.
 *
 * Returns null when the alert carries nothing better than "In range", which is
 * what the caller falls back to; the chip is never blank.
 */
internal fun huntInRangeChipText(alert: PokemonAlert): String? {
    val categories = alert.alertCategories()
    if (AlertCategory.RAID in categories) return null

    return when {
        AlertCategory.ROCKET in categories || AlertCategory.KECLEON in categories ->
            alert.venueName?.let(::truncateForChip)

        AlertCategory.QUEST in categories ->
            alert.questReward?.trim()?.takeIf { it.isNotEmpty() }?.let(::truncateForChip)
                ?: alert.venueName?.let(::truncateForChip)

        else -> alert.displayCp?.let { "CP $it" }
    }
}

/**
 * The chip is a handful of characters wide before the system elides it, and a
 * silent truncation reads better there than a hard cut mid-word.
 */
private fun truncateForChip(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.length <= CHIP_MAX_CHARS) return trimmed
    return trimmed.take(CHIP_MAX_CHARS - 1).trimEnd() + "…"
}

private const val CHIP_MAX_CHARS = 18
