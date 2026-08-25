package com.example.pokemonalertsv2.data.sim

import java.util.Locale

/**
 * The eighteen types, keyed by the `POKEMON_TYPE_*` ids Pokebattler uses.
 */
enum class PokemonType(val apiValue: String, val label: String) {
    NORMAL("POKEMON_TYPE_NORMAL", "Normal"),
    FIGHTING("POKEMON_TYPE_FIGHTING", "Fighting"),
    FLYING("POKEMON_TYPE_FLYING", "Flying"),
    POISON("POKEMON_TYPE_POISON", "Poison"),
    GROUND("POKEMON_TYPE_GROUND", "Ground"),
    ROCK("POKEMON_TYPE_ROCK", "Rock"),
    BUG("POKEMON_TYPE_BUG", "Bug"),
    GHOST("POKEMON_TYPE_GHOST", "Ghost"),
    STEEL("POKEMON_TYPE_STEEL", "Steel"),
    FIRE("POKEMON_TYPE_FIRE", "Fire"),
    WATER("POKEMON_TYPE_WATER", "Water"),
    GRASS("POKEMON_TYPE_GRASS", "Grass"),
    ELECTRIC("POKEMON_TYPE_ELECTRIC", "Electric"),
    PSYCHIC("POKEMON_TYPE_PSYCHIC", "Psychic"),
    ICE("POKEMON_TYPE_ICE", "Ice"),
    DRAGON("POKEMON_TYPE_DRAGON", "Dragon"),
    DARK("POKEMON_TYPE_DARK", "Dark"),
    FAIRY("POKEMON_TYPE_FAIRY", "Fairy");

    companion object {
        private val byApiValue = entries.associateBy { it.apiValue }

        fun fromApiValue(value: String?): PokemonType? {
            if (value.isNullOrBlank()) return null
            return byApiValue[value.uppercase(Locale.ROOT)]
        }
    }
}

/**
 * Type effectiveness, using Pokémon GO's multipliers rather than the main series'.
 *
 * GO has no immunities: what the main series treats as "no effect" is a double
 * resistance, [DOUBLE_RESIST].
 */
object TypeChart {

    const val SUPER_EFFECTIVE = 1.6
    const val NEUTRAL = 1.0
    const val RESIST = 0.625
    const val DOUBLE_RESIST = 0.390625

    /** Combined multiplier for one attacking type against a defender's one or two types. */
    fun effectiveness(attacking: PokemonType?, defenderTypes: List<PokemonType>): Double {
        if (attacking == null) return NEUTRAL
        return defenderTypes.fold(NEUTRAL) { acc, defending ->
            acc * singleEffectiveness(attacking, defending)
        }
    }

    fun singleEffectiveness(attacking: PokemonType, defending: PokemonType): Double = when {
        SUPER.getValue(attacking).contains(defending) -> SUPER_EFFECTIVE
        DOUBLE.getValue(attacking).contains(defending) -> DOUBLE_RESIST
        WEAK.getValue(attacking).contains(defending) -> RESIST
        else -> NEUTRAL
    }

    /** Same-type attack bonus. */
    const val STAB = 1.2

    private val SUPER: Map<PokemonType, Set<PokemonType>> = mapOf(
        PokemonType.NORMAL to emptySet(),
        PokemonType.FIGHTING to setOf(PokemonType.NORMAL, PokemonType.ROCK, PokemonType.STEEL, PokemonType.ICE, PokemonType.DARK),
        PokemonType.FLYING to setOf(PokemonType.FIGHTING, PokemonType.BUG, PokemonType.GRASS),
        PokemonType.POISON to setOf(PokemonType.GRASS, PokemonType.FAIRY),
        PokemonType.GROUND to setOf(PokemonType.POISON, PokemonType.ROCK, PokemonType.STEEL, PokemonType.FIRE, PokemonType.ELECTRIC),
        PokemonType.ROCK to setOf(PokemonType.FLYING, PokemonType.BUG, PokemonType.FIRE, PokemonType.ICE),
        PokemonType.BUG to setOf(PokemonType.GRASS, PokemonType.PSYCHIC, PokemonType.DARK),
        PokemonType.GHOST to setOf(PokemonType.GHOST, PokemonType.PSYCHIC),
        PokemonType.STEEL to setOf(PokemonType.ROCK, PokemonType.ICE, PokemonType.FAIRY),
        PokemonType.FIRE to setOf(PokemonType.BUG, PokemonType.STEEL, PokemonType.GRASS, PokemonType.ICE),
        PokemonType.WATER to setOf(PokemonType.GROUND, PokemonType.ROCK, PokemonType.FIRE),
        PokemonType.GRASS to setOf(PokemonType.GROUND, PokemonType.ROCK, PokemonType.WATER),
        PokemonType.ELECTRIC to setOf(PokemonType.FLYING, PokemonType.WATER),
        PokemonType.PSYCHIC to setOf(PokemonType.FIGHTING, PokemonType.POISON),
        PokemonType.ICE to setOf(PokemonType.FLYING, PokemonType.GROUND, PokemonType.GRASS, PokemonType.DRAGON),
        PokemonType.DRAGON to setOf(PokemonType.DRAGON),
        PokemonType.DARK to setOf(PokemonType.GHOST, PokemonType.PSYCHIC),
        PokemonType.FAIRY to setOf(PokemonType.FIGHTING, PokemonType.DRAGON, PokemonType.DARK)
    )

