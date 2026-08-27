package com.example.pokemonalertsv2.data.counters

import java.util.Locale

/** A raid party is six Pokémon. */
const val PERSONAL_TEAM_SIZE = 6

/**
 * True for a Pokebattler id that is a Mega Evolution or a Primal Reversion.
 *
 * Both are separate species to Pokebattler (`CHARIZARD_MEGA_Y`, `KYOGRE_PRIMAL`) and both are
 * subject to the same in-game rule: only one can be active at a time.
 */
fun String.isMegaOrPrimalId(): Boolean {
    val id = uppercase(Locale.ROOT)
    return id.contains("_MEGA") || id.contains("_PRIMAL")
}

/**
 * `CHARIZARD_MEGA_Y` -> `CHARIZARD`, `KYOGRE_PRIMAL` -> `KYOGRE`.
 *
 * Distinct from [PokebattlerNameNormalizer.baseSpeciesId], which only strips shadow
 * suffixes: a mega is a separate species to Pokebattler, but the Pokémon the trainer owns
 * and evolves is the base one, so this is what "you have one of these" has to be keyed on.
 */
fun String.megaBaseSpeciesId(): String {
    val id = uppercase(Locale.ROOT)
    val cut = listOf("_MEGA", "_PRIMAL").mapNotNull { marker ->
        id.indexOf(marker).takeIf { it > 0 }
    }.minOrNull() ?: return id
    return id.take(cut)
}

/**
 * The suggested six, honouring which mega the trainer actually has evolved.
 *
 * The old rule was "at most one mega, whichever ranks highest", which recommended a mega the
 * trainer usually could not field: a mega has to already be evolved to be brought into a
 * raid, and most of the time none is. So [activeMegaId] gates it instead — null means no mega
 * is eligible at all, and a non-null id admits only that one species.
 *
 * Duplicates are deliberately kept. If the best answer is five Shadow Machamp then that is
 * what to bring, and each copy is a distinct scanned Pokémon with its own level, IVs and
 * moveset. The one-mega rule is not a per-copy cap either: you cannot evolve two at once, so
 * a mega takes at most a single slot.
 */
fun suggestTeam(
    ranked: List<PersonalCounter>,
    activeMegaId: String?,
    size: Int = PERSONAL_TEAM_SIZE
): List<PersonalTeamSlot> {
    val active = activeMegaId?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)
    val members = mutableListOf<PersonalCounter>()
    var megaTaken = false

    for (candidate in ranked) {
        if (members.size >= size) break
        if (candidate.pokemonId.isMegaOrPrimalId()) {
            if (megaTaken) continue
            if (candidate.pokemonId.uppercase(Locale.ROOT) != active) continue
            megaTaken = true
        }
        members += candidate
    }

    // Group identical copies so the UI can render "×3" instead of three near-identical rows.
    // Grouped by species *and* moveset: two Machamp with different charged moves are not
    // interchangeable to someone assembling the party in game.
    return members
        .groupBy { listOf(it.pokemonId, it.fastMove.moveId, it.chargedMove.moveId) }
        .values
        .map { copies -> PersonalTeamSlot(copies.first(), copies.size, copies) }
}
