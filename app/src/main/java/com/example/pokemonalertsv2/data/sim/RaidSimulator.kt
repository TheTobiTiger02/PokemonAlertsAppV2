package com.example.pokemonalertsv2.data.sim

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import com.example.pokemonalertsv2.data.counters.CounterMetric

/** A move, reduced to what the damage model needs. */
data class SimMove(
    val moveId: String,
    val type: PokemonType?,
    val power: Double,
    val durationSeconds: Double,
    /** Positive for fast moves (energy gained), negative for charged moves (energy spent). */
    val energyDelta: Int,
    /** Damage is applied during this window rather than at the start of the animation. */
    val damageWindowStartSeconds: Double = 0.0,
    val damageWindowEndSeconds: Double = 0.0
) {
    val isFast: Boolean get() = energyDelta > 0
}

/** Species-level data needed to build an attacker or a boss. */
data class SimSpecies(
    val pokemonId: String,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseStamina: Int,
    val types: List<PokemonType>
)

/** One concrete Pokemon the user owns, at its real level, IVs and moveset. */
data class SimAttacker(
    val species: SimSpecies,
    val level: Double,
    val atkIv: Int,
    val defIv: Int,
    val staIv: Int,
    val fastMove: SimMove,
    val chargedMove: SimMove,
    val shadow: Boolean = false
)

/** The raid boss: fixed HP and CPM from its tier. */
data class SimBoss(
    val species: SimSpecies,
    val cpm: Double,
    val hp: Int,
    val fastMove: SimMove?,
    val chargedMove: SimMove?,
    val combatTimeSeconds: Double = 180.0
)

/** What the simulator reports for one attacker against one boss. */
data class SimResult(
    val dps: Double,
    /** Total damage this Pokemon deals before fainting. */
    val tdo: Double,
    /** Seconds it survives against the boss. */
    val survivalSeconds: Double,
    val effectiveHp: Int,
    /** sqrt(dps * tdo) — balances raw damage against staying alive. */
    val rating: Double,
    /** Roughly how many of this Pokemon it would take to clear the raid. */
    val estimatedAttackers: Double,
    /** Pokebattler-style trainer estimator (one trainer has six copies). */
    val estimator: Double = estimatedAttackers / 6.0,
    val overallPercent: Double = 0.0,
    val powerPercent: Double = 0.0,
    val deaths: Double = 0.0,
    val timeToWinSeconds: Double = 0.0,
    /** Damage this attacker contributes during the complete timer, including re-lobbies. */
    val totalDamageWithinTimer: Double = tdo
) {
    fun metricValue(metric: CounterMetric): Double = when (metric) {
        CounterMetric.OVERALL -> overallPercent
        CounterMetric.ESTIMATOR -> estimator
        CounterMetric.TIME -> timeToWinSeconds
        CounterMetric.POWER -> powerPercent
        CounterMetric.TDO -> tdo
    }
}

/** Result of simulating the user's actual sequential team of up to six Pokemon. */
data class TeamSimulationResult(
    val totalDamageWithinTimer: Double,
    val deaths: Double,
    val timeToWinSeconds: Double?
)

/**
 * Deterministic Pokémon GO damage and event-battle simulation.
 *
 * [simulate] remains a cheap exact-cycle calculation for move selection and compatibility.
 * [simulateTrials] and [simulateTeamTrials] replay timed actions with seeded variation so
 * personal rankings account for damage windows, energy, fainting and relobbies while staying
 * reproducible off the UI thread. This is calibrated to Pokébattler's metrics, not a
 * replacement for its server-side Monte Carlo implementation.
 *
 * Shadow Pokémon get the usual 1.2x attack and take 1.2x damage.
 */
object RaidSimulator {

    private const val SHADOW_ATTACK = 1.2
    private const val SHADOW_DEFENSE_PENALTY = 1.2

