package com.example.pokemonalertsv2.data.counters

import androidx.compose.runtime.Immutable
import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon

/**
 * Where the counter list comes from.
 *
 * Kept apart from [CounterSource] itself so that preferences and UI can refer to the
 * choice without pulling in the repositories the implementations need.
 */
enum class CounterSourceId(val label: String) {
    ALL_POKEMON("All Pokémon"),
    POKE_GENIE("My Pokémon"),

    /** Declared so the type is stable; the implementation lands with account linking. */
    POKEBATTLER_POKEBOX("My Pokébox")
}

/** A counter, plus the copy the user owns of it, if any. */
@Immutable
data class DecoratedCounter(
    val counter: RaidCounter,
    val owned: OwnedPokemon? = null
) {
    val isOwned: Boolean get() = owned != null

    /** True when the user's copy does not know the moveset the ranking assumes. */
    val movesetDiffers: Boolean
        get() {
            val mine = owned ?: return false
            val recommendedFast = counter.fastMove ?: return false
            val myFast = mine.quickMove ?: return false
            return !myFast.equals(recommendedFast, ignoreCase = true)
        }
}
