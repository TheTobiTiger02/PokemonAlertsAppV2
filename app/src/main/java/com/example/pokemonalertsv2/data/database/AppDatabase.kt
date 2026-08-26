package com.example.pokemonalertsv2.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlertEntity::class,
        HistoryAlertEntity::class,
        PokemonSpeciesEntity::class,
        GoDexEntryEntity::class,
        GoDexPendingUpdateEntity::class,
        PokebattlerRaidBossEntity::class,
        RaidCounterCacheEntity::class,
        PokeGenieMonEntity::class,
        PokebattlerSpeciesEntity::class,
        PokebattlerMoveEntity::class,
        PokebattlerRaidTierEntity::class
    ],
    version = 22,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun historyAlertDao(): HistoryAlertDao
    abstract fun pokemonSpeciesDao(): PokemonSpeciesDao
    abstract fun goDexEntryDao(): GoDexEntryDao
    abstract fun raidCounterDao(): RaidCounterDao
    abstract fun pokeGenieDao(): PokeGenieDao
    abstract fun gameMasterDao(): GameMasterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Migration 4 → 5: adds pokemonRewardsJson column to both tables. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN pokemonRewardsJson TEXT")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN pokemonRewardsJson TEXT")
            }
        }

        /** Migration 5 → 6: converts level column from INTEGER to REAL. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── alerts table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS alerts_new (
                        uniqueId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        imageUrl TEXT,
                        longitude REAL NOT NULL,
                        latitude REAL NOT NULL,
                        endTime TEXT NOT NULL,
                        type TEXT,
                        thumbnailUrl TEXT,
                        createdAt INTEGER NOT NULL,
                        pokemon TEXT,
                        pokemonForm TEXT,
                        pokedexId INTEGER,
                        iv TEXT,
                        ivAttack INTEGER,
                        ivDefense INTEGER,
                        ivStamina INTEGER,
                        gender TEXT,
                        isShiny INTEGER,
                        cp INTEGER,
                        level REAL,
                        isWeatherBoosted INTEGER,
                        currentWeather TEXT,
                        pokemonLocation TEXT,
                        gym TEXT,
                        pokestop TEXT,
                        movesFast TEXT,
                        movesCharged TEXT,
                        hundoCPL20 INTEGER,
                        hundoCPL25 INTEGER,
                        pvpRankingsJson TEXT,
                        gruntType TEXT,
                        pokemonRewardsJson TEXT,
                        questTask TEXT,
                        questReward TEXT,
                        requiresAR INTEGER,
                        alertCreatedAt TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO alerts_new (
                        uniqueId, name, description, imageUrl, longitude, latitude,
                        endTime, type, thumbnailUrl, createdAt, pokemon, pokemonForm,
                        pokedexId, iv, ivAttack, ivDefense, ivStamina, gender, isShiny,
                        cp, level, isWeatherBoosted, currentWeather, pokemonLocation,
                        gym, pokestop, movesFast, movesCharged, hundoCPL20, hundoCPL25,
                        pvpRankingsJson, gruntType, pokemonRewardsJson, questTask,
                        questReward, requiresAR, alertCreatedAt
                    ) SELECT
                        uniqueId, name, description, imageUrl, longitude, latitude,
                        endTime, type, thumbnailUrl, createdAt, pokemon, pokemonForm,
                        pokedexId, iv, ivAttack, ivDefense, ivStamina, gender, isShiny,
                        cp, CAST(level AS REAL), isWeatherBoosted, currentWeather,
                        pokemonLocation, gym, pokestop, movesFast, movesCharged,
                        hundoCPL20, hundoCPL25, pvpRankingsJson, gruntType,
                        pokemonRewardsJson, questTask, questReward, requiresAR,
                        alertCreatedAt
                    FROM alerts
                """.trimIndent())
                db.execSQL("DROP TABLE alerts")
                db.execSQL("ALTER TABLE alerts_new RENAME TO alerts")

                // ── history_alerts table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS history_alerts_new (
                        historyId INTEGER NOT NULL PRIMARY KEY,
                        uniqueId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        imageUrl TEXT,
                        longitude REAL NOT NULL,
                        latitude REAL NOT NULL,
                        endTime TEXT NOT NULL,
                        type TEXT,
                        thumbnailUrl TEXT,
                        cachedAt INTEGER NOT NULL,
                        pokemon TEXT,
                        pokemonForm TEXT,
                        pokedexId INTEGER,
                        iv TEXT,
                        ivAttack INTEGER,
                        ivDefense INTEGER,
                        ivStamina INTEGER,
                        gender TEXT,
                        isShiny INTEGER,
                        cp INTEGER,
                        level REAL,
                        isWeatherBoosted INTEGER,
                        currentWeather TEXT,
                        pokemonLocation TEXT,
                        gym TEXT,
                        pokestop TEXT,
                        movesFast TEXT,
                        movesCharged TEXT,
                        hundoCPL20 INTEGER,
                        hundoCPL25 INTEGER,
                        pvpRankingsJson TEXT,
                        gruntType TEXT,
                        pokemonRewardsJson TEXT,
                        questTask TEXT,
                        questReward TEXT,
                        requiresAR INTEGER,
                        alertCreatedAt TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO history_alerts_new (
                        historyId, uniqueId, name, description, imageUrl, longitude,
                        latitude, endTime, type, thumbnailUrl, cachedAt, pokemon,
                        pokemonForm, pokedexId, iv, ivAttack, ivDefense, ivStamina,
                        gender, isShiny, cp, level, isWeatherBoosted, currentWeather,
                        pokemonLocation, gym, pokestop, movesFast, movesCharged,
                        hundoCPL20, hundoCPL25, pvpRankingsJson, gruntType,
                        pokemonRewardsJson, questTask, questReward, requiresAR,
                        alertCreatedAt
                    ) SELECT
                        historyId, uniqueId, name, description, imageUrl, longitude,
                        latitude, endTime, type, thumbnailUrl, cachedAt, pokemon,
                        pokemonForm, pokedexId, iv, ivAttack, ivDefense, ivStamina,
                        gender, isShiny, cp, CAST(level AS REAL), isWeatherBoosted,
                        currentWeather, pokemonLocation, gym, pokestop, movesFast,
                        movesCharged, hundoCPL20, hundoCPL25, pvpRankingsJson,
                        gruntType, pokemonRewardsJson, questTask, questReward,
                        requiresAR, alertCreatedAt
                    FROM history_alerts
                """.trimIndent())
                db.execSQL("DROP TABLE history_alerts")
                db.execSQL("ALTER TABLE history_alerts_new RENAME TO history_alerts")
            }
        }

        /** Migration 3 → 4: adds the history_alerts cache table. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `history_alerts` (
                        `historyId` INTEGER NOT NULL,
                        `uniqueId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `imageUrl` TEXT,
                        `longitude` REAL NOT NULL,
                        `latitude` REAL NOT NULL,
                        `endTime` TEXT NOT NULL,
                        `type` TEXT,
                        `thumbnailUrl` TEXT,
                        `cachedAt` INTEGER NOT NULL,
                        `pokemon` TEXT,
                        `pokemonForm` TEXT,
                        `pokedexId` INTEGER,
                        `iv` TEXT,
                        `ivAttack` INTEGER,
                        `ivDefense` INTEGER,
                        `ivStamina` INTEGER,
                        `gender` TEXT,
                        `isShiny` INTEGER,
                        `cp` INTEGER,
                        `level` INTEGER,
                        `isWeatherBoosted` INTEGER,
                        `currentWeather` TEXT,
                        `pokemonLocation` TEXT,
                        `gym` TEXT,
                        `pokestop` TEXT,
                        `movesFast` TEXT,
                        `movesCharged` TEXT,
                        `hundoCPL20` INTEGER,
                        `hundoCPL25` INTEGER,
                        `pvpRankingsJson` TEXT,
                        `gruntType` TEXT,
                        `questTask` TEXT,
                        `questReward` TEXT,
                        `requiresAR` INTEGER,
                        `alertCreatedAt` TEXT,
                        PRIMARY KEY(`historyId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration 6 → 7: rebuilds both tables to clean up the stale
         * level_old column left behind by the original (broken) 5→6 migration.
         * Devices that never saw v6 will go 5→6→7 (6→7 is a no-op rebuild).
         * Devices stuck on the broken v6 get their schema fixed here.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Determine whether the old broken migration left a level_old column
                val needsRebuild = db.query("PRAGMA table_info(alerts)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    generateSequence { if (cursor.moveToNext()) cursor.getString(nameIdx) else null }
                        .any { it == "level_old" }
                }
                if (!needsRebuild) return // clean v6 schema, nothing to do

                // ── alerts ──
                db.execSQL("""CREATE TABLE IF NOT EXISTS alerts_new (
                    uniqueId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
                    description TEXT NOT NULL, imageUrl TEXT,
                    longitude REAL NOT NULL, latitude REAL NOT NULL,
                    endTime TEXT NOT NULL, type TEXT, thumbnailUrl TEXT,
                    createdAt INTEGER NOT NULL, pokemon TEXT, pokemonForm TEXT,
                    pokedexId INTEGER, iv TEXT, ivAttack INTEGER,
                    ivDefense INTEGER, ivStamina INTEGER, gender TEXT,
                    isShiny INTEGER, cp INTEGER, level REAL,
                    isWeatherBoosted INTEGER, currentWeather TEXT,
                    pokemonLocation TEXT, gym TEXT, pokestop TEXT,
                    movesFast TEXT, movesCharged TEXT, hundoCPL20 INTEGER,
                    hundoCPL25 INTEGER, pvpRankingsJson TEXT, gruntType TEXT,
                    pokemonRewardsJson TEXT, questTask TEXT, questReward TEXT,
                    requiresAR INTEGER, alertCreatedAt TEXT
                )""")
                db.execSQL("""INSERT INTO alerts_new SELECT
                    uniqueId, name, description, imageUrl, longitude, latitude,
                    endTime, type, thumbnailUrl, createdAt, pokemon, pokemonForm,
                    pokedexId, iv, ivAttack, ivDefense, ivStamina, gender, isShiny,
                    cp, level, isWeatherBoosted, currentWeather, pokemonLocation,
                    gym, pokestop, movesFast, movesCharged, hundoCPL20, hundoCPL25,
                    pvpRankingsJson, gruntType, pokemonRewardsJson, questTask,
                    questReward, requiresAR, alertCreatedAt
                FROM alerts""")
                db.execSQL("DROP TABLE alerts")
                db.execSQL("ALTER TABLE alerts_new RENAME TO alerts")

                // ── history_alerts ──
                db.execSQL("""CREATE TABLE IF NOT EXISTS history_alerts_new (
                    historyId INTEGER NOT NULL PRIMARY KEY, uniqueId TEXT NOT NULL,
                    name TEXT NOT NULL, description TEXT NOT NULL, imageUrl TEXT,
                    longitude REAL NOT NULL, latitude REAL NOT NULL,
                    endTime TEXT NOT NULL, type TEXT, thumbnailUrl TEXT,
                    cachedAt INTEGER NOT NULL, pokemon TEXT, pokemonForm TEXT,
                    pokedexId INTEGER, iv TEXT, ivAttack INTEGER,
                    ivDefense INTEGER, ivStamina INTEGER, gender TEXT,
                    isShiny INTEGER, cp INTEGER, level REAL,
                    isWeatherBoosted INTEGER, currentWeather TEXT,
                    pokemonLocation TEXT, gym TEXT, pokestop TEXT,
                    movesFast TEXT, movesCharged TEXT, hundoCPL20 INTEGER,
                    hundoCPL25 INTEGER, pvpRankingsJson TEXT, gruntType TEXT,
                    pokemonRewardsJson TEXT, questTask TEXT, questReward TEXT,
                    requiresAR INTEGER, alertCreatedAt TEXT
                )""")
                db.execSQL("""INSERT INTO history_alerts_new SELECT
                    historyId, uniqueId, name, description, imageUrl, longitude,
                    latitude, endTime, type, thumbnailUrl, cachedAt, pokemon,
                    pokemonForm, pokedexId, iv, ivAttack, ivDefense, ivStamina,
                    gender, isShiny, cp, level, isWeatherBoosted, currentWeather,
                    pokemonLocation, gym, pokestop, movesFast, movesCharged,
                    hundoCPL20, hundoCPL25, pvpRankingsJson, gruntType,
                    pokemonRewardsJson, questTask, questReward, requiresAR,
                    alertCreatedAt
                FROM history_alerts""")
                db.execSQL("DROP TABLE history_alerts")
                db.execSQL("ALTER TABLE history_alerts_new RENAME TO history_alerts")
            }
        }

        /** Migration 7 → 8: adds newCp and newIv columns for WeatherChange alerts. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN newCp INTEGER")
                db.execSQL("ALTER TABLE alerts ADD COLUMN newIv TEXT")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN newCp INTEGER")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN newIv TEXT")
            }
        }

        /** Migration 8 → 9: adds species replacement columns. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN oldSpecies TEXT")
                db.execSQL("ALTER TABLE alerts ADD COLUMN oldIv TEXT")
                db.execSQL("ALTER TABLE alerts ADD COLUMN oldCp INTEGER")
                db.execSQL("ALTER TABLE alerts ADD COLUMN newSpecies TEXT")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN oldSpecies TEXT")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN oldIv TEXT")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN oldCp INTEGER")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN newSpecies TEXT")
            }
        }

        /** Migration 9 → 10: adds area column. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN area TEXT")
                db.execSQL("ALTER TABLE history_alerts ADD COLUMN area TEXT")
            }
        }

        /** Migration 10 → 11: adds pokemon species table. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pokemon_species` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** Migration 11 -> 12: adds indexes for common list ordering and filters. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createPerformanceIndexes(db)
            }
        }

        /** Migration 12 -> 13: adds structured weather-change and invalidation metadata. */
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("alerts", "history_alerts").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN weatherFrom TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN weatherTo TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN affectedAlertsJson TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN invalidatedAt TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN invalidationReason TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN invalidatedByAlertId INTEGER")
                }
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `godex_entries` (`entryKey` TEXT NOT NULL, `pokedexId` INTEGER NOT NULL, `formSlug` TEXT, `gender` TEXT NOT NULL, `displayName` TEXT NOT NULL, `needed` INTEGER NOT NULL, PRIMARY KEY(`entryKey`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_godex_entries_pokedexId` ON `godex_entries` (`pokedexId`)")
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `godex_pending_updates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryKey` TEXT NOT NULL, `caught` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `godex_pending_updates_new` (
                        `entryKey` TEXT NOT NULL,
                        `caught` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `lastError` TEXT,
                        PRIMARY KEY(`entryKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `godex_pending_updates_new`
                        (`entryKey`, `caught`, `revision`, `timestamp`, `attemptCount`, `lastError`)
                    SELECT pending.`entryKey`, pending.`caught`,
                           CASE WHEN pending.`timestamp` > 0 THEN pending.`timestamp` ELSE pending.`id` END,
                           pending.`timestamp`, 0, NULL
                    FROM `godex_pending_updates` AS pending
                    WHERE pending.`id` = (
                        SELECT newest.`id`
                        FROM `godex_pending_updates` AS newest
                        WHERE newest.`entryKey` = pending.`entryKey`
                        ORDER BY newest.`timestamp` DESC, newest.`id` DESC
                        LIMIT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `godex_pending_updates`")
                db.execSQL("ALTER TABLE `godex_pending_updates_new` RENAME TO `godex_pending_updates`")
            }
        }

        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `godex_entries` ADD COLUMN `spriteUrl` TEXT")
            }
        }

        /** Migration 17 -> 18: adds the raid counters cache and the Poke Genie box. */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pokebattler_raid_bosses` (
                        `tier` TEXT NOT NULL,
                        `pokemonId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `cp` INTEGER,
                        `shiny` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tier`, `pokemonId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pokebattler_raid_bosses_pokemonId` " +
                        "ON `pokebattler_raid_bosses` (`pokemonId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `raid_counter_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `bossPokemonId` TEXT NOT NULL,
                        `raidLevel` TEXT NOT NULL,
                        `bossCp` INTEGER,
                        `bossMove1` TEXT,
                        `bossMove2` TEXT,
                        `countersJson` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `poke_genie_mons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `scanIndex` INTEGER,
                        `displayName` TEXT NOT NULL,
                        `form` TEXT,
                        `pokedexNumber` INTEGER,
                        `matchKey` TEXT NOT NULL,
                        `altMatchKeys` TEXT NOT NULL,
                        `cp` INTEGER,
                        `hp` INTEGER,
                        `atkIv` INTEGER,
                        `defIv` INTEGER,
                        `staIv` INTEGER,
                        `levelMin` REAL,
                        `levelMax` REAL,
                        `level` REAL,
                        `quickMove` TEXT,
                        `chargeMove` TEXT,
                        `chargeMove2` TEXT,
                        `gender` TEXT,
                        `shadowState` TEXT NOT NULL,
                        `lucky` INTEGER NOT NULL,
                        `favorite` INTEGER NOT NULL,
                        `importedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_poke_genie_mons_matchKey` " +
                        "ON `poke_genie_mons` (`matchKey`)"
                )
            }
        }

        /** Migration 18 -> 19: adds the game master tables used by the local raid simulator. */
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pokebattler_species` (
                        `pokemonId` TEXT NOT NULL,
                        `type1` TEXT,
                        `type2` TEXT,
                        `baseAttack` INTEGER NOT NULL,
                        `baseDefense` INTEGER NOT NULL,
                        `baseStamina` INTEGER NOT NULL,
                        `quickMoves` TEXT NOT NULL,
                        `chargedMoves` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`pokemonId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pokebattler_moves` (
                        `moveId` TEXT NOT NULL,
                        `type` TEXT,
                        `power` REAL NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `energyDelta` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`moveId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pokebattler_raid_tiers` (
                        `tier` TEXT NOT NULL,
                        `hp` INTEGER NOT NULL,
                        `cpm` REAL NOT NULL,
                        `combatTimeMs` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tier`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** Migration 19 -> 20: species rarity and dex number, for tier fallback and sprites. */
        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pokebattler_species` ADD COLUMN `rarity` TEXT")
                db.execSQL("ALTER TABLE `pokebattler_species` ADD COLUMN `dexNumber` INTEGER")
                // Existing rows predate both columns. Zeroing the timestamp makes the TTL
                // read as expired so the next card open backfills them.
                db.execSQL("UPDATE `pokebattler_species` SET `fetchedAt` = 0")
                // Every cached counters payload was stored worst-first by the old mapper.
                db.execSQL("DELETE FROM `raid_counter_cache`")
            }
        }

        /** Migration 20 -> 21: sprite variant numbers, so forms get their own icon. */
        internal val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pokebattler_species` ADD COLUMN `formId` INTEGER")
                db.execSQL("ALTER TABLE `pokebattler_species` ADD COLUMN `megaEvoId` INTEGER")
                // Existing rows predate both columns; expire them so the next sync backfills.
                db.execSQL("UPDATE `pokebattler_species` SET `fetchedAt` = 0")
            }
        }

        /** Migration 21 -> 22: move timing and boss-moveset-aware counter caches. */
        internal val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pokebattler_moves` ADD COLUMN `damageWindowStartMs` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `pokebattler_moves` ADD COLUMN `damageWindowEndMs` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `raid_counter_cache` ADD COLUMN `availableBossMovesetsJson` TEXT NOT NULL DEFAULT '[]'"
                )
                // The mapper and cache identity changed; these are disposable derived rows.
                db.execSQL("DELETE FROM `raid_counter_cache`")
                // Force the next game-master sync to refill the new timing fields.
                db.execSQL("DELETE FROM `pokebattler_moves`")
            }
        }

        private fun createPerformanceIndexes(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_endTime` ON `alerts` (`endTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_type` ON `alerts` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_area` ON `alerts` (`area`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_alerts_endTime` ON `history_alerts` (`endTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_alerts_type` ON `history_alerts` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_alerts_area` ON `history_alerts` (`area`)")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokemon_alerts_database"
                )
                .addMigrations(
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22
                )
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
