package com.example.pokemonalertsv2.data.pokegenie

import com.example.pokemonalertsv2.data.counters.PokebattlerNameNormalizer
import java.util.Locale

/**
 * A Poke Genie row reduced to what matching and display need.
 *
 * [matchKeys] is computed once at import so that decorating a counter list is a hash
 * lookup rather than a re-normalization of the whole box.
 */
data class OwnedPokemon(
    val displayName: String,
    val form: String?,
    val level: Double?,
    val atkIv: Int?,
    val defIv: Int?,
    val staIv: Int?,
    val cp: Int?,
    val quickMove: String?,
    val chargeMove: String?,
    val shadow: Boolean,
    val lucky: Boolean,
    val matchKeys: List<String>
) {
    /** Sum of IVs, for picking the best copy. Missing IVs sort last. */
    val ivTotal: Int? = if (atkIv != null && defIv != null && staIv != null) {
        atkIv + defIv + staIv
    } else {
        null
    }
}

/** Lookup from Pokebattler `pokemonId` to the copies the user owns. */
class PokeGenieIndex(private val byKey: Map<String, List<OwnedPokemon>>) {

    val size: Int get() = byKey.size

    /**
     * The best copy the user owns of [pokemonId], or null.
     *
     * Shadow and non-shadow are kept strictly separate: owning a regular Houndoom does not
     * mean you can bring `HOUNDOOM_SHADOW_FORM` to a raid, and vice versa.
     */
    fun bestOwned(pokemonId: String): OwnedPokemon? {
        val wantShadow = pokemonId.uppercase(Locale.ROOT).contains("SHADOW")
        val exact = byKey[pokemonId.uppercase(Locale.ROOT)]
        val loose = byKey[PokebattlerNameNormalizer.looseKey(pokemonId)]
        return (exact.orEmpty() + loose.orEmpty())
            .filter { it.shadow == wantShadow }
            .maxWithOrNull(BEST_COPY)
    }

    private companion object {
        /** Highest level, then best IVs, then highest CP. */
        val BEST_COPY: Comparator<OwnedPokemon> = compareBy(
            { it.level ?: Double.MIN_VALUE },
            { it.ivTotal ?: -1 },
            { it.cp ?: -1 }
        )
    }
}

object PokeGenieMatcher {

    /**
     * Candidate Pokebattler ids for one scanned Pokémon.
     *
     * Poke Genie records the shadow state in its own column rather than in the form, and
     * writes "Normal" as the form for plain Pokémon, so both are passed through rather
     * than being folded into the name.
     */
    fun matchKeysFor(row: PokeGenieRow): List<String> {
        val form = row.form?.trim().orEmpty()
        val lower = form.lowercase(Locale.ROOT)

        // Poke Genie writes megas in the form column as "Mega", "Mega X" or "Mega Y", and
        // primals as "Primal". These map to dedicated Pokebattler ids (CHARIZARD_MEGA_X,
        // GROUDON_PRIMAL), not to a generic *_FORM suffix, so they are passed as flags.
        val mega = lower == "mega" || lower.startsWith("mega ")
        val primal = lower == "primal"
        val megaVariant = if (mega) form.substringAfter(' ', "").trim().takeIf { it.isNotEmpty() } else null

        return PokebattlerNameNormalizer.candidateIds(
            displayName = row.name,
            form = form.takeUnless { mega || primal || it.isEmpty() },
            shadow = row.shadowState == ShadowState.SHADOW,
            mega = mega,
            primal = primal,
            megaVariant = megaVariant
        )
    }

    fun toOwned(row: PokeGenieRow): OwnedPokemon = OwnedPokemon(
        displayName = row.name,
        form = row.form,
        level = row.level,
        atkIv = row.atkIv,
        defIv = row.defIv,
        staIv = row.staIv,
        cp = row.cp,
        quickMove = row.quickMove,
        chargeMove = row.chargeMove,
        shadow = row.shadowState == ShadowState.SHADOW,
        lucky = row.lucky,
        matchKeys = matchKeysFor(row)
    )

    /** Indexes a whole box under both exact and underscore-insensitive keys. */
    fun index(owned: List<OwnedPokemon>): PokeGenieIndex {
        val byKey = HashMap<String, MutableList<OwnedPokemon>>()
        owned.forEach { mon ->
            mon.matchKeys.forEach { key ->
                val upper = key.uppercase(Locale.ROOT)
                byKey.getOrPut(upper) { mutableListOf() }.add(mon)
                val loose = PokebattlerNameNormalizer.looseKey(key)
                if (loose != upper) {
                    byKey.getOrPut(loose) { mutableListOf() }.add(mon)
                }
            }
        }
        return PokeGenieIndex(byKey)
    }
}
