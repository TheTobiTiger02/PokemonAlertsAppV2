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
    ALL_POKEMON("Pokébattler"),
    POKE_GENIE("My Pokémon"),

    /** Requires a linked Pokébattler account; see `PokebattlerAuth`. */
    POKEBATTLER_POKEBOX("My Pokébox")
}

/** A counter, plus the copy the user owns of it, if any. */
@Immutable
data class DecoratedCounter(
    val counter: RaidCounter,
    val owned: OwnedPokemon? = null
) {
    val isOwned: Boolean get() = owned != null

    /**
     * The recommended moves the user's copy cannot use, prettified for display.
     *
     * Both slots are checked, and a second charged move counts: Poke Genie records one when
     * it is unlocked, so a copy that already knows the recommended charged move in slot two
     * needs no TM. A move component the import simply did not record is not a mismatch.
     */
    val missingMoves: List<String>
        get() {
            val mine = owned ?: return emptyList()
            return buildList {
                val fast = counter.fastMove
                val myFast = mine.quickMove
                if (fast != null && myFast != null && !myFast.equals(fast, ignoreCase = true)) {
                    add(fast)
                }
                val charged = counter.chargedMove
                val myCharged = listOfNotNull(mine.chargeMove, mine.chargeMove2)
                if (charged != null && myCharged.isNotEmpty() &&
                    myCharged.none { it.equals(charged, ignoreCase = true) }
                ) {
                    add(charged)
                }
            }
        }

    /** True when the user's copy does not know the moveset the ranking assumes. */
    val movesetDiffers: Boolean get() = missingMoves.isNotEmpty()
}
