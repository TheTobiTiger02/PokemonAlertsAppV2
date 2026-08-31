package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.data.sim.SimMove
import java.util.Locale

/** Progress for a mixed-level Pokébattler request. */
data class PokebattlerPersonalProgress(
    val completedLevels: Int,
    val totalLevels: Int,
    val missingLevels: List<Double> = emptyList(),
    /**
     * How many owned Pokémon were left out for being under the minimum useful level.
     * Surfaced so the ranking never silently claims to cover the whole roster.
     */
    val skippedBelowMinimumLevel: Int = 0,
    /**
     * How many distinct Pokémon Pokébattler actually offered to match against.
     *
     * A by-level counters response carries only the **top 30** attackers per boss moveset,
     * not the full catalogue — verified live, and `numResults`/`limit`/`topN` are all
     * ignored. So the personal ranking is the intersection of the roster with those 30,
     * and the UI has to say so rather than implying the whole roster was ranked.
     */
    val serverCandidates: Int = 0,
    /**
     * Levels Pokébattler would not rank, whose Pokémon were scored against a lower level
     * that it did serve.
     *
     * For a boss outside the current raid rotation only `attackers/levels/40` is
     * precomputed, so the level-50 bucket lands here and those rows carry
     * `evaluatedLevel = 40`. Conservative, and never silent.
     */
    val substitutedLevels: List<Double> = emptyList()
) {
    val isComplete: Boolean get() = completedLevels == totalLevels && missingLevels.isEmpty()
}

/** Result of joining Pokébattler rows back to a local PokeGenie roster. */
data class PokebattlerPersonalResult(
    val ranking: PersonalRanking,
    val progress: PokebattlerPersonalProgress
)

internal fun PokebattlerSort.toCounterMetric(): CounterMetric = when (this) {
    PokebattlerSort.OVERALL -> CounterMetric.OVERALL
    PokebattlerSort.ESTIMATOR -> CounterMetric.ESTIMATOR
    PokebattlerSort.TIME -> CounterMetric.TIME
    PokebattlerSort.POWER -> CounterMetric.POWER
    PokebattlerSort.TDO -> CounterMetric.TDO
}

internal fun PbResult?.toNormalizedMetrics(): CounterMetrics = CounterMetrics(
    estimator = this?.estimator,
    overallPercent = reciprocalPercent(this?.overallRating),
    powerPercent = reciprocalPercent(this?.power),
    tdo = this?.tdo,
    deaths = this?.effectiveDeaths ?: this?.deaths,
    timeToWinSeconds = (this?.combatTime ?: this?.totalCombatTime ?: this?.effectiveCombatTime)?.div(1000.0)
)

internal fun pbMetric(result: PbResult?, metric: CounterMetric): Double? =
    result?.toNormalizedMetrics()?.valueFor(metric)

internal fun personalMoveKey(value: String?): String? = value
    ?.uppercase(Locale.ROOT)
    ?.removeSuffix("_FAST")
    ?.filter { it.isLetterOrDigit() }
    ?.takeIf { it.isNotEmpty() }

internal fun personalMovesMatch(left: String?, right: String?): Boolean =
    personalMoveKey(left) != null && personalMoveKey(left) == personalMoveKey(right)

internal fun syntheticFastMove(id: String): SimMove =
    SimMove(id, null, 0.0, 0.0, 1)

internal fun syntheticChargedMove(id: String): SimMove =
    SimMove(id, null, 0.0, 0.0, -1)

internal fun PbDefender.toOwnedPokemon(fastMove: String?, chargedMove: String?): OwnedPokemon =
    OwnedPokemon(
        displayName = nickname?.takeIf { it.isNotBlank() } ?: prettifyPokemonName(pokemonId),
        form = null,
        level = stats?.level?.toDoubleOrNull(),
        atkIv = stats?.attack,
        defIv = stats?.defense,
        staIv = stats?.stamina,
        cp = cp,
        quickMove = fastMove,
        chargeMove = chargedMove,
        shadow = pokemonId.contains("SHADOW", ignoreCase = true),
        lucky = false,
        matchKeys = listOf(pokemonId)
    )

