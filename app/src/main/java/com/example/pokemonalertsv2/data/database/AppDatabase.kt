package com.example.pokemonalertsv2.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlertEntity::class, HistoryAlertEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun historyAlertDao(): HistoryAlertDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokemon_alerts_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
