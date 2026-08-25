package com.example.pokemonalertsv2.data.counters

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.PokebattlerRaidBossEntity
import com.example.pokemonalertsv2.data.database.RaidCounterCacheEntity
import com.example.pokemonalertsv2.data.database.GameMasterDao
import com.example.pokemonalertsv2.data.database.PokebattlerRaidTierEntity
import com.example.pokemonalertsv2.data.database.RaidCounterDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
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
    val isStale: Boolean
)

sealed interface CountersError {
    /** Nothing cached and the network failed. */
    data object Offline : CountersError
    /** Pokebattler rate-limited us even after backing off. */
    data object RateLimited : CountersError
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
            RaidBossCatalogEntry(it.tier, it.pokemonId, it.displayName, it.cp)
        }
        // Rarity drives the tier fallback for bosses the catalogue only knows as "unset".
        val lookupIds = (candidates + candidates.map { PokebattlerNameNormalizer.baseSpeciesId(it) })
            .distinct()
        val rarity = runCatching { gameMasterDao.speciesLookup(lookupIds) }
            .getOrDefault(emptyList())
            .associate { it.pokemonId to it.rarity }
        return resolveBossFromCatalogue(candidates, RaidTierParser.parse(alert), catalogue, name) { id ->
            rarity[id] ?: rarity[PokebattlerNameNormalizer.baseSpeciesId(id)]
        }
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
        source: CounterSource
    ): Result<CountersResult> = withContext(Dispatchers.IO) {
        val attacker = source.attackerSpec(options)
        val key = raidCounterCacheKey(boss.pokemonId, boss.raidLevel, attacker, options)

        val cached = dao.getCache(key)
        val cachedAge = cached?.let { now() - it.fetchedAt }
        if (cached != null && cachedAge != null && cachedAge < CACHE_TTL_MILLIS) {
            return@withContext Result.success(cached.toResult(fromCache = true, isStale = false))
        }

        // One in-flight request at a time, so flicking between option chips cannot fan out
        // into parallel calls against a service that rate-limits.
        requestMutex.withLock {
            // Another caller may have populated the cache while we waited.
            dao.getCache(key)?.let { fresh ->
                if (now() - fresh.fetchedAt < CACHE_TTL_MILLIS) {
                    return@withContext Result.success(fresh.toResult(fromCache = true, isStale = false))
                }
            }

            suspend fun request(pokemonId: String, raidLevel: String) = service.getCounters(
                path = PokebattlerUrls.countersPath(
                    bossPokemonId = pokemonId,
                    raidLevel = raidLevel,
                    attacker = attacker,
                    attackStrategy = options.attackStrategy.apiValue
                ),
                query = PokebattlerUrls.queryParams(options),
                authorization = source.authorizationHeader()
            )

            var attempt = runCatching { request(boss.pokemonId, boss.raidLevel) }

            // Pokebattler has no ranking for some shadow bosses at all and answers 429
            // "Ranking capacity is busy" for every tier -- PALKIA_SHADOW_FORM does this while
            // plain PALKIA and MEWTWO_SHADOW_FORM both work. A shadow boss has the same
            // typing as its base form, so the base ranking is very nearly the same list and
            // beats showing nothing.
            val base = PokebattlerNameNormalizer.baseSpeciesId(boss.pokemonId)
            if (attempt.isFailure && base != boss.pokemonId.uppercase(java.util.Locale.ROOT)) {
                attempt = runCatching { request(base, normalizeTier(boss.raidLevel).removeSuffix("_SHADOW")) }
            }

            attempt.getOrNull()?.toPayload(options.sort)?.let { payload ->
                val timestamp = now()
                dao.saveCache(payload.toEntity(key, boss.raidLevel, timestamp))
                return@withContext Result.success(
                    CountersResult(payload, timestamp, fromCache = false, isStale = false)
                )
            }

            // Network failed, or the response had no usable block. Prefer stale data.
            cached?.let {
                return@withContext Result.success(it.toResult(fromCache = true, isStale = true))
            }
            Result.failure(attempt.exceptionOrNull().toCountersError())
        }
    }

    private fun RaidCounterCacheEntity.toResult(fromCache: Boolean, isStale: Boolean) = CountersResult(
        payload = RaidCountersPayload(
            bossPokemonId = bossPokemonId,
            bossCp = bossCp,
            bossMove1 = bossMove1,
            bossMove2 = bossMove2,
            counters = runCatching {
                json.decodeFromString<List<RaidCounter>>(countersJson)
            }.getOrDefault(emptyList())
        ),
        fetchedAtMillis = fetchedAt,
        fromCache = fromCache,
        isStale = isStale
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
            fetchedAt = timestamp
        )

    companion object {
        /** Pokebattler itself caches for an hour; matching that avoids pointless refetching. */
        private const val CACHE_TTL_MILLIS = 60L * 60 * 1000
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
