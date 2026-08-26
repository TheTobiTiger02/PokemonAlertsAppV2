package com.example.pokemonalertsv2.data.counters

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.RaidTier
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.PokebattlerRaidBossEntity
import com.example.pokemonalertsv2.data.database.RaidCounterCacheEntity
import com.example.pokemonalertsv2.data.database.GameMasterDao
import com.example.pokemonalertsv2.data.database.PokebattlerRaidTierEntity
import com.example.pokemonalertsv2.data.database.RaidCounterDao
import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** A counters list plus how fresh it is. */
data class CountersResult(
    val payload: RaidCountersPayload,
    val fetchedAtMillis: Long,
    val fromCache: Boolean,
    val isStale: Boolean,
    /**
     * Settings Pokébattler could not honour, as user-facing labels.
     *
     * Empty when the request was served exactly as asked. Non-empty means the answer came
     * from [RaidCounterOptions.precomputedBaseline] instead — see the KDoc there.
     */
    val degradedOptions: List<String> = emptyList()
)

sealed interface CountersError {
    /** Nothing cached and the network failed. */
    data object Offline : CountersError
    /** Pokebattler rate-limited us even after backing off. */
    data object RateLimited : CountersError

    /**
     * Pokebattler accepted the request but its own gateway gave up computing it.
     *
     * Distinct from [Offline] because the connection was fine: the origin takes longer than
     * its 30 s limit and returns 504. Pokébox ranking does this consistently for large
     * boxes, so the UI must say so rather than blaming the user's connection.
     */
    data object ServerTimeout : CountersError

    data class Unexpected(val message: String) : CountersError
}