    /** Damage of one hit. The floor and the +1 are both part of the game formula. */
    fun damagePerHit(
        move: SimMove,
        attack: Double,
        defense: Double,
        attackerTypes: List<PokemonType>,
        defenderTypes: List<PokemonType>,
        weatherBoosted: Boolean = false,
        friendshipMultiplier: Double = 1.0
    ): Int {
        if (defense <= 0.0) return 1
        val stab = if (move.type != null && attackerTypes.contains(move.type)) TypeChart.STAB else 1.0
        val effectiveness = TypeChart.effectiveness(move.type, defenderTypes)
        val weather = if (weatherBoosted) 1.2 else 1.0
        val raw = 0.5 * move.power * (attack / defense) * stab * effectiveness * weather * friendshipMultiplier
        return floor(raw).toInt() + 1
    }

    fun attackStat(species: SimSpecies, level: Double, iv: Int, shadow: Boolean): Double {
        val base = (species.baseAttack + iv) * CpmTable.forLevel(level)
        return if (shadow) base * SHADOW_ATTACK else base
    }

    fun defenseStat(species: SimSpecies, level: Double, iv: Int, shadow: Boolean): Double {
        val base = (species.baseDefense + iv) * CpmTable.forLevel(level)
        // Shadows take more damage, modelled here as reduced effective defence.
        return if (shadow) base / SHADOW_DEFENSE_PENALTY else base
    }

    fun staminaStat(species: SimSpecies, level: Double, iv: Int): Int =
        floor((species.baseStamina + iv) * CpmTable.forLevel(level)).toInt()

    /**
     * Damage per second from repeating one fast/charged cycle.
     *
     * The attacker throws just enough fast moves to afford the charged move, then uses it.
     * A charged move the fast move can never pay for degrades to fast-only DPS.
     */
    fun cycleDps(
        fastDamage: Int,
        fastDuration: Double,
        fastEnergy: Int,
        chargedDamage: Int,
        chargedDuration: Double,
        chargedCost: Int
    ): Double {
        val fastOnly = if (fastDuration > 0) fastDamage / fastDuration else 0.0
        if (fastEnergy <= 0 || chargedCost <= 0 || chargedDuration <= 0) return fastOnly

        val fastMovesNeeded = ceil(chargedCost.toDouble() / fastEnergy).toInt()
        val cycleTime = fastMovesNeeded * fastDuration + chargedDuration
        if (cycleTime <= 0) return fastOnly
        val cycleDamage = fastMovesNeeded.toDouble() * fastDamage + chargedDamage
        return cycleDamage / cycleTime
    }

    fun simulate(
        attacker: SimAttacker,
        boss: SimBoss,
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0
    ): SimResult {
        val atk = attackStat(attacker.species, attacker.level, attacker.atkIv, attacker.shadow)
        val def = defenseStat(attacker.species, attacker.level, attacker.defIv, attacker.shadow)
        val hp = staminaStat(attacker.species, attacker.level, attacker.staIv)

        val bossAtk = (boss.species.baseAttack + MAX_IV) * boss.cpm
        val bossDef = (boss.species.baseDefense + MAX_IV) * boss.cpm

        val fastDamage = damagePerHit(
            attacker.fastMove, atk, bossDef, attacker.species.types, boss.species.types,
            weather.boosts(attacker.fastMove.type), friendshipMultiplier
        )
        val chargedDamage = damagePerHit(
            attacker.chargedMove, atk, bossDef, attacker.species.types, boss.species.types,
            weather.boosts(attacker.chargedMove.type), friendshipMultiplier
        )

        val dps = cycleDps(
            fastDamage = fastDamage,
            fastDuration = attacker.fastMove.durationSeconds,
            fastEnergy = attacker.fastMove.energyDelta,
            chargedDamage = chargedDamage,
            chargedDuration = attacker.chargedMove.durationSeconds,
            chargedCost = -attacker.chargedMove.energyDelta
        )

        val incoming = incomingDps(boss, bossAtk, def, attacker.species.types, weather)
            .let { it * (1.0 - dodgeFraction.coerceIn(0.0, MAX_DODGE_REDUCTION)) }

        val survival = if (incoming > 0) hp / incoming else boss.combatTimeSeconds
        // Nobody keeps attacking past the raid timer.
        val effectiveSurvival = min(survival, boss.combatTimeSeconds)
        val tdo = dps * effectiveSurvival

        return SimResult(
            dps = dps,
            tdo = tdo,
            survivalSeconds = effectiveSurvival,
            effectiveHp = hp,
            rating = sqrt(max(0.0, dps) * max(0.0, tdo)),
            estimatedAttackers = if (tdo > 0) boss.hp / tdo else Double.MAX_VALUE,
            estimator = if (tdo > 0) boss.hp / (tdo * 6.0) else Double.MAX_VALUE,
            overallPercent = overallPercent(
                boss,
                if (boss.hp > 0) tdo * 6.0 / boss.hp * 100.0 else 0.0,
                if (dps > 0) boss.hp / (dps * 6.0) else Double.MAX_VALUE
            ),
            powerPercent = if (boss.hp > 0) tdo * 6.0 / boss.hp * 100.0 else 0.0,
            timeToWinSeconds = if (dps > 0) boss.hp / (dps * 6.0) else Double.MAX_VALUE
        )
    }

