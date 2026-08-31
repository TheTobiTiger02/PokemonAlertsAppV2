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
    val nickname: String? = null,
    val cp: Int? = null,
    val level: String? = null,
    val atkIv: Int? = null,
    val defIv: Int? = null,
    val staIv: Int? = null,
    val fastMove: String? = null,
    val chargedMove: String? = null,
    val estimator: Double? = null,
    val overallRating: Double? = null,
    /** Normalized website-style reciprocal percentage; raw [overallRating] remains for compatibility. */
    val overallPercent: Double? = null,
    val tdo: Double? = null,
    val power: Double? = null,
    /** Normalized website-style reciprocal percentage; raw [power] remains for compatibility. */
    val powerPercent: Double? = null,
    val deaths: Double? = null,
    val timeToWinSeconds: Double? = null,
    /**
     * The runner-up movesets for this counter, best first, excluding the chosen one.
     *
     * The response already carries every legal moveset's full result; collapsing to one and
     * discarding the rest threw away the answer to "how much does the wrong moveset cost me",
     * which is the question a player with an unTM'd copy actually has. Capped at
     * [MOVESET_ALTERNATIVES] so the durable cache stays small.
     *
     * Empty for a payload cached before this existed; the UI simply shows nothing.
     */
    val alternatives: List<CounterMoveset> = emptyList()
) {
    fun metrics(): CounterMetrics = CounterMetrics(
        estimator = estimator,
        overallPercent = overallPercent ?: reciprocalPercent(overallRating),
        powerPercent = powerPercent ?: reciprocalPercent(power),
        tdo = tdo,
        deaths = deaths,
        timeToWinSeconds = timeToWinSeconds
    )
}

/** One legal moveset for a counter, with the number the list is ranked by. */
@Serializable
@Immutable
data class CounterMoveset(
    val fastMove: String,
    val chargedMove: String,
    val estimator: Double? = null,
    val timeToWinSeconds: Double? = null
)

/** How many runner-up movesets are worth persisting per counter. */
const val MOVESET_ALTERNATIVES = 4

@Serializable
@Immutable
data class RaidCountersPayload(
    val bossPokemonId: String,
    val bossCp: Int? = null,
    val bossMove1: String? = null,
    val bossMove2: String? = null,
    val counters: List<RaidCounter> = emptyList(),
    val availableBossMovesets: List<RaidBossMoveset> = emptyList()
)

/**
 * Distils a ~1.6 MB response down to the ~8 KB we actually keep.
 *
 * Each counter carries ten-plus candidate movesets; we persist only the one Pokebattler
 * itself would headline for the chosen [sort]. That single reduction is what keeps the
 * durable cache small.
 */
fun PokebattlerCountersResponse.toPayload(sort: PokebattlerSort): RaidCountersPayload? =
    toPayload(sort, requestedMoves = null)

/**
 * True when the response carries an actual attacker ranking.
 *
 * When the ranking engine is saturated, Pokebattler sometimes answers HTTP 200 with a
 * moveset *summary* of the boss alone: every `defenders` list is empty. Treating that as
 * a valid ranking once cached "No counters available" for a full hour, so it must read
 * as "no data", not "no counters".
 */
fun PokebattlerCountersResponse.hasRankingData(): Boolean {
    val block = attackers.firstOrNull() ?: return false
    return (block.byMove + listOfNotNull(block.randomMove)).any { it.defenders.isNotEmpty() }
}

/** Distils a response while optionally selecting the alert's concrete boss moveset. */
fun PokebattlerCountersResponse.toPayload(
    sort: PokebattlerSort,
    requestedMoves: RaidBossMoveset?
): RaidCountersPayload? {
    val block = attackers.firstOrNull() ?: return null
    if (!hasRankingData()) return null
    val bossId = block.pokemonId ?: return null
    val moveset = selectBossMoveset(block, requestedMoves) ?: return null

    // Pokebattler returns `defenders` WORST-FIRST for every sort: with sort=ESTIMATOR the
    // array runs 1.60 -> 1.11, so the genuine best counter is the LAST element. Reversing
    // reproduces Pokebattler's own ranking exactly, for every sort, without having to encode
    // a direction per metric. asReversed() is a view, not a copy.
    val counters = moveset.defenders.asReversed().mapIndexed { index, defender ->
        val best = selectBestByMove(defender, sort)
        RaidCounter(
            rank = index + 1,
            pokemonId = defender.pokemonId,
            displayName = prettifyPokemonName(defender.pokemonId),
            nickname = defender.nickname,
            cp = defender.cp,
            level = defender.stats?.level,
            atkIv = defender.stats?.attack,
            defIv = defender.stats?.defense,
            staIv = defender.stats?.stamina,
            fastMove = prettifyMoveName(best?.move1),
            chargedMove = prettifyMoveName(best?.move2),
            estimator = defender.total?.estimator,
            overallRating = defender.total?.overallRating,
            overallPercent = reciprocalPercent(defender.total?.overallRating),
            tdo = defender.total?.tdo,
            power = defender.total?.power,
            powerPercent = reciprocalPercent(defender.total?.power),
            deaths = defender.total?.effectiveDeaths ?: defender.total?.deaths,
            timeToWinSeconds = (defender.total?.combatTime ?: defender.total?.totalCombatTime)
                ?.let { it / 1000.0 },
            alternatives = alternativeMovesets(defender, best)
        )
    }
    return RaidCountersPayload(
        bossPokemonId = bossId,
        bossCp = block.cp,
        // Raw ids, not display names: the local simulator needs to look these up.
        bossMove1 = moveset.move1,
        bossMove2 = moveset.move2,
        counters = counters,
        availableBossMovesets = buildList {
            add(RaidBossMoveset())
            block.byMove
                .map { RaidBossMoveset(it.move1, it.move2) }
                .filterNot { it.isRandom }
                .distinct()
                .forEach(::add)
        }
    )
}