class RaidCountersRepository @VisibleForTesting internal constructor(
    private val service: PokebattlerService,
    private val dao: RaidCounterDao,
    private val gameMasterDao: GameMasterDao,
    private val now: () -> Long = System::currentTimeMillis
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val catalogueMutex = Mutex()
    private val requestMutex = Mutex()
    private val personalRequestSemaphore = Semaphore(1)
    private val personalResponseCache = LinkedHashMap<String, TimedPersonalResponse>()

    /**
     * Bosses known to serve only [RaidCounterOptions.precomputedBaseline], keyed by
     * `pokemonId|raidLevel`, with the time we found out.
     *
     * Without this every reopen, every option chip and the personal path's second level
     * bucket each pay the discovery cost again — 3.5 s for a 429, up to
     * [FIRST_ATTEMPT_BUDGET_MILLIS] for a 504. In memory only; re-learning it after a
     * process restart costs one request.
     */
    private val baselineOnlyBosses = LinkedHashMap<String, Long>()

    /**
     * Refreshes the boss catalogue when it is older than [CATALOGUE_TTL_MILLIS].
     *
     * @param force bypasses the TTL, but never more often than [CATALOGUE_FORCE_FLOOR_MILLIS],
     *   so a boss that genuinely cannot be resolved cannot turn Retry into a download loop.
     */
    suspend fun syncCatalogueIfStale(force: Boolean = false) = withContext(Dispatchers.IO) {
        catalogueMutex.withLock {
            val fetchedAt = dao.catalogueFetchedAt() ?: 0L
            val age = now() - fetchedAt
            // The same payload also carries the per-tier boss stats, so refresh when those
            // are missing even if the boss list itself is still fresh.
            val tiersMissing = gameMasterDao.raidTierCount() == 0
            val due = if (force) age >= CATALOGUE_FORCE_FLOOR_MILLIS else age >= CATALOGUE_TTL_MILLIS
            if (!due && !tiersMissing) return@withLock

            runCatching { service.getRaidCatalogue() }
                .onSuccess { response ->
                    val timestamp = now()
                    val entities = response.toEntities(timestamp)
                    if (entities.isNotEmpty()) dao.replaceCatalogue(entities)
                    // The same payload carries each tier boss HP and CPM, which the local
                    // simulator needs and which no other endpoint exposes.
                    val tiers = response.toTierEntities(timestamp)
                    if (tiers.isNotEmpty()) gameMasterDao.insertRaidTiers(tiers)
                }
            // A failure here is not fatal: any previously cached catalogue still resolves
            // bosses, and the caller reports its own error if resolution then fails.
        }
    }

    suspend fun resolveBoss(alert: PokemonAlert): BossResolution = withContext(Dispatchers.IO) {
        syncCatalogueIfStale()
        resolveAgainstCatalogue(alert)
    }

    /** Re-resolves after forcing a catalogue refresh; for the Retry affordance. */
    suspend fun retryResolveBoss(alert: PokemonAlert): BossResolution = withContext(Dispatchers.IO) {
        syncCatalogueIfStale(force = true)
        resolveAgainstCatalogue(alert)
    }

    private suspend fun resolveAgainstCatalogue(alert: PokemonAlert): BossResolution {
        val name = alert.pokemon?.takeIf { it.isNotBlank() } ?: alert.cleanPokemonName
        val candidates = PokebattlerNameNormalizer.candidateIds(name, alert.pokemonForm)
        val catalogue = dao.getCatalogue().map {
            RaidBossCatalogEntry(it.tier, it.pokemonId, it.displayName, it.cp, it.shiny)
        }
        // Rarity drives the tier fallback for bosses the catalogue only knows as "unset".
        val lookupIds = (candidates + candidates.map { PokebattlerNameNormalizer.baseSpeciesId(it) })
            .distinct()
        val rarity = runCatching { gameMasterDao.speciesLookup(lookupIds) }
            .getOrDefault(emptyList())
            .associate { it.pokemonId to it.rarity }
        fun rarityFor(id: String) = rarity[id] ?: rarity[PokebattlerNameNormalizer.baseSpeciesId(id)]

        val resolved = resolveBossFromCatalogue(
            candidates, RaidTierParser.parse(alert), catalogue, name, ::rarityFor
        )
        if (resolved is BossResolution.Resolved) return resolved

        // The /raids catalogue omits a few Pokemon that really do appear as raid bosses --
        // HONEDGE is in /pokemon but has no catalogue row at all, which cost every Honedge
        // raid its counters. Pokebattler accepts any boss at any tier (the tier only sets
        // HP and CPM), so a species hit plus an inferred tier beats refusing outright.
        return resolveFromSpecies(candidates, RaidTierParser.parse(alert), ::rarityFor, name)
            ?: resolved
    }

    /** Last-resort resolution against the game master, for bosses absent from `/raids`. */
    private suspend fun resolveFromSpecies(
        candidates: List<String>,
        parsedTier: RaidTier?,
        rarityFor: (String) -> String?,
        attemptedName: String
    ): BossResolution.Resolved? {
        val known = runCatching { gameMasterDao.species(candidates) }
            .getOrDefault(emptyList())
            .associateBy { it.pokemonId.uppercase(java.util.Locale.ROOT) }
        if (known.isEmpty()) return null
        // Candidates are ordered most-specific-first, so keep that order rather than
        // whatever the query returned.
        val match = candidates
            .firstNotNullOfOrNull { known[it.uppercase(java.util.Locale.ROOT)] }
            ?: return null
        return BossResolution.Resolved(
            pokemonId = match.pokemonId,
            raidLevel = parsedTier?.pokebattlerRaidLevel
                ?: fallbackRaidLevel(match.pokemonId, rarityFor(match.pokemonId)),
            displayName = prettifyPokemonName(match.pokemonId),
            bossCp = null
        )
    }

    /**
     * Counters for a resolved boss, cache first.
     *
     * Serves a cached list immediately when it is younger than [CACHE_TTL_MILLIS]. Beyond
     * that it revalidates, but still falls back to the stale copy if the network fails, so
     * the card degrades to "showing cached counters" rather than to an error.
     */
    suspend fun loadCounters(
        boss: BossResolution.Resolved,
        options: RaidCounterOptions,
        source: CounterSource,
        bossMoveset: RaidBossMoveset? = null
    ): Result<CountersResult> = withContext(Dispatchers.IO) {
        // A boss already known to serve only the baseline skips straight to it, so the
        // discovery cost is paid once rather than on every reopen and every chip change.
        val requested = if (source.id == CounterSourceId.ALL_POKEMON && isBaselineOnly(boss)) {
            options.precomputedBaseline()
        } else {
            options
        }
        val first = loadCountersWith(boss, requested, options, source, bossMoveset)

        // The Pokebox source has its own failure mode (see loadPokeBoxPersonal) and its own
        // attacker spec, so downgrading the by-level options cannot rescue it.
        if (source.id != CounterSourceId.ALL_POKEMON) return@withContext first
        if (first.isSuccess) return@withContext first
        if (!first.shouldTryBaseline() || requested.isPrecomputedBaseline) return@withContext first

        markBaselineOnly(boss)
        loadCountersWith(boss, options.precomputedBaseline(), options, source, bossMoveset)
            .recoverCatching { throw first.exceptionOrNull() ?: it }
    }

    /**
     * Counters for a resolved boss, cache first.
     *
     * Serves a cached list immediately when it is younger than [CACHE_TTL_MILLIS]. Beyond
     * that it revalidates, but still falls back to the stale copy if the network fails, so
     * the card degrades to "showing cached counters" rather than to an error.
     *
     * @param effective the options actually sent.
     * @param requestedByUser what the user asked for, used only to describe the downgrade.
     */
    private suspend fun loadCountersWith(
        boss: BossResolution.Resolved,
        effective: RaidCounterOptions,
        requestedByUser: RaidCounterOptions,
        source: CounterSource,
        bossMoveset: RaidBossMoveset?
    ): Result<CountersResult> = withContext(Dispatchers.IO) {
        val attacker = source.attackerSpec(effective)
        val key = raidCounterCacheKey(boss.pokemonId, boss.raidLevel, attacker, effective, bossMoveset)
        val degraded = if (effective == requestedByUser) {
            emptyList()
        } else {
            requestedByUser.downgradesFromBaseline()
        }

        val cached = dao.getCache(key)
        val cachedAge = cached?.let { now() - it.fetchedAt }
        if (cached != null && cachedAge != null && cachedAge < CACHE_TTL_MILLIS) {
            return@withContext Result.success(
                cached.toResult(fromCache = true, isStale = false, degradedOptions = degraded)
            )
        }

        // One in-flight request at a time, so flicking between option chips cannot fan out
        // into parallel calls against a service that rate-limits.
        requestMutex.withLock {
            // Another caller may have populated the cache while we waited.
            dao.getCache(key)?.let { fresh ->
                if (now() - fresh.fetchedAt < CACHE_TTL_MILLIS) {
                    return@withContext Result.success(
                        fresh.toResult(fromCache = true, isStale = false, degradedOptions = degraded)
                    )
                }
            }

            suspend fun request(pokemonId: String, raidLevel: String) = service.getCounters(
                path = PokebattlerUrls.countersPath(
                    bossPokemonId = pokemonId,
                    raidLevel = raidLevel,
                    attacker = attacker,
                    attackStrategy = effective.attackStrategy.apiValue
                ),
                query = PokebattlerUrls.queryParams(effective),
                authorization = source.authorizationHeader()
            )

            // Budget the attempt: an unavailable combination answers 429 in ~3.5 s but 504
            // only after ~30 s, and waiting that long before downgrading reads as a hang.
            var attempt = budgeted { request(boss.pokemonId, boss.raidLevel) }

            // Pokebattler has no ranking for some shadow bosses at all and answers 429
            // "Ranking capacity is busy" for every tier -- PALKIA_SHADOW_FORM does this while
            // plain PALKIA and MEWTWO_SHADOW_FORM both work. A shadow boss has the same
            // typing as its base form, so the base ranking is very nearly the same list and
            // beats showing nothing.
            val base = PokebattlerNameNormalizer.baseSpeciesId(boss.pokemonId)
            if (attempt.isFailure && base != boss.pokemonId.uppercase(java.util.Locale.ROOT)) {
                attempt = budgeted { request(base, normalizeTier(boss.raidLevel).removeSuffix("_SHADOW")) }
            }

            // Costume forms are their own boss id but the same fight, and Pokebattler has
            // not precomputed most of them: PIKACHU_GOTOUR_2026_C_FORM, PSYDUCK_SWIM_2025_FORM
            // and TATSUGIRI_CURLY_FORM all fail while the plain species answers. Substituting
            // is exact rather than approximate here, because the substitution only happens
            // when the game master says the types and base stats are identical -- which is
            // also why KYUREM_BLACK_FORM (310 attack vs KYUREM's 246) is never substituted.
            if (attempt.isFailure) {
                cosmeticBaseFor(boss.pokemonId)?.let { plain ->
                    attempt = budgeted { request(plain, boss.raidLevel) }
                }
            }

            attempt.getOrNull()?.toPayload(effective.sort, bossMoveset)?.let { payload ->
                val timestamp = now()
                dao.saveCache(payload.toEntity(key, boss.raidLevel, timestamp))
                return@withContext Result.success(
                    CountersResult(
                        payload = payload,
                        fetchedAtMillis = timestamp,
                        fromCache = false,
                        isStale = false,
                        degradedOptions = degraded
                    )
                )
            }

            // Network failed, or the response had no usable block. Prefer stale data.
            cached?.let {
                return@withContext Result.success(
                    it.toResult(fromCache = true, isStale = true, degradedOptions = degraded)
                )
            }
            Result.failure(attempt.exceptionOrNull().toCountersError())
        }
    }

    /**
     * Runs one request under [FIRST_ATTEMPT_BUDGET_MILLIS], turning an overrun into a
     * [CountersError.ServerTimeout] rather than letting the 45 s OkHttp read timeout decide.
     */
    private suspend fun <T> budgeted(block: suspend () -> T): Result<T> = runCatching {
        withTimeoutOrNull(FIRST_ATTEMPT_BUDGET_MILLIS) { block() }
            ?: throw CountersException(CountersError.ServerTimeout)
    }

    /** Only a capacity or timeout verdict can be rescued by asking for less. */
    private fun Result<*>.shouldTryBaseline(): Boolean =
        when ((exceptionOrNull() as? CountersException)?.error) {
            CountersError.RateLimited, CountersError.ServerTimeout -> true
            else -> false
        }

    /**
     * The plain species behind a purely cosmetic form, or null if there is not one.
     *
     * Walks the id's underscore segments from longest to shortest and returns the first
     * ancestor the game master agrees is the *same fight* -- identical typing and identical
     * base attack, defense and stamina. A form that reshuffles stats (KYUREM_BLACK_FORM,
     * the Therian forms, any mega) can therefore never be substituted, while a costume can.
     */
    private suspend fun cosmeticBaseFor(pokemonId: String): String? {
        val id = pokemonId.uppercase(java.util.Locale.ROOT)
        val self = runCatching { gameMasterDao.species(id) }.getOrNull() ?: return null
        val parts = id.split("_")
        for (end in parts.size - 1 downTo 1) {
            val ancestor = parts.take(end).joinToString("_")
            val other = runCatching { gameMasterDao.species(ancestor) }.getOrNull() ?: continue
            val sameFight = other.type1 == self.type1 &&
                other.type2 == self.type2 &&
                other.baseAttack == self.baseAttack &&
                other.baseDefense == self.baseDefense &&
                other.baseStamina == self.baseStamina
            if (sameFight) return other.pokemonId
        }
        return null
    }

    private fun baselineKey(boss: BossResolution.Resolved) =
        boss.pokemonId + "|" + boss.raidLevel

    private fun isBaselineOnly(boss: BossResolution.Resolved): Boolean =
        synchronized(baselineOnlyBosses) {
            val key = baselineKey(boss)
            val at = baselineOnlyBosses[key] ?: return false
            if (now() - at >= CACHE_TTL_MILLIS) {
                baselineOnlyBosses.remove(key)
                false
            } else {
                true
            }
        }

    private fun markBaselineOnly(boss: BossResolution.Resolved) {
        synchronized(baselineOnlyBosses) {
            baselineOnlyBosses[baselineKey(boss)] = now()
            while (baselineOnlyBosses.size > MAX_BASELINE_ONLY_BOSSES) {
                baselineOnlyBosses.entries.firstOrNull()?.let { baselineOnlyBosses.remove(it.key) }
            }
        }
    }

    /**
     * Joins a PokeGenie roster to Pokébattler's by-level responses.
     *
     * This deliberately contains no damage or timing calculation. Every metric in the
     * returned rows is copied from Pokébattler; the local roster contributes only ownership,
     * level and the recorded moves used to select a server-side byMove result.
     *
     * The roster is bucketed onto a tiny set of levels before any request goes out. A
     * 2400-row export spans ~54 distinct half-levels, and one ~1 MB request per level is
     * both ~54 MB of traffic and far past the point where Pokébattler starts returning 429
     * (it does so after roughly three requests). Anything below [MIN_PERSONAL_LEVEL] is
     * dropped outright — it cannot out-rank the same species at 40 or 50 — and everything
     * else snaps to the nearer of 40 / 50, so this costs exactly two requests.
     *
     * Note the ceiling this join has regardless of the roster: a counters response carries
     * only Pokébattler's **top 30** attackers per boss moveset, so the result is the
     * intersection of the roster with those 30. That is the right answer for "what should I
     * bring" — nothing outside the global top 30 belongs in a best six — but the count it
     * produces is a match count, not a roster count, and
     * [PokebattlerPersonalProgress.serverCandidates] exists so the UI can say which.
     *
     * @param onPartial invoked after each bucket lands, so the level-40 list can render
     *   while level 50 is still in flight.
     */
    suspend fun loadPokeGeniePersonal(
        boss: BossResolution.Resolved,
        options: RaidCounterOptions,
        owned: List<OwnedPokemon>,
        movesMode: PersonalMovesMode,
        bossMoveset: RaidBossMoveset? = null,
        onPartial: suspend (PokebattlerPersonalResult) -> Unit = {}
    ): Result<PokebattlerPersonalResult> = withContext(Dispatchers.IO) {
        val plan = planPersonalLevels(owned)
        val byLevel = plan.byLevel
        val levels = plan.levels
        val skippedBelowMinimum = plan.skippedBelowMinimum
        if (levels.isEmpty()) {
            return@withContext Result.failure(
                CountersException(
                    CountersError.Unexpected(
                        if (skippedBelowMinimum == 0) {
                            "No imported Pokémon has a valid level."
                        } else {
                            "None of your $skippedBelowMinimum imported Pokémon is level " +
                                "${MIN_PERSONAL_LEVEL.toInt()} or above."
                        }
                    )
                )
            )
        }

        val metric = options.sort.toCounterMetric()
        val responses = LinkedHashMap<Double, PokebattlerCountersResponse>()
        val ranked = mutableListOf<PersonalCounter>()
        val missing = mutableListOf<Double>()
        val serverCandidates = mutableSetOf<String>()
        val substituted = mutableListOf<Double>()
        var firstFailure: Throwable? = null

        levels.forEachIndexed { index, level ->
            if (index > 0) delay(INTER_REQUEST_DELAY_MILLIS)
            val outcome = runCatching {
                personalResponse(boss = boss, options = options, level = level, bossMoveset = bossMoveset)
            }
            outcome.exceptionOrNull()?.let { if (firstFailure == null) firstFailure = it }

            // A level the service will not rank is still rankable at a lower one, and
            // that happens routinely: for a boss outside the current raid rotation only
            // `attackers/levels/40` is precomputed, so the level-50 bucket fails there.
            // Evaluating those Pokemon at the best level that did come back beats dropping
            // them, and understates rather than flatters them. The reported evaluatedLevel
            // always follows the response actually used, so the UI never claims a level we
            // did not fetch.
            val substitute = responses.keys.filter { it < level }.maxOrNull()
            val (evaluatedLevel, response) = when {
                outcome.isSuccess -> level to outcome.getOrThrow()
                substitute != null -> substitute to responses.getValue(substitute)
                else -> {
                    missing += level
                    return@forEachIndexed
                }
            }
            if (outcome.isFailure) substituted += level
            if (outcome.isSuccess) responses[level] = response

            serverCandidates += personalCandidateIds(response, bossMoveset)
            ranked += rankPersonalLevel(
                level = evaluatedLevel,
                owned = byLevel[level].orEmpty(),
                response = response,
                movesMode = movesMode,
                metric = metric,
                bossMoveset = bossMoveset
            )
            onPartial(
                buildPersonalResult(
                    ranked = ranked,
                    metric = metric,
                    completedLevels = index + 1,
                    totalLevels = levels.size,
                    missing = missing,
                    evaluatedLevels = responses.keys.toList(),
                    skippedBelowMinimum = skippedBelowMinimum,
                    serverCandidates = serverCandidates.size,
                    substitutedLevels = substituted.toList()
                )
            )
        }

        if (responses.isEmpty()) {
            return@withContext Result.failure(firstFailure.toCountersError())
        }
        Result.success(
            buildPersonalResult(
                ranked = ranked,
                metric = metric,
                completedLevels = levels.size - missing.size,
                totalLevels = levels.size,
                missing = missing,
                evaluatedLevels = responses.keys.toList(),
                skippedBelowMinimum = skippedBelowMinimum,
                serverCandidates = serverCandidates.size,
                substitutedLevels = substituted.toList()
            )
        )
    }

    /** The distinct attackers Pokebattler returned for the selected boss moveset. */
    private fun personalCandidateIds(
        response: PokebattlerCountersResponse,
        bossMoveset: RaidBossMoveset?
    ): Set<String> {
        val block = response.attackers.firstOrNull() ?: return emptySet()
        val moveset = selectPersonalBossMoveset(block, bossMoveset) ?: return emptySet()
        return moveset.defenders.map { it.pokemonId }.toSet()
    }

    private fun buildPersonalResult(
        ranked: List<PersonalCounter>,
        metric: CounterMetric,
        completedLevels: Int,
        totalLevels: Int,
        missing: List<Double>,
        evaluatedLevels: List<Double>,
        skippedBelowMinimum: Int,
        serverCandidates: Int,
        substitutedLevels: List<Double>
    ): PokebattlerPersonalResult {
        val sorted = ranked.sortedWith(personalComparator(metric))
        val groupedTeam = buildPersonalTeam(sorted)
            .groupBy { listOf(it.pokemonId, it.fastMove.moveId, it.chargedMove.moveId).joinToString("|") }
            .values
            .map { copies -> PersonalTeamSlot(copies.first(), copies.size) }
        return PokebattlerPersonalResult(
            ranking = PersonalRanking(
                ranked = sorted,
                team = groupedTeam,
                combinedTdo = 0.0,
                bossHp = 0,
                teamDamageWithinTimer = 0.0,
                teamDeaths = 0.0,
                teamTimeToWinSeconds = null,
                serverBacked = true,
                evaluatedLevels = evaluatedLevels,
                missingLevels = missing.toList()
            ),
            progress = PokebattlerPersonalProgress(
                completedLevels = completedLevels,
                totalLevels = totalLevels,
                missingLevels = missing.toList(),
                skippedBelowMinimumLevel = skippedBelowMinimum,
                serverCandidates = serverCandidates,
                substitutedLevels = substitutedLevels
            )
        )
    }

    /** Converts a personalized Pokébox response without running the local simulator. */
    suspend fun loadPokeBoxPersonal(
        boss: BossResolution.Resolved,
        options: RaidCounterOptions,
        userId: String,
        authorization: String?,
        bossMoveset: RaidBossMoveset? = null
    ): Result<PokebattlerPersonalResult> = loadCounters(
        boss = boss,
        options = options,
        source = PokebattlerPokeBoxSource(userId, authorization),
        bossMoveset = bossMoveset
    ).map { result ->
        val ranked = result.payload.counters.map { it.toPersonalCounterFromPokeBox() }
        val team = ranked.take(6)
            .groupBy { listOf(it.pokemonId, it.fastMove.moveId, it.chargedMove.moveId).joinToString("|") }
            .values
            .map { copies -> PersonalTeamSlot(copies.first(), copies.size) }
        PokebattlerPersonalResult(
            ranking = PersonalRanking(
                ranked = ranked,
                team = team,
                combinedTdo = 0.0,
                bossHp = 0,
                teamDamageWithinTimer = 0.0,
                serverBacked = true,
                evaluatedLevels = ranked.mapNotNull { it.evaluatedLevel }.distinct()
            ),
            progress = PokebattlerPersonalProgress(1, 1)
        )
    }

    /**
     * One by-level response, with the same precomputed-baseline fallback as [loadCounters].
     *
     * The level itself is part of the path, and only `levels/40` is precomputed for an
     * off-rotation boss, so a bucket other than 40 fails there however the query is
     * written. The caller handles that by re-ranking those Pokemon against the level-40
     * response and reporting `evaluatedLevel = 40`; this only strips the query parameters
     * that Pokebattler cannot serve.
     */
    private suspend fun personalResponse(
        boss: BossResolution.Resolved,
        options: RaidCounterOptions,
        level: Double,
        bossMoveset: RaidBossMoveset?
    ): PokebattlerCountersResponse {
        val effective = if (isBaselineOnly(boss)) options.precomputedBaseline() else options
        return runCatching { personalResponseWith(boss, effective, level, bossMoveset) }
            .getOrElse { throwable ->
                val retryable = (throwable as? CountersException)?.error
                    .let { it == CountersError.RateLimited || it == CountersError.ServerTimeout }
                if (!retryable || effective.isPrecomputedBaseline) throw throwable
                markBaselineOnly(boss)
                personalResponseWith(boss, options.precomputedBaseline(), level, bossMoveset)
            }
    }

    private suspend fun personalResponseWith(
        boss: BossResolution.Resolved,
        options: RaidCounterOptions,
        level: Double,
        bossMoveset: RaidBossMoveset?
    ): PokebattlerCountersResponse {
        val attacker = AttackerSpec.ExactLevel(level)
        val key = personalResponseCacheKey(boss.pokemonId, boss.raidLevel, attacker, options, bossMoveset)
        synchronized(personalResponseCache) {
            personalResponseCache[key]?.takeIf { now() - it.fetchedAt < CACHE_TTL_MILLIS }?.let { return it.response }
        }
        suspend fun request(pokemonId: String, raidLevel: String): PokebattlerCountersResponse =
            personalRequestSemaphore.withPermit {
                service.getCounters(
                    path = PokebattlerUrls.countersPath(
                        bossPokemonId = pokemonId,
                        raidLevel = raidLevel,
                        attacker = attacker,
                        attackStrategy = options.attackStrategy.apiValue
                    ),
                    query = PokebattlerUrls.queryParams(options),
                    authorization = null
                )
            }
        val direct = budgeted { request(boss.pokemonId, boss.raidLevel) }
        val response = direct.getOrElse {
            val base = PokebattlerNameNormalizer.baseSpeciesId(boss.pokemonId)
            if (base == boss.pokemonId.uppercase(java.util.Locale.ROOT)) {
                val plain = cosmeticBaseFor(boss.pokemonId)
                    ?: throw (it as? CountersException) ?: it.toCountersError()
                return@getOrElse budgeted { request(plain, boss.raidLevel) }.getOrElse { last ->
                    throw (last as? CountersException) ?: last.toCountersError()
                }
            }
            budgeted { request(base, normalizeTier(boss.raidLevel).removeSuffix("_SHADOW")) }
                .getOrElse { fallbackFailure ->
                    val plain = cosmeticBaseFor(boss.pokemonId)
                        ?: throw (fallbackFailure as? CountersException) ?: fallbackFailure.toCountersError()
                    budgeted { request(plain, boss.raidLevel) }.getOrElse { last ->
                        throw (last as? CountersException) ?: last.toCountersError()
                    }
                }
        }
        synchronized(personalResponseCache) {
            personalResponseCache[key] = TimedPersonalResponse(response, now())
            while (personalResponseCache.size > MAX_PERSONAL_RESPONSE_CACHE) {
                personalResponseCache.entries.firstOrNull()?.let { personalResponseCache.remove(it.key) }
            }
        }
        return response
    }

    private fun rankPersonalLevel(
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
                selectedMove = selected.first,
                evaluatedLevel = level,
                movesetAssumed = selected.second
            )
        }
    }

    private fun selectOwnedMoves(
        defender: PbDefender,
        mine: OwnedPokemon,
        mode: PersonalMovesMode,
        metric: CounterMetric
    ): Pair<PbByMove, Boolean>? {
        val moves = defender.byMove.filter { it.result != null }
        if (moves.isEmpty()) return null
        val fast = mine.quickMove
        val charges = listOfNotNull(mine.chargeMove, mine.chargeMove2)
        if (mode == PersonalMovesMode.CURRENT && (fast.isNullOrBlank() || charges.isEmpty())) return null

        val constrained = moves.filter { candidate ->
            (fast == null || personalMovesMatch(candidate.move1, fast)) &&
                (charges.isEmpty() || charges.any { personalMovesMatch(candidate.move2, it) })
        }
        val pool = if (mode == PersonalMovesMode.CURRENT) constrained else constrained.ifEmpty { moves }
        val selected = selectPersonalMove(pool, metric) ?: return null
        val assumed = mode == PersonalMovesMode.BEST_POTENTIAL &&
            (fast.isNullOrBlank() || charges.isEmpty() ||
                !personalMovesMatch(selected.move1, fast) ||
                charges.none { personalMovesMatch(selected.move2, it) })
        return selected to assumed
    }

    private fun selectPersonalMove(moves: List<PbByMove>, metric: CounterMetric): PbByMove? =
        when (metric) {
            CounterMetric.ESTIMATOR, CounterMetric.TIME -> moves.minByOrNull {
                pbMetric(it.result, metric) ?: Double.MAX_VALUE
            }
            CounterMetric.OVERALL, CounterMetric.POWER, CounterMetric.TDO -> moves.maxByOrNull {
                pbMetric(it.result, metric) ?: Double.MIN_VALUE
            }
        }

    private fun personalComparator(metric: CounterMetric): Comparator<PersonalCounter> =
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

    private fun buildPersonalTeam(ranked: List<PersonalCounter>): List<PersonalCounter> {
        var megaCount = 0
        return ranked.filter { candidate ->
            val mega = candidate.pokemonId.contains("_MEGA", true) || candidate.pokemonId.contains("_PRIMAL", true)
            if (mega && megaCount >= 1) return@filter false
            if (mega) megaCount++
            true
        }.take(6)
    }

    private fun PbDefender.matches(mine: OwnedPokemon): Boolean {
        val pbShadow = pokemonId.contains("SHADOW", ignoreCase = true)
        if (pbShadow != mine.shadow) return false
        return mine.matchKeys.any { key ->
            key.equals(pokemonId, ignoreCase = true) ||
                PokebattlerNameNormalizer.looseKey(key) == PokebattlerNameNormalizer.looseKey(pokemonId)
        }
    }

    private fun selectPersonalBossMoveset(block: PbAttackerBlock, requested: RaidBossMoveset?): PbMoveset? {
        if (requested == null || requested.isRandom) return block.randomMove ?: block.byMove.firstOrNull()
        return block.byMove.firstOrNull {
            personalMovesMatch(it.move1, requested.move1) && personalMovesMatch(it.move2, requested.move2)
        } ?: block.randomMove ?: block.byMove.firstOrNull()
    }

    private data class TimedPersonalResponse(
        val response: PokebattlerCountersResponse,
        val fetchedAt: Long
    )

    private fun RaidCounterCacheEntity.toResult(
        fromCache: Boolean,
        isStale: Boolean,
        degradedOptions: List<String> = emptyList()
    ) = CountersResult(
        payload = RaidCountersPayload(
            bossPokemonId = bossPokemonId,
            bossCp = bossCp,
            bossMove1 = bossMove1,
            bossMove2 = bossMove2,
            counters = runCatching {
                json.decodeFromString<List<RaidCounter>>(countersJson)
            }.getOrDefault(emptyList()),
            availableBossMovesets = runCatching {
                json.decodeFromString<List<RaidBossMoveset>>(availableBossMovesetsJson)
            }.getOrDefault(emptyList())
        ),
        fetchedAtMillis = fetchedAt,
        fromCache = fromCache,
        isStale = isStale,
        degradedOptions = degradedOptions
    )

    private fun RaidCountersPayload.toEntity(key: String, raidLevel: String, timestamp: Long) =
        RaidCounterCacheEntity(
            cacheKey = key,
            bossPokemonId = bossPokemonId,
            raidLevel = raidLevel,
            bossCp = bossCp,
            bossMove1 = bossMove1,
            bossMove2 = bossMove2,
            countersJson = json.encodeToString(counters),
            availableBossMovesetsJson = json.encodeToString(availableBossMovesets),
            fetchedAt = timestamp
        )

    companion object {
        /** Pokebattler itself caches for an hour; matching that avoids pointless refetching. */
        private const val CACHE_TTL_MILLIS = 60L * 60 * 1000
        private const val MAX_PERSONAL_RESPONSE_CACHE = 96

        /** Pokebattler starts returning 429 after roughly three rapid requests. */
        private const val INTER_REQUEST_DELAY_MILLIS = 400L

        /**
         * How long one attempt may take before it is treated as unavailable.
         *
         * A combination Pokebattler has not precomputed answers 429 in ~3.5 s but 504 only
         * after ~30 s. Waiting the full 30 s before downgrading reads as a hang, and a
         * served combination answers in well under a second, so this is generous.
         */
        private const val FIRST_ATTEMPT_BUDGET_MILLIS = 12_000L

        /** Plenty for one session's browsing; the map is only an optimisation. */
        private const val MAX_BASELINE_ONLY_BOSSES = 64
        private const val CATALOGUE_TTL_MILLIS = 6L * 60 * 60 * 1000
        private const val CATALOGUE_FORCE_FLOOR_MILLIS = 15L * 60 * 1000

        @Volatile
        private var INSTANCE: RaidCountersRepository? = null

        fun getInstance(context: Context): RaidCountersRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RaidCountersRepository(
                    service = PokebattlerApi.service(context.applicationContext),
                    dao = AppDatabase.getDatabase(context.applicationContext).raidCounterDao(),
                    gameMasterDao = AppDatabase.getDatabase(context.applicationContext).gameMasterDao()
                ).also { INSTANCE = it }
            }
        }
    }
}