internal fun PbDefender.toPersonalCounter(
    owned: OwnedPokemon,
    selectedMove: PbByMove,
    evaluatedLevel: Double,
    movesetAssumed: Boolean,
    movesetUnlisted: Boolean = false,
    rank: Int = 0
): PersonalCounter {
    val fastId = selectedMove.move1.orEmpty()
    val chargedId = selectedMove.move2.orEmpty()
    val metrics = selectedMove.result.toNormalizedMetrics()
    val tdo = metrics.tdo ?: 0.0
    val estimator = metrics.estimator ?: Double.POSITIVE_INFINITY
    val time = metrics.timeToWinSeconds ?: 180.0
    return PersonalCounter(
        owned = owned,
        pokemonId = pokemonId,
        displayName = nickname?.takeIf { it.isNotBlank() } ?: prettifyPokemonName(pokemonId),
        fastMove = syntheticFastMove(fastId),
        chargedMove = syntheticChargedMove(chargedId),
        movesetAssumed = movesetAssumed,
        movesetUnlisted = movesetUnlisted,
        dps = if (time > 0.0) tdo / time else 0.0,
        tdo = tdo,
        rating = metrics.overallPercent ?: 0.0,
        estimatedAttackers = estimator * 6.0,
        evaluatedLevel = evaluatedLevel,
        serverBacked = true,
        rankingIgnoresIv = true,
        metrics = metrics
    )
}

/** Converts the server's exact Pokébox rows into the same personal view model. */
internal fun RaidCounter.toPersonalCounterFromPokeBox(): PersonalCounter {
    val fastId = fastMove.orEmpty()
    val chargedId = chargedMove.orEmpty()
    val owned = OwnedPokemon(
        displayName = nickname ?: displayName,
        form = null,
        level = level?.toDoubleOrNull(),
        atkIv = atkIv,
        defIv = defIv,
        staIv = staIv,
        cp = cp,
        quickMove = fastId,
        chargeMove = chargedId,
        shadow = pokemonId.contains("SHADOW", ignoreCase = true),
        lucky = false,
        matchKeys = listOf(pokemonId)
    )
    val metrics = metrics()
    val dps = metrics.timeToWinSeconds?.takeIf { it > 0.0 }
        ?.let { (metrics.tdo ?: 0.0) / it }
        ?: 0.0
    return PersonalCounter(
        owned = owned,
        pokemonId = pokemonId,
        displayName = nickname ?: displayName,
        fastMove = syntheticFastMove(fastId),
        chargedMove = syntheticChargedMove(chargedId),
        movesetAssumed = false,
        dps = dps,
        tdo = metrics.tdo ?: 0.0,
        rating = metrics.overallPercent ?: 0.0,
        estimatedAttackers = (metrics.estimator ?: Double.POSITIVE_INFINITY) * 6.0,
        evaluatedLevel = level?.toDoubleOrNull(),
        serverBacked = true,
        rankingIgnoresIv = false,
        metrics = metrics
    )
}

// -- Personal ranking, extracted from the repository so it can be tested
// against a captured response without standing up Retrofit, Room and DataStore.

internal fun rankPersonalLevel(
    level: Double,
    owned: List<OwnedPokemon>,
    response: PokebattlerCountersResponse,
    movesMode: PersonalMovesMode,
    metric: CounterMetric,
    bossMoveset: RaidBossMoveset?
): List<PersonalCounter> {
    val block = response.attackers.firstOrNull() ?: return emptyList()
    val moveset = selectPersonalBossMoveset(block, bossMoveset) ?: return emptyList()
    val defenders = moveset.defenders.asReversed()
    return owned.mapNotNull { mine ->
        val defender = defenders.firstOrNull { it.matches(mine) } ?: return@mapNotNull null
        val selected = selectOwnedMoves(defender, mine, movesMode, metric) ?: return@mapNotNull null
        defender.toPersonalCounter(
            owned = mine,
            selectedMove = selected.move,
            evaluatedLevel = level,
            movesetAssumed = selected.assumed,
            movesetUnlisted = selected.unlisted
        )
    }
}

/** The moveset a copy is scored on, and why it is that one. */
internal data class OwnedMoveChoice(
    val move: PbByMove,
    val assumed: Boolean,
    val unlisted: Boolean = false
)

