package com.example.pokemonalertsv2.data.gamemaster

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.counters.PokebattlerApi
import com.example.pokemonalertsv2.data.counters.PokebattlerService
import com.example.pokemonalertsv2.data.counters.PokemonSpriteUrls
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.GameMasterDao
import com.example.pokemonalertsv2.data.database.PokebattlerMoveEntity
import com.example.pokemonalertsv2.data.database.PokebattlerRaidTierEntity
import com.example.pokemonalertsv2.data.database.PokebattlerSpeciesEntity
import com.example.pokemonalertsv2.data.database.SpeciesLookupRow
import com.example.pokemonalertsv2.data.counters.PokebattlerNameNormalizer
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import com.example.pokemonalertsv2.data.sim.PokemonType
import com.example.pokemonalertsv2.data.sim.SimMove
import com.example.pokemonalertsv2.data.sim.SimSpecies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Base stats, movesets and move numbers, cached locally.
 *
 * This is what makes the local simulator possible: without it we could only say which
 * of the user's Pokemon appear in a generic ranking, never how their actual level, IVs
 * and moveset perform.
 */
class GameMasterRepository @VisibleForTesting internal constructor(
    private val service: PokebattlerService,
    private val dao: GameMasterDao,
    private val now: () -> Long = System::currentTimeMillis
) {

    private val mutex = Mutex()

    /** True when the simulator has the data it needs. */
    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) { dao.speciesCount() > 0 }

    /**
     * Downloads the game master if it is missing or stale.
     *
     * Roughly 420 KB gzipped for both endpoints, refreshed weekly, so this costs about as
     * much as one counters query per week.
     */
    suspend fun syncIfNeeded(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val fetchedAt = dao.speciesFetchedAt() ?: 0L
            val stale = now() - fetchedAt >= TTL_MILLIS
            // Both tables are needed, so a previous run that stored species but failed on
            // moves must not be treated as a complete cache.
            val complete = dao.speciesCount() > 0 && dao.moveCount() > 0
            if (!force && !stale && complete) return@withLock true

            val timestamp = now()
            // Sprite variant numbers are a nice-to-have: without them forms fall back to the
            // base icon, so a failure here must not fail the whole sync.
            val variants = runCatching {
                SpriteVariantIndex.from(service.getMasterfile(SpriteVariantIndex.MASTERFILE_URL))
            }.getOrNull()

            val speciesOk = runCatching { service.getPokemon() }
                .mapCatching { response ->
                    val rows = response.toEntities(timestamp, variants)
                    if (rows.isNotEmpty()) dao.replaceSpecies(rows)
                    rows.isNotEmpty()
                }
                .getOrDefault(false)

            val movesOk = runCatching { service.getMoves() }
                .mapCatching { response ->
                    val rows = response.move
                        .filter { it.moveId.isNotBlank() }
                        .map { it.toEntity(timestamp) }
                    if (rows.isNotEmpty()) dao.replaceMoves(rows)
                    rows.isNotEmpty()
                }
                .getOrDefault(false)

            // Both halves are required: the simulator cannot rank anything without moves.
            // A partial failure leaves whatever was cached before intact.
            (speciesOk && movesOk) || (dao.speciesCount() > 0 && dao.moveCount() > 0)
        }
    }

    /**
     * Every Pokebattler id the cache holds, for arbitrating guessed form spellings.
     *
     * Empty until the first sync, which callers must treat as "no opinion" rather than as
     * "that form does not exist".
     */
    suspend fun speciesIds(): Set<String> = withContext(Dispatchers.IO) {
        dao.allSpeciesIds().toSet()
    }

    suspend fun simSpecies(pokemonId: String): SimSpecies? = withContext(Dispatchers.IO) {
        dao.species(pokemonId)?.toSimSpecies()
    }

    suspend fun simSpecies(ids: Collection<String>): Map<String, SimSpecies> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            dao.species(ids.distinct()).associate { it.pokemonId to it.toSimSpecies() }
        }

    /** Legal fast and charged moves for a species, used when a scan has no moveset. */
    suspend fun legalMoves(pokemonId: String): Pair<List<String>, List<String>> =
        withContext(Dispatchers.IO) {
            val row = dao.species(pokemonId) ?: return@withContext emptyList<String>() to emptyList()
            row.quickMoves.splitMoves() to row.chargedMoves.splitMoves()
        }

    /** Legal fast and charged move ids for each of [ids]. */
    suspend fun legalMovesFor(ids: Collection<String>): Map<String, Pair<List<String>, List<String>>> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            dao.species(ids.distinct()).associate {
                it.pokemonId to (it.quickMoves.splitMoves() to it.chargedMoves.splitMoves())
            }
        }

    /**
     * Rarity and dex number per id, falling back to the base species.
     *
     * Shadow forms are not separate entries in `/pokemon`, so `PALKIA_SHADOW_FORM` has to be
     * looked up as `PALKIA`.
     */
    suspend fun speciesLookup(ids: Collection<String>): Map<String, SpeciesLookupRow> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            val wanted = ids.distinct()
            val bases = wanted.map { PokebattlerNameNormalizer.baseSpeciesId(it) }
            val rows = dao.speciesLookup((wanted + bases).distinct()).associateBy { it.pokemonId }
            wanted.mapNotNull { id ->
                val row = rows[id] ?: rows[PokebattlerNameNormalizer.baseSpeciesId(id)]
                row?.let { id to it }
            }.toMap()
        }

    /**
     * The one or two types per Pokebattler id, for tinting rows.
     *
     * Rides on [speciesLookup] so that shadow forms inherit the base species' types, and
     * costs one local query — the same one the sprite cascade already runs.
     */
    suspend fun typesFor(ids: Collection<String>): Map<String, List<String>> {
        val lookup = speciesLookup(ids)
        return lookup.mapValues { (_, row) -> listOfNotNull(row.type1, row.type2) }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * Move type keyed by both the raw move id and its prettified label.
     *
     * The counters payload persists move *names*, not ids, so a display label is the only
     * key the cached rows can be looked up by. Keying on both avoids a payload schema
     * change that every cached entry would have to be re-fetched to fill in.
     */
    suspend fun moveTypesByLabel(): Map<String, String> = withContext(Dispatchers.IO) {
        cachedMoveTypes ?: buildMap {
            dao.allMoves().forEach { move ->
                val type = move.type ?: return@forEach
                put(move.moveId.uppercase(java.util.Locale.ROOT), type)
                prettifyMoveName(move.moveId)?.let { put(it.uppercase(java.util.Locale.ROOT), type) }
            }
        }.also { cachedMoveTypes = it }
    }

    @Volatile
    private var cachedMoveTypes: Map<String, String>? = null

    /** Best-first sprite URLs per id, for the counters card. */
    suspend fun spriteUrls(ids: Collection<String>): Map<String, List<String>> {
        val lookup = speciesLookup(ids)
        return ids.distinct().mapNotNull { id ->
            val row = lookup[id]
            val urls = PokemonSpriteUrls.candidates(
                dexNumber = row?.dexNumber,
                pokemonId = id,
                formId = row?.formId,
                megaEvoId = row?.megaEvoId
            )
            urls.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
    }

    suspend fun allMoves(): Map<String, SimMove> = withContext(Dispatchers.IO) {
        dao.allMoves().associate { it.moveId to it.toSimMove() }
    }

    suspend fun raidTier(tier: String): PokebattlerRaidTierEntity? =
        withContext(Dispatchers.IO) { dao.raidTier(tier) }

    /** Stored by the counters repository when it refreshes the boss catalogue. */
    suspend fun saveRaidTiers(rows: List<PokebattlerRaidTierEntity>) = withContext(Dispatchers.IO) {
        if (rows.isNotEmpty()) dao.insertRaidTiers(rows)
    }

    companion object {
        private const val TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

        @Volatile
        private var INSTANCE: GameMasterRepository? = null

        fun getInstance(context: Context): GameMasterRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GameMasterRepository(
                    service = PokebattlerApi.service(context.applicationContext),
                    dao = AppDatabase.getDatabase(context.applicationContext).gameMasterDao()
                ).also { INSTANCE = it }
            }
        }
    }
}

