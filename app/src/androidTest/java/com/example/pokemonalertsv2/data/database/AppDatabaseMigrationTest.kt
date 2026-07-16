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

    private companion object {
        const val TEST_DATABASE = "weather-change-migration-test"
    }
}