private fun selectBossMoveset(
    block: PbAttackerBlock,
    requested: RaidBossMoveset?
): PbMoveset? {
    // When the ranking engine is saturated the per-moveset blocks come back WITHOUT
    // defenders while `randomMove` still carries the overall ranking, so an empty
    // moveset is only ever a last-resort pick.
    fun ranked(vararg candidates: PbMoveset?): PbMoveset? =
        candidates.firstOrNull { it?.defenders?.isNotEmpty() == true }

    if (requested == null || requested.isRandom) {
        return ranked(block.randomMove, block.byMove.firstOrNull())
            ?: block.randomMove
            ?: block.byMove.firstOrNull()
    }
    val exact = block.byMove.firstOrNull { moveSet ->
        moveNamesMatch(moveSet.move1, requested.move1) &&
            moveNamesMatch(moveSet.move2, requested.move2)
    }
    return ranked(
        exact,
        block.randomMove,
        block.byMove.firstOrNull { it.defenders.isNotEmpty() }
    ) ?: exact
        ?: block.randomMove
        ?: block.byMove.firstOrNull()
}

private fun moveNamesMatch(apiName: String?, requested: String?): Boolean {
    val left = requestedMoveKey(apiName) ?: return requested.isNullOrBlank()
    val right = requestedMoveKey(requested) ?: return false
    return left == right
}

private fun requestedMoveKey(value: String?): String? = value
    ?.uppercase(java.util.Locale.ROOT)
    ?.removeSuffix("_FAST")
    ?.filter { it.isLetterOrDigit() }
    ?.takeIf { it.isNotEmpty() }

/**
 * Picks the moveset Pokebattler headlines: the one whose result equals `defender.total`.
 *
 * Inside `byMove`, `overallRating`, `estimator` and `power` are COSTS — lower is better.
 * Verified on HONCHKROW_SHADOW_FORM against Lunala: Snarl/Dark Pulse scores overallRating
 * 0.3622 and Peck/Frustration 19.1847, and `total.overallRating` equals the minimum exactly.
 * Only `tdo` is genuinely higher-is-better. Taking the maximum is what put
 * "Peck / Frustration" on a Dark counter facing a Psychic/Ghost boss.
 */
internal fun selectBestByMove(defender: PbDefender, sort: PokebattlerSort): PbByMove? {
    val moves = defender.byMove.filter { it.result != null }
    if (moves.isEmpty()) return defender.byMove.firstOrNull()

    val chosen = when (sort) {
        PokebattlerSort.OVERALL -> moves.minByOrNull { it.result?.overallRating ?: Double.MAX_VALUE }
        PokebattlerSort.ESTIMATOR -> moves.minByOrNull { it.result?.estimator ?: Double.MAX_VALUE }
        PokebattlerSort.TIME -> moves.minByOrNull {
            it.result?.totalCombatTime ?: it.result?.combatTime ?: it.result?.effectiveCombatTime
                ?: it.result?.estimator ?: Double.MAX_VALUE
        }
        PokebattlerSort.POWER -> moves.minByOrNull { it.result?.power ?: Double.MAX_VALUE }
        PokebattlerSort.TDO -> moves.maxByOrNull { it.result?.tdo ?: Double.MIN_VALUE }
    }
    // The chosen sort's metric can be missing on an entry; the estimator is always present
    // and always agrees with the correct moveset.
    return chosen ?: moves.minByOrNull { it.result?.estimator ?: Double.MAX_VALUE }
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

/**
 * Every other legal moveset for one counter, ordered best first.
 *
 * Ordered by estimator regardless of the requested sort: the estimator is the one metric
 * `byMove` always carries, and the comparison here is "which moveset", not "which counter".
 */
internal fun alternativeMovesets(defender: PbDefender, chosen: PbByMove?): List<CounterMoveset> =
    defender.byMove
        .asSequence()
        .filter { it.result?.estimator != null }
        .filterNot { it.move1 == chosen?.move1 && it.move2 == chosen?.move2 }
        .sortedBy { it.result?.estimator ?: Double.MAX_VALUE }
        .mapNotNull { entry ->
            val fast = prettifyMoveName(entry.move1) ?: return@mapNotNull null
            val charged = prettifyMoveName(entry.move2) ?: return@mapNotNull null
            CounterMoveset(
                fastMove = fast,
                chargedMove = charged,
                estimator = entry.result?.estimator,
                timeToWinSeconds = (entry.result?.combatTime ?: entry.result?.totalCombatTime)
                    ?.let { it / 1000.0 }
            )
        }
        .distinctBy { it.fastMove to it.chargedMove }
        .take(MOVESET_ALTERNATIVES)
        .toList()