    /**
     * Runs a small deterministic battle sample. The seeded trials keep a raid stable while
     * accounting for timing, energy, fainting, rejoining and the raid timer.
     */
    fun simulateTrials(
        attacker: SimAttacker,
        boss: SimBoss,
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0,
        trials: Int = DEFAULT_TRIALS,
        seed: Long = stableSeed(attacker, boss, weather, friendshipMultiplier, dodgeFraction)
    ): SimResult {
        val trialCount = trials.coerceIn(1, MAX_TRIALS)
        var totalDamage = 0.0
        var totalDps = 0.0
        var totalTdo = 0.0
        var totalSurvival = 0.0
        var totalTime = 0.0
        var totalDeaths = 0.0
        var completedTrials = 0
        repeat(trialCount) { index ->
            val sample = simulateBattle(
                attacker = attacker,
                boss = boss,
                weather = weather,
                friendshipMultiplier = friendshipMultiplier,
                dodgeFraction = dodgeFraction,
                random = BattleRandom(seed + index)
            )
            totalDamage += sample.totalDamage
            totalDps += sample.dps
            totalTdo += sample.oneLifeDamage
            totalSurvival += sample.firstFaintSeconds
            totalTime += sample.timeToWinSeconds ?: boss.combatTimeSeconds
            totalDeaths += sample.deaths
            completedTrials++
        }
        val count = completedTrials.toDouble().coerceAtLeast(1.0)
        val damage = totalDamage / count
        val dps = totalDps / count
        val tdo = totalTdo / count
        val survival = totalSurvival / count
        val time = totalTime / count
        val power = if (boss.hp > 0) damage * 6.0 / boss.hp * 100.0 else 0.0
        val estimator = if (damage > 0.0) boss.hp / (damage * 6.0) else Double.MAX_VALUE
        return SimResult(
            dps = dps,
            tdo = tdo,
            survivalSeconds = survival.coerceAtMost(boss.combatTimeSeconds),
            effectiveHp = staminaStat(attacker.species, attacker.level, attacker.staIv),
            rating = sqrt(max(0.0, dps) * max(0.0, tdo)),
            estimatedAttackers = if (tdo > 0.0) boss.hp / tdo else Double.MAX_VALUE,
            estimator = estimator,
            overallPercent = overallPercent(boss, power, time),
            powerPercent = power,
            deaths = totalDeaths / count,
            timeToWinSeconds = time,
            totalDamageWithinTimer = damage
        )
    }

