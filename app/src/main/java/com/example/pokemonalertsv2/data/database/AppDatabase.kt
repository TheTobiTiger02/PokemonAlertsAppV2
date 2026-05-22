package com.example.pokemonalertsv2.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlertEntity::class, HistoryAlertEntity::class, PokemonSpeciesEntity::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun historyAlertDao(): HistoryAlertDao
    abstract fun pokemonSpeciesDao(): PokemonSpeciesDao

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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