    private val WEAK: Map<PokemonType, Set<PokemonType>> = mapOf(
        PokemonType.NORMAL to setOf(PokemonType.ROCK, PokemonType.STEEL),
        PokemonType.FIGHTING to setOf(PokemonType.POISON, PokemonType.BUG, PokemonType.PSYCHIC, PokemonType.FLYING, PokemonType.FAIRY),
        PokemonType.FLYING to setOf(PokemonType.ROCK, PokemonType.STEEL, PokemonType.ELECTRIC),
        PokemonType.POISON to setOf(PokemonType.POISON, PokemonType.GROUND, PokemonType.ROCK, PokemonType.GHOST),
        PokemonType.GROUND to setOf(PokemonType.BUG, PokemonType.GRASS),
        PokemonType.ROCK to setOf(PokemonType.FIGHTING, PokemonType.GROUND, PokemonType.STEEL),
        PokemonType.BUG to setOf(
            PokemonType.FIGHTING, PokemonType.POISON, PokemonType.FLYING, PokemonType.GHOST,
            PokemonType.STEEL, PokemonType.FIRE, PokemonType.FAIRY
        ),
        PokemonType.GHOST to setOf(PokemonType.DARK),
        PokemonType.STEEL to setOf(PokemonType.STEEL, PokemonType.FIRE, PokemonType.WATER, PokemonType.ELECTRIC),
        PokemonType.FIRE to setOf(PokemonType.ROCK, PokemonType.FIRE, PokemonType.WATER, PokemonType.DRAGON),
        PokemonType.WATER to setOf(PokemonType.WATER, PokemonType.GRASS, PokemonType.DRAGON),
        PokemonType.GRASS to setOf(
            PokemonType.FLYING, PokemonType.POISON, PokemonType.BUG, PokemonType.STEEL,
            PokemonType.FIRE, PokemonType.GRASS, PokemonType.DRAGON
        ),
        PokemonType.ELECTRIC to setOf(PokemonType.GRASS, PokemonType.ELECTRIC, PokemonType.DRAGON),
        PokemonType.PSYCHIC to setOf(PokemonType.STEEL, PokemonType.PSYCHIC),
        PokemonType.ICE to setOf(PokemonType.STEEL, PokemonType.FIRE, PokemonType.WATER, PokemonType.ICE),
        PokemonType.DRAGON to setOf(PokemonType.STEEL),
        PokemonType.DARK to setOf(PokemonType.FIGHTING, PokemonType.DARK, PokemonType.FAIRY),
        PokemonType.FAIRY to setOf(PokemonType.POISON, PokemonType.STEEL, PokemonType.FIRE)
    )

    /** What the main series calls an immunity. */
    private val DOUBLE: Map<PokemonType, Set<PokemonType>> = mapOf(
        PokemonType.NORMAL to setOf(PokemonType.GHOST),
        PokemonType.FIGHTING to setOf(PokemonType.GHOST),
        PokemonType.FLYING to emptySet(),
        PokemonType.POISON to setOf(PokemonType.STEEL),
        PokemonType.GROUND to setOf(PokemonType.FLYING),
        PokemonType.ROCK to emptySet(),
        PokemonType.BUG to emptySet(),
        PokemonType.GHOST to setOf(PokemonType.NORMAL),
        PokemonType.STEEL to emptySet(),
        PokemonType.FIRE to emptySet(),
        PokemonType.WATER to emptySet(),
        PokemonType.GRASS to emptySet(),
        PokemonType.ELECTRIC to setOf(PokemonType.GROUND),
        PokemonType.PSYCHIC to setOf(PokemonType.DARK),
        PokemonType.ICE to emptySet(),
        PokemonType.DRAGON to setOf(PokemonType.FAIRY),
        PokemonType.DARK to emptySet(),
        PokemonType.FAIRY to emptySet()
    )
}
