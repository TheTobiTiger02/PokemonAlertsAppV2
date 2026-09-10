package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.MAX_FILTER_PROFILE_NAME
import kotlinx.serialization.Serializable

/**
 * Hunts you have run before, so the second Dragon-grunt hunt is one tap.
 *
 * Deliberately not [com.example.pokemonalertsv2.data.FilterProfile]: a profile is
 * something a *surface* is assigned, and a hunt has no persistent surface to
 * assign it to. Sharing that library would also put hunts in Filter Studio's
 * profile picker, where deleting one would quietly break a hunt, and a profile
 * left at "all alert types" reads to [chosenTypes] as nothing chosen at all.
 *
 * The list is a record of what you have hunted rather than something to set up in
 * advance, so entries are written by starting a hunt — there is no Save button.
 */
@Serializable
data class SavedHunt(
    val id: String,
    val name: String,
    val definition: FilterDefinition,
    val savedAtMillis: Long,
    val lastUsedAtMillis: Long = savedAtMillis
)

/** Past this, the list stops being a shortcut and becomes another thing to read. */
internal const val MAX_SAVED_HUNTS = 12

/** Most recently used first — the order the picker lists them in. */
internal fun savedHuntOrder(hunts: List<SavedHunt>): List<SavedHunt> =
    hunts.sortedWith(compareByDescending<SavedHunt> { it.lastUsedAtMillis }.thenBy { it.name })

/**
 * Records a hunt that has just been started.
 *
 * An identical definition is touched rather than duplicated, so starting "Dragon
 * grunts" ten times leaves one row rather than ten. Matching is on the definition,
 * not the name: two hunts that look for the same thing *are* the same hunt however
 * they were named.
 *
 * [replacingId] is the row a hunt was opened from. A hunt edited and started again
 * overwrites that row rather than leaving a near-duplicate beside the original.
 */
internal fun recordStartedHunt(
    existing: List<SavedHunt>,
    name: String,
    definition: FilterDefinition,
    nowMillis: Long,
    id: String,
    replacingId: String? = null,
    maxEntries: Int = MAX_SAVED_HUNTS
): Pair<List<SavedHunt>, SavedHunt> {
    val replacing = replacingId?.let { target -> existing.firstOrNull { it.id == target } }
    val match = replacing ?: existing.firstOrNull { it.definition == definition }
    val row = match?.copy(
        name = if (match.definition == definition) match.name else name.trimmedHuntName(),
        definition = definition,
        lastUsedAtMillis = nowMillis
    ) ?: SavedHunt(
        id = id,
        name = name.trimmedHuntName(),
        definition = definition,
        savedAtMillis = nowMillis,
        lastUsedAtMillis = nowMillis
    )
    val merged = existing.filterNot { it.id == row.id } + row
    return savedHuntOrder(merged).take(maxEntries) to row
}

internal fun renameSavedHunt(existing: List<SavedHunt>, id: String, name: String): List<SavedHunt> {
    val unique = uniqueHuntName(name, existing, replacingId = id)
    return existing.map { if (it.id == id) it.copy(name = unique) else it }
}

internal fun removeSavedHunt(existing: List<SavedHunt>, id: String): List<SavedHunt> =
    existing.filterNot { it.id == id }

/**
 * A name no other saved hunt is already using, suffixed the way Filter Studio's
 * profile names are.
 */
internal fun uniqueHuntName(
    requested: String,
    existing: List<SavedHunt>,
    replacingId: String? = null
): String {
    val base = requested.trimmedHuntName().ifEmpty { "Hunt" }
    val taken = existing.filterNot { it.id == replacingId }.mapTo(mutableSetOf()) { it.name }
    if (base !in taken) return base
    var suffix = 2
    while (true) {
        // The base is shortened to make room for the suffix rather than the
        // suffix being truncated away, which would loop forever on a long name.
        val tail = " ($suffix)"
        val candidate = base.take(MAX_FILTER_PROFILE_NAME - tail.length) + tail
        if (candidate !in taken) return candidate
        suffix++
    }
}

private fun String.trimmedHuntName(): String = trim().take(MAX_FILTER_PROFILE_NAME)
