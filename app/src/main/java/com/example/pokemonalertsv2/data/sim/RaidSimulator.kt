package com.example.pokemonalertsv2.data.sim

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A move, reduced to what the damage model needs. */
data class SimMove(
    val moveId: String,
    val type: PokemonType?,
    val power: Double,
    val durationSeconds: Double,
    /** Positive for fast moves (energy gained), negative for charged moves (energy spent). */
    val energyDelta: Int
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
    val estimatedAttackers: Double
)

/**
 * A deterministic, cycle-based raid damage model.
 *
 * This is the standard Pokemon GO damage formula rather than a Monte Carlo battle: damage
 * per hit is exact, and DPS comes from repeating a fast-move/charged-move cycle. It cannot
 * reproduce Pokebattler move for move, but it ranks a specific collection of Pokemon —
 * real level, real IVs, real moveset — which a server-side ranking of generic level-40
 * attackers fundamentally cannot do.
 *
 * Shadow Pokemon get the usual 1.2x attack and take 1.2x damage.
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
            estimatedAttackers = if (tdo > 0) boss.hp / tdo else Double.MAX_VALUE
        )
    }

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