internal fun selectOwnedMoves(
    defender: PbDefender,
    mine: OwnedPokemon,
    mode: PersonalMovesMode,
    metric: CounterMetric
): OwnedMoveChoice? {
    val moves = defender.byMove.filter { it.result != null }
    if (moves.isEmpty()) return null
    val fast = mine.quickMove
    val charges = listOfNotNull(mine.chargeMove, mine.chargeMove2)
    if (mode == PersonalMovesMode.CURRENT && (fast.isNullOrBlank() || charges.isEmpty())) return null

    val constrained = moves.filter { candidate ->
        (fast == null || personalMovesMatch(candidate.move1, fast)) &&
            (charges.isEmpty() || charges.any { personalMovesMatch(candidate.move2, it) })
    }
    // A response lists only the ten best movesets per attacker, so a recorded set can be
    // missing from it entirely. Scoring such a copy at the worst listed moveset understates
    // it -- the real one is by definition worse than all ten -- which is the right way to
    // be wrong. Dropping the row instead made the Pokemon vanish with no explanation.
    if (mode == PersonalMovesMode.CURRENT && constrained.isEmpty()) {
        val floor = worstPersonalMove(moves, metric) ?: return null
        return OwnedMoveChoice(move = floor, assumed = false, unlisted = true)
    }
    val pool = if (mode == PersonalMovesMode.CURRENT) constrained else constrained.ifEmpty { moves }
    val selected = selectPersonalMove(pool, metric) ?: return null
    val assumed = mode == PersonalMovesMode.BEST_POTENTIAL &&
        (fast.isNullOrBlank() || charges.isEmpty() ||
            !personalMovesMatch(selected.move1, fast) ||
            charges.none { personalMovesMatch(selected.move2, it) })
    return OwnedMoveChoice(move = selected, assumed = assumed)
}

/** The floor of [moves] for [metric]: the exact inverse of [selectPersonalMove]. */
internal fun worstPersonalMove(moves: List<PbByMove>, metric: CounterMetric): PbByMove? =
    when (metric) {
        CounterMetric.ESTIMATOR, CounterMetric.TIME -> moves.maxByOrNull {
            pbMetric(it.result, metric) ?: Double.MIN_VALUE
        }
        CounterMetric.OVERALL, CounterMetric.POWER, CounterMetric.TDO -> moves.minByOrNull {
            pbMetric(it.result, metric) ?: Double.MAX_VALUE
        }
    }

internal fun selectPersonalMove(moves: List<PbByMove>, metric: CounterMetric): PbByMove? =
    when (metric) {
        CounterMetric.ESTIMATOR, CounterMetric.TIME -> moves.minByOrNull {
            pbMetric(it.result, metric) ?: Double.MAX_VALUE
        }
        CounterMetric.OVERALL, CounterMetric.POWER, CounterMetric.TDO -> moves.maxByOrNull {
            pbMetric(it.result, metric) ?: Double.MIN_VALUE
        }
    }

internal fun personalComparator(metric: CounterMetric): Comparator<PersonalCounter> =
    Comparator { left, right ->
        val l = left.metrics.valueFor(metric) ?: Double.MAX_VALUE
        val r = right.metrics.valueFor(metric) ?: Double.MAX_VALUE
        val primary = when (metric) {
            CounterMetric.ESTIMATOR, CounterMetric.TIME -> l.compareTo(r)
            CounterMetric.OVERALL, CounterMetric.POWER, CounterMetric.TDO -> r.compareTo(l)
        }
        if (primary != 0) primary else {
            (right.evaluatedLevel ?: 0.0).compareTo(left.evaluatedLevel ?: 0.0)
        }
    }

internal fun PbDefender.matches(mine: OwnedPokemon): Boolean {
    val pbShadow = pokemonId.contains("SHADOW", ignoreCase = true)
    if (pbShadow != mine.shadow) return false
    return mine.matchKeys.any { key ->
        key.equals(pokemonId, ignoreCase = true) ||
            PokebattlerNameNormalizer.looseKey(key) == PokebattlerNameNormalizer.looseKey(pokemonId)
    }
}

internal fun selectPersonalBossMoveset(block: PbAttackerBlock, requested: RaidBossMoveset?): PbMoveset? {
    // Same saturation shape as the by-level path: per-moveset blocks can come back
    // without defenders while `randomMove` still carries the overall ranking.
    fun ranked(vararg candidates: PbMoveset?): PbMoveset? =
        candidates.firstOrNull { it?.defenders?.isNotEmpty() == true }
    if (requested == null || requested.isRandom) {
        return ranked(block.randomMove, block.byMove.firstOrNull())
            ?: block.randomMove
            ?: block.byMove.firstOrNull()
    }
    val exact = block.byMove.firstOrNull {
        personalMovesMatch(it.move1, requested.move1) && personalMovesMatch(it.move2, requested.move2)
    }
    return ranked(
        exact,
        block.randomMove,
        block.byMove.firstOrNull { it.defenders.isNotEmpty() }
    ) ?: exact
        ?: block.randomMove
        ?: block.byMove.firstOrNull()
}

