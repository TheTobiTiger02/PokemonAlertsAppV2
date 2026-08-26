package com.example.pokemonalertsv2.data.sim

import com.example.pokemonalertsv2.data.counters.CounterMetric

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

    /** Only one Mega Evolution can be active at a time in game. */
    const val MAX_MEGAS = 1

    fun <T> rank(
        candidates: List<Pair<T, SimAttacker>>,
        boss: SimBoss,
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0,
        metric: CounterMetric = CounterMetric.OVERALL,
        trials: Int = 1,
        bosses: List<SimBoss> = emptyList()
    ): List<RankedOwned<T>> {
        val targets = bosses.ifEmpty { listOf(boss) }

        // Imports commonly contain many identical copies. Their simulation result is fully
        // determined by the attacker and the shared battle setup, so reusing it keeps large
        // rosters responsive without changing the ranking or the six-copy team rules.
        val resultCache = HashMap<SimAttacker, SimResult>()
        return candidates
            .map { (owned, attacker) ->
                val result = resultCache.getOrPut(attacker) {
                    if (trials > 1) {
                        RaidSimulator.simulateTrials(
                            attacker,
                            targets,
                            weather,
                            friendshipMultiplier,
                            dodgeFraction,
                            trials
                        )
                    } else if (targets.size == 1) {
                        RaidSimulator.simulate(attacker, targets.first(), weather, friendshipMultiplier, dodgeFraction)
                    } else {
                        averageResults(targets.map { target ->
                            RaidSimulator.simulate(attacker, target, weather, friendshipMultiplier, dodgeFraction)
                        })
                    }
                }
                RankedOwned(owned = owned, attacker = attacker, result = result)
            }
            .sortedWith(
                when (metric) {
                    CounterMetric.ESTIMATOR,
                    CounterMetric.TIME -> compareBy<RankedOwned<T>> { it.result.metricValue(metric) }
                    CounterMetric.OVERALL,
                    CounterMetric.POWER,
                    CounterMetric.TDO -> compareByDescending { it.result.metricValue(metric) }
                }
            )
    }

    /**
     * Takes the strongest [teamSize], allowing at most one mega or primal.
     *
     * Only one Mega Evolution can be active at a time in game, so a team of six megas is not
     * a team the user could actually bring. Primals occupy the same slot.
     */
    fun <T> buildTeam(
        ranked: List<RankedOwned<T>>,
        boss: SimBoss,
        teamSize: Int = TEAM_SIZE,
        maxMegas: Int = MAX_MEGAS
    ): SuggestedTeam<T> {
        val members = mutableListOf<RankedOwned<T>>()
        var megas = 0
        for (candidate in ranked) {
            if (members.size >= teamSize) break
            val isMega = candidate.attacker.species.pokemonId.isMegaOrPrimal()
            if (isMega && megas >= maxMegas) continue
            if (isMega) megas++
            members += candidate
        }
        return SuggestedTeam(
            members = members.toList(),
            combinedTdo = members.sumOf { it.result.tdo },
            bossHp = boss.hp
        )
    }

    private fun averageResults(results: List<SimResult>): SimResult {
        if (results.size == 1) return results.first()
        fun avg(value: (SimResult) -> Double) = results.map(value).average()
        return SimResult(
            dps = avg { it.dps },
            tdo = avg { it.tdo },
            survivalSeconds = avg { it.survivalSeconds },
            effectiveHp = avg { it.effectiveHp.toDouble() }.toInt(),
            rating = avg { it.rating },
            estimatedAttackers = avg { it.estimatedAttackers },
            estimator = avg { it.estimator },
            overallPercent = avg { it.overallPercent },
            powerPercent = avg { it.powerPercent },
            deaths = avg { it.deaths },
            timeToWinSeconds = avg { it.timeToWinSeconds },
            totalDamageWithinTimer = avg { it.totalDamageWithinTimer }
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

private fun String.isMegaOrPrimal(): Boolean =
    contains("_MEGA", ignoreCase = true) || contains("_PRIMAL", ignoreCase = true)

/** Identical Pokemon in a suggested team, shown as one row with a count. */
data class TeamGroup<T>(
    val representative: RankedOwned<T>,
    val count: Int,
    val copies: List<RankedOwned<T>>
)