    /**
     * Simulates a uniformly sampled set of concrete boss movesets. The selection and event
     * randomness share one seeded generator, so the same roster/setup always produces the same
     * result while every returned moveset participates in the requested trial budget.
     */
    fun simulateTrials(
        attacker: SimAttacker,
        bosses: List<SimBoss>,
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0,
        trials: Int = DEFAULT_TRIALS,
        seed: Long = stableSeed(attacker, bosses, weather, friendshipMultiplier, dodgeFraction)
    ): SimResult {
        val targets = bosses.ifEmpty { return simulateTrials(attacker, SimBoss(
            species = SimSpecies("UNKNOWN_BOSS", 0, 0, 1, emptyList()),
            cpm = 0.0,
            hp = 1,
            fastMove = null,
            chargedMove = null
        ), weather, friendshipMultiplier, dodgeFraction, trials, seed) }
        if (targets.size == 1) {
            return simulateTrials(attacker, targets.first(), weather, friendshipMultiplier, dodgeFraction, trials, seed)
        }
        val trialCount = trials.coerceIn(1, MAX_TRIALS)
        val random = BattleRandom(seed)
        var totalDamage = 0.0
        var totalDps = 0.0
        var totalTdo = 0.0
        var totalSurvival = 0.0
        var totalTime = 0.0
        var totalDeaths = 0.0
        var totalPower = 0.0
        var totalEstimator = 0.0
        var totalEstimatedAttackers = 0.0
        var totalOverall = 0.0
        repeat(trialCount) {
            val target = targets[random.nextInt(targets.size)]
            val sample = simulateBattle(
                attacker = attacker,
                boss = target,
                weather = weather,
                friendshipMultiplier = friendshipMultiplier,
                dodgeFraction = dodgeFraction,
                random = random
            )
            val time = sample.timeToWinSeconds ?: target.combatTimeSeconds
            val power = if (target.hp > 0) sample.totalDamage * 6.0 / target.hp * 100.0 else 0.0
            totalDamage += sample.totalDamage
            totalDps += sample.dps
            totalTdo += sample.oneLifeDamage
            totalSurvival += sample.firstFaintSeconds.coerceAtMost(target.combatTimeSeconds)
            totalTime += time
            totalDeaths += sample.deaths
            totalPower += power
            totalEstimator += if (sample.totalDamage > 0.0) {
                target.hp / (sample.totalDamage * 6.0)
            } else {
                Double.MAX_VALUE
            }
            totalEstimatedAttackers += if (sample.oneLifeDamage > 0.0) {
                target.hp / sample.oneLifeDamage
            } else {
                Double.MAX_VALUE
            }
            totalOverall += overallPercent(target, power, time)
        }
        val count = trialCount.toDouble()
        val damage = totalDamage / count
        val dps = totalDps / count
        val tdo = totalTdo / count
        return SimResult(
            dps = dps,
            tdo = tdo,
            survivalSeconds = totalSurvival / count,
            effectiveHp = staminaStat(attacker.species, attacker.level, attacker.staIv),
            rating = sqrt(max(0.0, dps) * max(0.0, tdo)),
            estimatedAttackers = totalEstimatedAttackers / count,
            estimator = totalEstimator / count,
            overallPercent = totalOverall / count,
            powerPercent = totalPower / count,
            deaths = totalDeaths / count,
            timeToWinSeconds = totalTime / count,
            totalDamageWithinTimer = damage
        )
    }

    /**
     * Simulates a real six-slot raid party, rather than six identical copies used for a
     * Pokebattler-style individual metric. Pokemon enter one at a time, keep their own energy,
     * advance when they faint, and the party relobbies with the same six after all have fainted.
     */
    fun simulateTeamTrials(
        team: List<SimAttacker>,
        boss: SimBoss,
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0,
        trials: Int = DEFAULT_TRIALS,
        seed: Long = stableTeamSeed(team, boss, weather, friendshipMultiplier, dodgeFraction)
    ): TeamSimulationResult {
        if (team.isEmpty()) return TeamSimulationResult(0.0, 0.0, null)
        val samples = (0 until trials.coerceIn(1, MAX_TRIALS)).map { index ->
            simulateTeamBattle(
                team = team,
                boss = boss,
                weather = weather,
                friendshipMultiplier = friendshipMultiplier,
                dodgeFraction = dodgeFraction,
                random = BattleRandom(seed + index)
            )
        }
        return TeamSimulationResult(
            totalDamageWithinTimer = samples.map { it.totalDamageWithinTimer }.average(),
            deaths = samples.map { it.deaths }.average(),
            timeToWinSeconds = samples.mapNotNull { it.timeToWinSeconds }
                .takeIf { it.size == samples.size }
                ?.average()
        )
    }

