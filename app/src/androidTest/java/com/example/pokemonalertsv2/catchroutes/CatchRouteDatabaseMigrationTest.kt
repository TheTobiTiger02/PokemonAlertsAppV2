package com.example.pokemonalertsv2.catchroutes

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatchRouteDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CatchRouteDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate1To2_keepsSetupsAndTheRunningSessionAndAddsAreasAndHistory() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL("INSERT INTO setups (id, name, settings, updatedAt) VALUES ('s', 'Evening loop', '{}', 1)")
            execSQL("INSERT INTO active_session (id, payload) VALUES (1, '{\"walking\":true}')")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, CATCH_ROUTES_MIGRATION_1_2).apply {
            query("SELECT name FROM setups").use { it.moveToFirst(); assertEquals("Evening loop", it.getString(0)) }
            query("SELECT payload FROM active_session").use { it.moveToFirst(); assertEquals("{\"walking\":true}", it.getString(0)) }
            execSQL("INSERT INTO areas (id, name, corners, updatedAt) VALUES ('a', 'Alsbach', '[]', 2)")
            execSQL("INSERT INTO history (id, endedAt, payload) VALUES ('h', 3, '{}')")
            query("SELECT COUNT(*) FROM areas").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "catch-routes-migration-test"
    }
}
