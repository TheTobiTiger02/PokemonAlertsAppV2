package com.example.pokemonalertsv2.data.counters

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.Locale

/** One counter, distilled to what the UI shows and the cache stores. */
@Serializable
@Immutable
data class RaidCounter(
    val rank: Int,
    val pokemonId: String,
    val displayName: String,
    val cp: Int? = null,
    val level: String? = null,
    val atkIv: Int? = null,
    val defIv: Int? = null,
    val staIv: Int? = null,
    val fastMove: String? = null,
    val chargedMove: String? = null,
    val estimator: Double? = null,
    val overallRating: Double? = null,
    val tdo: Double? = null,
    val power: Double? = null,
    val deaths: Double? = null,
    val timeToWinSeconds: Double? = null
)

@Serializable
@Immutable
data class RaidCountersPayload(
    val bossPokemonId: String,
    val bossCp: Int? = null,
    val bossMove1: String? = null,
    val bossMove2: String? = null,
    val counters: List<RaidCounter> = emptyList()
)

/**
 * Distils a ~1.6 MB response down to the ~8 KB we actually keep.
 *
 * Each counter carries ten-plus candidate movesets; we persist only the one Pokebattler
 * itself would headline for the chosen [sort]. That single reduction is what keeps the
 * durable cache small.
 */
fun PokebattlerCountersResponse.toPayload(sort: PokebattlerSort): RaidCountersPayload? {
    val block = attackers.firstOrNull() ?: return null
    val bossId = block.pokemonId ?: return null
    val moveset = block.randomMove ?: block.byMove.firstOrNull() ?: return null

    val counters = moveset.defenders.mapIndexed { index, defender ->
        val best = selectBestByMove(defender, sort)
        RaidCounter(
            rank = index + 1,
            pokemonId = defender.pokemonId,
            displayName = prettifyPokemonName(defender.pokemonId),
            cp = defender.cp,
            level = defender.stats?.level,
            atkIv = defender.stats?.attack,
            defIv = defender.stats?.defense,
            staIv = defender.stats?.stamina,
            fastMove = prettifyMoveName(best?.move1),
            chargedMove = prettifyMoveName(best?.move2),
            estimator = defender.total?.estimator,
            overallRating = defender.total?.overallRating,
            tdo = defender.total?.tdo,
            power = defender.total?.power,
            deaths = defender.total?.effectiveDeaths ?: defender.total?.deaths,
            timeToWinSeconds = defender.total?.combatTime?.let { it / 1000.0 }
        )
    }
    return RaidCountersPayload(
        bossPokemonId = bossId,
        bossCp = block.cp,
        bossMove1 = prettifyMoveName(moveset.move1),
        bossMove2 = prettifyMoveName(moveset.move2),
        counters = counters
    )
}

/**
 * Picks the moveset Pokebattler would headline for this counter, matching the sort the
 * user asked for. Ties fall back to the API's own ordering.
 */
internal fun selectBestByMove(defender: PbDefender, sort: PokebattlerSort): PbByMove? {
    val moves = defender.byMove.filter { it.result != null }
    if (moves.isEmpty()) return defender.byMove.firstOrNull()
    return when (sort) {
        // Lower is better: fewer trainers, less time.
        PokebattlerSort.ESTIMATOR,
        PokebattlerSort.TIME -> moves.minByOrNull { it.result?.estimator ?: Double.MAX_VALUE }
        PokebattlerSort.POWER -> moves.maxByOrNull { it.result?.power ?: Double.MIN_VALUE }
        PokebattlerSort.TDO -> moves.maxByOrNull { it.result?.tdo ?: Double.MIN_VALUE }
        PokebattlerSort.OVERALL -> moves.maxByOrNull { it.result?.overallRating ?: Double.MIN_VALUE }
    }
}

/** `SHADOW_CLAW_FAST` -> "Shadow Claw". Saves fetching the 196 KB move list. */
fun prettifyMoveName(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val stripped = value.removeSuffix("_FAST")
    MOVE_NAME_OVERRIDES[stripped]?.let { return it }
    // Some ids are species-qualified, e.g. HYDRO_PUMP_BLASTOISE.
    val withoutSpecies = MOVE_SPECIES_SUFFIXES.firstOrNull { stripped.endsWith("_$it") }
        ?.let { stripped.removeSuffix("_$it") }
        ?: stripped
    MOVE_NAME_OVERRIDES[withoutSpecies]?.let { return it }
    return withoutSpecies.split("_").filter { it.isNotEmpty() }.joinToString(" ") { titleCase(it) }
}

/** `KYUREM_BLACK_FORM` -> "Kyurem (Black)", `HOUNDOOM_SHADOW_FORM` -> "Shadow Houndoom". */
fun prettifyPokemonName(pokemonId: String): String {
    var parts = pokemonId.split("_").filter { it.isNotEmpty() && it != "FORM" }
    if (parts.isEmpty()) return pokemonId
    val species = titleCase(parts.first())
    parts = parts.drop(1)

    val shadow = parts.contains("SHADOW")
    val mega = parts.contains("MEGA")
    val primal = parts.contains("PRIMAL")
    val descriptors = parts.filterNot { it == "SHADOW" || it == "MEGA" || it == "PRIMAL" }

    val base = buildString {
        if (shadow) append("Shadow ")
        if (mega) append("Mega ")
        if (primal) append("Primal ")
        append(species)
        // Mega X / Mega Y read better appended than parenthesised.
        if (mega && descriptors.size == 1 && descriptors.first().length == 1) {
            append(" ").append(descriptors.first())
            return@buildString
        }
        if (descriptors.isNotEmpty()) {
            append(" (").append(descriptors.joinToString(" ") { titleCase(it) }).append(")")
        }
    }
    return base
}

private fun titleCase(token: String): String =
    token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

private val MOVE_NAME_OVERRIDES = mapOf(
    "V_CREATE" to "V-create",
    "X_SCISSOR" to "X-Scissor",
    "POWER_UP_PUNCH" to "Power-Up Punch",
    "LOCK_ON" to "Lock-On",
    "DOUBLE_EDGE" to "Double-Edge",
    "WILL_O_WISP" to "Will-O-Wisp",
    "MUD_SLAP" to "Mud-Slap",
    "SOFT_BOILED" to "Soft-Boiled",
    "TRI_ATTACK" to "Tri Attack",
    "WING_ATTACK" to "Wing Attack"
)

/** Species qualifiers Pokebattler appends to shared move ids. */
private val MOVE_SPECIES_SUFFIXES = listOf(
    "BLASTOISE", "CHARIZARD", "VENUSAUR", "MEWTWO", "RAICHU", "PIKACHU", "GENGAR"
)