private const val MOVE_SEPARATOR = "\n"

private fun String.splitMoves(): List<String> =
    split(MOVE_SEPARATOR).filter { it.isNotBlank() }

private fun PokebattlerSpeciesEntity.toSimSpecies() = SimSpecies(
    pokemonId = pokemonId,
    baseAttack = baseAttack,
    baseDefense = baseDefense,
    baseStamina = baseStamina,
    types = listOfNotNull(PokemonType.fromApiValue(type1), PokemonType.fromApiValue(type2))
)

private fun PokebattlerMoveEntity.toSimMove() = SimMove(
    moveId = moveId,
    type = PokemonType.fromApiValue(type),
    power = power,
    durationSeconds = durationMs / 1000.0,
    energyDelta = energyDelta,
    damageWindowStartSeconds = damageWindowStartMs / 1000.0,
    damageWindowEndSeconds = damageWindowEndMs / 1000.0
)

private fun PbMove.toEntity(timestamp: Long) = PokebattlerMoveEntity(
    moveId = moveId,
    type = type,
    power = power,
    durationMs = durationMs,
    energyDelta = energyDelta,
    damageWindowStartMs = damageWindowStartMs,
    damageWindowEndMs = damageWindowEndMs,
    fetchedAt = timestamp
)

/**
 * Flattens the response, emitting mega and primal forms as their own rows.
 *
 * A temporary evolution inherits its base form legal moves, which is correct in game: a
 * Mega Tyranitar knows whatever the Tyranitar knew.
 */