// ── Level bucketing ───────────────────────────────────────────────────────────

/** Below this an owned copy cannot out-rank the same species at level 40 or 50. */
const val MIN_PERSONAL_LEVEL = 40.0
/** The top of the normal level cap, and the fallback for best-buddy levels. */
const val MAX_PERSONAL_LEVEL = 50.0
/**
 * Best Buddy adds a level, so Poke Genie exports do contain 51 — but Pokebattler cannot
 * rank it. `attackers/levels/51` hangs and the gateway returns 504 after 30 s (levels 40
 * and 50 answer in ~300 ms), and with the retry interceptor that alone added ~12 s to
 * every personal load. Best-buddy Pokemon are therefore evaluated at 50.
 */
const val ABSOLUTE_MAX_PERSONAL_LEVEL = 50.0
/**
 * Hard cap on requests above [MAX_PERSONAL_LEVEL]. [personalLevelBucket] currently folds
 * them all into 50, so this only guards against a future bucketing change reopening the
 * per-level fan-out.
 */
const val MAX_ABOVE_MAX_LEVEL_REQUESTS = 4

/**
 * Maps an owned Pokémon's level onto the level Pokébattler will be asked about, or null
 * when it is not worth asking at all.
 *
 * Below [MIN_PERSONAL_LEVEL] the copy is excluded: the same species at 40 or 50 always beats
 * it, so it can never reach a suggested team. Between 40 and 50 it snaps to the nearer
 * endpoint, with an exact 45 rounding *down* so the ranking never flatters a Pokémon. Above
 * 50 it is a Best Buddy, which Pokebattler refuses to rank (see [ABSOLUTE_MAX_PERSONAL_LEVEL]),
 * so it is evaluated at 50 — slightly conservative, and it costs no extra request at all.
 */
fun personalLevelBucket(level: Double): Double? = when {
    !level.isFinite() -> null
    level < MIN_PERSONAL_LEVEL -> null
    level <= MAX_PERSONAL_LEVEL ->
        if (level - MIN_PERSONAL_LEVEL <= MAX_PERSONAL_LEVEL - level) MIN_PERSONAL_LEVEL
        else MAX_PERSONAL_LEVEL
    else -> ABSOLUTE_MAX_PERSONAL_LEVEL
}

/**
 * Which levels to ask Pokébattler about for a roster, and who lands on each.
 *
 * A 2400-row export spans ~54 distinct half-levels. One ~1 MB request per level is both
 * ~54 MB of traffic and far past the point where Pokébattler starts returning 429 (it does
 * so after roughly three rapid requests), which is why the personal tab used to spin
 * forever. This plan is two requests plus at most [MAX_ABOVE_MAX_LEVEL_REQUESTS] extras.
 */
data class PersonalLevelPlan(
    val byLevel: Map<Double, List<OwnedPokemon>>,
    /** Owned Pokémon dropped for being under [MIN_PERSONAL_LEVEL]. */
    val skippedBelowMinimum: Int,
    /** Owned Pokémon with no usable level at all. */
    val skippedWithoutLevel: Int
) {
    val levels: List<Double> get() = byLevel.keys.sorted()
}

fun planPersonalLevels(owned: List<OwnedPokemon>): PersonalLevelPlan {
    val withLevels = owned.mapNotNull { pokemon ->
        pokemon.level?.takeIf { it.isFinite() }?.let { level -> level to pokemon }
    }
    val bucketed = withLevels.mapNotNull { (level, pokemon) ->
        personalLevelBucket(level)?.let { bucket -> bucket to pokemon }
    }
    val grouped = bucketed.groupBy(keySelector = { it.first }, valueTransform = { it.second })
    // Keep 40 and 50 always; cap the best-buddy extras.
    val extras = grouped.keys.filter { it > MAX_PERSONAL_LEVEL }.sorted().take(MAX_ABOVE_MAX_LEVEL_REQUESTS)
    val kept = grouped.filterKeys { it <= MAX_PERSONAL_LEVEL || it in extras }
    return PersonalLevelPlan(
        byLevel = kept,
        skippedBelowMinimum = withLevels.size - bucketed.size,
        skippedWithoutLevel = owned.size - withLevels.size
    )
}