    /** Six-slot counterpart to [simulateTrials] with uniform concrete moveset sampling. */
    fun simulateTeamTrials(
        team: List<SimAttacker>,
        bosses: List<SimBoss>,
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0,
        trials: Int = DEFAULT_TRIALS,
        seed: Long = stableTeamSeed(team, bosses, weather, friendshipMultiplier, dodgeFraction)
    ): TeamSimulationResult {
        if (team.isEmpty() || bosses.isEmpty()) return TeamSimulationResult(0.0, 0.0, null)
        if (bosses.size == 1) {
            return simulateTeamTrials(team, bosses.first(), weather, friendshipMultiplier, dodgeFraction, trials, seed)
        }
        val trialCount = trials.coerceIn(1, MAX_TRIALS)
        val random = BattleRandom(seed)
        var totalDamage = 0.0
        var totalDeaths = 0.0
        var totalTime = 0.0
        var wins = 0
        repeat(trialCount) {
            val target = bosses[random.nextInt(bosses.size)]
            val sample = simulateTeamBattle(
                team = team,
                boss = target,
                weather = weather,
                friendshipMultiplier = friendshipMultiplier,
                dodgeFraction = dodgeFraction,
                random = random
            )
            totalDamage += sample.totalDamageWithinTimer
            totalDeaths += sample.deaths
            sample.timeToWinSeconds?.let {
                wins++
                totalTime += it
            }
        }
        val count = trialCount.toDouble()
        return TeamSimulationResult(
            totalDamageWithinTimer = totalDamage / count,
            deaths = totalDeaths / count,
            timeToWinSeconds = if (wins == trialCount) totalTime / count else null
        )
    }

    private data class BattleSample(
        val totalDamage: Double,
        val oneLifeDamage: Double,
        val dps: Double,
        val firstFaintSeconds: Double,
        val deaths: Int,
        val timeToWinSeconds: Double?
    )

    private data class TeamBattleSample(
        val totalDamageWithinTimer: Double,
        val deaths: Int,
        val timeToWinSeconds: Double?
    )

    private data class TeamMemberState(
        val attacker: SimAttacker,
        val maxHp: Double,
        var hp: Double,
        var energy: Int = 0
    )

    /** Small deterministic generator; a raid calculation can consume millions of samples. */
    private class BattleRandom(seed: Long) {
        private var state = if (seed == 0L) 0x9E3779B97F4A7C15UL.toLong() else seed

        fun nextDouble(): Double {
            var value = state
            value = value xor (value shl 13)
            value = value xor (value ushr 7)
            value = value xor (value shl 17)
            state = value
            return (value ushr 11).toDouble() * (1.0 / (1L shl 53))
        }

        fun nextInt(bound: Int): Int =
            (nextDouble() * bound).toInt().coerceIn(0, bound - 1)
    }

