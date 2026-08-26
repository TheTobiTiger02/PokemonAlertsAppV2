package com.example.pokemonalertsv2.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate12To13_preservesRowsAndDefaultsNewColumnsToNull() {
        helper.createDatabase(TEST_DATABASE, 12).apply {
            execSQL(
                """
                INSERT INTO alerts (
                    uniqueId, name, description, longitude, latitude,
                    endTime, createdAt
                ) VALUES (
                    'active-id', 'Zubat', 'Existing active alert', 8.62, 49.74,
                    '2026-07-16 20:00:00', 1234
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO history_alerts (
                    historyId, uniqueId, name, description, longitude, latitude,
                    endTime, cachedAt
                ) VALUES (
                    42, 'history-id', 'Pikachu', 'Existing history alert', 8.62, 49.74,
                    '2026-07-16 18:00:00', 5678
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            13,
            true,
            AppDatabase.MIGRATION_12_13
        ).apply {
            query(
                """
                SELECT name, weatherFrom, weatherTo, affectedAlertsJson,
                       invalidatedAt, invalidationReason, invalidatedByAlertId
                FROM alerts WHERE uniqueId = 'active-id'
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Zubat", cursor.getString(0))
                for (index in 1..6) {
                    assertNull(cursor.getString(index))
                }
            }
            query(
                """
                SELECT name, weatherFrom, weatherTo, affectedAlertsJson,
                       invalidatedAt, invalidationReason, invalidatedByAlertId
                FROM history_alerts WHERE historyId = 42
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Pikachu", cursor.getString(0))
                for (index in 1..6) {
                    assertNull(cursor.getString(index))
                }
            }
            close()
        }
    }

    @Test
    fun migrate21To22_addsTimingAndMovesetColumnsAndClearsDerivedRows() {
        helper.createDatabase(TEST_DATABASE, 21).apply {
            execSQL(
                """
                INSERT INTO pokebattler_moves
                    (moveId, type, power, durationMs, energyDelta, fetchedAt)
                VALUES ('TACKLE_FAST', 'NORMAL', 5.0, 500, 5, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO raid_counter_cache
                    (cacheKey, bossPokemonId, raidLevel, bossCp, bossMove1,
                     bossMove2, countersJson, fetchedAt)
                VALUES ('old', 'MEWTWO', 'RAID_LEVEL_5', 1000, 'CONFUSION_FAST',
                        'FOCUS_BLAST', '[]', 1)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            22,
            true,
            AppDatabase.MIGRATION_21_22
        ).apply {
            query("PRAGMA table_info(pokebattler_moves)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertEquals(true, "damageWindowStartMs" in columns)
                assertEquals(true, "damageWindowEndMs" in columns)
            }
            query("PRAGMA table_info(raid_counter_cache)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertEquals(true, "availableBossMovesetsJson" in columns)
            }
            query("SELECT COUNT(*) FROM pokebattler_moves").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM raid_counter_cache").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate13To14_createsGoDexCacheTableAndIndex() {
        helper.createDatabase(TEST_DATABASE, 13).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            14,
            true,
            AppDatabase.MIGRATION_13_14
        ).apply {
            execSQL(
                """
                INSERT INTO godex_entries
                    (entryKey, pokedexId, formSlug, gender, displayName, needed)
                VALUES ('0026_alola-female', 26, 'alola', 'female', 'Raichu', 1)
                """.trimIndent()
            )
            query("SELECT pokedexId, formSlug, gender, needed FROM godex_entries").use { cursor ->
                cursor.moveToFirst()
                assertEquals(26, cursor.getInt(0))
                assertEquals("alola", cursor.getString(1))
                assertEquals("female", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }
            close()
        }
    }

    @Test
    fun migrate15To16_coalescesPendingRowsToLatestDesiredState() {
        helper.createDatabase(TEST_DATABASE, 15).apply {
            execSQL(
                "INSERT INTO godex_pending_updates (entryKey, caught, timestamp) VALUES ('0025-none', 1, 100)"
            )
            execSQL(
                "INSERT INTO godex_pending_updates (entryKey, caught, timestamp) VALUES ('0025-none', 0, 200)"
            )
            execSQL(
                "INSERT INTO godex_pending_updates (entryKey, caught, timestamp) VALUES ('0026_alola-female', 1, 150)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            16,
            true,
            AppDatabase.MIGRATION_15_16
        ).apply {
            query(
                """
                SELECT entryKey, caught, revision, timestamp, attemptCount, lastError
                FROM godex_pending_updates
                ORDER BY entryKey
                """.trimIndent()
            ).use { cursor ->
                assertEquals(2, cursor.count)
                cursor.moveToFirst()
                assertEquals("0025-none", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(200L, cursor.getLong(2))
                assertEquals(200L, cursor.getLong(3))
                assertEquals(0, cursor.getInt(4))
                assertNull(cursor.getString(5))
                cursor.moveToNext()
                assertEquals("0026_alola-female", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            close()
        }
    }

    @Test
    fun migrate16To17_preservesGoDexEntriesAndPendingUpdates() {
        helper.createDatabase(TEST_DATABASE, 16).apply {
            execSQL(
                """
                INSERT INTO godex_entries
                    (entryKey, pokedexId, formSlug, gender, displayName, needed)
                VALUES ('0026_alola-female', 26, 'alola', 'female', 'Raichu', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO godex_pending_updates
                    (entryKey, caught, revision, timestamp, attemptCount, lastError)
                VALUES ('0026_alola-female', 1, 123, 120, 2, 'offline')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            17,
            true,
            AppDatabase.MIGRATION_16_17
        ).apply {
            query(
                """
                SELECT pokedexId, formSlug, gender, needed, spriteUrl
                FROM godex_entries WHERE entryKey = '0026_alola-female'
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(26, cursor.getInt(0))
                assertEquals("alola", cursor.getString(1))
                assertEquals("female", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
                assertNull(cursor.getString(4))
            }
            query(
                """
                SELECT caught, revision, timestamp, attemptCount, lastError
                FROM godex_pending_updates WHERE entryKey = '0026_alola-female'
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals(123L, cursor.getLong(1))
                assertEquals(120L, cursor.getLong(2))
                assertEquals(2, cursor.getInt(3))
                assertEquals("offline", cursor.getString(4))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "weather-change-migration-test"
    }
}
