package com.example.pokemonalertsv2.raidwatch

import com.example.pokemonalertsv2.data.counters.PokemonGoSearch
import kotlinx.serialization.Serializable

/**
 * The suggested six, flattened for the raid Live Update.
 *
 * A promoted ongoing notification may not carry custom RemoteViews, so the Live Update is text
 * only -- no sprites, no metric bars, no expandable rows. Everything the counters screen shows
 * beyond a name and a moveset is unusable here, which is why this is a handful of strings
 * rather than a reference to [com.example.pokemonalertsv2.data.counters.PersonalTeamSlot].
 * Those domain types are not serializable either, so they could not be persisted as-is.
 *
 * Computed once by [RaidTeamPrefetcher] when the journey reaches the gym, then read by both
 * the notification builder and the Copy team action.
 */
@Serializable
data class RaidTeamSnapshot(
    /** Guards against showing one raid's team on another's notification. */
    val alertUniqueId: String,
    val members: List<RaidTeamMember> = emptyList(),
    /** What Copy team writes: compact species, CP, fast-move and charged-move groups. */
    val goQuery: String = "",
    /** The stale-roster fallback retained for snapshots written by older app versions. */
    val speciesQuery: String = "",
    /**
     * Why there is no team, when there is none. An empty snapshot is still saved so the
     * notification can tell "nothing to copy" apart from "not computed yet".
     */
    val note: String? = null,
    val computedAtMillis: Long = 0L
) {
    val hasTeam: Boolean get() = members.isNotEmpty() && clipboardQuery.isNotBlank()

    /**
     * Compatibility guard for a snapshot written by the older MonGO-expanded query builder.
     * New compact queries stay far below this threshold.
     */
    val exactQueryTooLong: Boolean
        get() = goQuery.length > MAX_EXACT_QUERY_CHARS && speciesQuery.isNotBlank()

    /**
     * What Copy team actually writes: the exact query when it is pasteable, and otherwise the
     * species list, which [PokemonGoSearch.speciesQuery] documents as the escape hatch for
     * exactly this. A query nobody can paste is worth less than a coarser one they can.
     */
    val clipboardQuery: String
        get() = if (exactQueryTooLong) speciesQuery else goQuery

    companion object {
        /**
         * Comfortably above the largest exact query seen in practice (the clipboard Compose
         * test pins one at 2,316 characters) and far below the point where pasting stops
         * being realistic.
         */
        const val MAX_EXACT_QUERY_CHARS = 4_096
    }
}

/** One row of the suggested six, reduced to what a notification line can show. */
@Serializable
data class RaidTeamMember(
    val displayName: String,
    /** How many copies of this Pokemon the team brings. */
    val count: Int,
    val fastMove: String,
    val chargedMove: String
) {
    val moveLine: String get() = "$fastMove / $chargedMove"
}