    private fun simulateBattle(
        attacker: SimAttacker,
        boss: SimBoss,
        weather: WeatherBoost,
        friendshipMultiplier: Double,
        dodgeFraction: Double,
        random: BattleRandom
    ): BattleSample {
        val atk = attackStat(attacker.species, attacker.level, attacker.atkIv, attacker.shadow)
        val def = defenseStat(attacker.species, attacker.level, attacker.defIv, attacker.shadow)
        val maxHp = staminaStat(attacker.species, attacker.level, attacker.staIv).toDouble()
        val bossAtk = (boss.species.baseAttack + MAX_IV) * boss.cpm
        val bossDef = (boss.species.baseDefense + MAX_IV) * boss.cpm
        val averageIncoming = incomingDps(boss, bossAtk, def, attacker.species.types, weather)
            .let { it * (1.0 - dodgeFraction.coerceIn(0.0, MAX_DODGE_REDUCTION)) }
        val fast = attacker.fastMove
        val charged = attacker.chargedMove
        val chargedCost = -charged.energyDelta
        val fastDamage = damagePerHit(
            move = fast,
            attack = atk,
            defense = bossDef,
            attackerTypes = attacker.species.types,
            defenderTypes = boss.species.types,
            weatherBoosted = weather.boosts(fast.type),
            friendshipMultiplier = friendshipMultiplier
        )
        val chargedDamage = damagePerHit(
            move = charged,
            attack = atk,
            defense = bossDef,
            attackerTypes = attacker.species.types,
            defenderTypes = boss.species.types,
            weatherBoosted = weather.boosts(charged.type),
            friendshipMultiplier = friendshipMultiplier
        )
        var hp = maxHp
        var energy = 0
        var time = 0.0
        var totalDamage = 0.0
        var oneLifeDamage = 0.0
        var firstFaint = boss.combatTimeSeconds
        var deaths = 0
        var winTime: Double? = null
        var guard = 0
        val fastDuration = fast.durationSeconds.coerceAtLeast(0.05)
        val chargedDuration = charged.durationSeconds.coerceAtLeast(0.05)
        val fastDamageAt = (fast.damageWindowEndSeconds.takeIf { it > 0.0 } ?: fastDuration)
            .coerceIn(fast.damageWindowStartSeconds.coerceIn(0.0, fastDuration), fastDuration)
        val chargedDamageAt = (charged.damageWindowEndSeconds.takeIf { it > 0.0 } ?: chargedDuration)
            .coerceIn(charged.damageWindowStartSeconds.coerceIn(0.0, chargedDuration), chargedDuration)
        while (time < boss.combatTimeSeconds && guard++ < MAX_ACTIONS) {
            val useCharged = chargedCost > 0 && energy >= chargedCost
            val duration = if (useCharged) chargedDuration else fastDuration
            val damage = if (useCharged) chargedDamage else fastDamage
            val damageAtOffset = if (useCharged) chargedDamageAt else fastDamageAt
            val incomingJitter = 0.85 + random.nextDouble() * 0.30
            val incomingDamage = averageIncoming * duration * incomingJitter
            hp -= incomingDamage
            val startTime = time
            time += duration

            if (hp <= 0.0) {
                deaths++
                if (deaths == 1) {
                    firstFaint = time.coerceAtMost(boss.combatTimeSeconds)
                    oneLifeDamage = totalDamage
                }
                time += RELOBBY_SECONDS
                hp = maxHp
                energy = 0
                continue
            }

            val damageAt = startTime + damageAtOffset
            if (damageAt <= boss.combatTimeSeconds) {
                totalDamage += damage
                if (winTime == null && totalDamage >= boss.hp) winTime = damageAt
            }
            if (winTime != null) break
            // Pokémon GO grants roughly one energy per 2.5 HP lost. This matters for charged
            // cadence, especially for fragile attackers that take several small hits.
            val energyFromDamage = (incomingDamage / DAMAGE_TO_ENERGY).toInt()
            energy = if (useCharged) {
                (energy - chargedCost + energyFromDamage).coerceAtLeast(0).coerceAtMost(MAX_ENERGY)
            } else {
                (energy + fast.energyDelta + energyFromDamage).coerceAtMost(MAX_ENERGY)
            }
        }
        if (deaths == 0) oneLifeDamage = totalDamage
        val elapsed = time.coerceAtMost(boss.combatTimeSeconds).coerceAtLeast(0.1)
        return BattleSample(
            totalDamage = totalDamage,
            oneLifeDamage = oneLifeDamage,
            dps = totalDamage / elapsed,
            firstFaintSeconds = firstFaint,
            deaths = deaths,
            timeToWinSeconds = winTime
        )
    }

