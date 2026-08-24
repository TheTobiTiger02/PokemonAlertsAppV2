package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RaidCounterDao {

    // ── Boss catalogue ──

    @Query("SELECT * FROM pokebattler_raid_bosses")
    suspend fun getCatalogue(): List<PokebattlerRaidBossEntity>

    @Query("SELECT MAX(fetchedAt) FROM pokebattler_raid_bosses")
    suspend fun catalogueFetchedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogue(entries: List<PokebattlerRaidBossEntity>)

    @Query("DELETE FROM pokebattler_raid_bosses")
    suspend fun clearCatalogue()

    @Transaction
    suspend fun replaceCatalogue(entries: List<PokebattlerRaidBossEntity>) {
        clearCatalogue()
        insertCatalogue(entries)
    }

    // ── Counters cache ──

    @Query("SELECT * FROM raid_counter_cache WHERE cacheKey = :cacheKey")
    suspend fun getCache(cacheKey: String): RaidCounterCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(entry: RaidCounterCacheEntity)

    @Query("DELETE FROM raid_counter_cache")
    suspend fun clearCache()

    /** Keeps the cache bounded; the newest [keep] entries survive. */
    @Query(
        "DELETE FROM raid_counter_cache WHERE cacheKey NOT IN " +
            "(SELECT cacheKey FROM raid_counter_cache ORDER BY fetchedAt DESC LIMIT :keep)"
    )
    suspend fun trimCache(keep: Int)

    @Transaction
    suspend fun saveCache(entry: RaidCounterCacheEntity) {
        upsertCache(entry)
        trimCache(MAX_CACHED_RESULTS)
    }

    companion object {
        /** Roughly 8 KB per row, so this bounds the cache at a few hundred KB. */
        const val MAX_CACHED_RESULTS = 60
    }
}

@Dao
interface PokeGenieDao {

    @Query("SELECT * FROM poke_genie_mons")
    suspend fun getAll(): List<PokeGenieMonEntity>

    @Query("SELECT COUNT(*) FROM poke_genie_mons")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mons: List<PokeGenieMonEntity>)

    @Query("DELETE FROM poke_genie_mons")
    suspend fun clear()

    /** An import always replaces the whole box, so re-importing cannot leave stale scans. */
    @Transaction
    suspend fun replaceAll(mons: List<PokeGenieMonEntity>) {
        clear()
        insertAll(mons)
    }
}
