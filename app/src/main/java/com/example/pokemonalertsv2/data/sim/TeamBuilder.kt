package com.example.pokemonalertsv2.data.sim

/** One of the user's Pokemon, scored against a specific boss. */
data class RankedOwned<T>(
    val owned: T,
    val attacker: SimAttacker,
    val result: SimResult
)

/** A suggested six, plus what it is expected to achieve. */
data class SuggestedTeam<T>(
    val members: List<RankedOwned<T>>,
    /** Combined damage of the six, before any relobby. */
    val combinedTdo: Double,
    val bossHp: Int
) {
    /** Fraction of the boss the team removes in one run of six. */
    val bossFraction: Double get() = if (bossHp > 0) (combinedTdo / bossHp).coerceAtMost(1.0) else 0.0

    /** True when six of these can take the boss down without relobbying. */
    val canSolo: Boolean get() = combinedTdo >= bossHp
}

/**
 * Picks the strongest six from a collection.
 *
 * Duplicates are deliberately allowed: a raid party is six Pokemon, so if the best answer
 * is five Shadow Tyranitar then that is what the user should bring, and each of their five
 * copies is scored separately at its own level, IVs and moveset. Only distinct scanned
 * Pokemon are used — the same copy is never suggested twice.
 */
object TeamBuilder {

    const val TEAM_SIZE = 6

    fun <T> rank(
        candidates: List<Pair<T, SimAttacker>>,
        boss: SimBoss,
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0
    ): List<RankedOwned<T>> = candidates
        .map { (owned, attacker) ->
            RankedOwned(
                owned = owned,
                attacker = attacker,
                result = RaidSimulator.simulate(attacker, boss, weather, friendshipMultiplier, dodgeFraction)
            )
        }
        .sortedByDescending { it.result.rating }

    fun <T> buildTeam(
        ranked: List<RankedOwned<T>>,
        boss: SimBoss,
        teamSize: Int = TEAM_SIZE
    ): SuggestedTeam<T> {
        val members = ranked.take(teamSize)
        return SuggestedTeam(
            members = members,
            combinedTdo = members.sumOf { it.result.tdo },
            bossHp = boss.hp
        )
    }

    /**
     * Collapses a team into "Shadow Tyranitar x3" style groups, preserving order.
     *
     * Copies of the same species are grouped only when they would be interchangeable to the
     * user, i.e. same species and same moveset.
     */
    fun <T> groupTeam(members: List<RankedOwned<T>>): List<TeamGroup<T>> {
        val groups = LinkedHashMap<String, MutableList<RankedOwned<T>>>()
        members.forEach { member ->
            val key = listOf(
                member.attacker.species.pokemonId,
                member.attacker.fastMove.moveId,
                member.attacker.chargedMove.moveId
            ).joinToString("|")
            groups.getOrPut(key) { mutableListOf() }.add(member)
        }
        return groups.values.map { copies ->
            TeamGroup(
                representative = copies.first(),
                count = copies.size,
                copies = copies
            )
        }
    }
}

/** Identical Pokemon in a suggested team, shown as one row with a count. */
data class TeamGroup<T>(
    val representative: RankedOwned<T>,
    val count: Int,
    val copies: List<RankedOwned<T>>
)