    private fun simulateTeamBattle(
        team: List<SimAttacker>,
        boss: SimBoss,
        weather: WeatherBoost,
        friendshipMultiplier: Double,
        dodgeFraction: Double,
        random: BattleRandom
    ): TeamBattleSample {
        val members = team.map { attacker ->
            val hp = staminaStat(attacker.species, attacker.level, attacker.staIv).toDouble()
            TeamMemberState(attacker = attacker, maxHp = hp, hp = hp)
        }
        var currentIndex = 0
        var time = 0.0
        var totalDamage = 0.0
        var deaths = 0
        var winTime: Double? = null
        var guard = 0

        while (time < boss.combatTimeSeconds && guard++ < MAX_ACTIONS * members.size) {
            val member = members[currentIndex]
            val attacker = member.attacker
            val atk = attackStat(attacker.species, attacker.level, attacker.atkIv, attacker.shadow)
            val def = defenseStat(attacker.species, attacker.level, attacker.defIv, attacker.shadow)
            val bossAtk = (boss.species.baseAttack + MAX_IV) * boss.cpm
            val bossDef = (boss.species.baseDefense + MAX_IV) * boss.cpm
            val incoming = incomingDps(boss, bossAtk, def, attacker.species.types, weather)
                .let { it * (1.0 - dodgeFraction.coerceIn(0.0, MAX_DODGE_REDUCTION)) }
            val fast = attacker.fastMove
            val charged = attacker.chargedMove
            val chargedCost = -charged.energyDelta
            val useCharged = chargedCost > 0 && member.energy >= chargedCost
            val move = if (useCharged) charged else fast
            val duration = move.durationSeconds.coerceAtLeast(0.05)
            val start = time
            val incomingDamage = incoming * duration * (0.85 + random.nextDouble() * 0.30)
            member.hp -= incomingDamage
            time += duration

            if (member.hp <= 0.0) {
                deaths++
                currentIndex++
                if (currentIndex >= members.size) {
                    // Rejoining resets each Pokemon's HP and energy, just like a fresh party.
                    time += RELOBBY_SECONDS
                    members.forEach { it.hp = it.maxHp; it.energy = 0 }
                    currentIndex = 0
                }
                continue
            }

            val damage = damagePerHit(
                move = move,
                attack = atk,
                defense = bossDef,
                attackerTypes = attacker.species.types,
                defenderTypes = boss.species.types,
                weatherBoosted = weather.boosts(move.type),
                friendshipMultiplier = friendshipMultiplier
            )
            val windowStart = move.damageWindowStartSeconds.coerceIn(0.0, duration)
            val windowEnd = (move.damageWindowEndSeconds.takeIf { it > 0.0 } ?: duration)
                .coerceIn(windowStart, duration)
            val damageAt = start + windowEnd
            if (damageAt <= boss.combatTimeSeconds) {
                totalDamage += damage
                if (winTime == null && totalDamage >= boss.hp) winTime = damageAt
            }
            val energyFromDamage = (incomingDamage / DAMAGE_TO_ENERGY).toInt()
            member.energy = (member.energy + energyFromDamage).coerceAtMost(MAX_ENERGY)
            member.energy = if (useCharged) {
                (member.energy - chargedCost).coerceAtLeast(0)
            } else {
                (member.energy + fast.energyDelta).coerceAtMost(MAX_ENERGY)
            }
            if (winTime != null) break
        }

        return TeamBattleSample(
            totalDamageWithinTimer = totalDamage,
            deaths = deaths,
            timeToWinSeconds = winTime
        )
    }

    private fun overallPercent(boss: SimBoss, powerPercent: Double, timeToWinSeconds: Double): Double {
        val timeQuality = if (timeToWinSeconds > 0.0 && timeToWinSeconds.isFinite()) {
            (boss.combatTimeSeconds / timeToWinSeconds * 100.0).coerceIn(0.0, 200.0)
        } else {
            0.0
        }
        return (0.333 * powerPercent + 0.667 * timeQuality).coerceAtLeast(0.0)
    }

    private fun stableSeed(
        attacker: SimAttacker,
        boss: SimBoss,
        weather: WeatherBoost,
        friendshipMultiplier: Double,
        dodgeFraction: Double
    ): Long = listOf(
        attacker.species.pokemonId,
        attacker.level,
        attacker.atkIv,
        attacker.defIv,
        attacker.staIv,
        attacker.fastMove.moveId,
        attacker.chargedMove.moveId,
        attacker.shadow,
        boss.species.pokemonId,
        boss.fastMove?.moveId,
        boss.chargedMove?.moveId,
        weather.name,
        friendshipMultiplier,
        dodgeFraction
    ).joinToString("|").hashCode().toLong()

    private fun stableSeed(
        attacker: SimAttacker,
        bosses: List<SimBoss>,
        weather: WeatherBoost,
        friendshipMultiplier: Double,
        dodgeFraction: Double
    ): Long = (listOf(
        attacker.species.pokemonId,
        attacker.level,
        attacker.atkIv,
        attacker.defIv,
        attacker.staIv,
        attacker.fastMove.moveId,
        attacker.chargedMove.moveId,
        attacker.shadow
    ) + bosses.flatMap { boss ->
        listOf(boss.species.pokemonId, boss.fastMove?.moveId, boss.chargedMove?.moveId)
    } + listOf(weather.name, friendshipMultiplier, dodgeFraction))
        .joinToString("|")
        .hashCode()
        .toLong()

