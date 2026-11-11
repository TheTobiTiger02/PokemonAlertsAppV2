package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.tracking.alertDetailLines
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.alertCategories
import com.example.pokemonalertsv2.ui.alerts.displayCp
import com.example.pokemonalertsv2.ui.alerts.questAlertPresentation

/**
 * The one fact worth reading from the status bar once you are close enough to
 * see the alert in the game.
 *
 * Up to this point the chip counts down the distance, which is the only useful
 * thing while walking. Once in range the distance stops mattering — you are
 * looking at the thing — and what you actually need is whatever decides your
 * next tap: the CP of a spawn, the stop a grunt is sitting on.
 *
 * For anything sitting on a PokeStop that is the **stop's name**, reward or no
 * reward: at ten metres you are looking for a stop, not for a thing you already
 * know you came for. The reward and the task moved to the expanded card, which
 * is what [huntInRangeLines] fills.
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
        alert.isVenueAlert(categories) -> alert.venueName?.let(::truncateForChip)
        else -> alert.displayCp?.let { "CP $it" }
    }
}

/**
 * The headline for an alert you have arrived at.
 *
 * "Going to Brunnen" stops being true the moment you are standing at it, and the
 * name of the place is what you are scanning the street for — so at a stop the title
 * becomes the stop. Everything else keeps the caller's own wording, which is why this
 * returns null rather than inventing one; raids do too, so nothing competes with the
 * raid Live Update.
 */
internal fun huntInRangeTitle(alert: PokemonAlert): String? {
    val categories = alert.alertCategories()
    if (AlertCategory.RAID in categories) return null
    if (!alert.isVenueAlert(categories)) return null
    return alert.venueName?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * What the expanded notification says once you are in range.
 *
 * While walking the card is a distance and a progress bar, both of which are spent
 * the moment you arrive. What replaces them is the thing you pulled the shade down
 * for: the task you have to do, the reward it pays, which grunt you are about to
 * fight. Built on [alertDetailLines] so the live card and the arrival heads-up
 * cannot describe the same alert two different ways.
 *
 * Empty for a raid, and for anything that carries nothing worth a second line; the
 * caller keeps its own body then.
 */
internal fun huntInRangeLines(
    alert: PokemonAlert,
    nowMillis: Long = System.currentTimeMillis()
): List<String> {
    val categories = alert.alertCategories()
    if (AlertCategory.RAID in categories) return emptyList()

    val lead = buildList {
        val quest = questAlertPresentation(alert)
        if (quest != null) {
            quest.task?.let(::add)
            quest.reward?.let { add("Reward: $it") }
            if (quest.requiresAr) add("AR required")
        }
        if (AlertCategory.ROCKET in categories) {
            val grunt = alert.gruntType?.trim()?.takeIf { it.isNotEmpty() }
            add(if (grunt != null) "$grunt grunt" else "Team GO Rocket")
        }
        if (AlertCategory.KECLEON in categories) add("Kecleon")
        // The venue is the title for these, so it is only worth a line when the
        // title could not take it -- which is also when you most need to know
        // which kind of place you are looking for.
        if (alert.isVenueAlert(categories) && huntInRangeTitle(alert) == null) {
            alert.venueTypeLabel?.let(::add)
        }
    }

    // Deduplicated against the lead: a quest's task and reward are in both lists by
    // design, and repeating them under the heading they already form reads as a bug.
    val details = alertDetailLines(alert, nowMillis).filterNot { detail ->
        lead.any { it.equals(detail, ignoreCase = true) || it.endsWith(": $detail") }
    }
    return lead + details
}

/**
 * Whether this is an alert you find by the name of the place it is at, rather than by
 * the creature standing there. Quests, Rockets and Kecleon all are.
 */
private fun PokemonAlert.isVenueAlert(categories: Set<AlertCategory>): Boolean =
    AlertCategory.QUEST in categories ||
        AlertCategory.ROCKET in categories ||
        AlertCategory.KECLEON in categories

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