private fun Throwable?.toCountersError(): CountersException = CountersException(
    when {
        this is HttpException && code() == 429 -> CountersError.RateLimited
        // 504/524 from the gateway, 408/522 from the edge: the origin ran out of time.
        this is HttpException && code() in setOf(408, 504, 522, 524) -> CountersError.ServerTimeout
        this is HttpException && code() in 500..599 ->
            CountersError.Unexpected("Pokébattler is unavailable (HTTP ${code()}).")
        this is java.net.SocketTimeoutException -> CountersError.ServerTimeout
        this is IOException -> CountersError.Offline
        else -> CountersError.Unexpected(this?.message ?: "Couldn't load counters")
    }
)

class CountersException(val error: CountersError) : Exception()

/**
 * Flattens the tier buckets into rows.
 *
 * A boss can appear in several tiers, and `RAID_LEVEL_UNSET` holds every Pokémon rather
 * than a real tier; both are kept, because the resolver needs the full id vocabulary and
 * decides for itself which tiers are queryable.
 */
private fun PokebattlerRaidsResponse.toEntities(timestamp: Long): List<PokebattlerRaidBossEntity> =
    tiers.flatMap { bucket ->
        val tier = bucket.tier ?: return@flatMap emptyList()
        bucket.raids.mapNotNull { raid ->
            val id = raid.pokemonId ?: raid.pokemon ?: return@mapNotNull null
            PokebattlerRaidBossEntity(
                tier = tier,
                pokemonId = id,
                displayName = prettifyPokemonName(raid.pokemon ?: id),
                cp = raid.cp?.takeIf { it > 0 },
                shiny = raid.shiny,
                fetchedAt = timestamp
            )
        }
    }.distinctBy { it.tier to it.pokemonId }

/** Per-tier boss stats, keyed by the normalized (queryable) tier token. */
private fun PokebattlerRaidsResponse.toTierEntities(timestamp: Long): List<PokebattlerRaidTierEntity> =
    tiers.mapNotNull { bucket ->
        val tier = bucket.tier ?: return@mapNotNull null
        val info = bucket.info ?: return@mapNotNull null
        if (info.hp <= 0 || info.cpm <= 0.0) return@mapNotNull null
        PokebattlerRaidTierEntity(
            tier = normalizeTier(tier),
            hp = info.hp,
            cpm = info.cpm,
            combatTimeMs = info.combatTimeMs.takeIf { it > 0 } ?: 180_000,
            fetchedAt = timestamp
        )
    }.distinctBy { it.tier }