internal fun PokebattlerPokemonResponse.toEntities(
    timestamp: Long,
    variants: SpriteVariantIndex? = null
): List<PokebattlerSpeciesEntity> {
    val rows = mutableListOf<PokebattlerSpeciesEntity>()
    pokemon.forEach { species ->
        val stats = species.stats ?: return@forEach
        val quick = (species.quickMoves + species.eliteQuickMove).distinct().joinToString(MOVE_SEPARATOR)
        val charged = (species.cinematicMoves + species.eliteCinematicMove).distinct().joinToString(MOVE_SEPARATOR)

        rows += PokebattlerSpeciesEntity(
            pokemonId = species.pokemonId,
            type1 = species.type,
            type2 = species.type2,
            baseAttack = stats.baseAttack,
            baseDefense = stats.baseDefense,
            baseStamina = stats.baseStamina,
            quickMoves = quick,
            chargedMoves = charged,
            rarity = species.rarity,
            dexNumber = species.pokedex?.pokemonNum,
            formId = variants?.formId(
                species.pokedex?.pokemonNum,
                species.pokedex?.form,
                species.pokedex?.pokemonId
            ),
            fetchedAt = timestamp
        )

        species.temporaryEvolution.forEach { temp ->
            val id = temp.evolution ?: return@forEach
            val tempStats = temp.stats ?: return@forEach
            rows += PokebattlerSpeciesEntity(
                pokemonId = id,
                type1 = temp.type ?: species.type,
                type2 = temp.type2 ?: species.type2,
                baseAttack = tempStats.baseAttack,
                baseDefense = tempStats.baseDefense,
                baseStamina = tempStats.baseStamina,
                quickMoves = quick,
                chargedMoves = charged,
                // A mega has no pokedex block or rarity of its own, but it shares the
                // parent dex number for sprites and must still read as legendary for the
                // raid tier fallback.
                rarity = species.rarity,
                dexNumber = species.pokedex?.pokemonNum,
                formId = variants?.formId(
                    species.pokedex?.pokemonNum,
                    species.pokedex?.form,
                    species.pokedex?.pokemonId
                ),
                // Matched on base attack so Mega Charizard X and Y cannot be swapped.
                megaEvoId = variants?.megaEvoId(species.pokedex?.pokemonNum, tempStats.baseAttack),
                fetchedAt = timestamp
            )
        }
    }
    return rows.distinctBy { it.pokemonId }
}