    private fun stableTeamSeed(
        team: List<SimAttacker>,
        boss: SimBoss,
        weather: WeatherBoost,
        friendshipMultiplier: Double,
        dodgeFraction: Double
    ): Long = (team.map { attacker ->
        listOf(
            attacker.species.pokemonId,
            attacker.level,
            attacker.atkIv,
            attacker.defIv,
            attacker.staIv,
            attacker.fastMove.moveId,
            attacker.chargedMove.moveId,
            attacker.shadow
        ).joinToString("~")
    } + listOf(
        boss.species.pokemonId,
        boss.fastMove?.moveId,
        boss.chargedMove?.moveId,
        weather.name,
        friendshipMultiplier,
        dodgeFraction
    )).joinToString("|").hashCode().toLong()

    private fun stableTeamSeed(
        team: List<SimAttacker>,
        bosses: List<SimBoss>,
        weather: WeatherBoost,
        friendshipMultiplier: Double,
        dodgeFraction: Double
    ): Long = (team.map { attacker ->
        listOf(
            attacker.species.pokemonId,
            attacker.level,
            attacker.atkIv,
            attacker.defIv,
            attacker.staIv,
            attacker.fastMove.moveId,
            attacker.chargedMove.moveId,
            attacker.shadow
        ).joinToString("~")
    } + bosses.flatMap { boss ->
        listOf(boss.species.pokemonId, boss.fastMove?.moveId, boss.chargedMove?.moveId)
    } + listOf(weather.name, friendshipMultiplier, dodgeFraction))
        .joinToString("|")
        .hashCode()
        .toLong()

    /**
     * Damage per second the boss deals to this attacker.
     *
     * Bosses mix fast moves with an occasional charged move; this blends the two assuming a
     * charged move lands roughly every [BOSS_CHARGED_PERIOD] seconds. Falls back to a
     * neutral estimate when the boss moveset is unknown.
     */
    private fun incomingDps(
        boss: SimBoss,
        bossAttack: Double,
        attackerDefense: Double,
        attackerTypes: List<PokemonType>,
        weather: WeatherBoost
    ): Double {
        val fast = boss.fastMove ?: return DEFAULT_INCOMING_DPS
        val fastDamage = damagePerHit(
            fast, bossAttack, attackerDefense, boss.species.types, attackerTypes,
            weather.boosts(fast.type)
        )
        val fastDps = if (fast.durationSeconds > 0) fastDamage / fast.durationSeconds else 0.0

        val charged = boss.chargedMove ?: return fastDps
        val chargedDamage = damagePerHit(
            charged, bossAttack, attackerDefense, boss.species.types, attackerTypes,
            weather.boosts(charged.type)
        )
        return fastDps + chargedDamage / BOSS_CHARGED_PERIOD
    }

    private const val MAX_IV = 15
    private const val DEFAULT_INCOMING_DPS = 35.0
    private const val BOSS_CHARGED_PERIOD = 12.0
    private const val DEFAULT_TRIALS = 32
    private const val MAX_TRIALS = 64
    private const val MAX_ACTIONS = 2_000
    private const val MAX_ENERGY = 100
    private const val DAMAGE_TO_ENERGY = 2.5
    private const val RELOBBY_SECONDS = 10.0

    /** Even perfect dodging cannot avoid every hit in a real raid. */
    private const val MAX_DODGE_REDUCTION = 0.75
}

/** Which attacking types the current weather boosts. */
enum class WeatherBoost(val boostedTypes: Set<PokemonType>) {
    NONE(emptySet()),
    CLEAR(setOf(PokemonType.GRASS, PokemonType.GROUND, PokemonType.FIRE)),
    RAINY(setOf(PokemonType.WATER, PokemonType.ELECTRIC, PokemonType.BUG)),
    PARTLY_CLOUDY(setOf(PokemonType.NORMAL, PokemonType.ROCK)),
    OVERCAST(setOf(PokemonType.FAIRY, PokemonType.FIGHTING, PokemonType.POISON)),
    WINDY(setOf(PokemonType.DRAGON, PokemonType.FLYING, PokemonType.PSYCHIC)),
    SNOW(setOf(PokemonType.ICE, PokemonType.STEEL)),
    FOG(setOf(PokemonType.DARK, PokemonType.GHOST));

    fun boosts(type: PokemonType?): Boolean = type != null && boostedTypes.contains(type)
}
